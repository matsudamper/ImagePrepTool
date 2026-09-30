package com.imagepreptool.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import java.io.File
import com.imagepreptool.model.ExternalTools
import com.imagepreptool.presentation.ImageGroup
import com.imagepreptool.presentation.ImageItem
import com.imagepreptool.presentation.SelectMode
import com.imagepreptool.resources.Res
import com.imagepreptool.resources.ic_broken_image
import com.imagepreptool.resources.ic_close
import com.imagepreptool.resources.ic_folder
import com.imagepreptool.ui.components.Tooltip
import com.imagepreptool.ui.theme.AppTheme
import com.imagepreptool.ui.theme.MonoNumberStyle
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageListPanel(
    imageGroups: List<ImageGroup>,
    focusedFile: File?,
    selectedFiles: Set<File>,
    isSelectionMode: Boolean,
    tools: ExternalTools?,
    onClickImage: (File, SelectMode) -> Unit,
    onRemoveSelection: () -> Unit,
    onUndoRemoval: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onMoveFocus: (Int) -> Unit,
    onRemove: (File) -> Unit,
    onReveal: (File) -> Unit,
    onOpenFolder: (File) -> Unit,
    onRemoveFolder: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val gridState = rememberLazyGridState()
    val focusRequester = remember { FocusRequester() }
    var collapsedFolders by remember { mutableStateOf(setOf<File>()) }
    val imageCount = imageGroups.sumOf { it.images.size }
    val density = LocalDensity.current
    val folderHeaderHeightPx = with(density) { FolderHeaderHeight.roundToPx() }

    LaunchedEffect(focusedFile) {
        val index = focusedFile?.let { gridIndexOf(imageGroups, collapsedFolders, it) } ?: return@LaunchedEffect
        val visible = gridState.layoutInfo.visibleItemsInfo
        // 上端に固定された見出しの下に隠れている画像は見えていない扱いにする
        val fullyVisible = visible.any { it.index == index } &&
            visible.first { it.index == index }.let { item ->
                item.offset.y >= folderHeaderHeightPx && item.offset.y + item.size.height <= gridState.layoutInfo.viewportEndOffset
            }
        if (!fullyVisible) {
            gridState.animateScrollToItem(index)
            gridState.animateScrollBy(-folderHeaderHeightPx.toFloat())
        }
    }

    Column(modifier = modifier.fillMaxHeight().background(colors.surface)) {
        if (isSelectionMode) {
            SelectionBar(
                count = selectedFiles.size,
                onRemove = onRemoveSelection,
                onClear = onClearSelection,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("画像", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    "$imageCount 枚",
                    style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle),
                    color = colors.onSurfaceVariant,
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = ThumbnailMinSize),
            state = gridState,
            contentPadding = PaddingValues(start = GridHorizontalPadding, end = GridHorizontalPadding, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(GridColumnSpacing),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press) focusRequester.requestFocus()
                        }
                    }
                }
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val columns = density.adaptiveColumnCount(gridState.layoutInfo.viewportSize.width)
                    val visibleGroups = imageGroups.filter { it.folder !in collapsedFolders }
                    val visibleFocusedFile = focusedFile?.takeIf { file -> visibleGroups.any { group -> group.images.any { it.file == file } } }
                    val moveFocusInVisible = { visibleDelta: Int ->
                        onMoveFocus(focusDeltaInAllImages(imageGroups, visibleGroups, visibleFocusedFile, visibleDelta))
                    }
                    when (event.key) {
                        Key.DirectionLeft -> moveFocusInVisible(-1)
                        Key.DirectionRight -> moveFocusInVisible(1)
                        Key.DirectionUp -> moveFocusInVisible(verticalMoveDelta(visibleGroups, visibleFocusedFile, columns, downward = false))
                        Key.DirectionDown -> moveFocusInVisible(verticalMoveDelta(visibleGroups, visibleFocusedFile, columns, downward = true))
                        Key.Delete -> if (isSelectionMode) onRemoveSelection() else visibleFocusedFile?.let(onRemove)
                        Key.Escape -> onClearSelection()
                        Key.A -> if (event.isCtrlPressed || event.isMetaPressed) onSelectAll() else return@onPreviewKeyEvent false
                        Key.Z -> if (event.isCtrlPressed || event.isMetaPressed) onUndoRemoval() else return@onPreviewKeyEvent false
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                }
                .focusable(),
        ) {
            imageGroups.forEach { group ->
                val expanded = group.folder !in collapsedFolders
                stickyHeader(key = "folder:${group.folder.absolutePath}", contentType = "folder") {
                    FolderHeader(
                        group = group,
                        expanded = expanded,
                        onToggleExpand = {
                            collapsedFolders = if (expanded) collapsedFolders + group.folder else collapsedFolders - group.folder
                        },
                        onOpen = { onOpenFolder(group.folder) },
                        onRemove = { onRemoveFolder(group.folder) },
                    )
                }
                items(if (expanded) group.images else listOf(), key = { it.file.absolutePath }, contentType = { "image" }) { item ->
                    // 複数選択中の画像に対する操作は選択中の全画像に反映される
                    val inGroup = isSelectionMode && item.file in selectedFiles
                    val prefix = if (inGroup) "選択中の ${selectedFiles.size} 枚を" else ""
                    ContextMenuArea(
                        items = {
                            listOf(
                                ContextMenuItem("エクスプローラーで表示") { onReveal(item.file) },
                                ContextMenuItem(prefix + "一覧から削除") { onRemove(item.file) },
                            )
                        },
                    ) {
                        Thumbnail(
                            item = item,
                            focused = item.file == focusedFile,
                            selected = inGroup,
                            tools = tools,
                            onClick = { mode ->
                                focusRequester.requestFocus()
                                onClickImage(item.file, mode)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 見出しを含めたグリッド上の位置。見出しはフォルダごとに 1 つ入り、折りたたまれたフォルダの画像は入らない */
private fun gridIndexOf(imageGroups: List<ImageGroup>, collapsedFolders: Set<File>, file: File): Int? =
    imageGroups
        .flatMap { group ->
            if (group.folder in collapsedFolders) listOf(group.folder) else listOf(group.folder) + group.images.map { it.file }
        }
        .indexOf(file)
        .takeIf { it >= 0 }

/** 折りたたまれたフォルダの画像を飛ばした移動枚数を、全画像の並びでの移動枚数に直す */
internal fun focusDeltaInAllImages(
    imageGroups: List<ImageGroup>,
    visibleGroups: List<ImageGroup>,
    visibleFocusedFile: File?,
    visibleDelta: Int,
): Int {
    val allFiles = imageGroups.flatMap { group -> group.images.map { it.file } }
    val visibleFiles = visibleGroups.flatMap { group -> group.images.map { it.file } }
    val visibleIndex = visibleFiles.indexOf(visibleFocusedFile).takeIf { it >= 0 } ?: return 0
    val target = visibleFiles.getOrNull(visibleIndex + visibleDelta) ?: return 0
    return allFiles.indexOf(target) - allFiles.indexOf(visibleFocusedFile)
}

/**
 * 上下キーで移動する枚数。フォルダごとに見出しで行が改まるため、
 * 隣のフォルダへ移るときは一覧の通し番号ではなく同じ列の画像を移動先にする
 */
internal fun verticalMoveDelta(imageGroups: List<ImageGroup>, focusedFile: File?, columns: Int, downward: Boolean): Int {
    val groupIndex = imageGroups.indexOfFirst { group -> group.images.any { it.file == focusedFile } }.takeIf { it >= 0 } ?: return 0
    val group = imageGroups[groupIndex]
    val groupStart = imageGroups.take(groupIndex).sumOf { it.images.size }
    val indexInGroup = group.images.indexOfFirst { it.file == focusedFile }
    val column = indexInGroup % columns
    val row = indexInGroup / columns
    val lastRow = (group.images.size - 1) / columns
    val target = when {
        downward && row < lastRow -> groupStart + minOf(indexInGroup + columns, group.images.lastIndex)
        downward -> imageGroups.getOrNull(groupIndex + 1)?.let { next ->
            groupStart + group.images.size + minOf(column, next.images.lastIndex)
        }
        row > 0 -> groupStart + indexInGroup - columns
        else -> imageGroups.getOrNull(groupIndex - 1)?.let { previous ->
            val previousLastRowStart = previous.images.lastIndex / columns * columns
            groupStart - previous.images.size + minOf(previousLastRowStart + column, previous.images.lastIndex)
        }
    } ?: return 0
    return target - (groupStart + indexInGroup)
}

/** GridCells.Adaptive と同じ計算でグリッドの列数を求める。見えている行が短い位置までスクロールしていても正しい列数になる */
private fun Density.adaptiveColumnCount(viewportWidthPx: Int): Int {
    val gridWidth = viewportWidthPx - (GridHorizontalPadding * 2).roundToPx()
    val spacing = GridColumnSpacing.roundToPx()
    return ((gridWidth + spacing) / (ThumbnailMinSize.roundToPx() + spacing)).coerceAtLeast(1)
}

/** 親の contentPadding を越えて左右いっぱいまで広げる */
private fun Modifier.extendHorizontally(extension: Dp): Modifier = layout { measurable, constraints ->
    val extensionPx = extension.roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = extensionPx * 2))
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-extensionPx, 0)
    }
}

private val FolderHeaderHeight = 44.dp
private val ThumbnailMinSize = 84.dp
private val GridHorizontalPadding = 12.dp
private val GridColumnSpacing = 8.dp

@Composable
private fun FolderHeader(group: ImageGroup, expanded: Boolean, onToggleExpand: () -> Unit, onOpen: () -> Unit, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ContextMenuArea(
        items = {
            listOf(
                ContextMenuItem("エクスプローラーで開く", onOpen),
                ContextMenuItem("このフォルダの ${group.images.size} 枚を一覧から削除", onRemove),
            )
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .extendHorizontally(GridHorizontalPadding)
                .height(FolderHeaderHeight)
                .clickable(onClick = onToggleExpand)
                .background(colors.surface)
                .padding(horizontal = GridHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (expanded) "▼" else "▶",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.width(16.dp),
            )
            Icon(painterResource(Res.drawable.ic_folder), null, tint = colors.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Tooltip(group.folder.path, modifier = Modifier.weight(1f)) {
                Column {
                    Text(
                        group.folder.name.ifEmpty { group.folder.path },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        group.folder.parent.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                "${group.images.size} 枚",
                style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle),
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
            Tooltip("このフォルダを一覧から除外") {
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(painterResource(Res.drawable.ic_close), "このフォルダを一覧から除外", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(count: Int, onRemove: () -> Unit, onClear: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.small)
            .background(colors.primaryContainer)
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tooltip("選択を解除 (Esc)") {
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(painterResource(Res.drawable.ic_close), "選択を解除", tint = colors.onPrimaryContainer, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            "$count 枚を選択中",
            style = MaterialTheme.typography.titleSmall.merge(MonoNumberStyle),
            color = colors.onPrimaryContainer,
            modifier = Modifier.weight(1f).padding(start = 2.dp),
        )
        Tooltip("一覧から削除 (Delete)") {
            TextButton(onClick = onRemove, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(30.dp)) {
                Text("削除", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Thumbnail(
    item: ImageItem,
    focused: Boolean,
    selected: Boolean,
    tools: ExternalTools?,
    onClick: (SelectMode) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val thumbnail by rememberThumbnail(item.file, tools)
    val shape = RoundedCornerShape(10.dp)

    Column(
        modifier = Modifier
            .hoverable(interaction)
            .onClick(keyboardModifiers = { isShiftPressed }) { onClick(SelectMode.Range) }
            .onClick(keyboardModifiers = { isCtrlPressed || isMetaPressed }) { onClick(SelectMode.Toggle) }
            .onClick(keyboardModifiers = { !isShiftPressed && !isCtrlPressed && !isMetaPressed }) { onClick(SelectMode.Single) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(colors.surfaceContainerHigh)
                .border(
                    width = if (focused || selected) 2.5.dp else 1.dp,
                    color = when {
                        focused -> colors.primary
                        selected -> colors.primary.copy(alpha = 0.55f)
                        hovered -> colors.outline
                        else -> colors.outlineVariant
                    },
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = thumbnail) {
                ThumbnailState.Loading -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                is ThumbnailState.Ready -> Image(
                    bitmap = state.bitmap,
                    contentDescription = item.file.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().padding(if (focused || selected) 2.5.dp else 1.dp).clip(RoundedCornerShape(8.dp)),
                )
                is ThumbnailState.Failed -> Tooltip(state.reason) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(Res.drawable.ic_broken_image), null, tint = colors.error, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(item.file.extension.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                }
            }
            if (selected) {
                Box(Modifier.fillMaxSize().background(colors.primary.copy(alpha = 0.18f)))
            }
        }
        Text(
            item.file.name,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp, start = 2.dp, end = 2.dp),
        )
    }
}

@Preview
@Composable
private fun ImageListPanelPreview() {
    val imageGroups = listOf(
        ImageGroup(File("/photos/trip_2026"), (1..5).map { ImageItem(File("/photos/trip_2026/IMG_$it.jpg")) }),
        ImageGroup(File("/photos/misc"), (1..3).map { ImageItem(File("/photos/misc/IMG_$it.jpg")) }),
    )
    AppTheme(darkTheme = false) {
        ImageListPanel(
            imageGroups = imageGroups,
            focusedFile = imageGroups.first().images.first().file,
            selectedFiles = setOf(imageGroups.first().images.first().file),
            isSelectionMode = false,
            tools = null,
            onClickImage = { _, _ -> },
            onRemoveSelection = {},
            onUndoRemoval = {},
            onSelectAll = {},
            onClearSelection = {},
            onMoveFocus = {},
            onRemove = {},
            onReveal = {},
            onOpenFolder = {},
            onRemoveFolder = {},
            modifier = Modifier.width(312.dp).height(640.dp),
        )
    }
}
