package com.imagepreptool.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.ImageSelection
import com.imagepreptool.model.ProcessResult
import com.imagepreptool.model.WorkflowStep
import com.imagepreptool.service.ExternalToolChecker
import com.imagepreptool.service.ImageProcessor

class ImagePrepViewModel : ViewModel() {

    private val viewModelStateFlow = MutableStateFlow(ImagePrepViewModelState())

    val uiStateFlow: StateFlow<ImagePrepUiState> =
        MutableStateFlow(ImagePrepUiState()).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.value = viewModelState.toUiState()
                }
            }
        }.asStateFlow()

    init {
        refreshToolCheck()
    }

    private fun mutate(transform: (ImagePrepViewModelState) -> ImagePrepViewModelState) {
        viewModelStateFlow.value = transform(viewModelStateFlow.value)
    }

    fun goToStep(step: WorkflowStep) {
        when (step) {
            WorkflowStep.Edit -> {
                val state = viewModelStateFlow.value
                if (state.rootDirectory == null) {
                    mutate { it.copy(statusMessage = "先に対象フォルダを選んでください") }
                    return
                }
                if (state.images.none { it.selected }) {
                    mutate { it.copy(statusMessage = "処理する画像を1枚以上選択してください") }
                    return
                }
            }
            WorkflowStep.Select -> Unit
        }
        mutate { it.copy(currentStep = step) }
    }

    fun chooseRootDirectory(dir: File) {
        mutate {
            it.copy(
                rootDirectory = dir,
                outputDirectory = File(dir, "output"),
                statusMessage = "フォルダ: ${dir.absolutePath}",
            )
        }
        reloadImages()
    }

    fun reloadImages() {
        val dir = viewModelStateFlow.value.rootDirectory ?: return
        val supported = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")
        val loaded = dir.listFiles()
            ?.filter { file -> file.isFile && file.extension.lowercase() in supported }
            ?.sortedBy { file -> file.name.lowercase() }
            ?.map { file -> ImageSelection(file, selected = true) }
            .orEmpty()
        mutate { it.copy(images = loaded) }
    }

    fun toggleImage(index: Int, selected: Boolean) {
        mutate { state ->
            state.copy(
                images = state.images.toMutableList().also { list ->
                    list[index] = list[index].copy(selected = selected)
                },
            )
        }
    }

    fun selectAll(selected: Boolean) {
        mutate { state -> state.copy(images = state.images.map { it.copy(selected = selected) }) }
    }

    fun updateEditOptions(transform: (EditOptions) -> EditOptions) {
        mutate { state -> state.copy(editOptions = transform(state.editOptions)) }
    }

    fun chooseOutputDirectory(dir: File) {
        mutate { it.copy(outputDirectory = dir) }
    }

    fun refreshToolCheck() {
        val toolStatuses = ExternalToolChecker.checkAll()
        val missing = toolStatuses.filter { !it.available }.joinToString { tool -> tool.name }
        val statusMessage = if (missing.isBlank()) {
            "外部ツール確認 OK"
        } else {
            "未検出: $missing （WebP/HEIF 変換時に必要）"
        }
        mutate {
            it.copy(
                toolStatuses = toolStatuses,
                toolsChecked = true,
                statusMessage = statusMessage,
            )
        }
    }

    fun runProcessing() {
        val state = viewModelStateFlow.value
        val out = state.outputDirectory
        val sources = state.images.filter { it.selected }.map { it.file }
        if (out == null) {
            mutate { it.copy(statusMessage = "出力先を指定してください") }
            return
        }
        if (sources.isEmpty()) {
            mutate { it.copy(statusMessage = "画像が選択されていません") }
            return
        }
        mutate { it.copy(isProcessing = true, processLog = emptyList()) }
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                ImageProcessor().processBatch(sources, out, state.editOptions) { done, total, result ->
                    mutate { vm ->
                        vm.copy(
                            processLog = vm.processLog + result,
                            statusMessage = "処理中 $done / $total",
                        )
                    }
                }
            }
            val ok = results.count(ProcessResult::success)
            mutate {
                it.copy(
                    isProcessing = false,
                    statusMessage = "完了: 成功 $ok / ${results.size} → ${out.absolutePath}",
                )
            }
        }
    }
}
