package com.imagepreptool.data

import java.io.File
import com.imagepreptool.model.CropRect
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.OutputPathMode
import com.imagepreptool.model.PenStroke
import com.imagepreptool.model.PenTool
import com.imagepreptool.model.Rotation
import com.imagepreptool.presentation.ImageSortOrder

/** プロジェクト一覧に並べる情報 */
data class ProjectSummary(
    val id: Long,
    val name: String,
    val lastOpenedAtMillis: Long,
    /** 一覧から外していない画像の数 */
    val imageCount: Int,
)

/** 書き出しに使う設定。プロジェクトごとに持ち、新しいプロジェクトには最後に書き出した設定を引き継ぐ */
data class ExportSettings(
    val options: EditOptions,
    val outputPathMode: OutputPathMode,
    val relativeOutputPath: String,
    val absoluteOutputDir: File?,
)

/** プロジェクトに保存する作業内容 */
data class ProjectContent(
    val exportSettings: ExportSettings,
    val sortOrder: ImageSortOrder,
    val focusedFile: File?,
    val isSelectionMode: Boolean,
    /** 一覧に並ぶ画像を並び順に。続けて一覧から外した画像や見つからない画像も含む */
    val images: List<ProjectImage>,
    /**
     * 切り抜き・回転・ペンを編集したときの画像ファイルの大きさと日時。編集の無い画像は含めない。
     * 保存した後に中身が変わっていないかの照合に使うため、保存した時点ではなく編集した時点の値を持つ
     */
    val editStamps: Map<File, FileStamp>,
)

data class ProjectImage(
    val file: File,
    val removed: Boolean,
    val selected: Boolean,
    val crop: CropRect?,
    val rotation: Rotation,
    val strokes: List<PenStroke>,
)

/** 保存したときの画像ファイルの大きさと更新日時。中身が変わったかどうかの照合に使う */
data class FileStamp(val size: Long, val modifiedAtMillis: Long) {
    companion object {
        fun of(file: File): FileStamp = FileStamp(file.length(), file.lastModified())
    }
}

class StoredProject(
    val id: Long,
    val name: String,
    val content: ProjectContent,
    val stamps: Map<File, FileStamp>,
)

interface ProjectStore {
    suspend fun listProjects(): List<ProjectSummary>

    suspend fun createProject(name: String, exportSettings: ExportSettings, nowMillis: Long): Long

    suspend fun loadProject(id: Long): StoredProject?

    /** [previous] は前回保存した内容。変わった部分だけを書く。null なら [current] をすべて書く */
    suspend fun saveProject(id: Long, previous: ProjectContent?, current: ProjectContent)

    suspend fun markOpened(id: Long, nowMillis: Long)

    suspend fun renameProject(id: Long, name: String)

    suspend fun deleteProject(id: Long)

    suspend fun loadLastProjectId(): Long?

    suspend fun saveLastProjectId(id: Long?)

    suspend fun loadLastExportSettings(): ExportSettings?

    suspend fun saveLastExportSettings(settings: ExportSettings)

    suspend fun loadPenTool(): PenTool?

    suspend fun savePenTool(tool: PenTool)
}
