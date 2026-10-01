package com.imagepreptool.presentation

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import com.imagepreptool.model.CaptionField
import com.imagepreptool.model.ConflictPolicy
import com.imagepreptool.model.CropRect
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.ExternalTools
import com.imagepreptool.model.ImageSize
import com.imagepreptool.model.OutputFormat
import com.imagepreptool.model.ProcessResult
import com.imagepreptool.service.PlannedOutput

enum class SelectMode { Single, Toggle, Range }

data class ImageItem(
    val file: File,
)

/** 一覧で同じフォルダの画像をまとめて見出しを付ける単位 */
data class ImageGroup(
    val folder: File,
    val images: List<ImageItem>,
)

data class PreviewState(
    val file: File?,
    val original: ImageBitmap?,
    val processed: ImageBitmap?,
    val originalSize: ImageSize?,
    val outputSize: ImageSize?,
    val outputFormat: OutputFormat?,
    /** 書き出し後のおおよそのファイルサイズ。キャプションを含まず、プレビュー用に縮小して読んだ場合は出さない */
    val outputByteSize: Long?,
    /** この画像の撮影情報（テンプレートの項目ごと） */
    val captionFields: Map<CaptionField, String>,
    /** この画像の切り抜き範囲。切り抜かないときは null */
    val crop: CropRect?,
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
    RemoveUnreadable("一覧から削除"),
}

/** スナックバーで一度だけ表示するメッセージ */
data class SnackbarMessage(
    val text: String,
    val canUndoRemoval: Boolean = false,
)

data class Notice(
    val text: String,
    val blocking: Boolean,
    val action: NoticeAction?,
)

data class ImagePrepUiState(
    val images: List<ImageItem>,
    val isWorkspaceOpen: Boolean,
    /** [images] をフォルダごとに区切ったもの。並び順は [images] と同じ */
    val imageGroups: List<ImageGroup>,
    /** 書き出す枚数。複数選択中は選択中の画像、そうでなければ一覧のすべて */
    val exportCount: Int,
    val isExportingSelection: Boolean,
    val focusedFile: File?,
    val selectedFiles: Set<File>,
    /** 画像を開くダイアログで最初に表示するフォルダ */
    val pickerInitialDirectory: File?,
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
    val canExport: Boolean get() = exportCount > 0 && notices.none { it.blocking } && export == ExportState.Idle && !isLoading
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
        fun selectAll()
        fun clearSelection()
        fun moveFocus(delta: Int)
        fun removeImage(file: File)
        fun removeSelection()
        fun removeFolder(folder: File)
        fun removeUnreadable()
        fun undoRemoval()
        fun updateOptions(transform: (EditOptions) -> EditOptions)
        fun setInputValid(field: String, valid: Boolean)
        fun setCrop(file: File, crop: CropRect?)
        fun chooseOutputDirectory(dir: File)
        fun resetOutputDirectory()
        fun requestExport()
        fun resolveConflicts(policy: ConflictPolicy?)
        fun cancelExport()
        fun dismissExport()
    }
}
