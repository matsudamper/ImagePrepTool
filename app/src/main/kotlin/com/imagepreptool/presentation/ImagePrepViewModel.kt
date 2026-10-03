package com.imagepreptool.presentation

import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.imagepreptool.data.ExportSettings
import com.imagepreptool.data.FileStamp
import com.imagepreptool.data.PreferencesSettingsStore
import com.imagepreptool.data.ProjectContent
import com.imagepreptool.data.ProjectImage
import com.imagepreptool.data.ProjectStore
import com.imagepreptool.data.ProjectSummary
import com.imagepreptool.data.SettingsStore
import com.imagepreptool.data.StoredProject
import com.imagepreptool.model.CaptionField
import com.imagepreptool.model.ConflictPolicy
import com.imagepreptool.model.CropRect
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.ExternalTools
import com.imagepreptool.model.FileDates
import com.imagepreptool.model.ImageSize
import com.imagepreptool.model.OutputFormat
import com.imagepreptool.model.OutputPathMode
import com.imagepreptool.model.PenKind
import com.imagepreptool.model.PenStroke
import com.imagepreptool.model.PenTool
import com.imagepreptool.model.ProcessResult
import com.imagepreptool.model.Rotation
import com.imagepreptool.service.CaptionRenderer
import com.imagepreptool.service.CaptionTemplate
import com.imagepreptool.service.Cropper
import com.imagepreptool.service.ExifService
import com.imagepreptool.service.ExternalToolChecker
import com.imagepreptool.service.FileDatesReader
import com.imagepreptool.service.ImageEncoder
import com.imagepreptool.service.ImageLoader
import com.imagepreptool.service.ImageProcessor
import com.imagepreptool.service.LoadedImage
import com.imagepreptool.service.OutputPlanner
import com.imagepreptool.service.PenPainter
import com.imagepreptool.service.PlannedOutput
import com.imagepreptool.service.RelativeOutputPath
import com.imagepreptool.service.Resizer
import com.imagepreptool.service.Rotator

/**
 * @param initialFiles 起動引数で渡された画像やフォルダ。あれば前回のプロジェクトではなく新しいプロジェクトで開く
 */
