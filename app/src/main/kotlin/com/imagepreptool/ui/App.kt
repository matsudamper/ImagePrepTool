package com.imagepreptool.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import java.awt.Window
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.io.IOException
import kotlinx.coroutines.launch
import com.imagepreptool.AppRelauncher
import com.imagepreptool.presentation.ExportState
import com.imagepreptool.presentation.ImagePrepUiState
import com.imagepreptool.presentation.ImagePrepViewModel
import com.imagepreptool.presentation.NoticeAction
import com.imagepreptool.presentation.ProjectItem
import com.imagepreptool.resources.Res
import com.imagepreptool.resources.ic_add_photo
import com.imagepreptool.resources.ic_download
import com.imagepreptool.resources.ic_extension
import com.imagepreptool.resources.ic_folder_open
import com.imagepreptool.resources.ic_home
import com.imagepreptool.ui.components.Tooltip
import com.imagepreptool.ui.theme.AppTheme
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun App(
    viewModel: ImagePrepViewModel,
    dialogParent: Window?,
    exitApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiStateFlow.collectAsState()
    val listener = uiState.listener
    val latestUiState by rememberUpdatedState(uiState)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showTools by remember { mutableStateOf(false) }
    var showRenameProject by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<ProjectItem?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbar.currentSnackbarData?.dismiss()
            // collect を止めないよう、表示と結果待ちは別のコルーチンで行う
            launch {
                if (message.canUndoRemoval) {
                    val result = snackbar.showSnackbar(message.text, actionLabel = "元に戻す", duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) latestUiState.listener.undoRemoval()
                } else {
                    snackbar.showSnackbar(message.text)
                }
            }
        }
    }

    val actions = remember(listener, dialogParent) {
        AppActions(
            openFolder = {
                scope.launch {
                    DesktopDialogs.pickDirectory(dialogParent, "画像のあるフォルダを選択", latestUiState.pickerInitialDirectory)
                        ?.let(listener::openFolder)
                }
            },
            pickImages = {
                scope.launch {
                    DesktopDialogs.pickImages(dialogParent, latestUiState.pickerInitialDirectory)
                        .takeIf { it.isNotEmpty() }
                        ?.let(listener::addFiles)
                }
            },
            showMessage = { message -> scope.launch { snackbar.showSnackbar(message) } },
        )
    }

    var dragging by remember { mutableStateOf(false) }
    val dropTarget = remember(listener) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                dragging = true
            }

            override fun onExited(event: DragAndDropEvent) {
                dragging = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                dragging = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                dragging = false
                val files = droppedFiles(event)
                if (files.isEmpty()) return false
                listener.addFiles(files)
                return true
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropTarget),
        ) {
            when {
                // 前回のプロジェクトを開き終えるまでは何も出さず、ホームが一瞬見えないようにする
                uiState.isRestoring -> Unit
                !uiState.isWorkspaceOpen -> {
                    EmptyState(
                        projects = uiState.projects,
                        isLoading = uiState.isLoading,
                        onOpenFolder = actions.openFolder,
                        onPickImages = actions.pickImages,
                        onDeleteProject = { projectToDelete = it },
                    )
                    ToolsButton(uiState, onClick = { showTools = true }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
                }
                else -> Workspace(
                    uiState = uiState,
                    actions = actions,
                    dialogParent = dialogParent,
                    onRenameProject = { showRenameProject = true },
                    onDeleteProject = { projectToDelete = it },
                )
            }

            AnimatedVisibility(visible = dragging, enter = fadeIn(), exit = fadeOut()) {
                DropOverlay()
            }

            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
        }
    }

    when (val export = uiState.export) {
        ExportState.Idle, ExportState.Preparing -> Unit
        is ExportState.ConfirmConflicts -> ConflictDialog(export, listener::resolveConflicts)
        is ExportState.Running -> ProgressDialog(export, listener::cancelExport)
        is ExportState.Finished -> ResultDialog(
            state = export,
            onOpenFolder = {
                DesktopDialogs.openFolder(export.outputDir)?.let(actions.showMessage)
                listener.dismissExport()
            },
            onClose = listener::dismissExport,
        )
    }

    val projectName = uiState.projectName
    if (showRenameProject && projectName != null) {
        RenameProjectDialog(
            currentName = projectName,
            onConfirm = { name ->
                showRenameProject = false
                listener.renameProject(name)
            },
            onCancel = { showRenameProject = false },
        )
    }

    projectToDelete?.let { project ->
        DeleteProjectDialog(
            project = project,
            onConfirm = {
                projectToDelete = null
                project.listener.delete()
            },
            onCancel = { projectToDelete = null },
        )
    }

    if (showTools) {
        ToolsDialog(
            tools = uiState.tools,
            onRestart = {
                try {
                    AppRelauncher.launchNewInstance()
                    exitApp()
                } catch (e: IOException) {
                    actions.showMessage("再起動できませんでした: ${e.message}")
                }
            },
            onClose = { showTools = false },
        )
    }
}

