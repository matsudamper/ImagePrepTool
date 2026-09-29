package com.imagepreptool.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.imagepreptool.model.ConflictPolicy
import com.imagepreptool.model.ExternalTool
import com.imagepreptool.model.ExternalTools
import com.imagepreptool.presentation.ExportState
import com.imagepreptool.resources.Res
import com.imagepreptool.resources.ic_check_circle
import com.imagepreptool.resources.ic_error
import com.imagepreptool.resources.ic_folder_open
import com.imagepreptool.resources.ic_info
import com.imagepreptool.resources.ic_remove_circle_outline
import com.imagepreptool.resources.ic_warning_amber
import com.imagepreptool.ui.theme.AppTheme
import com.imagepreptool.ui.theme.MonoNumberStyle
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
private fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    dismissible: Boolean = true,
    width: Int = 440,
    icon: DrawableResource? = null,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 12.dp,
            modifier = modifier.width(width.dp),
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(painterResource(icon), null, tint = iconTint, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(16.dp))
                content()
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = buttons,
                )
            }
        }
    }
}

@Composable
fun ConflictDialog(state: ExportState.ConfirmConflicts, onResolve: (ConflictPolicy?) -> Unit, modifier: Modifier = Modifier) {
    val conflicts = state.plan.filter { it.exists }
    AppDialog(
        modifier = modifier,
        onDismiss = { onResolve(null) },
        icon = Res.drawable.ic_warning_amber,
        iconTint = AppTheme.extended.warning,
        title = "同じ名前のファイルがあります",
        buttons = {
            TextButton(onClick = { onResolve(null) }) { Text("キャンセル") }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { onResolve(ConflictPolicy.Skip) }) { Text(ConflictPolicy.Skip.label) }
            OutlinedButton(onClick = { onResolve(ConflictPolicy.Overwrite) }) { Text(ConflictPolicy.Overwrite.label) }
            Button(onClick = { onResolve(ConflictPolicy.Rename) }) { Text(ConflictPolicy.Rename.label) }
        },
        width = 520,
    ) {
        Text(
            "書き出し先に同じ名前のファイルが ${conflicts.size} 件あります。どう扱いますか？",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(12.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                conflicts.take(4).forEach {
                    Text(it.target.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (conflicts.size > 4) {
                    Text("ほか ${conflicts.size - 4} 件", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "「別名で保存」は末尾に (2) などを付けて保存します。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ProgressDialog(state: ExportState.Running, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    AppDialog(
        modifier = modifier,
        onDismiss = {},
        dismissible = false,
        title = if (state.cancelling) "キャンセルしています…" else "書き出し中…",
        buttons = {
            OutlinedButton(onClick = onCancel, enabled = !state.cancelling) { Text("キャンセル") }
        },
    ) {
        val progress = if (state.total == 0) 0f else state.done.toFloat() / state.total
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.currentName.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("${state.done} / ${state.total}", style = MaterialTheme.typography.labelLarge.merge(MonoNumberStyle))
        }
    }
}

@Composable
fun ResultDialog(state: ExportState.Finished, onOpenFolder: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val failures = state.failures
    val ext = AppTheme.extended
    val (icon, tint, title) = when {
        state.cancelled -> Triple(Res.drawable.ic_info, MaterialTheme.colorScheme.onSurfaceVariant, "書き出しをキャンセルしました")
        failures.isEmpty() -> Triple(Res.drawable.ic_check_circle, ext.success, "書き出しが完了しました")
        state.successCount == 0 -> Triple(Res.drawable.ic_error, MaterialTheme.colorScheme.error, "書き出せませんでした")
        else -> Triple(Res.drawable.ic_warning_amber, ext.warning, "一部の画像を書き出せませんでした")
    }
    AppDialog(
        modifier = modifier,
        onDismiss = onClose,
        icon = icon,
        iconTint = tint,
        title = title,
        width = 520,
        buttons = {
            TextButton(onClick = onClose) { Text("閉じる") }
            if (state.successCount > 0) {
                Button(onClick = onOpenFolder) {
                    Icon(painterResource(Res.drawable.ic_folder_open), null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("フォルダを開く")
                }
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Stat("書き出し", state.successCount, ext.success)
            if (state.skippedCount > 0) Stat("スキップ", state.skippedCount, MaterialTheme.colorScheme.onSurfaceVariant)
            if (failures.isNotEmpty()) Stat("失敗", failures.size, MaterialTheme.colorScheme.error)
            if (state.cancelled) Stat("未処理", state.total - state.results.size, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            state.outputDir.absolutePath,
            style = MaterialTheme.typography.bodySmall.merge(MonoNumberStyle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (failures.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                    items(failures) { failure ->
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Icon(painterResource(Res.drawable.ic_remove_circle_outline), null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(failure.source.name, style = MaterialTheme.typography.bodyMedium)
                                Text(failure.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall.merge(MonoNumberStyle), color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ToolsDialog(tools: ExternalTools?, onRecheck: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    AppDialog(
        modifier = modifier,
        onDismiss = onClose,
        title = "外部ツール",
        width = 540,
        buttons = {
            TextButton(onClick = onRecheck) { Text("再確認") }
            Button(onClick = onClose) { Text("閉じる") }
        },
    ) {
        Text(
            "WebP の書き出しと HEIC の読み込みには、次のコマンドが PATH に必要です。JPEG・PNG・WebP の読み込みと JPEG・PNG の書き出しは追加のツールなしで使えます。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        if (tools == null) {
            Text("確認中…", style = MaterialTheme.typography.bodyMedium)
            return@AppDialog
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.heightIn(max = 420.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                tools.statuses.forEachIndexed { index, status ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 1.dp)) {
                            if (status.available) {
                                Icon(painterResource(Res.drawable.ic_check_circle), "利用可能", tint = AppTheme.extended.success, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(painterResource(Res.drawable.ic_remove_circle_outline), "見つかりません", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(status.tool.command, style = MaterialTheme.typography.titleSmall.merge(MonoNumberStyle))
                                Spacer(Modifier.width(8.dp))
                                Text(status.tool.purpose, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                if (status.available) status.detail else "${status.detail} · ${installHint(status.tool)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val command = installCommand(status.tool)
                            if (!status.available && command != null) {
                                Spacer(Modifier.height(6.dp))
                                CopyableCommand(command)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "HEIC は heif-dec / heif-convert / magick のいずれか 1 つがあれば読み込めます。インストール後は PATH がアプリに反映されないため、アプリを再起動してください。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun installHint(tool: ExternalTool): String = when (tool) {
    ExternalTool.Cwebp -> "libwebp に含まれます"
    ExternalTool.HeifDec, ExternalTool.HeifConvert -> "libheif に含まれます。下のコマンドで magick（ImageMagick）を入れれば代替できます"
    ExternalTool.Magick -> "ImageMagick に含まれます（HEIC 対応版）"
}

private fun installCommand(tool: ExternalTool): String? = when (tool) {
    ExternalTool.Cwebp -> "winget install -e --id Google.Libwebp"
    ExternalTool.HeifDec, ExternalTool.HeifConvert, ExternalTool.Magick -> "winget install ImageMagick.ImageMagick"
}

@Composable
private fun CopyableCommand(command: String) {
    val clipboard = LocalClipboardManager.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.extraSmall) {
        Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(command, style = MaterialTheme.typography.bodySmall.merge(MonoNumberStyle), modifier = Modifier.weight(1f))
            TextButton(onClick = { clipboard.setText(AnnotatedString(command)) }) { Text("コピー") }
        }
    }
}
