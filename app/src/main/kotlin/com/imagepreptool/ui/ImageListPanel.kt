package com.imagepreptool.ui

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

@Composable
fun ImageListPanel(
    images: List<ImageItem>,
    includedCount: Int,
    focusedFile: File?,
    tools: ExternalTools?,
    onFocus: (File) -> Unit,
    onToggle: (File) -> Unit,
    onSetAll: (Boolean) -> Unit,
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
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = 6.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val toggleState = when (includedCount) {
                0 -> ToggleableState.Off
                images.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
            Tooltip(if (toggleState == ToggleableState.On) "すべて外す" else "すべて選択") {
                TriStateCheckbox(state = toggleState, onClick = { onSetAll(toggleState != ToggleableState.On) })
            }
            Text("画像", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                "$includedCount / ${images.size} 枚",
                style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle),
                color = colors.onSurfaceVariant,
            )
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
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                }
                .focusable(),
        ) {
            items(images, key = { it.file.absolutePath }) { item ->
                ContextMenuArea(
                    items = {
                        listOf(
                            ContextMenuItem(if (item.included) "書き出しから外す" else "書き出しに含める") { onToggle(item.file) },
                            ContextMenuItem("エクスプローラーで表示") { onReveal(item.file) },
                            ContextMenuItem("一覧から削除") { onRemove(item.file) },
                        )
                    },
                ) {
                    Thumbnail(
                        item = item,
                        focused = item.file == focusedFile,
                        tools = tools,
                        onClick = {
                            focusRequester.requestFocus()
                            onFocus(item.file)
                        },
                        onToggle = { onToggle(item.file) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(
    item: ImageItem,
    focused: Boolean,
    tools: ExternalTools?,
    onClick: () -> Unit,
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
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(colors.surfaceContainerHigh)
                .border(
                    width = if (focused) 2.5.dp else 1.dp,
                    color = when {
                        focused -> colors.primary
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
                    modifier = Modifier.fillMaxSize().padding(if (focused) 2.5.dp else 1.dp).clip(RoundedCornerShape(8.dp)).alpha(dim),
                )
                is ThumbnailState.Failed -> Tooltip(state.reason) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(dim)) {
                        Icon(Icons.Rounded.BrokenImage, null, tint = colors.error, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(item.file.extension.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                }
            }
            IncludeBadge(
                included = item.included,
                visible = hovered || !item.included || focused,
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
