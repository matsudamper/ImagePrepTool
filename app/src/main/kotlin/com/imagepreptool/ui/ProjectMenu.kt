package com.imagepreptool.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.imagepreptool.presentation.ProjectItem
import com.imagepreptool.resources.Res
import com.imagepreptool.resources.ic_add
import com.imagepreptool.resources.ic_check
import com.imagepreptool.resources.ic_chevron_right
import org.jetbrains.compose.resources.painterResource

/** 開いているプロジェクトの名前。押すとプロジェクトの切り替えや作成・名前変更・削除ができる */
@Composable
internal fun ProjectMenu(
    projectName: String,
    projects: List<ProjectItem>,
    onCreate: () -> Unit,
    onRename: () -> Unit,
    onDelete: (ProjectItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box(modifier) {
        TextButton(onClick = { expanded = true }) {
            Text(
                projectName,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 360.dp),
            )
            Spacer(Modifier.width(4.dp))
            Icon(painterResource(Res.drawable.ic_chevron_right), null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp).rotate(90f))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                "プロジェクト",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            projects.forEach { project ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 320.dp))
                            Text(project.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    },
                    leadingIcon = {
                        if (project.isCurrent) {
                            Icon(painterResource(Res.drawable.ic_check), "開いているプロジェクト", tint = colors.primary, modifier = Modifier.size(18.dp))
                        } else {
                            Spacer(Modifier.size(18.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        project.listener.open()
                    },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = colors.outlineVariant)
            DropdownMenuItem(
                text = { Text("新しいプロジェクト") },
                leadingIcon = { Icon(painterResource(Res.drawable.ic_add), null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    expanded = false
                    onCreate()
                },
            )
            DropdownMenuItem(
                text = { Text("名前を変更…") },
                leadingIcon = { Spacer(Modifier.size(18.dp)) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            val current = projects.firstOrNull { it.isCurrent }
            if (current != null) {
                DropdownMenuItem(
                    text = { Text("このプロジェクトを削除…", color = colors.error) },
                    leadingIcon = { Spacer(Modifier.size(18.dp)) },
                    onClick = {
                        expanded = false
                        onDelete(current)
                    },
                )
            }
        }
    }
}

/** 「12 枚・2026/10/03 14:20」 */
internal val ProjectItem.subtitle: String
    get() = "$imageCount 枚・${LastOpenedFormat.format(Instant.ofEpochMilli(lastOpenedAtMillis))}"

private val LastOpenedFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.systemDefault())