class AppActions(
    val openFolder: () -> Unit,
    val pickImages: () -> Unit,
    val showMessage: (String) -> Unit,
)

@OptIn(ExperimentalComposeUiApi::class)
private fun droppedFiles(event: DragAndDropEvent): List<File> {
    val transferable = event.awtTransferable
    if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return emptyList()
    return runCatching {
        (transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>).filterIsInstance<File>()
    }.getOrDefault(emptyList())
}

@Composable
private fun Workspace(
    uiState: ImagePrepUiState,
    actions: AppActions,
    dialogParent: Window?,
    onRenameProject: () -> Unit,
    onDeleteProject: (ProjectItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var imageListWidth by remember { mutableStateOf(312.dp) }
    Column(modifier.fillMaxSize()) {
        TopBar(
            projectName = uiState.projectName.orEmpty(),
            projects = uiState.projects,
            actions = actions,
            onCreateProject = uiState.listener::createProject,
            onRenameProject = onRenameProject,
            onDeleteProject = onDeleteProject,
            onGoHome = uiState.listener::closeAll,
        )
        Row(Modifier.weight(1f).fillMaxWidth()) {
            ImageListPanel(
                imageGroups = uiState.imageGroups,
                focusedFile = uiState.focusedFile,
                selectedFiles = uiState.selectedFiles,
                isSelectionMode = uiState.isExportingSelection,
                sortOrder = uiState.sortOrder,
                tools = uiState.tools,
                onSelectSortKey = uiState.listener::selectSortKey,
                onToggleSortDirection = uiState.listener::toggleSortDirection,
                onClickImage = uiState.listener::clickImage,
                onRemoveSelection = uiState.listener::removeSelection,
                onUndoRemoval = uiState.listener::undoRemoval,
                onSelectAll = uiState.listener::selectAll,
                onClearSelection = uiState.listener::clearSelection,
                onMoveFocus = uiState.listener::moveFocus,
                onExtendSelection = uiState.listener::extendSelection,
                onRemove = uiState.listener::removeImage,
                onReveal = { file -> DesktopDialogs.revealFile(file)?.let(actions.showMessage) },
                onOpenFolder = { folder -> DesktopDialogs.openFolder(folder)?.let(actions.showMessage) },
                onRemoveFolder = uiState.listener::removeFolder,
                modifier = Modifier.width(imageListWidth),
            )
            PanelResizeHandle(
                onDrag = { delta -> imageListWidth = (imageListWidth + delta).coerceIn(ImageListMinWidth, ImageListMaxWidth) },
            )
            val index = uiState.images.indexOfFirst { it.file == uiState.focusedFile }
            PreviewPane(
                preview = uiState.preview,
                item = uiState.images.getOrNull(index),
                index = index,
                total = uiState.images.size,
                options = uiState.options,
                penTool = uiState.penTool,
                onMove = uiState.listener::moveFocus,
                onCropChange = uiState.listener::setCrop,
                onRotateClockwise = uiState.listener::rotateClockwise,
                onRotateCounterClockwise = uiState.listener::rotateCounterClockwise,
                onStrokesChange = uiState.listener::setStrokes,
                onPenToolChange = uiState.listener::setPenTool,
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(color = colors.outlineVariant)
            SettingsPanel(
                options = uiState.options,
                captionFields = uiState.preview.captionFields,
                hasFocusedImage = uiState.focusedFile != null && uiState.preview.original != null,
                sampleFile = uiState.focusedFile ?: uiState.images.firstOrNull()?.file,
                outputDirectory = uiState.outputDirectory,
                outputPathMode = uiState.outputPathMode,
                relativeOutputPath = uiState.relativeOutputPath,
                notices = uiState.notices,
                exportCount = uiState.exportCount,
                canExport = uiState.canExport,
                onOptionsChange = uiState.listener::updateOptions,
                onInputValidityChange = uiState.listener::setInputValid,
                onChooseOutput = {
                    scope.launch {
                        DesktopDialogs.pickDirectory(dialogParent, "書き出し先のフォルダを選択", uiState.outputDirectory)
                            ?.let(uiState.listener::chooseOutputDirectory)
                    }
                },
                onOutputPathModeChange = uiState.listener::setOutputPathMode,
                onRelativeOutputPathChange = uiState.listener::setRelativeOutputPath,
                onNoticeAction = { action ->
                    when (action) {
                        NoticeAction.RemoveUnreadable -> uiState.listener.removeUnreadable()
                    }
                },
                onExport = uiState.listener::requestExport,
                modifier = Modifier.width(348.dp),
            )
        }
    }
}

/** 左右のパネル境界。ドラッグで左側パネルの幅を変える */
@Composable
private fun PanelResizeHandle(onDrag: (Dp) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(PanelResizeHandleWidth)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { deltaPx -> onDrag(with(density) { deltaPx.toDp() }) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        VerticalDivider(color = colors.outlineVariant)
    }
}

private val ImageListMinWidth = 240.dp
private val ImageListMaxWidth = 720.dp
private val PanelResizeHandleWidth = 6.dp

@Composable
private fun TopBar(
    projectName: String,
    projects: List<ProjectItem>,
    actions: AppActions,
    onCreateProject: () -> Unit,
    onRenameProject: () -> Unit,
    onDeleteProject: (ProjectItem) -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surface, modifier = modifier) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(start = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProjectMenu(
                    projectName = projectName,
                    projects = projects,
                    onCreate = onCreateProject,
                    onRename = onRenameProject,
                    onDelete = onDeleteProject,
                )
                Spacer(Modifier.weight(1f))
                Tooltip("別のフォルダを開く (Ctrl+O)") {
                    IconButton(onClick = actions.openFolder) { Icon(painterResource(Res.drawable.ic_folder_open), "フォルダを追加") }
                }
                Tooltip("画像を追加") {
                    IconButton(onClick = actions.pickImages) { Icon(painterResource(Res.drawable.ic_add_photo), "画像を追加") }
                }
                VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp), color = colors.outlineVariant)
                Tooltip("ホームに戻る") {
                    IconButton(onClick = onGoHome) { Icon(painterResource(Res.drawable.ic_home), "ホームに戻る") }
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
        }
    }
}

@Composable
private fun ToolsButton(uiState: ImagePrepUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val missing = uiState.missingToolCount
    Tooltip(if (missing > 0) "外部ツール（一部の機能が使えません）" else "外部ツール", modifier = modifier) {
        IconButton(onClick = onClick) {
            BadgedBox(badge = { if (missing > 0) Badge(containerColor = AppTheme.extended.warning) }) {
                Icon(painterResource(Res.drawable.ic_extension), "外部ツール")
            }
        }
    }
}

@Composable
private fun DropOverlay() {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxSize().background(colors.scrim.copy(alpha = 0.45f)).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.primaryContainer.copy(alpha = 0.92f), MaterialTheme.shapes.extraLarge)
                .dashedBorder(colors.primary, 24.dp, 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(Res.drawable.ic_download), null, tint = colors.onPrimaryContainer, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("ドロップして追加", style = MaterialTheme.typography.titleLarge, color = colors.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text("フォルダの場合は中の画像を追加します", style = MaterialTheme.typography.bodyMedium, color = colors.onPrimaryContainer)
            }
        }
    }
}
