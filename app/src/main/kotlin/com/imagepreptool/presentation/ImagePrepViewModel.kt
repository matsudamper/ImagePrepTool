package com.imagepreptool.presentation

import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
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
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.ExternalTools
import com.imagepreptool.model.OutputFormat
import com.imagepreptool.model.ProcessResult
import com.imagepreptool.service.CaptionTemplate
import com.imagepreptool.service.ExifService
import com.imagepreptool.service.ExternalToolChecker
import com.imagepreptool.service.ImageLoader
import com.imagepreptool.service.ImageProcessor
import com.imagepreptool.service.LoadedImage
import com.imagepreptool.service.OutputPlanner
import com.imagepreptool.service.PlannedOutput
import com.imagepreptool.service.Resizer

class ImagePrepViewModel(
    private val settings: SettingsStore = PreferencesSettingsStore(),
    private val checkTools: () -> ExternalTools = ExternalToolChecker::checkAll,
) : ViewModel(), WorkspaceEvents {

    private val viewModelStateFlow = MutableStateFlow(
        ImagePrepViewModelState(
            options = settings.loadOptions(),
            customOutputDir = settings.loadCustomOutputDir(),
            recentFolders = settings.loadRecentFolders(),
        ),
    )

    val uiStateFlow: StateFlow<ImagePrepUiState> =
        MutableStateFlow(viewModelStateFlow.value.toUiState()).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.value = viewModelState.toUiState()
                }
            }
        }.asStateFlow()

    private val messageChannel = Channel<String>(Channel.BUFFERED)

    /** スナックバーで一度だけ表示するメッセージ */
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    private var exportJob: Job? = null
    private var loadJob: Job? = null
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

    fun openFolder(dir: File) {
        // 後から開いたフォルダを優先し、前の読み込み結果で上書きしない
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            mutate { it.copy(isLoading = true) }
            val files = try {
                runInterruptible(Dispatchers.IO) { listImages(dir) }
            } finally {
                mutate { it.copy(isLoading = false) }
            }
            if (files.isEmpty()) {
                messageChannel.send("「${dir.name}」に読み込める画像がありません")
                return@launch
            }
            mutate {
                it.copy(
                    images = files.map(::ImageItem),
                    sourceFolder = dir,
                    focusedFile = files.first(),
                    selection = setOf(files.first()),
                    anchor = files.first(),
                )
            }
            rememberRecent(dir)
        }
    }

    /** ドロップやファイル選択で追加する。フォルダが含まれていれば中の画像を追加する */
    fun addFiles(files: List<File>) {
        if (files.size == 1 && files.single().isDirectory && viewModelStateFlow.value.images.isEmpty()) {
            openFolder(files.single())
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val expanded = withContext(Dispatchers.IO) {
                files.flatMap { if (it.isDirectory) listImages(it) else listOf(it) }
            }
            val supported = expanded.filter { it.isFile && ImageLoader.isSupported(it) }
            val ignored = expanded.size - supported.size
            val existing = viewModelStateFlow.value.images.map { it.file.absoluteFile }.toSet()
            val added = supported.map { it.absoluteFile }.distinct().filter { it !in existing }
            mutate { state ->
                state.copy(
                    images = state.images + added.map(::ImageItem),
                    sourceFolder = if (state.images.isEmpty()) null else state.sourceFolder,
                    focusedFile = state.focusedFile ?: added.firstOrNull(),
                )
            }
            val message = buildList {
                if (added.isNotEmpty()) add("${added.size} 枚を追加しました")
                if (supported.size > added.size) add("${supported.size - added.size} 枚は追加済みです")
                if (ignored > 0) add("非対応の $ignored 件を除外しました")
            }.ifEmpty { listOf("追加できる画像がありません") }
            messageChannel.send(message.joinToString("・"))
        }
    }

    internal fun snapshotForTest(): ImagePrepUiState = viewModelStateFlow.value.toUiState()

    internal fun addFilesForTest(files: List<File>) {
        mutate { it.copy(images = files.map(::ImageItem), focusedFile = files.firstOrNull()) }
    }

    override fun closeAll() {
        // 書き出しの準備中・実行中は閉じない（閉じた画像が書き出されるのを防ぐ）
        val export = viewModelStateFlow.value.export
        if (export is ExportState.Preparing || export is ExportState.Running || exportJob?.isActive == true) return
        loadJob?.cancel()
        mutate { it.copy(images = emptyList(), sourceFolder = null, focusedFile = null, selection = emptySet(), anchor = null, export = ExportState.Idle) }
    }

    /** [file] が複数選択に含まれていれば選択中の全画像を一覧から外す */
    override fun removeImage(file: File) {
        mutate { state ->
            val targets = state.targetsFor(file)
            val index = state.images.indexOfFirst { it.file == file }
            if (index < 0) return@mutate state
            val images = state.images.filter { it.file !in targets }
            val focused = if (state.focusedFile in targets) {
                // 消した位置の次にある画像をプレビューする
                val after = state.images.drop(index).firstOrNull { it.file !in targets }
                (after ?: images.lastOrNull())?.file
            } else {
                state.focusedFile
            }
            state.copy(
                images = images,
                focusedFile = focused,
                selection = setOfNotNull(focused),
                anchor = focused,
                sourceFolder = if (images.isEmpty()) null else state.sourceFolder,
            )
        }
    }

    fun forgetRecent(dir: File) {
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

    fun focus(file: File) {
        clickImage(file, SelectMode.Single)
    }

    /**
     * 一覧でのクリック。Single はプレビューも切り替える。
     * Toggle（Ctrl）と Range（Shift）は選択だけを変え、プレビュー中の画像はそのまま。
     */
    override fun clickImage(file: File, mode: SelectMode) {
        mutate { state ->
            when (mode) {
                SelectMode.Single -> state.copy(focusedFile = file, selection = setOf(file), anchor = file)
                SelectMode.Toggle -> {
                    val base = state.effectiveSelection
                    state.copy(
                        selection = if (file in base) base - file else base + file,
                        anchor = file,
                        focusedFile = state.focusedFile ?: file,
                    )
                }
                SelectMode.Range -> {
                    val from = state.images.indexOfFirst { it.file == (state.anchor ?: state.focusedFile) }
                    val to = state.images.indexOfFirst { it.file == file }
                    if (from < 0 || to < 0) return@mutate state.copy(focusedFile = file, selection = setOf(file), anchor = file)
                    val range = state.images.subList(minOf(from, to), maxOf(from, to) + 1).map { it.file }
                    state.copy(selection = range.toSet(), focusedFile = state.focusedFile ?: file)
                }
            }
        }
    }

    override fun selectAll() {
        mutate { state -> state.copy(selection = state.images.map { it.file }.toSet()) }
    }

    override fun clearSelection() {
        mutate { state -> state.copy(selection = setOfNotNull(state.focusedFile), anchor = state.focusedFile) }
    }

    override fun moveFocus(delta: Int) {
        mutate { state ->
            if (state.images.isEmpty()) return@mutate state
            val current = state.images.indexOfFirst { it.file == state.focusedFile }.coerceAtLeast(0)
            val next = state.images[(current + delta).coerceIn(0, state.images.lastIndex)].file
            state.copy(focusedFile = next, selection = setOf(next), anchor = next)
        }
    }

    /** [file] が複数選択に含まれていれば選択中の全画像を、そうでなければ [file] だけを切り替える */
    override fun toggleIncluded(file: File) {
        mutate { state ->
            val targets = state.targetsFor(file)
            val included = !(state.images.firstOrNull { it.file == file }?.included ?: true)
            state.copy(images = state.images.map { if (it.file in targets) it.copy(included = included) else it })
        }
    }

    override fun toggleFocusedIncluded() {
        viewModelStateFlow.value.focusedFile?.let(::toggleIncluded)
    }

    override fun setSelectionIncluded(included: Boolean) {
        mutate { state ->
            val targets = state.effectiveSelection
            state.copy(images = state.images.map { if (it.file in targets) it.copy(included = included) else it })
        }
    }

    override fun setAllIncluded(included: Boolean) {
        mutate { state -> state.copy(images = state.images.map { it.copy(included = included) }) }
    }

    override fun excludeUnreadable() {
        mutate { state ->
            val tools = state.tools ?: return@mutate state
            state.copy(images = state.images.map { if (!state.canRead(it.file, tools)) it.copy(included = false) else it })
        }
    }

    // endregion

    // region 設定

    override fun updateOptions(transform: (EditOptions) -> EditOptions) {
        mutate { it.copy(options = transform(it.options)) }
    }

    override fun chooseOutputDirectory(dir: File) {
        mutate { it.copy(customOutputDir = dir) }
        settings.saveCustomOutputDir(dir)
    }

    override fun resetOutputDirectory() {
        mutate { it.copy(customOutputDir = null) }
        settings.saveCustomOutputDir(null)
    }

    fun refreshTools() {
        viewModelScope.launch {
            val tools = withContext(Dispatchers.IO) { checkTools() }
            previewCache.clear()
            mutate { it.copy(tools = tools) }
        }
    }

    // endregion

    // region 書き出し

    override fun requestExport() {
        val state = viewModelStateFlow.value
        val ui = state.toUiState()
        if (!ui.canExport) return
        val outputDir = ui.outputDirectory ?: return
        // 計画中に再度呼ばれても二重に書き出さないよう、先に状態を確保する
        mutate { it.copy(export = ExportState.Preparing) }
        viewModelScope.launch {
            val plan = try {
                withContext(Dispatchers.IO) {
                    OutputPlanner.plan(
                        sources = state.images.filter { it.included }.map { it.file },
                        outputDir = outputDir,
                        options = state.options,
                        // 書き出しから外した画像も元画像なので上書きしない
                        protectedFiles = state.images.map { it.file },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutate { it.copy(export = ExportState.Idle) }
                messageChannel.send("書き出しを開始できません（${e.message ?: e.javaClass.simpleName}）")
                return@launch
            }
            // 計画中に閉じられた・取り消された場合は書き出さない
            if (viewModelStateFlow.value.export != ExportState.Preparing) return@launch
            if (plan.any { it.exists }) {
                mutate { it.copy(export = ExportState.ConfirmConflicts(plan)) }
            } else {
                startExport(plan, outputDir)
            }
        }
    }

    fun resolveConflicts(policy: ConflictPolicy?) {
        val confirm = viewModelStateFlow.value.export as? ExportState.ConfirmConflicts ?: return
        if (policy == null) {
            mutate { it.copy(export = ExportState.Idle) }
            return
        }
        val outputDir = confirm.plan.first().target.parentFile
        startExport(OutputPlanner.applyPolicy(confirm.plan, policy), outputDir)
    }

    fun cancelExport() {
        val running = viewModelStateFlow.value.export as? ExportState.Running ?: return
        mutate { it.copy(export = running.copy(cancelling = true)) }
        exportJob?.cancel()
    }

    fun dismissExport() {
        if (viewModelStateFlow.value.export is ExportState.Running) return
        mutate { it.copy(export = ExportState.Idle) }
    }

    private fun startExport(plan: List<PlannedOutput>, outputDir: File) {
        val state = viewModelStateFlow.value
        val options = state.options
        val processor = ImageProcessor(state.tools ?: ExternalTools.None)
        val results = Collections.synchronizedList(mutableListOf<ProcessResult>())
        mutate { it.copy(export = ExportState.Running(0, plan.size, plan.firstOrNull()?.source?.name)) }

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
            mutate { it.copy(preview = PreviewState()) }
            return
        }
        mutate { it.copy(preview = PreviewState(file = file, loading = true)) }
        val tools = key.tools ?: viewModelStateFlow.value.tools ?: ExternalTools.None
        val cacheKey = "${file.absolutePath}:${file.lastModified()}"
        val source = previewCache[cacheKey] ?: try {
            // 別の画像に切り替えたら HEIC 変換などの外部プロセスも止める
            runInterruptible(Dispatchers.IO) {
                PreviewSource(ImageLoader.load(file, tools, maxDimension = PREVIEW_MAX), ExifService.readFields(file))
            }.also { previewCache[cacheKey] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            mutate { it.copy(preview = PreviewState(file = file, error = e.message ?: "読み込めません")) }
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

        viewModelStateFlow.map { it.options }.distinctUntilChanged().collectLatestSafe { options ->
            mutate { it.copy(preview = it.preview.copy(loading = true)) }
            delay(80)
            val outputSize = Resizer.targetSize(source.loaded.size, options)
            val image = source.loaded.image
            val renderSize = Resizer.fitWithin(outputSize, image.width, image.height)
            val processor = ImageProcessor(tools)
            val caption = if (options.captionEnabled) {
                CaptionTemplate.render(options.captionTemplate, source.fields).takeIf { it.isNotBlank() }
            } else {
                null
            }
            val bitmap = withContext(Dispatchers.Default) {
                processor.render(image, caption, options, renderSize).toComposeImageBitmap()
            }
            mutate {
                it.copy(
                    preview = it.preview.copy(
                        processed = bitmap,
                        outputSize = outputSize,
                        outputFormat = OutputPlanner.resolveFormat(file, options.outputFormat),
                        loading = false,
                    ),
                )
            }
        }
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

internal data class ImagePrepViewModelState(
    val images: List<ImageItem> = emptyList(),
    val sourceFolder: File? = null,
    val focusedFile: File? = null,
    /** 一覧で選択中の画像（Shift / Ctrl で複数）。プレビューは [focusedFile] */
    val selection: Set<File> = emptySet(),
    val anchor: File? = null,
    val options: EditOptions = EditOptions(),
    val customOutputDir: File? = null,
    val tools: ExternalTools? = null,
    val recentFolders: List<File> = emptyList(),
    val preview: PreviewState = PreviewState(),
    val export: ExportState = ExportState.Idle,
    val isLoading: Boolean = false,
) {
    val defaultOutputDir: File?
        get() = (sourceFolder ?: images.firstOrNull()?.file?.absoluteFile?.parentFile)?.let { File(it, "output") }

    val effectiveSelection: Set<File>
        get() = selection.ifEmpty { setOfNotNull(focusedFile) }

    fun targetsFor(file: File): Set<File> = effectiveSelection.takeIf { file in it } ?: setOf(file)

    fun canRead(file: File, tools: ExternalTools): Boolean = !ImageLoader.isHeif(file) || tools.heifDecoder != null
}

internal fun ImagePrepViewModelState.toUiState(): ImagePrepUiState {
    val included = images.filter { it.included }
    val outputDir = customOutputDir ?: defaultOutputDir
    val notices = buildList {
        val tools = tools
        val needsTools = included.any {
            ImageLoader.isHeif(it.file) || OutputPlanner.resolveFormat(it.file, options.outputFormat) == OutputFormat.Webp
        }
        if (tools == null && needsTools) {
            add(Notice("外部ツールを確認しています…", blocking = true))
        }
        if (tools != null) {
            val needsWebp = included.any { OutputPlanner.resolveFormat(it.file, options.outputFormat) == OutputFormat.Webp }
            if (needsWebp && !tools.canWriteWebp) {
                add(Notice("WebP で書き出すには cwebp が必要です。形式を変更するか、cwebp をインストールしてください。", blocking = true, action = NoticeAction.ShowTools))
            }
            val unreadable = included.count { !canRead(it.file, tools) }
            if (unreadable > 0) {
                add(Notice("HEIC の $unreadable 枚は heif-dec / magick が無いため読み込めません。", blocking = true, action = NoticeAction.ExcludeUnreadable))
            }
        }
        if (outputDir != null &&
            options.fileNameSuffix.isBlank() &&
            included.any { it.file.absoluteFile.parentFile?.normalize() == outputDir.absoluteFile.normalize() }
        ) {
            add(Notice("出力先が元画像と同じフォルダです。元画像は上書きされず「(2)」付きの名前で保存されます。接尾辞の設定がおすすめです。", blocking = false))
        }
    }
    val folder = sourceFolder
    return ImagePrepUiState(
        images = images,
        includedCount = included.size,
        focusedFile = focusedFile,
        selectedFiles = effectiveSelection,
        sourceTitle = folder?.name?.ifEmpty { folder.path } ?: if (images.isEmpty()) null else "追加した画像",
        sourcePath = folder?.absolutePath,
        options = options,
        outputDirectory = outputDir,
        isCustomOutputDirectory = customOutputDir != null,
        tools = tools,
        recentFolders = recentFolders,
        preview = preview,
        export = export,
        notices = notices,
        isLoading = isLoading,
    )
}

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
