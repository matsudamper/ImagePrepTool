package com.imagepreptool.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.onClick
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import com.imagepreptool.presentation.SelectMode
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imagepreptool.model.ExternalTools
import com.imagepreptool.presentation.ImageItem
import com.imagepreptool.ui.components.Tooltip
import com.imagepreptool.ui.theme.MonoNumberStyle
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageListPanel(
    images: List<ImageItem>,
    includedCount: Int,
    focusedFile: File?,
    selectedFiles: Set<File>,
    tools: ExternalTools?,
    onClickImage: (File, SelectMode) -> Unit,
    onToggle: (File) -> Unit,
    onSetAll: (Boolean) -> Unit,
    onSetSelectionIncluded: (Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onMoveFocus: (Int) -> Unit,
    onToggleFocused: () -> Unit,
    onRemove: (File) -> Unit,
    onReveal: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val gridState = rememberLazyGridState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(focusedFile) {
        val index = images.indexOfFirst { it.file == focusedFile }
        if (index < 0) return@LaunchedEffect
        val visible = gridState.layoutInfo.visibleItemsInfo
        val fullyVisible = visible.any { it.index == index } &&
            visible.first { it.index == index }.let { item ->
                item.offset.y >= 0 && item.offset.y + item.size.height <= gridState.layoutInfo.viewportEndOffset
            }
        if (!fullyVisible) gridState.animateScrollToItem(index)
    }

    Column(modifier = modifier.fillMaxHeight().background(colors.surface)) {
        val multiSelected = selectedFiles.size > 1
        if (multiSelected) {
            SelectionBar(
                count = selectedFiles.size,
                onInclude = { onSetSelectionIncluded(true) },
                onExclude = { onSetSelectionIncluded(false) },
                onClear = onClearSelection,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = 6.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val toggleState = when (includedCount) {
                    0 -> ToggleableState.Off
                    images.size -> ToggleableState.On
                    else -> ToggleableState.Indeterminate
                }
                Tooltip(if (toggleState == ToggleableState.On) "すべて外す" else "すべて含める") {
                    TriStateCheckbox(state = toggleState, onClick = { onSetAll(toggleState != ToggleableState.On) })
                }
                Text("画像", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    "$includedCount / ${images.size} 枚",
                    style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle),
                    color = colors.onSurfaceVariant,
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 84.dp),
            state = gridState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val columns = gridState.layoutInfo.visibleItemsInfo.map { it.column }.distinct().size.coerceAtLeast(1)
                    when (event.key) {
                        Key.DirectionLeft -> onMoveFocus(-1)
                        Key.DirectionRight -> onMoveFocus(1)
                        Key.DirectionUp -> onMoveFocus(-columns)
                        Key.DirectionDown -> onMoveFocus(columns)
                        Key.Spacebar -> onToggleFocused()
                        Key.Delete -> focusedFile?.let(onRemove)
                        Key.Escape -> onClearSelection()
                        Key.A -> if (event.isCtrlPressed || event.isMetaPressed) onSelectAll() else return@onPreviewKeyEvent false
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                }
                .focusable(),
        ) {
            items(images, key = { it.file.absolutePath }) { item ->
                // 複数選択中の画像に対する操作は選択中の全画像に反映される
                val inGroup = multiSelected && item.file in selectedFiles
                val prefix = if (inGroup) "選択中の ${selectedFiles.size} 枚を" else ""
                ContextMenuArea(
                    items = {
                        listOf(
                            ContextMenuItem(prefix + if (item.included) "書き出しから外す" else "書き出しに含める") { onToggle(item.file) },
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
                        onToggle = { onToggle(item.file) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(count: Int, onInclude: () -> Unit, onExclude: () -> Unit, onClear: () -> Unit) {
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
                Icon(Icons.Rounded.Close, "選択を解除", tint = colors.onPrimaryContainer, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            "$count 枚を選択中",
            style = MaterialTheme.typography.titleSmall.merge(MonoNumberStyle),
            color = colors.onPrimaryContainer,
            modifier = Modifier.weight(1f).padding(start = 2.dp),
        )
        TextButton(onClick = onInclude, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(30.dp)) {
            Text("含める", style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = onExclude, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(30.dp)) {
            Text("外す", style = MaterialTheme.typography.labelLarge)
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
    onToggle: () -> Unit,
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
            val dim = if (item.included) 1f else 0.35f
            when (val state = thumbnail) {
                ThumbnailState.Loading -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                is ThumbnailState.Ready -> Image(
                    bitmap = state.bitmap,
                    contentDescription = item.file.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().padding(if (focused || selected) 2.5.dp else 1.dp).clip(RoundedCornerShape(8.dp)).alpha(dim),
                )
                is ThumbnailState.Failed -> Tooltip(state.reason) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(dim)) {
                        Icon(Icons.Rounded.BrokenImage, null, tint = colors.error, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(item.file.extension.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                }
            }
            if (selected) {
                Box(Modifier.fillMaxSize().background(colors.primary.copy(alpha = 0.18f)))
            }
            IncludeBadge(
                included = item.included,
                visible = hovered || !item.included || focused || selected,
                onToggle = onToggle,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            )
        }
        Text(
            item.file.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (item.included) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp, start = 2.dp, end = 2.dp),
        )
    }
}

/** サムネイル左上の丸いチェック。書き出しに含めるかどうかを切り替える */
@Composable
private fun IncludeBadge(included: Boolean, visible: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    if (!visible) return
    val colors = MaterialTheme.colorScheme
    Tooltip(if (included) "書き出しから外す (Space)" else "書き出しに含める (Space)", modifier = modifier) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (included) colors.primary else Color.Black.copy(alpha = 0.35f))
                .border(1.5.dp, if (included) colors.primary else Color.White, CircleShape)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (included) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(15.dp))
        }
    }
}
