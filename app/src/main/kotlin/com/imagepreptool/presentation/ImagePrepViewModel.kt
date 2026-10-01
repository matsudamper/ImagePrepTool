package com.imagepreptool.presentation

import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import com.imagepreptool.data.PreferencesSettingsStore
import com.imagepreptool.data.SettingsStore
import com.imagepreptool.model.CaptionField
import com.imagepreptool.model.ConflictPolicy
import com.imagepreptool.model.CropRect
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.ExternalTools
import com.imagepreptool.model.ImageSize
import com.imagepreptool.model.OutputFormat
import com.imagepreptool.model.ProcessResult
import com.imagepreptool.service.CaptionRenderer
import com.imagepreptool.service.CaptionTemplate
import com.imagepreptool.service.Cropper
import com.imagepreptool.service.ExifService
import com.imagepreptool.service.ExternalToolChecker
import com.imagepreptool.service.ImageEncoder
import com.imagepreptool.service.ImageLoader
import com.imagepreptool.service.ImageProcessor
import com.imagepreptool.service.LoadedImage
import com.imagepreptool.service.OutputPlanner
import com.imagepreptool.service.PlannedOutput
import com.imagepreptool.service.Resizer

class ImagePrepViewModel(
    private val settings: SettingsStore = PreferencesSettingsStore(),
    private val checkTools: () -> ExternalTools = ExternalToolChecker::checkAll,
) : ViewModel() {

    private val viewModelStateFlow = MutableStateFlow(
        ImagePrepViewModelState(
            options = settings.loadOptions(),
            customOutputDir = settings.loadCustomOutputDir(),
            recentFolders = settings.loadRecentFolders(),
        ),
    )

    private val listener = object : ImagePrepUiState.Listener {
        override fun openFolder(dir: File) = this@ImagePrepViewModel.openFolder(dir)
        override fun addFiles(files: List<File>) = this@ImagePrepViewModel.addFiles(files)
        override fun forgetRecent(dir: File) = this@ImagePrepViewModel.forgetRecent(dir)
        override fun closeAll() = this@ImagePrepViewModel.closeAll()
        override fun clickImage(file: File, mode: SelectMode) = this@ImagePrepViewModel.clickImage(file, mode)
        override fun selectAll() = this@ImagePrepViewModel.selectAll()
        override fun clearSelection() = this@ImagePrepViewModel.clearSelection()
        override fun moveFocus(delta: Int) = this@ImagePrepViewModel.moveFocus(delta)
        override fun removeImage(file: File) = this@ImagePrepViewModel.removeImage(file)
        override fun removeSelection() = this@ImagePrepViewModel.removeSelection()
        override fun removeFolder(folder: File) = this@ImagePrepViewModel.removeFolder(folder)
        override fun removeUnreadable() = this@ImagePrepViewModel.removeUnreadable()
        override fun undoRemoval() = this@ImagePrepViewModel.undoRemoval()
        override fun updateOptions(transform: (EditOptions) -> EditOptions) = this@ImagePrepViewModel.updateOptions(transform)
        override fun setInputValid(field: String, valid: Boolean) = this@ImagePrepViewModel.setInputValid(field, valid)
        override fun setCrop(file: File, crop: CropRect?) = this@ImagePrepViewModel.setCrop(file, crop)
        override fun chooseOutputDirectory(dir: File) = this@ImagePrepViewModel.chooseOutputDirectory(dir)
        override fun resetOutputDirectory() = this@ImagePrepViewModel.resetOutputDirectory()
        override fun requestExport() = this@ImagePrepViewModel.requestExport()
        override fun resolveConflicts(policy: ConflictPolicy?) = this@ImagePrepViewModel.resolveConflicts(policy)
        override fun cancelExport() = this@ImagePrepViewModel.cancelExport()
        override fun dismissExport() = this@ImagePrepViewModel.dismissExport()
    }

    val uiStateFlow: StateFlow<ImagePrepUiState> =
        MutableStateFlow(viewModelStateFlow.value.toUiState(listener)).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.value = viewModelState.toUiState(listener)
                }
            }
        }.asStateFlow()

    private val messageChannel = Channel<SnackbarMessage>(Channel.BUFFERED)

    val messages: Flow<SnackbarMessage> = messageChannel.receiveAsFlow()

    private var exportJob: Job? = null
    private var loadJob: Job? = null

    /** 読み込みを始めるたびに増やす。取り消された古い読み込みが新しい読み込み中表示を消さないようにする */
    private var loadGeneration = 0
    private var pendingExportSettings: Pair<EditOptions, ExternalTools?>? = null
    private val previewCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, PreviewSource>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PreviewSource>?) = size > 4
        },
    )

    init {
        refreshTools()
        observePreview()
        viewModelScope.launch {
            viewModelStateFlow.map { it.options }.distinctUntilChanged().drop(1).collect(settings::saveOptions)
        }
    }

    private fun mutate(transform: (ImagePrepViewModelState) -> ImagePrepViewModelState) {
        viewModelStateFlow.update(transform)
    }

    // region 画像の読み込み

    private fun openFolder(dir: File) {
        if (rejectWhileExporting()) return
        // 後から開いたフォルダを優先し、前の読み込み結果で上書きしない
        loadJob?.cancel()
        val generation = ++loadGeneration
        loadJob = viewModelScope.launch {
            mutate { it.copy(isLoading = true) }
            val files = try {
                runInterruptible(Dispatchers.IO) { listImages(dir) }
            } finally {
                finishLoading(generation)
            }
            if (files.isEmpty()) {
                messageChannel.send(SnackbarMessage("「${dir.name}」に読み込める画像がありません"))
                return@launch
            }
            mutate { state ->
                val existing = state.images.map { it.file.absoluteFile }.toSet()
                val added = files.map { it.absoluteFile }.filter { it !in existing }
                if (state.images.isEmpty()) {
                    state.copy(
                        images = added.map(::ImageItem),
                        isWorkspaceOpen = true,
                        focusedFile = added.firstOrNull(),
                        selection = added.take(1).toSet(),
                        anchor = added.firstOrNull(),
                        isSelectionMode = false,
                        lastRemoval = null,
                        removedFiles = emptySet(),
                    )
                } else {
                    state.copy(
                        images = (state.images + added.map(::ImageItem)).groupedByFolder(),
                        lastRemoval = null,
                    )
                }
            }
            rememberRecent(dir)
        }
    }

    /** ドロップやファイル選択で追加する。フォルダが含まれていれば中の画像を追加する */
    private fun addFiles(files: List<File>) {
        if (rejectWhileExporting()) return
        if (files.size == 1 && files.single().isDirectory && viewModelStateFlow.value.images.isEmpty()) {
            openFolder(files.single())
            return
        }
        loadJob?.cancel()
        val generation = ++loadGeneration
        loadJob = viewModelScope.launch {
            mutate { it.copy(isLoading = true) }
            val expanded = try {
                runInterruptible(Dispatchers.IO) {
                    files.flatMap { if (it.isDirectory) listImages(it) else listOf(it) }
                }
            } finally {
                finishLoading(generation)
            }
            val supported = expanded.filter { it.isFile && ImageLoader.isSupported(it) }
            val ignored = expanded.size - supported.size
            val existing = viewModelStateFlow.value.images.map { it.file.absoluteFile }.toSet()
            val added = supported.map { it.absoluteFile }.distinct().filter { it !in existing }
            mutate { state ->
                state.copy(
                    images = (state.images + added.map(::ImageItem)).groupedByFolder(),
                    isWorkspaceOpen = state.isWorkspaceOpen || added.isNotEmpty(),
                    focusedFile = state.focusedFile ?: added.firstOrNull(),
                )
            }
            val message = buildList {
                if (added.isNotEmpty()) add("${added.size} 枚を追加しました")
                if (supported.size > added.size) add("${supported.size - added.size} 枚は追加済みです")
                if (ignored > 0) add("非対応の $ignored 件を除外しました")
            }.ifEmpty { listOf("追加できる画像がありません") }
            messageChannel.send(SnackbarMessage(message.joinToString("・")))
        }
    }

    private fun finishLoading(generation: Int) {
        if (generation == loadGeneration) mutate { it.copy(isLoading = false) }
    }

    /** 書き出しの準備中・実行中は一覧を差し替えない（表示と書き出し対象がずれるのを防ぐ） */
    private fun rejectWhileExporting(): Boolean {
        val export = viewModelStateFlow.value.export
        val busy = export is ExportState.Preparing || export is ExportState.Running || export is ExportState.ConfirmConflicts
        if (busy) messageChannel.trySend(SnackbarMessage("書き出しが終わってから画像を読み込んでください"))
        return busy
    }

    internal fun snapshotForTest(): ImagePrepUiState = viewModelStateFlow.value.toUiState(listener)

    internal fun addFilesForTest(files: List<File>) {
        mutate { it.copy(images = files.map(::ImageItem), isWorkspaceOpen = true, focusedFile = files.firstOrNull()) }
    }

    internal fun setToolsForTest(tools: ExternalTools) {
        mutate { it.copy(tools = tools) }
    }

    private fun closeAll() {
        // 書き出しの準備中・実行中は閉じない（閉じた画像が書き出されるのを防ぐ）
        val export = viewModelStateFlow.value.export
        if (export is ExportState.Preparing || export is ExportState.Running || exportJob?.isActive == true) return
        loadJob?.cancel()
        mutate {
            it.copy(
                images = emptyList(),
                isWorkspaceOpen = false,
                focusedFile = null,
                selection = emptySet(),
                anchor = null,
                isSelectionMode = false,
                export = ExportState.Idle,
                lastRemoval = null,
                removedFiles = emptySet(),
                crops = mapOf(),
            )
        }
    }

    /** [file] が複数選択に含まれていれば選択中の全画像を一覧から削除する */
    private fun removeImage(file: File) {
        removeImages(viewModelStateFlow.value.targetsFor(file))
    }

    private fun removeSelection() {
        removeImages(viewModelStateFlow.value.effectiveSelection)
    }

    private fun removeFolder(folder: File) {
        removeImages(viewModelStateFlow.value.images.map { it.file }.filter { it.folder == folder }.toSet())
    }

    private fun removeUnreadable() {
        val state = viewModelStateFlow.value
        val tools = state.tools ?: return
        removeImages(state.exportTargets.map { it.file }.filter { !state.canRead(it, tools) }.toSet())
    }

    private fun removeImages(targets: Set<File>) {
        val before = viewModelStateFlow.value
        mutate { state ->
            val removed = state.images.withIndex().filter { it.value.file in targets }
            if (removed.isEmpty()) return@mutate state
            val images = state.images.filter { it.file !in targets }
            val focused = if (state.focusedFile in targets) {
                // 消した位置の次にある画像をプレビューする
                val firstRemovedIndex = removed.first().index
                val after = state.images.drop(firstRemovedIndex).firstOrNull { it.file !in targets }
                (after ?: images.lastOrNull())?.file
            } else {
                state.focusedFile
            }
            // 選択の一部だけを削除したときは残りの選択を保ち、書き出し対象が一覧全体に広がらないようにする
            val remainingSelection = state.selection - targets
            state.copy(
                images = images,
                focusedFile = focused,
                selection = remainingSelection.ifEmpty { setOfNotNull(focused) },
                anchor = state.anchor?.takeIf { it !in targets } ?: focused,
                isSelectionMode = state.isSelectionMode && remainingSelection.isNotEmpty(),
                lastRemoval = ImagePrepViewModelState.Removal(
                    entries = removed,
                    focusedFile = state.focusedFile,
                    selection = state.selection,
                    anchor = state.anchor,
                    isSelectionMode = state.isSelectionMode,
                ),
                removedFiles = state.removedFiles + removed.map { it.value.file },
            )
        }
        val removedCount = before.images.size - viewModelStateFlow.value.images.size
        if (removedCount > 0) {
            messageChannel.trySend(SnackbarMessage("$removedCount 枚を一覧から削除しました", canUndoRemoval = true))
        }
    }

    /** 直前の削除を取り消し、画像を元の位置に、選択とプレビューを削除前の状態に戻す */
    private fun undoRemoval() {
        mutate { state ->
            val removal = state.lastRemoval ?: return@mutate state
            val present = state.images.map { it.file }.toSet()
            val restoring = removal.entries.filter { it.value.file !in present }
            if (restoring.isEmpty()) return@mutate state.copy(lastRemoval = null)
            val images = state.images.toMutableList()
            restoring.forEach { (index, item) -> images.add(index.coerceAtMost(images.size), item) }
            val restoredFiles = restoring.map { it.value.file }
            val presentAfterRestore = present + restoredFiles
            val before = removal
            val focused = before.focusedFile?.takeIf { it in presentAfterRestore } ?: restoredFiles.first()
            val selection = before.selection.filter { it in presentAfterRestore }.toSet()
            state.copy(
                // 削除後に同じフォルダの画像を追加していても、フォルダごとのまとまりを崩さない
                images = images.groupedByFolder(),
                focusedFile = focused,
                selection = selection.ifEmpty { setOf(focused) },
                anchor = before.anchor?.takeIf { it in presentAfterRestore } ?: focused,
                isSelectionMode = before.isSelectionMode && selection.isNotEmpty(),
                lastRemoval = null,
                removedFiles = state.removedFiles - restoredFiles.toSet(),
            )
        }
    }

    private fun forgetRecent(dir: File) {
        mutate { it.copy(recentFolders = it.recentFolders - dir) }
        settings.saveRecentFolders(viewModelStateFlow.value.recentFolders)
    }

    private fun rememberRecent(dir: File) {
        mutate { state -> state.copy(recentFolders = (listOf(dir) + state.recentFolders.filter { it != dir }).take(6)) }
        settings.saveRecentFolders(viewModelStateFlow.value.recentFolders)
    }

    private fun listImages(dir: File): List<File> =
        dir.listFiles()
            ?.filter { it.isFile && !it.isHidden && ImageLoader.isSupported(it) }
            ?.sortedWith(NaturalOrder)
            .orEmpty()

    // endregion

    // region 選択とフォーカス

    /**
     * 一覧でのクリック。Single はプレビューも切り替える。
     * Toggle（Ctrl）と Range（Shift）は選択だけを変え、プレビュー中の画像はそのまま。
     */
    private fun clickImage(file: File, mode: SelectMode) {
        mutate { state ->
            when (mode) {
                SelectMode.Single -> state.copy(focusedFile = file, selection = setOf(file), anchor = file, isSelectionMode = false)
                SelectMode.Toggle -> {
                    val base = state.effectiveSelection
                    val selection = if (file in base) base - file else base + file
                    state.copy(
                        selection = selection,
                        anchor = file,
                        isSelectionMode = selection.size > 1 || (state.isSelectionMode && selection.isNotEmpty()),
                        focusedFile = state.focusedFile ?: file,
                    )
                }
                SelectMode.Range -> {
                    val from = state.images.indexOfFirst { it.file == (state.anchor ?: state.focusedFile) }
                    val to = state.images.indexOfFirst { it.file == file }
                    if (from < 0 || to < 0) return@mutate state.copy(focusedFile = file, selection = setOf(file), anchor = file, isSelectionMode = false)
                    val range = state.images.subList(minOf(from, to), maxOf(from, to) + 1).map { it.file }
                    state.copy(selection = range.toSet(), focusedFile = state.focusedFile ?: file, isSelectionMode = range.size > 1)
                }
            }
        }
    }

    private fun selectAll() {
        mutate { state -> state.copy(selection = state.images.map { it.file }.toSet(), isSelectionMode = state.images.size > 1) }
    }

    private fun clearSelection() {
        mutate { state -> state.copy(selection = setOfNotNull(state.focusedFile), anchor = state.focusedFile, isSelectionMode = false) }
    }

    private fun moveFocus(delta: Int) {
        mutate { state ->
            if (state.images.isEmpty()) return@mutate state
            val current = state.images.indexOfFirst { it.file == state.focusedFile }.coerceAtLeast(0)
            val next = state.images[(current + delta).coerceIn(0, state.images.lastIndex)].file
            state.copy(focusedFile = next, selection = setOf(next), anchor = next, isSelectionMode = false)
        }
    }

    // endregion

    // region 設定

    private fun updateOptions(transform: (EditOptions) -> EditOptions) {
        mutate { it.copy(options = transform(it.options)) }
    }

    private fun setInputValid(field: String, valid: Boolean) {
        mutate { it.copy(invalidInputs = if (valid) it.invalidInputs - field else it.invalidInputs + field) }
    }

    private fun setCrop(file: File, crop: CropRect?) {
        mutate { state ->
            if (crop == null || crop.isFull) state.copy(crops = state.crops - file) else state.copy(crops = state.crops + (file to crop))
        }
    }

    private fun chooseOutputDirectory(dir: File) {
        mutate { it.copy(customOutputDir = dir) }
        settings.saveCustomOutputDir(dir)
    }

    private fun resetOutputDirectory() {
        mutate { it.copy(customOutputDir = null) }
        settings.saveCustomOutputDir(null)
    }

    private fun refreshTools() {
        viewModelScope.launch {
            val tools = withContext(Dispatchers.IO) { checkTools() }
            previewCache.clear()
            mutate { it.copy(tools = tools) }
        }
    }

    // endregion

    // region 書き出し

    private fun requestExport() {
        val state = viewModelStateFlow.value
        val ui = state.toUiState(listener)
        // 読み込み中に書き出すと、完了後に一覧が入れ替わり画面と違う画像を書き出してしまう
        if (!ui.canExport || loadJob?.isActive == true) return
        val outputDir = ui.outputDirectory ?: return
        // 計画中に再度呼ばれても二重に書き出さないよう、先に状態を確保する
        mutate { it.copy(export = ExportState.Preparing) }
        viewModelScope.launch {
            val plan = try {
                withContext(Dispatchers.IO) {
                    OutputPlanner.plan(
                        sources = state.exportTargets.map { it.file },
                        outputDir = outputDir,
                        options = state.options,
                        // 書き出さない画像や一覧から削除した画像も元画像なので上書きしない
                        protectedFiles = state.images.map { it.file } + state.removedFiles,
                    ).map { it.copy(crop = state.crops[it.source]) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutate { it.copy(export = ExportState.Idle) }
                messageChannel.send(SnackbarMessage("書き出しを開始できません（${e.message ?: e.javaClass.simpleName}）"))
                return@launch
            }
            // 計画中に閉じられた・取り消された場合は書き出さない
            if (viewModelStateFlow.value.export != ExportState.Preparing) return@launch
            if (plan.any { it.exists }) {
                // 同名確認の後も、要求した時点の設定で書き出す
                pendingExportSettings = state.options to state.tools
                mutate { it.copy(export = ExportState.ConfirmConflicts(plan)) }
            } else {
                startExport(plan, outputDir, state.options, state.tools)
            }
        }
    }

    private fun resolveConflicts(policy: ConflictPolicy?) {
        val confirm = viewModelStateFlow.value.export as? ExportState.ConfirmConflicts ?: return
        if (policy == null) {
            pendingExportSettings = null
            mutate { it.copy(export = ExportState.Idle) }
            return
        }
        val outputDir = confirm.plan.first().target.parentFile
        val current = viewModelStateFlow.value
        val (options, tools) = pendingExportSettings ?: (current.options to current.tools)
        pendingExportSettings = null
        startExport(OutputPlanner.applyPolicy(confirm.plan, policy), outputDir, options, tools)
    }

    private fun cancelExport() {
        val running = viewModelStateFlow.value.export as? ExportState.Running ?: return
        mutate { it.copy(export = running.copy(cancelling = true)) }
        exportJob?.cancel()
    }

    private fun dismissExport() {
        if (viewModelStateFlow.value.export is ExportState.Running) return
        mutate { it.copy(export = ExportState.Idle) }
    }

    /** 書き出しは [options] と [tools]（要求した時点の値）で行い、準備中・実行中の設定変更は反映しない */
    private fun startExport(plan: List<PlannedOutput>, outputDir: File, options: EditOptions, tools: ExternalTools?) {
        val processor = ImageProcessor(tools ?: ExternalTools.None)
        val results = Collections.synchronizedList(mutableListOf<ProcessResult>())
        mutate { it.copy(export = ExportState.Running(done = 0, total = plan.size, currentName = plan.firstOrNull()?.source?.name, cancelling = false)) }

        exportJob = viewModelScope.launch {
            var cancelled = false
            try {
                withContext(Dispatchers.IO) {
                    outputDir.mkdirs()
                    val parallelism = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 3)
                    val semaphore = Semaphore(parallelism)
                    coroutineScope {
                        plan.map { item ->
                            async {
                                semaphore.withPermit {
                                    ensureActive()
                                    mutate { vm ->
                                        val running = vm.export as? ExportState.Running ?: return@mutate vm
                                        vm.copy(export = running.copy(currentName = item.source.name))
                                    }
                                    // キャンセル時はスレッドに割り込み、外部プロセスも止める
                                    val result = runInterruptible { processor.export(item, options) }
                                    results += result
                                    mutate { vm ->
                                        val running = vm.export as? ExportState.Running ?: return@mutate vm
                                        vm.copy(export = running.copy(done = results.size))
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                }
            } catch (e: CancellationException) {
                cancelled = true
            }
            val ordered = synchronized(results) { results.sortedBy { r -> plan.indexOfFirst { it.source == r.source } } }
            mutate {
                it.copy(export = ExportState.Finished(ordered, outputDir, cancelled = cancelled, total = plan.size))
            }
        }
    }

    // endregion

    // region プレビュー

    private class PreviewSource(val loaded: LoadedImage, val fields: Map<CaptionField, String>)

    private fun observePreview() {
        viewModelScope.launch {
            viewModelStateFlow
                .map { state ->
                    val file = state.focusedFile
                    // HEIC はツール確認後に読み直す必要がある
                    PreviewKey(file, if (file != null && ImageLoader.isHeif(file)) state.tools else null)
                }
                .distinctUntilChanged()
                .collectLatestSafe { key -> renderPreview(key) }
        }
    }

    private data class PreviewKey(val file: File?, val tools: ExternalTools?)

    private suspend fun renderPreview(key: PreviewKey) {
        val file = key.file
        if (file == null) {
            mutate { it.copy(preview = EmptyPreview) }
            return
        }
        mutate { it.copy(preview = EmptyPreview.copy(file = file, loading = true)) }
        val tools = key.tools ?: viewModelStateFlow.value.tools ?: ExternalTools.None
        val cacheKey = "${file.absolutePath}:${file.lastModified()}"
        val source = previewCache[cacheKey] ?: try {
            // 別の画像に切り替えたら HEIC 変換などの外部プロセスも止める
            runInterruptible(Dispatchers.IO) {
                PreviewSource(ImageLoader.load(file, tools, maxDimension = PREVIEW_MAX, smoothDownscale = true), ExifService.readFields(file))
            }.also { previewCache[cacheKey] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            mutate { it.copy(preview = EmptyPreview.copy(file = file, error = e.message ?: "読み込めません")) }
            return
        }
        val original = withContext(Dispatchers.Default) { source.loaded.image.toComposeImageBitmap() }
        mutate {
            it.copy(
                preview = it.preview.copy(
                    original = original,
                    originalSize = source.loaded.size,
                    captionFields = source.fields,
                ),
            )
        }

        // 非 HEIF 画像はツール確認前に開かれることがあり、cwebp が見つかったら WebP のプレビューを作り直す
        viewModelStateFlow.map { ProcessedPreviewKey(it.options, it.tools ?: tools, it.crops[file]) }.distinctUntilChanged().collectLatestSafe { key ->
            val options = key.options
            val currentTools = key.tools
            mutate { it.copy(preview = it.preview.copy(loading = true)) }
            val outputFormat = OutputPlanner.resolveFormat(file, options.outputFormat)
            // cwebp は外部プロセスで重いため、品質スライダー操作中は待ってからまとめてエンコードする
            delay(if (outputFormat == OutputFormat.Webp) 300 else 80)
            val outputSize = Resizer.targetSize(Cropper.croppedSize(source.loaded.size, key.crop), options)
            val image = Cropper.crop(source.loaded.image, key.crop)
            val renderSize = Resizer.fitWithin(outputSize, image.width, image.height)
            val processor = ImageProcessor(currentTools)
            val caption = if (options.captionEnabled) {
                CaptionTemplate.render(options.captionTemplate, source.fields).takeIf { it.isNotBlank() }
            } else {
                null
            }
            val rendered = runInterruptible(Dispatchers.Default) {
                renderWithOutputQuality(processor, image, caption, options, renderSize, outputFormat, currentTools)
            }
            val bitmap = withContext(Dispatchers.Default) { rendered.image.toComposeImageBitmap() }
            mutate {
                it.copy(
                    preview = it.preview.copy(
                        processed = bitmap,
                        outputSize = outputSize,
                        outputFormat = outputFormat,
                        outputByteSize = rendered.encodedByteSize.takeIf { renderSize == outputSize },
                        loading = false,
                    ),
                )
            }
        }
    }

    private data class ProcessedPreviewKey(val options: EditOptions, val tools: ExternalTools, val crop: CropRect?)

    private class RenderedPreview(val image: BufferedImage, val encodedByteSize: Long?)

    /**
     * 書き出しと同じ形式・品質でエンコードした劣化具合をプレビューに反映する。
     * 画質の劣化で文字が読みにくくならないよう、キャプションはエンコード後に重ねる
     */
    private fun renderWithOutputQuality(
        processor: ImageProcessor,
        image: BufferedImage,
        caption: String?,
        options: EditOptions,
        renderSize: ImageSize,
        outputFormat: OutputFormat,
        tools: ExternalTools,
    ): RenderedPreview {
        val resized = processor.render(image, null, options, renderSize)
        val encoded = try {
            ImageEncoder.encodeForPreview(resized, outputFormat, options.quality, tools)
        } catch (e: IOException) {
            null
        }
        val degraded = when {
            encoded != null -> encoded.image
            outputFormat == OutputFormat.Jpeg && resized.colorModel.hasAlpha() -> ImageEncoder.flattenOnWhite(resized)
            else -> resized
        }
        if (caption != null) CaptionRenderer.draw(degraded, caption, options)
        return RenderedPreview(degraded, encoded?.byteSize)
    }

    // endregion

    override fun onCleared() {
        exportJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val PREVIEW_MAX = 2000
    }
}

private suspend fun <T> Flow<T>.collectLatestSafe(action: suspend (T) -> Unit) {
    collectLatest { value ->
        try {
            action(value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}

private val EmptyPreview = PreviewState(
    file = null,
    original = null,
    processed = null,
    originalSize = null,
    outputSize = null,
    outputFormat = null,
    outputByteSize = null,
    captionFields = mapOf(),
    crop = null,
    loading = false,
    error = null,
)

internal data class ImagePrepViewModelState(
    /** 同じフォルダの画像が続けて並ぶ（[groupedByFolder]） */
    val images: List<ImageItem> = emptyList(),
    /** 一覧の画像をすべて削除してもホームに戻さないため、画像の有無とは別に持つ */
    val isWorkspaceOpen: Boolean = false,
    val focusedFile: File? = null,
    /** 一覧で選択中の画像（Shift / Ctrl で複数）。プレビューは [focusedFile] */
    val selection: Set<File> = emptySet(),
    val anchor: File? = null,
    /**
     * 選択中の画像だけを書き出すかどうか。Ctrl / Shift で複数選択すると有効になる。
     * 削除で選択が 1 枚に減っても、書き出し対象が一覧全体に広がらないよう枚数とは独立して持つ
     */
    val isSelectionMode: Boolean = false,
    val options: EditOptions = EditOptions(),
    val customOutputDir: File? = null,
    val tools: ExternalTools? = null,
    val recentFolders: List<File> = emptyList(),
    val preview: PreviewState = EmptyPreview,
    val export: ExportState = ExportState.Idle,
    val isLoading: Boolean = false,
    /** 入力欄が不正な値になっている設定項目 */
    val invalidInputs: Set<String> = setOf(),
    /** 元に戻せる直前の削除 */
    val lastRemoval: Removal? = null,
    /** このフォルダを開いてから一覧から削除した画像。元画像なので書き出しで上書きしない */
    val removedFiles: Set<File> = setOf(),
    /** 画像ごとの切り抜き範囲。切り抜かない画像は含めない */
    val crops: Map<File, CropRect> = mapOf(),
) {
    class Removal(
        /** 削除した画像と、削除前の一覧での位置（昇順） */
        val entries: List<IndexedValue<ImageItem>>,
        /** ここから下は削除前の選択とプレビューの状態 */
        val focusedFile: File?,
        val selection: Set<File>,
        val anchor: File?,
        val isSelectionMode: Boolean,
    )

    val exportTargets: List<ImageItem>
        get() = images.filter { it.file in effectiveSelection }

    val defaultOutputDir: File?
        get() = images.firstOrNull()?.file?.folder?.let { File(it, "output") }

    val effectiveSelection: Set<File>
        get() = selection.ifEmpty { setOfNotNull(focusedFile) }

    fun targetsFor(file: File): Set<File> = effectiveSelection.takeIf { file in it } ?: setOf(file)

    fun canRead(file: File, tools: ExternalTools): Boolean = !ImageLoader.isHeif(file) || tools.heifDecoder != null
}

internal fun ImagePrepViewModelState.toUiState(listener: ImagePrepUiState.Listener): ImagePrepUiState {
    val targets = exportTargets
    val outputDir = customOutputDir ?: defaultOutputDir
    val notices = buildList {
        if (invalidInputs.isNotEmpty()) {
            add(Notice("サイズは ${EditOptions.MIN_DIMENSION}〜${EditOptions.MAX_DIMENSION} px で入力してください。", blocking = true, action = null))
        }
        val tools = tools
        val needsTools = targets.any {
            ImageLoader.isHeif(it.file) || OutputPlanner.resolveFormat(it.file, options.outputFormat) == OutputFormat.Webp
        }
        if (tools == null && needsTools) {
            add(Notice("外部ツールを確認しています…", blocking = true, action = null))
        }
        if (tools != null) {
            val needsWebp = targets.any { OutputPlanner.resolveFormat(it.file, options.outputFormat) == OutputFormat.Webp }
            if (needsWebp && !tools.canWriteWebp) {
                add(Notice("WebP で書き出すには cwebp が必要です。形式を変更するか、cwebp をインストールしてください。", blocking = true, action = null))
            }
            val unreadable = targets.count { !canRead(it.file, tools) }
            if (unreadable > 0) {
                add(Notice("HEIC の $unreadable 枚は heif-dec / magick が無いため読み込めません。", blocking = true, action = NoticeAction.RemoveUnreadable))
            }
        }
        if (outputDir != null &&
            options.fileNameSuffix.isBlank() &&
            targets.any { it.file.absoluteFile.parentFile?.normalize() == outputDir.absoluteFile.normalize() }
        ) {
            add(Notice("出力先が元画像と同じフォルダです。元画像は上書きされず「(2)」付きの名前で保存されます。接尾辞の設定がおすすめです。", blocking = false, action = null))
        }
        val targetFolderCount = targets.map { it.file.folder }.distinct().size
        if (outputDir != null && customOutputDir == null && targetFolderCount > 1) {
            add(Notice("$targetFolderCount つのフォルダの画像を 1 つの出力先にまとめて書き出します。", blocking = false, action = null))
        }
    }
    return ImagePrepUiState(
        images = images,
        isWorkspaceOpen = isWorkspaceOpen,
        imageGroups = images.groupBy { it.file.folder }.map { (folder, items) -> ImageGroup(folder, items) },
        exportCount = targets.size,
        isExportingSelection = isSelectionMode,
        focusedFile = focusedFile,
        selectedFiles = effectiveSelection,
        pickerInitialDirectory = (focusedFile ?: images.lastOrNull()?.file)?.folder,
        options = options,
        outputDirectory = outputDir,
        isCustomOutputDirectory = customOutputDir != null,
        tools = tools,
        recentFolders = recentFolders,
        preview = preview.copy(crop = preview.file?.let(crops::get)),
        export = export,
        notices = notices,
        isLoading = isLoading,
        listener = listener,
    )
}

private val File.folder: File get() = absoluteFile.parentFile

/** フォルダが最初に現れた順にまとめ、フォルダ内の並びは保つ */
private fun List<ImageItem>.groupedByFolder(): List<ImageItem> = groupBy { it.file.folder }.values.flatten()

/** "IMG_2.jpg" が "IMG_10.jpg" より前に来る並び順 */
internal object NaturalOrder : Comparator<File> {
    private val chunk = Regex("""\d+|\D+""")

    override fun compare(a: File, b: File): Int {
        val x = chunk.findAll(a.name.lowercase()).map { it.value }.toList()
        val y = chunk.findAll(b.name.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]
            val q = y[i]
            val result = if (p[0].isDigit() && q[0].isDigit()) {
                p.trimStart('0').length.compareTo(q.trimStart('0').length).takeIf { it != 0 }
                    ?: p.trimStart('0').compareTo(q.trimStart('0'))
            } else {
                p.compareTo(q)
            }
            if (result != 0) return result
        }
        return x.size.compareTo(y.size)
    }
}
