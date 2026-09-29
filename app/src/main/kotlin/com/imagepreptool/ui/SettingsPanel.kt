package com.imagepreptool.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imagepreptool.model.CaptionPosition
import com.imagepreptool.model.CaptionSource
import com.imagepreptool.model.CaptionStyle
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.OutputFormat
import com.imagepreptool.model.ResizeMode
import com.imagepreptool.presentation.Notice
import com.imagepreptool.presentation.NoticeAction
import com.imagepreptool.service.OutputPlanner
import com.imagepreptool.ui.components.CompactTextField
import com.imagepreptool.ui.components.FieldLabel
import com.imagepreptool.ui.components.NoticeCard
import com.imagepreptool.ui.components.NoticeTone
import com.imagepreptool.ui.components.NumberField
import com.imagepreptool.ui.components.SegmentedControl
import com.imagepreptool.ui.components.SettingsSection
import com.imagepreptool.ui.theme.MonoNumberStyle
import java.io.File
import kotlin.math.roundToInt

@Composable
fun SettingsPanel(
    options: EditOptions,
    exifCaption: String?,
    hasFocusedImage: Boolean,
    sampleFile: File?,
    outputDirectory: File?,
    isCustomOutputDirectory: Boolean,
    notices: List<Notice>,
    includedCount: Int,
    canExport: Boolean,
    onOptionsChange: ((EditOptions) -> EditOptions) -> Unit,
    onChooseOutput: () -> Unit,
    onResetOutput: () -> Unit,
    onNoticeAction: (NoticeAction) -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxHeight().background(colors.surface)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SizeSection(options, onOptionsChange)
            HorizontalDivider(color = colors.outlineVariant)
            FormatSection(options, onOptionsChange)
            HorizontalDivider(color = colors.outlineVariant)
            CaptionSection(options, exifCaption, hasFocusedImage, onOptionsChange)
            HorizontalDivider(color = colors.outlineVariant)
            OutputSection(options, sampleFile, outputDirectory, isCustomOutputDirectory, onOptionsChange, onChooseOutput, onResetOutput)
        }

        // 書き出しボタンは常に見える位置に置く
        HorizontalDivider(color = colors.outlineVariant)
        Column(
            modifier = Modifier.fillMaxWidth().background(colors.surfaceContainerLow).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            notices.forEach { notice ->
                NoticeCard(
                    text = notice.text,
                    tone = if (notice.blocking) NoticeTone.Error else NoticeTone.Warning,
                    actionLabel = notice.action?.label,
                    onAction = { notice.action?.let(onNoticeAction) },
                )
            }
            Button(
                onClick = onExport,
                enabled = canExport,
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp),
            ) {
                Icon(Icons.Rounded.FileUpload, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (includedCount == 0) "書き出す画像を選択してください" else "$includedCount 枚を書き出す")
            }
            Text(
                "Ctrl+Enter で書き出し",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun SizeSection(options: EditOptions, onChange: ((EditOptions) -> EditOptions) -> Unit) {
    val range = EditOptions.MIN_DIMENSION..EditOptions.MAX_DIMENSION
    SettingsSection("サイズ") {
        SegmentedControl(
            options = ResizeMode.entries,
            selected = options.resizeMode,
            onSelect = { mode -> onChange { it.copy(resizeMode = mode) } },
            label = { it.label },
        )
        when (options.resizeMode) {
            ResizeMode.None -> Hint("元画像のピクセル数のまま書き出します。")
            ResizeMode.LongEdge -> {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        value = options.longEdge,
                        onValueChange = { v -> onChange { it.copy(longEdge = v) } },
                        range = range,
                        suffix = "px",
                        modifier = Modifier.width(112.dp),
                    )
                    Row(Modifier.height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf(1080, 2048, 3840).forEach { preset ->
                            PresetChip(
                                label = if (preset == 3840) "4K" else preset.toString(),
                                selected = options.longEdge == preset,
                                onClick = { onChange { it.copy(longEdge = preset) } },
                            )
                        }
                    }
                }
                Hint("長い方の辺をこの長さに縮小します。小さい画像は拡大しません。")
            }
            ResizeMode.Fit -> {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        value = options.fitWidth,
                        onValueChange = { v -> onChange { it.copy(fitWidth = v) } },
                        range = range,
                        suffix = "px",
                        modifier = Modifier.weight(1f),
                    )
                    Text("×", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NumberField(
                        value = options.fitHeight,
                        onValueChange = { v -> onChange { it.copy(fitHeight = v) } },
                        range = range,
                        suffix = "px",
                        modifier = Modifier.weight(1f),
                    )
                }
                Hint("縦横比を保ったまま、この枠に収まるよう縮小します。")
            }
        }
    }
}

@Composable
private fun FormatSection(options: EditOptions, onChange: ((EditOptions) -> EditOptions) -> Unit) {
    SettingsSection("形式") {
        SegmentedControl(
            options = OutputFormat.entries,
            selected = options.outputFormat,
            onSelect = { format -> onChange { it.copy(outputFormat = format) } },
            label = { it.label },
        )
        if (options.outputFormat == OutputFormat.Original) {
            Hint("元と同じ形式で保存します（HEIC は JPEG、GIF・BMP は PNG）。")
        }
        if (options.outputFormat.lossy) {
            LabeledSlider(
                label = "品質",
                valueText = options.quality.toString(),
                value = options.quality.toFloat(),
                valueRange = 40f..100f,
                onValueChange = { v -> onChange { it.copy(quality = v.roundToInt()) } },
            )
        }
        Hint("位置情報などのメタデータは書き出し時に取り除かれます。")
    }
}

