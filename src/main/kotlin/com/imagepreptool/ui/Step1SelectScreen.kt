package com.imagepreptool.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.imagepreptool.model.WorkflowStep
import com.imagepreptool.state.AppState

@Composable
fun Step1SelectScreen(state: AppState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("起動時に対象フォルダを選び、処理する画像をチェックしてください。", fontWeight = FontWeight.SemiBold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                DesktopDialogs.pickDirectoryWithAwt("対象フォルダを選択")?.let(state::chooseRootDirectory)
            }) {
                Text("対象フォルダを選択")
            }
            OutlinedButton(onClick = { state.reloadImages() }, enabled = state.rootDirectory != null) {
                Text("一覧を更新")
            }
            OutlinedButton(onClick = { state.refreshToolCheck() }) {
                Text("外部ツール再確認")
            }
        }

        state.rootDirectory?.let { dir ->
            Text("現在: ${dir.absolutePath}")
        }

        ToolCheckPanel(state)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("画像 (${state.selectedImages.size}/${state.images.size} 選択)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { state.selectAll(true) }) { Text("全選択") }
                OutlinedButton(onClick = { state.selectAll(false) }) { Text("全解除") }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(state.images) { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { state.toggleImage(index, !item.selected) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = item.selected,
                        onCheckedChange = { state.toggleImage(index, it) },
                    )
                    Column {
                        Text(item.file.name, fontWeight = FontWeight.Medium)
                        Text("${item.file.length() / 1024} KB · ${item.file.extension.uppercase()}")
                    }
                }
            }
        }

        Button(
            onClick = { state.goToStep(WorkflowStep.Edit) },
            enabled = state.rootDirectory != null && state.selectedImages.isNotEmpty(),
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("Step2: 編集と出力へ")
        }
    }
}

@Composable
fun ToolCheckPanel(state: AppState) {
    if (!state.toolsChecked) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("外部コマンド確認（PATH 上の cwebp / dwebp / magick / heif-convert）", fontWeight = FontWeight.SemiBold)
            state.toolStatuses.forEach { tool ->
                val mark = if (tool.available) "✓" else "✗"
                Text("$mark ${tool.name}: ${tool.detail}")
            }
        }
    }
}
