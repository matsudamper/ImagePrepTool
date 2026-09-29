package com.imagepreptool.presentation

import java.io.File
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.ExternalToolStatus
import com.imagepreptool.model.ImageSelection
import com.imagepreptool.model.ProcessResult
import com.imagepreptool.model.WorkflowStep

data class ImagePrepUiState(
    val currentStep: WorkflowStep = WorkflowStep.Select,
    val rootDirectory: File? = null,
    val images: List<ImageSelection> = emptyList(),
    val selectedImageCount: Int = 0,
    val selectedImages: List<File> = emptyList(),
    val editOptions: EditOptions = EditOptions(),
    val outputDirectory: File? = null,
    val toolStatuses: List<ExternalToolStatus> = emptyList(),
    val toolsChecked: Boolean = false,
    val isProcessing: Boolean = false,
    val processLog: List<ProcessResult> = emptyList(),
    val statusMessage: String = "対象フォルダを選択してください（Windows 向け）",
)

internal data class ImagePrepViewModelState(
    val currentStep: WorkflowStep = WorkflowStep.Select,
    val rootDirectory: File? = null,
    val images: List<ImageSelection> = emptyList(),
    val editOptions: EditOptions = EditOptions(),
    val outputDirectory: File? = null,
    val toolStatuses: List<ExternalToolStatus> = emptyList(),
    val toolsChecked: Boolean = false,
    val isProcessing: Boolean = false,
    val processLog: List<ProcessResult> = emptyList(),
    val statusMessage: String = "対象フォルダを選択してください（Windows 向け）",
)

internal fun ImagePrepViewModelState.toUiState(): ImagePrepUiState {
    val selected = images.filter { it.selected }
    return ImagePrepUiState(
        currentStep = currentStep,
        rootDirectory = rootDirectory,
        images = images,
        selectedImageCount = selected.size,
        selectedImages = selected.map { it.file },
        editOptions = editOptions,
        outputDirectory = outputDirectory,
        toolStatuses = toolStatuses,
        toolsChecked = toolsChecked,
        isProcessing = isProcessing,
        processLog = processLog,
        statusMessage = statusMessage,
    )
}