class ImagePrepViewModel(
    private val projectStore: ProjectStore,
    initialFiles: List<File>,
    private val settings: SettingsStore = PreferencesSettingsStore(),
    private val checkTools: () -> ExternalTools = ExternalToolChecker::checkAll,
) : ViewModel() {

    private val viewModelStateFlow = MutableStateFlow(
        ImagePrepViewModelState(
            options = settings.loadOptions(),
            outputPathMode = settings.loadOutputPathMode(),
            relativeOutputPath = settings.loadRelativeOutputPath(),
        ),
    )

    private val listener = object : ImagePrepUiState.Listener {
        override fun openFolder(dir: File) = this@ImagePrepViewModel.openFolder(dir)
        override fun addFiles(files: List<File>) = this@ImagePrepViewModel.addFiles(files)
        override fun createProject() = this@ImagePrepViewModel.createProject()
        override fun renameProject(name: String) = this@ImagePrepViewModel.renameProject(name)
        override fun closeAll() = this@ImagePrepViewModel.closeAll()
        override fun clickImage(file: File, mode: SelectMode) = this@ImagePrepViewModel.clickImage(file, mode)
        override fun selectAll() = this@ImagePrepViewModel.selectAll()
        override fun clearSelection() = this@ImagePrepViewModel.clearSelection()
        override fun moveFocus(delta: Int) = this@ImagePrepViewModel.moveFocus(delta)
        override fun extendSelection(delta: Int) = this@ImagePrepViewModel.extendSelection(delta)
        override fun selectSortKey(key: ImageSortKey) = this@ImagePrepViewModel.selectSortKey(key)
        override fun toggleSortDirection() = this@ImagePrepViewModel.toggleSortDirection()
        override fun removeImage(file: File) = this@ImagePrepViewModel.removeImage(file)
        override fun removeSelection() = this@ImagePrepViewModel.removeSelection()
        override fun removeFolder(folder: File) = this@ImagePrepViewModel.removeFolder(folder)
        override fun removeUnreadable() = this@ImagePrepViewModel.removeUnreadable()
        override fun undoRemoval() = this@ImagePrepViewModel.undoRemoval()
        override fun updateOptions(transform: (EditOptions) -> EditOptions) = this@ImagePrepViewModel.updateOptions(transform)
        override fun setInputValid(field: String, valid: Boolean) = this@ImagePrepViewModel.setInputValid(field, valid)
        override fun setCrop(file: File, crop: CropRect?) = this@ImagePrepViewModel.setCrop(file, crop)
        override fun rotateClockwise(file: File) =
            this@ImagePrepViewModel.rotate(file, Rotation::rotatedClockwise, CropRect::rotatedClockwise, PenStroke::rotatedClockwise)
        override fun rotateCounterClockwise(file: File) =
            this@ImagePrepViewModel.rotate(file, Rotation::rotatedCounterClockwise, CropRect::rotatedCounterClockwise, PenStroke::rotatedCounterClockwise)
        override fun setStrokes(file: File, strokes: List<PenStroke>) = this@ImagePrepViewModel.setStrokes(file, strokes)
        override fun setPenTool(tool: PenTool) = this@ImagePrepViewModel.setPenTool(tool)
        override fun chooseOutputDirectory(dir: File) = this@ImagePrepViewModel.chooseOutputDirectory(dir)
        override fun setOutputPathMode(mode: OutputPathMode) = this@ImagePrepViewModel.setOutputPathMode(mode)
        override fun setRelativeOutputPath(path: String) = this@ImagePrepViewModel.setRelativeOutputPath(path)
        override fun requestExport() = this@ImagePrepViewModel.requestExport()
        override fun resolveConflicts(policy: ConflictPolicy?) = this@ImagePrepViewModel.resolveConflicts(policy)
        override fun cancelExport() = this@ImagePrepViewModel.cancelExport()
        override fun dismissExport() = this@ImagePrepViewModel.dismissExport()
        override fun saveBeforeExit() = this@ImagePrepViewModel.saveBeforeExit()
    }

    /** プロジェクトごとに使い回し、一覧の項目が状態の更新のたびに変わったことにならないようにする */
    private val projectListeners = ConcurrentHashMap<Long, ProjectItem.Listener>()

    private fun projectListenerOf(summary: ProjectSummary): ProjectItem.Listener =
        projectListeners.computeIfAbsent(summary.id) { id ->
            object : ProjectItem.Listener {
                override fun open() = switchProject(id)
                override fun delete() = deleteProject(id)
            }
        }

    val uiStateFlow: StateFlow<ImagePrepUiState> =
        MutableStateFlow(viewModelStateFlow.value.toUiState(listener, ::projectListenerOf)).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.value = viewModelState.toUiState(listener, ::projectListenerOf)
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

    /** 保存と削除が入れ違って、消したプロジェクトに書き込まないようにする */
    private val saveMutex = Mutex()

    /** 最後に保存したプロジェクトとその内容。次の保存では変わった部分だけを書く */
    private var savedProject: Pair<Long, ProjectContent>? = null

    /** 画像の追加が重なっても、プロジェクトを 2 つ作らないようにする */
    private val projectCreationMutex = Mutex()

    /**
     * 切り替えやホームへの移動で閉じたが、まだ保存し終えていないプロジェクト（プロジェクトごと）。
     * 直後に終了しても失わないよう終了時にも書き込む
     */
    private val closingProjects = ConcurrentHashMap<Long, ImagePrepViewModelState>()
    private val previewCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, PreviewSource>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PreviewSource>?) = size > 4
        },
    )

    init {
        refreshTools()
        observePreview()
        observeChangesToSave()
        viewModelScope.launch { restore(initialFiles) }
    }

    private fun mutate(transform: (ImagePrepViewModelState) -> ImagePrepViewModelState) {
        viewModelStateFlow.update(transform)
    }

    // region プロジェクト

    private suspend fun restore(initialFiles: List<File>) {
        projectStore.loadPenTool()?.let { tool -> mutate { it.copy(penTool = tool) } }
        if (initialFiles.isNotEmpty()) {
            addFiles(initialFiles)
        } else {
            projectStore.loadLastProjectId()?.let { openProject(it) }
        }
        refreshProjects()
        mutate { it.copy(isRestoring = false) }
    }

    private suspend fun refreshProjects() {
        val projects = projectStore.listProjects()
        mutate { it.copy(projects = projects) }
    }

    /** 開いているプロジェクトが無ければ、最後に書き出した設定で新しく作って開く */
    private suspend fun ensureProject() {
        projectCreationMutex.withLock {
            if (viewModelStateFlow.value.project != null) return
            val exportSettings = projectStore.loadLastExportSettings() ?: legacyExportSettings()
            val now = System.currentTimeMillis()
            val name = DefaultProjectNameFormat.format(Instant.ofEpochMilli(now))
            val id = projectStore.createProject(name, exportSettings, now)
            val opened = viewModelStateFlow.updateAndGet { state ->
                state.copy(project = OpenProject(id, name), isWorkspaceOpen = true).withExportSettings(exportSettings)
            }
            saveMutex.withLock { savedProject = id to opened.toProjectContent() }
            projectStore.saveLastProjectId(id)
        }
        refreshProjects()
    }

    private fun legacyExportSettings() = ExportSettings(
        options = settings.loadOptions(),
        outputPathMode = settings.loadOutputPathMode(),
        relativeOutputPath = settings.loadRelativeOutputPath(),
        absoluteOutputDir = null,
    )

    private fun switchProject(id: Long) {
        if (rejectWhileExporting()) return
        if (viewModelStateFlow.value.project?.id == id) return
        loadJob?.cancel()
        val closing = viewModelStateFlow.value
        rememberClosingProject(closing)
        loadJob = viewModelScope.launch {
            saveClosingProject(closing)
            openProject(id)
        }
    }

    private suspend fun openProject(id: Long) {
        // ホームに戻った直後に開き直すと、閉じたときの保存がまだ終わっていないことがある。
        // 保存前の内容を読むと、その後の差分保存で直前の編集を上書きしてしまう
        val pending = closingProjects[id]
        if (pending != null && !saveClosingProject(pending)) return
        val stored = projectStore.loadProject(id)
        if (stored == null) {
            messageChannel.send(SnackbarMessage("プロジェクトが見つかりません"))
            refreshProjects()
            return
        }
        val restored = runInterruptible(Dispatchers.IO) { restoreImages(stored) }
        saveMutex.withLock { savedProject = id to stored.content }
        mutate { it.withoutProject().withProject(stored, restored) }
        projectStore.markOpened(id, System.currentTimeMillis())
        projectStore.saveLastProjectId(id)
        loadFileDates(restored.available.filter { !it.removed }.map { it.file })
        refreshProjects()
        val message = buildList {
            if (restored.missing.isNotEmpty()) add("${restored.missing.size} 枚の画像が見つかりません")
            if (restored.changedCount > 0) add("内容が変わった ${restored.changedCount} 枚の編集を取り消しました")
        }
        if (message.isNotEmpty()) messageChannel.send(SnackbarMessage(message.joinToString("・")))
    }

    /** 画像ファイルが残っているかと、保存した後に中身が変わっていないかを確かめる */
    private fun restoreImages(stored: StoredProject): RestoredImages {
        val (available, missing) = stored.content.images.partition { it.removed || it.file.isFile }
        val checked = available.map { it.withoutEditsIfChanged(stored.stamps[it.file]) }
        val missingFiles = missing.map { it.file }.toSet()
        return RestoredImages(
            available = checked,
            missing = missing,
            missingStamps = stored.stamps.filterKeys { it in missingFiles },
            changedCount = checked.zip(available).count { (after, before) -> after != before },
        )
    }

    private class RestoredImages(
        /** 一覧から外した画像は、ファイルが無くても外したまま残す */
        val available: List<ProjectImage>,
        val missing: List<ProjectImage>,
        val missingStamps: Map<File, FileStamp>,
        val changedCount: Int,
    )

    private fun ImagePrepViewModelState.withProject(stored: StoredProject, restored: RestoredImages): ImagePrepViewModelState {
        val content = stored.content
        val active = restored.available.filter { !it.removed }.map { it.file }
        val activeSet = active.toSet()
        val selection = restored.available.filter { !it.removed && it.selected }.map { it.file }.toSet()
        val focused = content.focusedFile?.takeIf { it in activeSet } ?: active.firstOrNull()
        return copy(
            project = OpenProject(stored.id, stored.name),
            isWorkspaceOpen = true,
            images = active.map(::ImageItem),
            removedFiles = restored.available.filter { it.removed }.map { it.file }.toSet(),
            unavailableImages = restored.missing,
            unavailableStamps = restored.missingStamps,
            focusedFile = focused,
            selection = selection.ifEmpty { setOfNotNull(focused) },
            anchor = focused,
            isSelectionMode = content.isSelectionMode && selection.isNotEmpty(),
            crops = restored.available.mapNotNull { image -> image.crop?.let { image.file to it } }.toMap(),
            rotations = restored.available.filter { it.rotation != Rotation.None }.associate { it.file to it.rotation },
            strokes = restored.available.filter { it.strokes.isNotEmpty() }.associate { it.file to it.strokes },
            sortOrder = content.sortOrder,
        ).withExportSettings(content.exportSettings)
    }

    private fun createProject() {
        if (rejectWhileExporting()) return
        loadJob?.cancel()
        val closing = viewModelStateFlow.value
        rememberClosingProject(closing)
        // 作り終えるまでの間にホームが見えないよう、作業画面のまま空にする
        mutate { it.withoutProject().copy(isWorkspaceOpen = true) }
        loadJob = viewModelScope.launch {
            saveClosingProject(closing)
            ensureProject()
        }
    }

    private fun renameProject(name: String) {
        val project = viewModelStateFlow.value.project ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        mutate { it.copy(project = project.copy(name = trimmed)) }
        viewModelScope.launch {
            projectStore.renameProject(project.id, trimmed)
            refreshProjects()
        }
    }

    private fun deleteProject(id: Long) {
        val isCurrent = viewModelStateFlow.value.project?.id == id
        if (isCurrent && rejectWhileExporting()) return
        if (isCurrent) {
            loadJob?.cancel()
            mutate { it.withoutProject() }
        }
        viewModelScope.launch {
            saveMutex.withLock {
                projectStore.deleteProject(id)
                if (savedProject?.first == id) savedProject = null
            }
            refreshProjects()
        }
    }

    /** 操作が落ち着いたところで、開いているプロジェクトと道具の設定を保存する */
    private fun observeChangesToSave() {
        viewModelScope.launch {
            viewModelStateFlow
                .map { it.savedFields() }
                .distinctUntilChanged()
                .debounce(SAVE_DELAY_MILLIS)
                .collect { saveProject(viewModelStateFlow.value) }
        }
        viewModelScope.launch {
            viewModelStateFlow.map { it.penTool }.distinctUntilChanged().drop(1).debounce(SAVE_DELAY_MILLIS).collect(projectStore::savePenTool)
        }
    }

    /**
     * 保存の完了まで UI スレッドを止めることがあるため、保存は UI スレッドに戻らずに済ませる。
     * 戻る必要があると [saveBeforeExit] と待ち合って止まる
     */
    /** @return 保存できなかったときは false。書く必要が無かったときは true */
    private suspend fun saveProject(state: ImagePrepViewModelState): Boolean {
        val project = state.project ?: return true
        val result = withContext(Dispatchers.IO) {
            val content = state.toProjectContent()
            saveMutex.withLock {
                val previous = savedProject?.takeIf { it.first == project.id }?.second
                if (previous == content) return@withLock SaveResult.Unchanged
                try {
                    projectStore.saveProject(project.id, previous, content)
                    savedProject = project.id to content
                    SaveResult.Saved
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                    messageChannel.trySend(SnackbarMessage("作業内容を保存できませんでした（${e.message ?: e.javaClass.simpleName}）"))
                    SaveResult.Failed
                }
            }
        }
        // プロジェクト一覧に出す画像の数を合わせる
        if (result == SaveResult.Saved) refreshProjects()
        return result != SaveResult.Failed
    }

    private enum class SaveResult { Saved, Unchanged, Failed }

    private fun rememberClosingProject(closing: ImagePrepViewModelState) {
        val project = closing.project ?: return
        closingProjects[project.id] = closing
    }

    /** @return 保存できなかったときは false。失敗した状態は終了時にもう一度書き込むため残す */
    private suspend fun saveClosingProject(closing: ImagePrepViewModelState): Boolean {
        val project = closing.project ?: return true
        val saved = saveProject(closing)
        if (saved) closingProjects.remove(project.id, closing)
        return saved
    }

    /** 終了の直前に、まだ保存していない変更を書き込む */
    private fun saveBeforeExit() {
        val state = viewModelStateFlow.value
        val closing = closingProjects.values.toList()
        runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(EXIT_SAVE_TIMEOUT_MILLIS) {
                closing.forEach { saveProject(it) }
                saveProject(state)
                projectStore.savePenTool(state.penTool)
            }
        }
    }

    // endregion

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
            ensureProject()
            mutate { state ->
                val existing = state.images.map { it.file.absoluteFile }.toSet()
                val added = files.map { it.absoluteFile }.filter { it !in existing }
                if (state.images.isEmpty()) {
                    val loaded = state.withAddedImages(added)
                    val first = loaded.orderedImages.firstOrNull()?.file
                    loaded.copy(
                        focusedFile = first,
                        selection = setOfNotNull(first),
                        anchor = first,
                        isSelectionMode = false,
                        lastRemoval = null,
                    )
                } else {
                    state.withAddedImages(added).copy(lastRemoval = null)
                }
            }
            loadFileDates(files.map { it.absoluteFile })
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
            if (added.isNotEmpty()) {
                ensureProject()
                mutate { state -> state.withAddedImages(added).copy(focusedFile = state.focusedFile ?: added.first()) }
            }
            loadFileDates(added)
            val message = buildList {
                if (added.isNotEmpty()) add("${added.size} 枚を追加しました")
                if (supported.size > added.size) add("${supported.size - added.size} 枚は追加済みです")
                if (ignored > 0) add("非対応の $ignored 件を除外しました")
            }.ifEmpty { listOf("追加できる画像がありません") }
            messageChannel.send(SnackbarMessage(message.joinToString("・")))
        }
    }

    /** 撮影日などでの並べ替えに使う日時を後から読む。読み終わるまでの画像は日時の無い画像として並ぶ */
    private fun loadFileDates(files: List<File>) {
        if (files.isEmpty()) return
        viewModelScope.launch {
            val dates = runInterruptible(Dispatchers.IO) { files.associateWith(FileDatesReader::read) }
            mutate { it.copy(fileDates = it.fileDates + dates) }
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

    internal fun snapshotForTest(): ImagePrepUiState = viewModelStateFlow.value.toUiState(listener, ::projectListenerOf)

    internal fun addFilesForTest(files: List<File>) {
        mutate { it.copy(images = files.map(::ImageItem), isWorkspaceOpen = true, focusedFile = files.firstOrNull()) }
    }

    internal fun setToolsForTest(tools: ExternalTools) {
        mutate { it.copy(tools = tools) }
    }

    /** ホームに戻る。プロジェクトは保存してあるので、一覧からまた開ける */
    private fun closeAll() {
        // 書き出しの準備中・実行中は閉じない（閉じた画像が書き出されるのを防ぐ）
        val export = viewModelStateFlow.value.export
        if (export is ExportState.Preparing || export is ExportState.Running || exportJob?.isActive == true) return
        loadJob?.cancel()
        val closing = viewModelStateFlow.value
        rememberClosingProject(closing)
        mutate { it.withoutProject() }
        viewModelScope.launch {
            saveClosingProject(closing)
            projectStore.saveLastProjectId(null)
            refreshProjects()
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
                val ordered = state.orderedImages
                val firstRemovedIndex = ordered.indexOfFirst { it.file in targets }
                val after = ordered.drop(firstRemovedIndex).firstOrNull { it.file !in targets }
                (after ?: ordered.lastOrNull { it.file !in targets })?.file
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
                    val ordered = state.orderedImages
                    val from = ordered.indexOfFirst { it.file == (state.anchor ?: state.focusedFile) }
                    val to = ordered.indexOfFirst { it.file == file }
                    if (from < 0 || to < 0) return@mutate state.copy(focusedFile = file, selection = setOf(file), anchor = file, isSelectionMode = false)
                    val range = ordered.subList(minOf(from, to), maxOf(from, to) + 1).map { it.file }
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
            val ordered = state.orderedImages
            if (ordered.isEmpty()) return@mutate state
            val current = ordered.indexOfFirst { it.file == state.focusedFile }.coerceAtLeast(0)
            val next = ordered[(current + delta).coerceIn(0, ordered.lastIndex)].file
            state.copy(focusedFile = next, selection = setOf(next), anchor = next, isSelectionMode = false)
        }
    }

    /** Shift + 矢印。フォーカスを動かし、起点からフォーカス先までを選択する */
    private fun extendSelection(delta: Int) {
        mutate { state ->
            val ordered = state.orderedImages
            if (ordered.isEmpty()) return@mutate state
            val current = ordered.indexOfFirst { it.file == state.focusedFile }.coerceAtLeast(0)
            val next = (current + delta).coerceIn(0, ordered.lastIndex)
            val anchor = ordered.indexOfFirst { it.file == state.anchor }.takeIf { it >= 0 } ?: current
            val range = ordered.subList(minOf(anchor, next), maxOf(anchor, next) + 1).map { it.file }
            state.copy(
                focusedFile = ordered[next].file,
                selection = range.toSet(),
                anchor = ordered[anchor].file,
                isSelectionMode = range.size > 1,
            )
        }
    }

    private fun selectSortKey(key: ImageSortKey) {
        mutate { it.copy(sortOrder = it.sortOrder.copy(key = key)) }
    }

    private fun toggleSortDirection() {
        mutate { it.copy(sortOrder = it.sortOrder.copy(ascending = !it.sortOrder.ascending)) }
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

    /** 切り抜き範囲とペンの線は回転後の画像に対する割合なので、同じ部分を指し続けるよう一緒に回す */
    private fun rotate(
        file: File,
        rotateRotation: (Rotation) -> Rotation,
        rotateCrop: (CropRect) -> CropRect,
        rotateStroke: (PenStroke) -> PenStroke,
    ) {
        mutate { state ->
            val rotation = rotateRotation(state.rotations[file] ?: Rotation.None)
            val crop = state.crops[file]
            val strokes = state.strokes[file]
            state.copy(
                rotations = if (rotation == Rotation.None) state.rotations - file else state.rotations + (file to rotation),
                crops = if (crop == null) state.crops else state.crops + (file to rotateCrop(crop)),
                strokes = if (strokes == null) state.strokes else state.strokes + (file to strokes.map(rotateStroke)),
            )
        }
    }

    private fun setStrokes(file: File, strokes: List<PenStroke>) {
        mutate { state ->
            if (strokes.isEmpty()) state.copy(strokes = state.strokes - file) else state.copy(strokes = state.strokes + (file to strokes))
        }
    }

    private fun chooseOutputDirectory(dir: File) {
        mutate { it.copy(selectedOutputDir = dir) }
    }

    private fun setOutputPathMode(mode: OutputPathMode) {
        mutate { it.copy(outputPathMode = mode) }
    }

    private fun setRelativeOutputPath(path: String) {
        mutate { it.copy(relativeOutputPath = path) }
    }

    private fun setPenTool(tool: PenTool) {
        mutate { it.copy(penTool = tool) }
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
        val ui = state.toUiState(listener, ::projectListenerOf)
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
                    ).map {
                        it.copy(
                            rotation = state.rotations[it.source] ?: Rotation.None,
                            crop = state.crops[it.source],
                            strokes = state.strokes[it.source].orEmpty(),
                        )
                    }
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
        val exportSettings = viewModelStateFlow.value.exportSettings.copy(options = options)
        viewModelScope.launch { projectStore.saveLastExportSettings(exportSettings) }
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
        viewModelStateFlow.map { it.rotations[file] ?: Rotation.None }.distinctUntilChanged().collectLatestSafe { rotation ->
            val rotated = withContext(Dispatchers.Default) { Rotator.rotate(source.loaded.image, rotation) }
            val rotatedSize = Rotator.rotatedSize(source.loaded.size, rotation)
            val original = withContext(Dispatchers.Default) { rotated.toComposeImageBitmap() }
            mutate {
                it.copy(
                    preview = it.preview.copy(
                        original = original,
                        originalSize = rotatedSize,
                        captionFields = source.fields,
                    ),
                )
            }
            renderProcessedPreview(file, LoadedImage(rotated, rotatedSize), source.fields, tools)
        }
    }

    private suspend fun renderProcessedPreview(file: File, rotated: LoadedImage, fields: Map<CaptionField, String>, tools: ExternalTools) {
        coroutineScope {
            // ペンで描いている間は書き出し後のプレビューより先に、線を描いた元画像だけを素早く返す
            launch { observePaintedPreview(file, rotated) }
            observeProcessedPreview(file, rotated, fields, tools)
        }
    }

    private suspend fun observePaintedPreview(file: File, rotated: LoadedImage) {
        viewModelStateFlow.map { it.strokes[file].orEmpty() }.distinctUntilChanged().collectLatestSafe { strokes ->
            val painted = if (strokes.isEmpty()) {
                null
            } else {
                runInterruptible(Dispatchers.Default) { PenPainter.paint(rotated.image, strokes).toComposeImageBitmap() }
            }
            mutate { it.copy(preview = it.preview.copy(painted = painted)) }
        }
    }

    private suspend fun observeProcessedPreview(file: File, rotated: LoadedImage, fields: Map<CaptionField, String>, tools: ExternalTools) {
        // 非 HEIF 画像はツール確認前に開かれることがあり、cwebp が見つかったら WebP のプレビューを作り直す
        val keys = viewModelStateFlow.map { ProcessedPreviewKey(it.options, it.tools ?: tools, it.crops[file], it.strokes[file].orEmpty()) }
        keys.distinctUntilChanged().collectLatestSafe { key ->
            val options = key.options
            val currentTools = key.tools
            mutate { it.copy(preview = it.preview.copy(loading = true)) }
            val outputFormat = OutputPlanner.resolveFormat(file, options.outputFormat)
            // cwebp は外部プロセスで重いため、品質スライダー操作中は待ってからまとめてエンコードする
            delay(if (outputFormat == OutputFormat.Webp) 300 else 80)
            val outputSize = Resizer.targetSize(Cropper.croppedSize(rotated.size, key.crop), options)
            val image = runInterruptible(Dispatchers.Default) { Cropper.crop(PenPainter.paint(rotated.image, key.strokes), key.crop) }
            val renderSize = Resizer.fitWithin(outputSize, image.width, image.height)
            val processor = ImageProcessor(currentTools)
            val caption = if (options.captionEnabled) {
                CaptionTemplate.render(options.captionTemplate, fields).takeIf { it.isNotBlank() }
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

    private data class ProcessedPreviewKey(
        val options: EditOptions,
        val tools: ExternalTools,
        val crop: CropRect?,
        val strokes: List<PenStroke>,
    )

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
        private const val SAVE_DELAY_MILLIS = 300L
        private const val EXIT_SAVE_TIMEOUT_MILLIS = 5000L
        private val DefaultProjectNameFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
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

private val DefaultPenTool = PenTool(kind = PenKind.Draw, widthPercent = 1.5f, color = 0xFFE53935.toInt(), blurPercent = 1.5f)

private val EmptyPreview = PreviewState(
    file = null,
    original = null,
    painted = null,
    processed = null,
    originalSize = null,
    outputSize = null,
    outputFormat = null,
    outputByteSize = null,
    captionFields = mapOf(),
    crop = null,
    strokes = listOf(),
    loading = false,
    error = null,
)

/** 開いているプロジェクト */
internal data class OpenProject(val id: Long, val name: String)

internal data class ImagePrepViewModelState(
    val project: OpenProject? = null,
    /** プロジェクト一覧。最後に開いた順 */
    val projects: List<ProjectSummary> = listOf(),
    /** 起動時に前回のプロジェクトを開き終えるまで */
    val isRestoring: Boolean = true,
    /** 同じフォルダの画像が続けて並ぶ（[groupedByFolder]）。フォルダ内は追加した順で、画面の並びは [orderedImages] */
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
    /** 絶対パスで選んだ書き出し先 */
    val selectedOutputDir: File? = null,
    val outputPathMode: OutputPathMode = OutputPathMode.Relative,
    /** [outputPathMode] が相対パスのときの、元画像のフォルダからのパス */
    val relativeOutputPath: String = RelativeOutputPath.DEFAULT,
    val tools: ExternalTools? = null,
    val preview: PreviewState = EmptyPreview,
    val export: ExportState = ExportState.Idle,
    val isLoading: Boolean = false,
    /** 入力欄が不正な値になっている設定項目 */
    val invalidInputs: Set<String> = setOf(),
    /** 元に戻せる直前の削除 */
    val lastRemoval: Removal? = null,
    /** このプロジェクトで一覧から削除した画像。元画像なので書き出しで上書きしない */
    val removedFiles: Set<File> = setOf(),
    /** プロジェクトを開いたときに見つからなかった画像。一覧には出さず、ファイルが戻ったときのために保存だけしておく */
    val unavailableImages: List<ProjectImage> = listOf(),
    /** [unavailableImages] を保存したときの大きさと日時。ファイルが戻ったときに中身が同じかを確かめる */
    val unavailableStamps: Map<File, FileStamp> = mapOf(),
    /** 画像ごとの切り抜き範囲。切り抜かない画像は含めない */
    val crops: Map<File, CropRect> = mapOf(),
    /** 画像ごとの回転。回さない画像は含めない */
    val rotations: Map<File, Rotation> = mapOf(),
    /** 画像ごとのペンの線。描いていない画像は含めない */
    val strokes: Map<File, List<PenStroke>> = mapOf(),
    val sortOrder: ImageSortOrder = ImageSortOrder(ImageSortKey.Name, ascending = true),
    /** 並べ替えに使う日時。読み込み中の画像は含まれない */
    val fileDates: Map<File, FileDates> = mapOf(),
    val penTool: PenTool = DefaultPenTool,
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

    /** 画面に並べる順。フォルダの順は [images] のまま、フォルダ内を [sortOrder] で並べ替える */
    val orderedImages: List<ImageItem>
        get() = images.groupBy { it.file.folder }.values.flatMap { group -> group.sortedWith(sortOrder.comparator(fileDates)) }

    val exportTargets: List<ImageItem>
        get() = orderedImages.filter { it.file in effectiveSelection }

    /** 相対パスの基準。複数フォルダの画像があっても先頭の画像のフォルダにまとめる */
    val sourceFolder: File?
        get() = images.firstOrNull()?.file?.folder

    val outputDir: File?
        get() = when (outputPathMode) {
            OutputPathMode.Absolute -> selectedOutputDir
            OutputPathMode.Relative -> sourceFolder?.let { RelativeOutputPath.resolve(it, relativeOutputPath) }
        }

    val isRelativeOutputPathInvalid: Boolean
        get() = outputPathMode == OutputPathMode.Relative && !RelativeOutputPath.isValid(relativeOutputPath)

    val effectiveSelection: Set<File>
        get() = selection.ifEmpty { setOfNotNull(focusedFile) }

    fun targetsFor(file: File): Set<File> = effectiveSelection.takeIf { file in it } ?: setOf(file)

    fun canRead(file: File, tools: ExternalTools): Boolean = !ImageLoader.isHeif(file) || tools.heifDecoder != null

    val exportSettings: ExportSettings
        get() = ExportSettings(options, outputPathMode, relativeOutputPath, selectedOutputDir)

    fun withExportSettings(settings: ExportSettings): ImagePrepViewModelState = copy(
        options = settings.options,
        outputPathMode = settings.outputPathMode,
        relativeOutputPath = settings.relativeOutputPath,
        selectedOutputDir = settings.absoluteOutputDir,
    )

    /** 一覧に画像を加える。前に一覧から外した画像や見つからなかった画像なら、編集した内容を戻す */
    fun withAddedImages(added: List<File>): ImagePrepViewModelState {
        val addedSet = added.toSet()
        val (missingReturned, stillMissing) = unavailableImages.partition { it.file in addedSet }
        // 同じ場所に別の画像を置いた場合、前の画像に向けた編集は当てはまらない
        val returned = missingReturned.map { it.withoutEditsIfChanged(unavailableStamps[it.file]) }
        return copy(
            images = (images + added.map(::ImageItem)).groupedByFolder(),
            isWorkspaceOpen = true,
            removedFiles = removedFiles - addedSet,
            unavailableImages = stillMissing,
            unavailableStamps = unavailableStamps - addedSet,
            crops = crops + returned.mapNotNull { image -> image.crop?.let { image.file to it } },
            rotations = rotations + returned.filter { it.rotation != Rotation.None }.map { it.file to it.rotation },
            strokes = strokes + returned.filter { it.strokes.isNotEmpty() }.map { it.file to it.strokes },
        )
    }

    /** ホームに戻ったときの状態。道具の設定やプロジェクト一覧など、プロジェクトに属さないものは残す */
    fun withoutProject(): ImagePrepViewModelState = ImagePrepViewModelState(
        projects = projects,
        isRestoring = isRestoring,
        options = options,
        tools = tools,
        isLoading = isLoading,
        penTool = penTool,
    )

    /** 保存する内容に関わる値。変わったかどうかを安く比べるため、まとめる前の値を並べる */
    fun savedFields(): List<Any?> = listOf(
        project?.id,
        images,
        removedFiles,
        unavailableImages,
        focusedFile,
        selection,
        isSelectionMode,
        exportSettings,
        crops,
        rotations,
        strokes,
        sortOrder,
    )

    fun toProjectContent(): ProjectContent {
        val active = images.map { it.file }
        val activeSet = active.toSet()
        val effectiveSelection = effectiveSelection
        fun imageOf(file: File, removed: Boolean) = ProjectImage(
            file = file,
            removed = removed,
            selected = !removed && file in effectiveSelection,
            crop = crops[file],
            rotation = rotations[file] ?: Rotation.None,
            strokes = strokes[file].orEmpty(),
        )
        return ProjectContent(
            exportSettings = exportSettings,
            sortOrder = sortOrder,
            focusedFile = focusedFile,
            isSelectionMode = isSelectionMode,
            images = active.map { imageOf(it, removed = false) } +
                removedFiles.filter { it !in activeSet }.map { imageOf(it, removed = true) } +
                unavailableImages,
        )
    }
}

internal fun ImagePrepViewModelState.toUiState(
    listener: ImagePrepUiState.Listener,
    projectListenerOf: (ProjectSummary) -> ProjectItem.Listener,
): ImagePrepUiState {
    val targets = exportTargets
    val outputDir = outputDir
    val notices = buildList {
        if (isRelativeOutputPathInvalid) {
            add(Notice("書き出し先の相対パスを入力してください（絶対パスは指定できません）。", blocking = true, action = null))
        }
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
        if (outputDir != null && outputPathMode == OutputPathMode.Relative && targetFolderCount > 1) {
            add(Notice("$targetFolderCount つのフォルダの画像を 1 つの出力先にまとめて書き出します。", blocking = false, action = null))
        }
    }
    val orderedImages = orderedImages
    return ImagePrepUiState(
        projectName = project?.name,
        projects = projects.map { summary ->
            ProjectItem(
                name = summary.name,
                imageCount = summary.imageCount,
                lastOpenedAtMillis = summary.lastOpenedAtMillis,
                isCurrent = summary.id == project?.id,
                listener = projectListenerOf(summary),
            )
        },
        isRestoring = isRestoring,
        images = orderedImages,
        isWorkspaceOpen = isWorkspaceOpen,
        imageGroups = orderedImages.groupBy { it.file.folder }.map { (folder, items) -> ImageGroup(folder, items) },
        exportCount = targets.size,
        isExportingSelection = isSelectionMode,
        focusedFile = focusedFile,
        selectedFiles = effectiveSelection,
        sortOrder = sortOrder,
        pickerInitialDirectory = (focusedFile ?: images.lastOrNull()?.file)?.folder,
        options = options,
        outputDirectory = outputDir,
        outputPathMode = outputPathMode,
        relativeOutputPath = relativeOutputPath,
        tools = tools,
        preview = preview.copy(crop = preview.file?.let(crops::get), strokes = preview.file?.let(strokes::get).orEmpty()),
        penTool = penTool,
        export = export,
        notices = notices,
        isLoading = isLoading,
        listener = listener,
    )
}

private val File.folder: File get() = absoluteFile.parentFile

/** 保存した後に中身が変わった画像なら、前の中身に向けた切り抜き・回転・ペンを取り消す */
private fun ProjectImage.withoutEditsIfChanged(stamp: FileStamp?): ProjectImage {
    val hasEdits = crop != null || rotation != Rotation.None || strokes.isNotEmpty()
    val isChanged = hasEdits && stamp != null && file.isFile && stamp != FileStamp.of(file)
    return if (isChanged) copy(crop = null, rotation = Rotation.None, strokes = listOf()) else this
}

/** フォルダが最初に現れた順にまとめ、フォルダ内の並びは保つ */
private fun List<ImageItem>.groupedByFolder(): List<ImageItem> = groupBy { it.file.folder }.values.flatten()

private fun ImageSortOrder.comparator(fileDates: Map<File, FileDates>): Comparator<ImageItem> {
    val byName = compareBy(NaturalOrder) { item: ImageItem -> item.file }.let { if (ascending) it else it.reversed() }
    val dateOf: (FileDates) -> Long? = when (key) {
        ImageSortKey.Name -> return byName
        ImageSortKey.Captured -> FileDates::capturedAtMillis
        ImageSortKey.Modified -> FileDates::modifiedAtMillis
        ImageSortKey.Created -> FileDates::createdAtMillis
    }
    val dateOrder: Comparator<Long> = if (ascending) naturalOrder() else reverseOrder()
    // 日時を読めない・まだ読んでいない画像は降順でも末尾に置く
    return compareBy(nullsLast(dateOrder)) { item: ImageItem -> fileDates[item.file]?.let(dateOf) }.then(byName)
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
