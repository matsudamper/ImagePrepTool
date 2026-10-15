package com.imagepreptool.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.imagepreptool.presentation.ProjectItem
import com.imagepreptool.resources.Res
import com.imagepreptool.resources.ic_add_photo
import com.imagepreptool.resources.ic_close
import com.imagepreptool.resources.ic_folder_open
import com.imagepreptool.resources.ic_photo_library
import org.jetbrains.compose.resources.painterResource

@Composable
fun EmptyState(
    projects: List<ProjectItem>,
    isLoading: Boolean,
    onOpenFolder: () -> Unit,
    onPickImages: () -> Unit,
    onDeleteProject: (ProjectItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("画像を公開用に整える", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "リサイズ・形式変換・撮影情報の書き込みをまとめて行います",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            Surface(
                color = colors.surface,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().dashedBorder(colors.outline, 16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 40.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(colors.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(Res.drawable.ic_photo_library), null, tint = colors.onPrimaryContainer, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        if (isLoading) "読み込み中…" else "フォルダや画像をここにドロップ",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "JPEG・PNG・WebP・HEIC・GIF・BMP に対応",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onOpenFolder, enabled = !isLoading) {
                            Icon(painterResource(Res.drawable.ic_folder_open), null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("フォルダを開く")
                        }
                        OutlinedButton(onClick = onPickImages, enabled = !isLoading) {
                            Icon(painterResource(Res.drawable.ic_add_photo), null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("画像を選択")
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Ctrl+O でフォルダを開く", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }

            if (projects.isNotEmpty()) {
                Spacer(Modifier.height(32.dp))
                Text(
                    "プロジェクト",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp),
                )
                Surface(color = colors.surface, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        projects.forEach { project ->
                            ProjectRow(project, onDelete = { onDeleteProject(project) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectRow(project: ProjectItem, onDelete: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interaction)
            .background(if (hovered) colors.surfaceContainer else Color.Transparent)
            .clickable(onClick = project.listener::open)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(Res.drawable.ic_photo_library), null, tint = colors.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(project.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                project.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            if (hovered) {
                Icon(painterResource(Res.drawable.ic_close), contentDescription = "プロジェクトを削除", tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}

fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.5.dp): Modifier = drawWithContent {
    drawContent()
    val stroke = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
    )
}
