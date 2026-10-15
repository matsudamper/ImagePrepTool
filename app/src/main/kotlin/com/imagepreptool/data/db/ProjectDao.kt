package com.imagepreptool.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.imagepreptool.data.ProjectSummary

@Dao
internal interface ProjectDao {
    @Query(
        """
        SELECT p.id, p.name, p.lastOpenedAtMillis,
            (SELECT COUNT(*) FROM project_image i WHERE i.projectId = p.id AND i.removed = 0) AS imageCount
        FROM project p
        ORDER BY p.lastOpenedAtMillis DESC
        """,
    )
    suspend fun listSummaries(): List<ProjectSummary>

    @Insert
    suspend fun insertProject(project: ProjectEntity): Long

    @Query("SELECT * FROM project WHERE id = :id")
    suspend fun findProject(id: Long): ProjectEntity?

    @Update(entity = ProjectEntity::class)
    suspend fun updateProjectState(update: ProjectStateUpdate)

    @Query("UPDATE project SET lastOpenedAtMillis = :nowMillis WHERE id = :id")
    suspend fun markOpened(id: Long, nowMillis: Long)

    @Query("UPDATE project SET name = :name WHERE id = :id")
    suspend fun renameProject(id: Long, name: String)

    @Query("DELETE FROM project WHERE id = :id")
    suspend fun deleteProject(id: Long)

    @Query("SELECT * FROM project_image WHERE projectId = :projectId ORDER BY position")
    suspend fun images(projectId: Long): List<ProjectImageEntity>

    @Query("SELECT * FROM project_image WHERE projectId = :projectId AND path = :path")
    suspend fun image(projectId: Long, path: String): ProjectImageEntity?

    @Upsert
    suspend fun upsertImages(images: List<ProjectImageEntity>)

    @Query("DELETE FROM project_image WHERE projectId = :projectId AND path = :path")
    suspend fun deleteImage(projectId: Long, path: String)

    @Query("SELECT * FROM pen_stroke WHERE projectId = :projectId ORDER BY position")
    suspend fun strokes(projectId: Long): List<PenStrokeEntity>

    @Query("DELETE FROM pen_stroke WHERE projectId = :projectId AND path = :path")
    suspend fun deleteStrokes(projectId: Long, path: String)

    @Insert
    suspend fun insertStrokes(strokes: List<PenStrokeEntity>)

    @Query("SELECT * FROM app_state WHERE id = $SINGLE_ROW_ID")
    suspend fun appState(): AppStateEntity?

    @Upsert
    suspend fun upsertAppState(state: AppStateEntity)

    @Query("SELECT * FROM last_export_settings WHERE id = $SINGLE_ROW_ID")
    suspend fun lastExportSettings(): LastExportSettingsEntity?

    @Upsert
    suspend fun upsertLastExportSettings(settings: LastExportSettingsEntity)

    @Query("SELECT * FROM pen_tool WHERE id = $SINGLE_ROW_ID")
    suspend fun penTool(): PenToolEntity?

    @Upsert
    suspend fun upsertPenTool(tool: PenToolEntity)
}
