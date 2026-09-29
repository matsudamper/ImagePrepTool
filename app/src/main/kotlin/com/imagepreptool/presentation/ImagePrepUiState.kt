package com.imagepreptool.presentation

import androidx.compose.ui.graphics.ImageBitmap
import com.imagepreptool.model.CaptionField
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.ExternalTools
import com.imagepreptool.model.ImageSize
import com.imagepreptool.model.OutputFormat
import com.imagepreptool.model.ProcessResult
import com.imagepreptool.service.PlannedOutput
import java.io.File

enum class SelectMode { Single, Toggle, Range }

data class ImageItem(
    val file: File,
    val included: Boolean = true,
)

data class PreviewState(
    val file: File? = null,
    val original: ImageBitmap? = null,
    val processed: ImageBitmap? = null,
    val originalSize: ImageSize? = null,
    val outputSize: ImageSize? = null,
    val outputFormat: OutputFormat? = null,
    /** この画像の撮影情報（テンプレートの項目ごと） */
    val captionFields: Map<CaptionField, String> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null,
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
        val cancelling: Boolean = false,
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
    val action: NoticeAction? = null,
)

data class ImagePrepUiState(
    val images: List<ImageItem> = emptyList(),
    val includedCount: Int = 0,
    val focusedFile: File? = null,
    val selectedFiles: Set<File> = emptySet(),
    /** 一覧の見出し（フォルダ名など） */
    val sourceTitle: String? = null,
    val sourcePath: String? = null,
    val options: EditOptions = EditOptions(),
    val outputDirectory: File? = null,
    val isCustomOutputDirectory: Boolean = false,
    val tools: ExternalTools? = null,
    val recentFolders: List<File> = emptyList(),
    val preview: PreviewState = PreviewState(),
    val export: ExportState = ExportState.Idle,
    val notices: List<Notice> = emptyList(),
    val isLoading: Boolean = false,
) {
    val hasImages: Boolean get() = images.isNotEmpty()
    val canExport: Boolean get() = includedCount > 0 && notices.none { it.blocking } && export == ExportState.Idle
    val missingToolCount: Int
        get() = tools?.let { t ->
            listOf(t.canWriteWebp, t.heifDecoder != null).count { !it }
        } ?: 0
}
