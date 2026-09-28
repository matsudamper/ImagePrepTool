package com.imagepreptool.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.ExternalToolStatus
import com.imagepreptool.model.ImageSelection
import com.imagepreptool.model.ProcessResult
import com.imagepreptool.model.WorkflowStep
import com.imagepreptool.service.ExternalToolChecker
import com.imagepreptool.service.ImageProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppState {
    var currentStep by mutableStateOf(WorkflowStep.Select)
        private set

    var rootDirectory by mutableStateOf<File?>(null)
        private set

    var images by mutableStateOf<List<ImageSelection>>(emptyList())
        private set

    var editOptions by mutableStateOf(EditOptions())
        private set

    var outputDirectory by mutableStateOf<File?>(null)
        private set

    var toolStatuses by mutableStateOf<List<ExternalToolStatus>>(emptyList())
        private set

    var toolsChecked by mutableStateOf(false)
        private set

    var isProcessing by mutableStateOf(false)
        private set

    var processLog by mutableStateOf<List<ProcessResult>>(emptyList())
        private set

    var statusMessage by mutableStateOf("対象フォルダを選択してください（Windows 向け）")
        private set

    val selectedImages: List<File>
        get() = images.filter { it.selected }.map { it.file }

    fun goToStep(step: WorkflowStep) {
        when (step) {
            WorkflowStep.Edit -> {
                if (rootDirectory == null) {
                    statusMessage = "先に対象フォルダを選んでください"
                    return
                }
                if (selectedImages.isEmpty()) {
                    statusMessage = "処理する画像を1枚以上選択してください"
                    return
                }
            }
            WorkflowStep.Select -> Unit
        }
        currentStep = step
    }

    fun chooseRootDirectory(dir: File) {
        rootDirectory = dir
        outputDirectory = File(dir, "output")
        reloadImages()
        statusMessage = "フォルダ: ${dir.absolutePath}"
    }

    fun reloadImages() {
        val dir = rootDirectory ?: return
        val supported = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")
        images = dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in supported }
            ?.sortedBy { it.name.lowercase() }
            ?.map { ImageSelection(it, selected = true) }
            .orEmpty()
    }

    fun toggleImage(index: Int, selected: Boolean) {
        images = images.toMutableList().also { it[index] = it[index].copy(selected = selected) }
    }

    fun selectAll(selected: Boolean) {
        images = images.map { it.copy(selected = selected) }
    }

    fun updateEditOptions(transform: (EditOptions) -> EditOptions) {
        editOptions = transform(editOptions)
    }

    fun chooseOutputDirectory(dir: File) {
        outputDirectory = dir
    }

    fun refreshToolCheck() {
        toolStatuses = ExternalToolChecker.checkAll()
        toolsChecked = true
        val missing = toolStatuses.filter { !it.available }.joinToString { it.name }
        statusMessage = if (missing.isBlank()) {
            "外部ツール確認 OK"
        } else {
            "未検出: $missing （WebP/HEIF 変換時に必要）"
        }
    }

    fun runProcessing(scope: CoroutineScope) {
        val out = outputDirectory
        val sources = selectedImages
        if (out == null) {
            statusMessage = "出力先を指定してください"
            return
        }
        if (sources.isEmpty()) {
            statusMessage = "画像が選択されていません"
            return
        }
        isProcessing = true
        processLog = emptyList()
        scope.launch {
            val results = withContext(Dispatchers.IO) {
                ImageProcessor().processBatch(sources, out, editOptions) { done, total, result ->
                    processLog = processLog + result
                    statusMessage = "処理中 $done / $total"
                }
            }
            isProcessing = false
            val ok = results.count { it.success }
            statusMessage = "完了: 成功 $ok / ${results.size} → ${out.absolutePath}"
        }
    }
}
