package com.imagepreptool.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imagepreptool.presentation.ExportState
import com.imagepreptool.presentation.ImagePrepUiState
import com.imagepreptool.presentation.ImagePrepViewModel
import com.imagepreptool.presentation.NoticeAction
import com.imagepreptool.ui.components.Tooltip
import com.imagepreptool.ui.theme.AppTheme
import java.awt.Component
import java.awt.datatransfer.DataFlavor
import java.io.File
import kotlinx.coroutines.launch

/** ファイル選択ダイアログの親にするウィンドウ */
val LocalDialogParent = staticCompositionLocalOf<Component?> { null }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun App(viewModel: ImagePrepViewModel) {
    val uiState by viewModel.uiStateFlow.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val parent = LocalDialogParent.current
    var showTools by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    val actions = remember(viewModel, parent) {
        AppActions(
            openFolder = {
                DesktopDialogs.pickDirectory(parent, "画像のあるフォルダを選択", viewModel.uiStateFlow.value.sourcePath?.let(::File))
                    ?.let(viewModel::openFolder)
            },
            pickImages = {
                DesktopDialogs.pickImages(parent, viewModel.uiStateFlow.value.sourcePath?.let(::File))
                    .takeIf { it.isNotEmpty() }
                    ?.let(viewModel::addFiles)
            },
            showMessage = { message -> scope.launch { snackbar.showSnackbar(message) } },
        )
    }

    var dragging by remember { mutableStateOf(false) }
    val dropTarget = remember(viewModel) {
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
                viewModel.addFiles(files)
                return true
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropTarget),
    ) {
        if (!uiState.hasImages) {
            EmptyState(
                recentFolders = uiState.recentFolders,
                isLoading = uiState.isLoading,
                onOpenFolder = actions.openFolder,
                onPickImages = actions.pickImages,
                onOpenRecent = viewModel::openFolder,
                onForgetRecent = viewModel::forgetRecent,
            )
            ToolsButton(uiState, onClick = { showTools = true }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
        } else {
            Workspace(uiState, viewModel, actions, onShowTools = { showTools = true })
        }

        AnimatedVisibility(visible = dragging, enter = fadeIn(), exit = fadeOut()) {
            DropOverlay()
        }

        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }
    }

    when (val export = uiState.export) {
        ExportState.Idle -> Unit
        is ExportState.ConfirmConflicts -> ConflictDialog(export, viewModel::resolveConflicts)
        is ExportState.Running -> ProgressDialog(export, viewModel::cancelExport)
        is ExportState.Finished -> ResultDialog(
            state = export,
            onOpenFolder = {
                DesktopDialogs.openFolder(export.outputDir)?.let(actions.showMessage)
                viewModel.dismissExport()
            },
            onClose = viewModel::dismissExport,
        )
    }

    if (showTools) {
        ToolsDialog(uiState.tools, onRecheck = viewModel::refreshTools, onClose = { showTools = false })
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
    viewModel: ImagePrepViewModel,
    actions: AppActions,
    onShowTools: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val parent = LocalDialogParent.current
    Column(Modifier.fillMaxSize()) {
        TopBar(uiState, viewModel, actions, onShowTools)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            ImageListPanel(
                images = uiState.images,
                includedCount = uiState.includedCount,
                focusedFile = uiState.focusedFile,
                tools = uiState.tools,
                onFocus = viewModel::focus,
                onToggle = viewModel::toggleIncluded,
                onSetAll = viewModel::setAllIncluded,
                onMoveFocus = viewModel::moveFocus,
                onToggleFocused = viewModel::toggleFocusedIncluded,
                onRemove = viewModel::removeImage,
                onReveal = { file -> DesktopDialogs.revealFile(file)?.let(actions.showMessage) },
                modifier = Modifier.width(312.dp),
            )
            VerticalDivider(color = colors.outlineVariant)
            val index = uiState.images.indexOfFirst { it.file == uiState.focusedFile }
            PreviewPane(
                preview = uiState.preview,
                item = uiState.images.getOrNull(index),
                index = index,
                total = uiState.images.size,
                options = uiState.options,
                onToggleIncluded = viewModel::toggleFocusedIncluded,
                onMove = viewModel::moveFocus,
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(color = colors.outlineVariant)
            SettingsPanel(
                options = uiState.options,
                exifCaption = uiState.preview.exifCaption,
                hasFocusedImage = uiState.focusedFile != null && uiState.preview.original != null,
                sampleFile = uiState.focusedFile ?: uiState.images.firstOrNull()?.file,
                outputDirectory = uiState.outputDirectory,
                isCustomOutputDirectory = uiState.isCustomOutputDirectory,
                notices = uiState.notices,
                includedCount = uiState.includedCount,
                canExport = uiState.canExport,
                onOptionsChange = viewModel::updateOptions,
                onChooseOutput = {
                    DesktopDialogs.pickDirectory(parent, "書き出し先のフォルダを選択", uiState.outputDirectory)
                        ?.let(viewModel::chooseOutputDirectory)
                },
                onResetOutput = viewModel::resetOutputDirectory,
                onNoticeAction = { action ->
                    when (action) {
                        NoticeAction.ExcludeUnreadable -> viewModel.excludeUnreadable()
                        NoticeAction.ShowTools -> onShowTools()
                    }
                },
                onExport = viewModel::requestExport,
                modifier = Modifier.width(348.dp),
            )
        }
    }
}

@Composable
private fun TopBar(
    uiState: ImagePrepUiState,
    viewModel: ImagePrepViewModel,
    actions: AppActions,
    onShowTools: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surface) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Folder, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        uiState.sourceTitle.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    uiState.sourcePath?.let { path ->
                        Text(
                            path,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Tooltip("別のフォルダを開く (Ctrl+O)") {
                    IconButton(onClick = actions.openFolder) { Icon(Icons.Rounded.FolderOpen, "別のフォルダを開く") }
                }
                Tooltip("画像を追加") {
                    IconButton(onClick = actions.pickImages) { Icon(Icons.Rounded.AddPhotoAlternate, "画像を追加") }
                }
                ToolsButton(uiState, onClick = onShowTools)
                VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp), color = colors.outlineVariant)
                Tooltip("すべて閉じる") {
                    IconButton(onClick = viewModel::closeAll) { Icon(Icons.Rounded.Close, "すべて閉じる") }
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
                Icon(Icons.Rounded.Extension, "外部ツール")
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
                Icon(Icons.Rounded.Download, null, tint = colors.onPrimaryContainer, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("ドロップして追加", style = MaterialTheme.typography.titleLarge, color = colors.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text("フォルダの場合は中の画像を追加します", style = MaterialTheme.typography.bodyMedium, color = colors.onPrimaryContainer)
            }
        }
    }
}
