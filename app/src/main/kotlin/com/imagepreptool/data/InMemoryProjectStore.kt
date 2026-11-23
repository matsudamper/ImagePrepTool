package com.imagepreptool.data

import com.imagepreptool.model.PenTool
import com.imagepreptool.presentation.ImageSortKey
import com.imagepreptool.presentation.ImageSortOrder

class InMemoryProjectStore : ProjectStore {
    private val projects = linkedMapOf<Long, StoredProject>()
    private val lastOpened = mutableMapOf<Long, Long>()
    private var nextId = 1L
    private var lastProjectId: Long? = null
    private var lastExportSettings: ExportSettings? = null
    private var penTool: PenTool? = null

    override suspend fun listProjects(): List<ProjectSummary> =
        projects.values
            .map { project ->
                ProjectSummary(project.id, project.name, lastOpened[project.id] ?: 0, project.content.images.count { !it.removed })
            }
            .sortedByDescending { it.lastOpenedAtMillis }

    override suspend fun createProject(name: String, exportSettings: ExportSettings, nowMillis: Long): Long {
        val id = nextId++
        val content = ProjectContent(
            exportSettings = exportSettings,
            sortOrder = ImageSortOrder(ImageSortKey.Name, ascending = true),
            focusedFile = null,
            isSelectionMode = false,
            images = listOf(),
            editStamps = mapOf(),
        )
        projects[id] = StoredProject(id, name, content, stamps = mapOf())
        lastOpened[id] = nowMillis
        return id
    }

    override suspend fun loadProject(id: Long): StoredProject? = projects[id]

    override suspend fun saveProject(id: Long, previous: ProjectContent?, current: ProjectContent) {
        val project = projects[id] ?: return
        projects[id] = StoredProject(id, project.name, current, project.stamps + current.editStamps)
    }

    override suspend fun markOpened(id: Long, nowMillis: Long) {
        lastOpened[id] = nowMillis
    }

    override suspend fun renameProject(id: Long, name: String) {
        val project = projects[id] ?: return
        projects[id] = StoredProject(id, name, project.content, project.stamps)
    }

    override suspend fun deleteProject(id: Long) {
        projects -= id
        if (lastProjectId == id) lastProjectId = null
    }

    override suspend fun loadLastProjectId(): Long? = lastProjectId

    override suspend fun saveLastProjectId(id: Long?) {
        lastProjectId = id
    }

    override suspend fun loadLastExportSettings(): ExportSettings? = lastExportSettings

    override suspend fun saveLastExportSettings(settings: ExportSettings) {
        lastExportSettings = settings
    }

    override suspend fun loadPenTool(): PenTool? = penTool

    override suspend fun savePenTool(tool: PenTool) {
        penTool = tool
    }
}
