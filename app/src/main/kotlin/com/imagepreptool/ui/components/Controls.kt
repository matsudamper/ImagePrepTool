package com.imagepreptool.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imagepreptool.resources.Res
import com.imagepreptool.resources.ic_error_outline
import com.imagepreptool.resources.ic_info
import com.imagepreptool.resources.ic_warning_amber
import com.imagepreptool.ui.theme.AppTheme
import com.imagepreptool.ui.theme.MonoNumberStyle
import org.jetbrains.compose.resources.painterResource

/** 設定パネルの 1 セクション */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            trailing?.invoke(this)
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** デスクトップ向けのコンパクトなセグメント切り替え */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(MaterialTheme.shapes.small)
            .background(colors.surfaceContainerHigh)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val background by animateColorAsState(
                when {
                    isSelected -> colors.surface
                    hovered && enabled -> colors.surfaceContainerHighest
                    else -> Color.Transparent
                },
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(background)
                    .then(
                        if (isSelected) Modifier.border(1.dp, colors.outlineVariant, RoundedCornerShape(6.dp)) else Modifier,
                    )
                    .hoverable(interaction)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        role = Role.RadioButton,
                    ) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        !enabled -> colors.onSurface.copy(alpha = 0.38f)
                        isSelected -> colors.onSurface
                        else -> colors.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 数値入力欄。入力中は文字列のまま保持し、範囲内の値になったときだけ [onValueChange] を呼ぶ。
 */
@Composable
fun NumberField(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    onValidityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    enabled: Boolean = true,
) {
    var text by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        if (text.toIntOrNull() != value) text = value.toString()
    }
    val parsed = text.toIntOrNull()
    val isError = parsed == null || parsed !in range
    // 不正な入力のまま書き出すと、表示と違う直前の値で書き出されるため親へ伝える
    val currentOnValidityChange by rememberUpdatedState(onValidityChange)
    LaunchedEffect(isError) { currentOnValidityChange(!isError) }
    DisposableEffect(Unit) { onDispose { currentOnValidityChange(true) } }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val borderColor = when {
        isError -> colors.error
        focused -> colors.primary
        else -> colors.outline
    }
    Column(modifier) {
        BasicTextField(
            value = text,
            onValueChange = { new ->
                val digits = new.filter(Char::isDigit).take(5)
                text = digits
                digits.toIntOrNull()?.takeIf { it in range }?.let(onValueChange)
            },
            enabled = enabled,
            singleLine = true,
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            // フォーカスが外れたら直前の有効な値に戻す
            modifier = Modifier.onFocusChanged { state -> if (!state.isFocused && isError) text = value.toString() },
            textStyle = MaterialTheme.typography.bodyLarge.merge(MonoNumberStyle).copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(colors.surface)
                        .border(if (focused || isError) 1.5.dp else 1.dp, borderColor, MaterialTheme.shapes.small)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) { inner() }
                    if (suffix != null) {
                        Text(suffix, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            },
        )
        if (isError) {
            Text(
                "${range.first}〜${range.last} で入力",
                style = MaterialTheme.typography.labelSmall,
                color = colors.error,
                modifier = Modifier.padding(top = 3.dp, start = 2.dp),
            )
        }
    }
}

@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        interactionSource = interaction,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f)),
        cursorBrush = SolidColor(colors.primary),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(colors.surface)
                    .border(if (focused) 1.5.dp else 1.dp, if (focused) colors.primary else colors.outline, MaterialTheme.shapes.small)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1)
                }
                inner()
            }
        },
    )
}

enum class NoticeTone { Info, Warning, Error }

@Composable
fun NoticeCard(
    text: String,
    tone: NoticeTone,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val ext = AppTheme.extended
    val colors = MaterialTheme.colorScheme
    val (container, content, icon) = when (tone) {
        NoticeTone.Info -> Triple(colors.secondaryContainer, colors.onSecondaryContainer, Res.drawable.ic_info)
        NoticeTone.Warning -> Triple(ext.warningContainer, ext.onWarningContainer, Res.drawable.ic_warning_amber)
        NoticeTone.Error -> Triple(colors.errorContainer, colors.onErrorContainer, Res.drawable.ic_error_outline)
    }
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small, modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(top = 1.dp, end = 6.dp))
            if (actionLabel != null) {
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.height(24.dp),
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.labelMedium, color = content)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tooltip(text: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // TooltipBox は modifier を内側に適用するため、配置用の modifier は外側の Box に付ける
    Box(modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(text) } },
            state = rememberTooltipState(),
            content = content,
        )
    }
}

/** 状態を表す小さなピル */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    border: BorderStroke? = null,
) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50), border = border, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(text, style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}
