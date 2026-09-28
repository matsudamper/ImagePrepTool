package com.imagepreptool.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.imagepreptool.model.ExifTextPosition
import com.imagepreptool.model.OutputFormat
import com.imagepreptool.model.WorkflowStep
import com.imagepreptool.presentation.ImagePrepUiState
import com.imagepreptool.presentation.ImagePrepViewModel
import com.imagepreptool.service.ExifService

@Composable
fun Step2EditScreen(uiState: ImagePrepUiState, viewModel: ImagePrepViewModel) {
    val options = uiState.editOptions
    val previewCaption = uiState.selectedImages.firstOrNull()?.let { file ->
        ExifService.buildCaption(file, options.customExifLine)
    }.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "選択 ${uiState.selectedImageCount} 枚 · リサイズ / 形式変換 / EXIF テキスト焼き込み",
            fontWeight = FontWeight.SemiBold,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.goToStep(WorkflowStep.Select) }) {
                Text("Step1 に戻る")
            }
            OutlinedButton(onClick = {
                DesktopDialogs.pickDirectoryWithAwt("出力フォルダ", uiState.outputDirectory)
                    ?.let(viewModel::chooseOutputDirectory)
            }) {
                Text("出力先を変更")
            }
        }

        Text("出力先: ${uiState.outputDirectory?.absolutePath ?: "未設定（対象フォルダ/output）"}")

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = options.maxWidth?.toString().orEmpty(),
                onValueChange = { v ->
                    viewModel.updateEditOptions { it.copy(maxWidth = v.toIntOrNull()) }
                },
                label = { Text("最大幅 px") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = options.maxHeight?.toString().orEmpty(),
                onValueChange = { v ->
                    viewModel.updateEditOptions { it.copy(maxHeight = v.toIntOrNull()) }
                },
                label = { Text("最大高 px") },
                modifier = Modifier.weight(1f),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = options.keepAspectRatio,
                onCheckedChange = { checked ->
                    viewModel.updateEditOptions { it.copy(keepAspectRatio = checked) }
                },
            )
            Text("アスペクト比を維持")
        }

        EnumDropdown(
            label = "出力形式",
            value = options.outputFormat,
            options = OutputFormat.entries,
            labelFor = { it.label },
            onSelect = { selected -> viewModel.updateEditOptions { opts -> opts.copy(outputFormat = selected) } },
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = options.burnExifText,
                onCheckedChange = { checked ->
                    viewModel.updateEditOptions { it.copy(burnExifText = checked) }
                },
            )
            Text("EXIF 情報を画像に焼き込む")
        }

        EnumDropdown(
            label = "EXIF テキスト位置",
            value = options.exifPosition,
            options = ExifTextPosition.entries,
            labelFor = { it.label },
            onSelect = { selected -> viewModel.updateEditOptions { opts -> opts.copy(exifPosition = selected) } },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = options.exifFontSize.toString(),
                onValueChange = { v ->
                    viewModel.updateEditOptions { it.copy(exifFontSize = v.toIntOrNull() ?: it.exifFontSize) }
                },
                label = { Text("文字サイズ") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = options.exifMargin.toString(),
                onValueChange = { v ->
                    viewModel.updateEditOptions { it.copy(exifMargin = v.toIntOrNull() ?: it.exifMargin) }
                },
                label = { Text("余白 px") },
                modifier = Modifier.weight(1f),
            )
        }

        OutlinedTextField(
            value = options.customExifLine,
            onValueChange = { line -> viewModel.updateEditOptions { it.copy(customExifLine = line) } },
            label = { Text("カスタム EXIF 行（空なら自動取得）") },
            modifier = Modifier.fillMaxWidth(),
        )

        if (previewCaption.isNotBlank()) {
            Text("プレビュー文言: $previewCaption")
        }

        Button(
            onClick = { viewModel.runProcessing() },
            enabled = !uiState.isProcessing && uiState.selectedImageCount > 0,
        ) {
            Text(if (uiState.isProcessing) "処理中…" else "選択画像を処理して出力")
        }

        if (uiState.processLog.isNotEmpty()) {
            Text("結果", fontWeight = FontWeight.SemiBold)
            uiState.processLog.forEach { result ->
                val prefix = if (result.success) "OK" else "NG"
                Text("$prefix · ${result.source.name} · ${result.message}")
            }
        }
    }
}

@Composable
private fun <T> EnumDropdown(
    label: String,
    value: T,
    options: List<T>,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, fontWeight = FontWeight.Medium)
        OutlinedButton(onClick = { expanded = true }) {
            Text(labelFor(value))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(onClick = {
                    onSelect(option)
                    expanded = false
                }) {
                    Text(labelFor(option))
                }
            }
        }
    }
}
