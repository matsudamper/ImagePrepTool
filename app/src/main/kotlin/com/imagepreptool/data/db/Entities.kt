package com.imagepreptool.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.OutputPathMode
import com.imagepreptool.model.PenKind
import com.imagepreptool.model.PenTool
import com.imagepreptool.model.Rotation
import com.imagepreptool.presentation.ImageSortKey

/** 1 行だけのテーブルの主キー */
internal const val SINGLE_ROW_ID = 0

internal data class ExportSettingsColumns(
    @Embedded(prefix = "options_") val options: EditOptions,
    val outputPathMode: OutputPathMode,
    val relativeOutputPath: String,
    val absoluteOutputDir: String?,
)

@Entity(tableName = "project")
internal data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val createdAtMillis: Long,
    val lastOpenedAtMillis: Long,
    @Embedded val exportSettings: ExportSettingsColumns,
    val sortKey: ImageSortKey,
    val sortAscending: Boolean,
    val focusedPath: String?,
    val isSelectionMode: Boolean,
)

/** 作業中に変わる [ProjectEntity] の列 */
internal data class ProjectStateUpdate(
    val id: Long,
    @Embedded val exportSettings: ExportSettingsColumns,
    val sortKey: ImageSortKey,
    val sortAscending: Boolean,
    val focusedPath: String?,
    val isSelectionMode: Boolean,
)

@Entity(
    tableName = "project_image",
    primaryKeys = ["projectId", "path"],
    foreignKeys = [
        ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE),
    ],
)
internal data class ProjectImageEntity(
    val projectId: Long,
    val path: String,
    val position: Int,
    val removed: Boolean,
    val selected: Boolean,
    val cropLeft: Float?,
    val cropTop: Float?,
    val cropRight: Float?,
    val cropBottom: Float?,
    val rotation: Rotation,
    val fileSize: Long,
    val fileModifiedAtMillis: Long,
)

@Entity(
    tableName = "pen_stroke",
    foreignKeys = [
        ForeignKey(
            entity = ProjectImageEntity::class,
            parentColumns = ["projectId", "path"],
            childColumns = ["projectId", "path"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId", "path")],
)
internal class PenStrokeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val projectId: Long,
    val path: String,
    val position: Int,
    val kind: PenKind,
    val widthPercent: Float,
    val color: Int,
    val blurPercent: Float,
    /** x, y の Float を交互に並べたもの */
    val points: ByteArray,
)

@Entity(tableName = "app_state")
internal data class AppStateEntity(
    @PrimaryKey val id: Int,
    val lastProjectId: Long?,
)

@Entity(tableName = "last_export_settings")
internal data class LastExportSettingsEntity(
    @PrimaryKey val id: Int,
    @Embedded val exportSettings: ExportSettingsColumns,
)

@Entity(tableName = "pen_tool")
internal data class PenToolEntity(
    @PrimaryKey val id: Int,
    @Embedded val tool: PenTool,
)