@Composable
private fun CaptionSection(
    options: EditOptions,
    exifCaption: String?,
    hasFocusedImage: Boolean,
    onChange: ((EditOptions) -> EditOptions) -> Unit,
) {
    SettingsSection(
        title = "キャプション",
        trailing = {
            Switch(
                checked = options.captionEnabled,
                onCheckedChange = { enabled -> onChange { it.copy(captionEnabled = enabled) } },
                modifier = Modifier.scale(0.8f),
            )
        },
    ) {
        if (!options.captionEnabled) {
            Hint("画像の隅に撮影情報やクレジットを書き込めます。")
            return@SettingsSection
        }
        SegmentedControl(
            options = CaptionSource.entries,
            selected = options.captionSource,
            onSelect = { source -> onChange { it.copy(captionSource = source) } },
            label = { it.label },
        )
        when (options.captionSource) {
            CaptionSource.Exif -> Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    FieldLabel("選択中の画像")
                    Spacer(Modifier.height(2.dp))
                    Text(
                        when {
                            !hasFocusedImage -> "—"
                            exifCaption != null -> exifCaption
                            else -> "撮影情報がありません（この画像には書き込まれません）"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (exifCaption != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            CaptionSource.Custom -> CompactTextField(
                value = options.customCaption,
                onValueChange = { text -> onChange { it.copy(customCaption = text) } },
                placeholder = "例: © 2026 Your Name",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                FieldLabel("位置")
                Spacer(Modifier.height(6.dp))
                PositionPicker(options.captionPosition) { pos -> onChange { it.copy(captionPosition = pos) } }
            }
            Column(Modifier.weight(1f)) {
                FieldLabel("スタイル")
                Spacer(Modifier.height(6.dp))
                SegmentedControl(
                    options = CaptionStyle.entries,
                    selected = options.captionStyle,
                    onSelect = { style -> onChange { it.copy(captionStyle = style) } },
                    label = { it.label },
                )
            }
        }
        LabeledSlider(
            label = "文字の大きさ",
            valueText = "%.1f%%".format(options.captionSizePercent),
            value = options.captionSizePercent,
            valueRange = EditOptions.MIN_CAPTION_PERCENT..EditOptions.MAX_CAPTION_PERCENT,
            onValueChange = { v -> onChange { it.copy(captionSizePercent = (v * 10).roundToInt() / 10f) } },
        )
    }
}

@Composable
private fun OutputSection(
    options: EditOptions,
    sampleFile: File?,
    outputDirectory: File?,
    isCustom: Boolean,
    onChange: ((EditOptions) -> EditOptions) -> Unit,
    onChoose: () -> Unit,
    onReset: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    SettingsSection("書き出し先") {
        Surface(
            color = colors.surfaceContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Folder, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        outputDirectory?.name ?: "未設定",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        outputDirectory?.parentFile?.absolutePath.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = onChoose,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.height(32.dp),
                ) { Text("変更…", style = MaterialTheme.typography.labelLarge) }
            }
        }
        if (isCustom) {
            TextButton(onClick = onReset, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.height(28.dp)) {
                Text("元画像のフォルダ内「output」に戻す", style = MaterialTheme.typography.labelMedium)
            }
        } else {
            Hint("元画像と同じフォルダに「output」を作って保存します。")
        }
        Column {
            FieldLabel("ファイル名の末尾に追加")
            Spacer(Modifier.height(6.dp))
            CompactTextField(
                value = options.fileNameSuffix,
                onValueChange = { text -> onChange { it.copy(fileNameSuffix = text.take(40)) } },
                placeholder = "例: _web",
                modifier = Modifier.fillMaxWidth(),
            )
            if (sampleFile != null) {
                Spacer(Modifier.height(6.dp))
                val format = OutputPlanner.resolveFormat(sampleFile, options.outputFormat)
                Text(
                    "→ " + OutputPlanner.outputName(sampleFile, format, options.fileNameSuffix),
                    style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle),
            color = if (selected) colors.onPrimaryContainer else colors.primary,
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldLabel(label, Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge.merge(MonoNumberStyle))
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth().height(28.dp),
        )
    }
}

/** 画像の四隅を選ぶミニチュア */
@Composable
private fun PositionPicker(selected: CaptionPosition, onSelect: (CaptionPosition) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(width = 84.dp, height = 58.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surfaceContainerHigh)
            .border(1.dp, colors.outline, RoundedCornerShape(6.dp)),
    ) {
        CaptionPosition.entries.forEach { position ->
            val alignment = when (position) {
                CaptionPosition.TopLeft -> Alignment.TopStart
                CaptionPosition.TopRight -> Alignment.TopEnd
                CaptionPosition.BottomLeft -> Alignment.BottomStart
                CaptionPosition.BottomRight -> Alignment.BottomEnd
            }
            val isSelected = position == selected
            Box(
                modifier = Modifier
                    .align(alignment)
                    .size(width = 42.dp, height = 29.dp)
                    .clickable { onSelect(position) },
                contentAlignment = alignment,
            ) {
                Box(
                    Modifier
                        .padding(5.dp)
                        .size(width = 22.dp, height = 7.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isSelected) colors.primary else colors.outline.copy(alpha = 0.5f)),
                )
            }
        }
    }
}
