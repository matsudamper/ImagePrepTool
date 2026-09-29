package com.imagepreptool.presentation

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import com.imagepreptool.model.CaptionField
import com.imagepreptool.model.ConflictPolicy
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.ExternalTools
import com.imagepreptool.model.ImageSize
import com.imagepreptool.model.OutputFormat
import com.imagepreptool.model.ProcessResult
import com.imagepreptool.service.PlannedOutput

enum class SelectMode { Single, Toggle, Range }

data class ImageItem(
    val file: File,
    val included: Boolean,
)

data class PreviewState(
    val file: File?,
    val original: ImageBitmap?,
    val processed: ImageBitmap?,
    val originalSize: ImageSize?,
    val outputSize: ImageSize?,
    val outputFormat: OutputFormat?,
    /** この画像の撮影情報（テンプレートの項目ごと） */
    val captionFields: Map<CaptionField, String>,
    val loading: Boolean,
    val error: String?,
)

sealed interface ExportState {
    data object Idle : ExportState

    /** 出力ファイルを決めている間 */
    data object Preparing : ExportState

    data class ConfirmConflicts(val plan: List<PlannedOutput>) : ExportState {
        val conflictCount: Int get() = plan.count { it.exists }
    }

    data class Running(
        val done: Int,
        val total: Int,
        val currentName: String?,
        val cancelling: Boolean,
    ) : ExportState

    data class Finished(
        val results: List<ProcessResult>,
        val outputDir: File,
        val cancelled: Boolean,
        val total: Int,
    ) : ExportState {
        val successCount: Int get() = results.count { it.status == ProcessResult.Status.Success }
        val skippedCount: Int get() = results.count { it.status == ProcessResult.Status.Skipped }
        val failures: List<ProcessResult> get() = results.filter { it.status == ProcessResult.Status.Failed }
    }
}

enum class NoticeAction(val label: String) {
    ExcludeUnreadable("除外する"),
    ShowTools("詳細"),
}

data class Notice(
    val text: String,
    val blocking: Boolean,
    val action: NoticeAction?,
)

data class ImagePrepUiState(
    val images: List<ImageItem>,
    val includedCount: Int,
    val focusedFile: File?,
    val selectedFiles: Set<File>,
    /** 一覧の見出し（フォルダ名など） */
    val sourceTitle: String?,
    val sourcePath: String?,
    val options: EditOptions,
    val outputDirectory: File?,
    val isCustomOutputDirectory: Boolean,
    val tools: ExternalTools?,
    val recentFolders: List<File>,
    val preview: PreviewState,
    val export: ExportState,
    val notices: List<Notice>,
    val isLoading: Boolean,
    val listener: Listener,
) {
    val hasImages: Boolean get() = images.isNotEmpty()
    val canExport: Boolean get() = includedCount > 0 && notices.none { it.blocking } && export == ExportState.Idle && !isLoading
    val missingToolCount: Int
        get() = tools?.let { t ->
            listOf(t.canWriteWebp, t.heifDecoder != null).count { !it }
        } ?: 0

    /** UI から ViewModel へ伝える操作 */
    @Stable
    interface Listener {
        fun openFolder(dir: File)
        fun addFiles(files: List<File>)
        fun forgetRecent(dir: File)
        fun closeAll()
        fun clickImage(file: File, mode: SelectMode)
        fun toggleIncluded(file: File)
        fun toggleFocusedIncluded()
        fun setSelectionIncluded(included: Boolean)
        fun setAllIncluded(included: Boolean)
        fun selectAll()
        fun clearSelection()
        fun moveFocus(delta: Int)
        fun removeImage(file: File)
        fun excludeUnreadable()
        fun updateOptions(transform: (EditOptions) -> EditOptions)
        fun setInputValid(field: String, valid: Boolean)
        fun chooseOutputDirectory(dir: File)
        fun resetOutputDirectory()
        fun refreshTools()
        fun requestExport()
        fun resolveConflicts(policy: ConflictPolicy?)
        fun cancelExport()
        fun dismissExport()
    }
}
