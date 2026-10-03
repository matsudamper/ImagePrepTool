package com.imagepreptool.data.db

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.imagepreptool.data.ExportSettings
import com.imagepreptool.data.FileStamp
import com.imagepreptool.data.ProjectContent
import com.imagepreptool.data.ProjectImage
import com.imagepreptool.data.ProjectStore
import com.imagepreptool.data.ProjectSummary
import com.imagepreptool.data.StoredProject
import com.imagepreptool.model.CropRect
import com.imagepreptool.model.PenPoint
import com.imagepreptool.model.PenStroke
import com.imagepreptool.model.PenTool
import com.imagepreptool.presentation.ImageSortKey
import com.imagepreptool.presentation.ImageSortOrder

internal class RoomProjectStore(private val database: AppDatabase) : ProjectStore {

    private val dao = database.projectDao()

    override suspend fun listProjects(): List<ProjectSummary> = dao.listSummaries()

    override suspend fun createProject(name: String, exportSettings: ExportSettings, nowMillis: Long): Long =
        dao.insertProject(
            ProjectEntity(
                id = 0,
                name = name,
                createdAtMillis = nowMillis,
                lastOpenedAtMillis = nowMillis,
                exportSettings = exportSettings.toColumns(),
                sortKey = ImageSortKey.Name,
                sortAscending = true,
                focusedPath = null,
                isSelectionMode = false,
            ),
        )

    override suspend fun loadProject(id: Long): StoredProject? {
        val project = dao.findProject(id) ?: return null
        val images = dao.images(id)
        val strokesByPath = dao.strokes(id).groupBy({ it.path }, { it.toModel() })
        val content = ProjectContent(
            exportSettings = project.exportSettings.toModel(),
            sortOrder = ImageSortOrder(project.sortKey, project.sortAscending),
            focusedFile = project.focusedPath?.let(::File),
            isSelectionMode = project.isSelectionMode,
            images = images.map { it.toModel(strokesByPath[it.path].orEmpty()) },
        )
        val stamps = images.associate { File(it.path) to FileStamp(it.fileSize, it.fileModifiedAtMillis) }
        return StoredProject(id = project.id, name = project.name, content = content, stamps = stamps)
    }

    override suspend fun saveProject(id: Long, previous: ProjectContent?, current: ProjectContent) {
        val previousImages = previous?.images.orEmpty().withIndex().associateBy { it.value.file }
        val changedImages = current.images.withIndex().filter { previousImages[it.value.file] != it }
        // 前回の内容が無いときは DB に何が残っているか分からないため、線が無い画像も書き直して消す
        val changedStrokes = if (previous == null) {
            current.images
        } else {
            current.images.filter { image -> previousImages[image.file]?.value?.strokes.orEmpty() != image.strokes }
        }
        val deletedFiles = previousImages.keys - current.images.map { it.file }.toSet()
        val isStateChanged = previous == null || previous.copy(images = listOf()) != current.copy(images = listOf())
        val currentStamps = withContext(Dispatchers.IO) {
            changedImages.map { it.value.file }.filter { it.isFile }.associateWith(FileStamp::of)
        }
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                if (isStateChanged) dao.updateProjectState(current.toStateUpdate(id))
                deletedFiles.forEach { dao.deleteImage(id, it.path) }
                val storedImages = dao.images(id).associateBy { it.path }
                if (previous == null) {
                    // DB に残っている、今の内容に無い画像を消す
                    val currentPaths = current.images.map { it.file.path }.toSet()
                    storedImages.keys.filter { it !in currentPaths }.forEach { dao.deleteImage(id, it) }
                }
                val rows = changedImages.map { (position, image) ->
                    val storedStamp = storedImages[image.file.path]?.let { FileStamp(it.fileSize, it.fileModifiedAtMillis) }
                    // 大きさと日時は、編集したときの中身を表す。選択など編集と関係ない変更では更新せず、
                    // 開いている間に外で差し替えられても、次に開いたときに照合できるようにする。
                    // 見つからない画像も、戻ってきたときに照合できるよう前の値を残す
                    val keepsStoredStamp = previous == null || previousImages[image.file]?.value?.hasSameEdits(image) == true
                    val stamp = if (keepsStoredStamp) {
                        storedStamp ?: currentStamps[image.file]
                    } else {
                        currentStamps[image.file] ?: storedStamp
                    }
                    image.toEntity(id, position, stamp ?: FileStamp(0, 0))
                }
                if (rows.isNotEmpty()) dao.upsertImages(rows)
                changedStrokes.forEach { image ->
                    dao.deleteStrokes(id, image.file.path)
                    dao.insertStrokes(image.strokes.mapIndexed { position, stroke -> stroke.toEntity(id, image.file.path, position) })
                }
            }
        }
    }

    override suspend fun markOpened(id: Long, nowMillis: Long) = dao.markOpened(id, nowMillis)

    override suspend fun renameProject(id: Long, name: String) = dao.renameProject(id, name)

    override suspend fun deleteProject(id: Long) {
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                dao.deleteProject(id)
                if (dao.appState()?.lastProjectId == id) dao.upsertAppState(AppStateEntity(SINGLE_ROW_ID, lastProjectId = null))
            }
        }
    }

    override suspend fun loadLastProjectId(): Long? = dao.appState()?.lastProjectId

    override suspend fun saveLastProjectId(id: Long?) = dao.upsertAppState(AppStateEntity(SINGLE_ROW_ID, lastProjectId = id))

    override suspend fun loadLastExportSettings(): ExportSettings? = dao.lastExportSettings()?.exportSettings?.toModel()

    override suspend fun saveLastExportSettings(settings: ExportSettings) =
        dao.upsertLastExportSettings(LastExportSettingsEntity(SINGLE_ROW_ID, settings.toColumns()))

    override suspend fun loadPenTool(): PenTool? = dao.penTool()?.tool

    override suspend fun savePenTool(tool: PenTool) = dao.upsertPenTool(PenToolEntity(SINGLE_ROW_ID, tool))
}

private fun ExportSettings.toColumns() = ExportSettingsColumns(
    options = options,
    outputPathMode = outputPathMode,
    relativeOutputPath = relativeOutputPath,
    absoluteOutputDir = absoluteOutputDir?.path,
)

private fun ExportSettingsColumns.toModel() = ExportSettings(
    options = options,
    outputPathMode = outputPathMode,
    relativeOutputPath = relativeOutputPath,
    absoluteOutputDir = absoluteOutputDir?.let(::File),
)

private fun ProjectContent.toStateUpdate(id: Long) = ProjectStateUpdate(
    id = id,
    exportSettings = exportSettings.toColumns(),
    sortKey = sortOrder.key,
    sortAscending = sortOrder.ascending,
    focusedPath = focusedFile?.path,
    isSelectionMode = isSelectionMode,
)

private fun ProjectImage.hasSameEdits(other: ProjectImage): Boolean =
    crop == other.crop && rotation == other.rotation && strokes == other.strokes

private fun ProjectImage.toEntity(projectId: Long, position: Int, stamp: FileStamp) = ProjectImageEntity(
    projectId = projectId,
    path = file.path,
    position = position,
    removed = removed,
    selected = selected,
    cropLeft = crop?.left,
    cropTop = crop?.top,
    cropRight = crop?.right,
    cropBottom = crop?.bottom,
    rotation = rotation,
    fileSize = stamp.size,
    fileModifiedAtMillis = stamp.modifiedAtMillis,
)

private fun ProjectImageEntity.toModel(strokes: List<PenStroke>): ProjectImage {
    val left = cropLeft
    val top = cropTop
    val right = cropRight
    val bottom = cropBottom
    return ProjectImage(
        file = File(path),
        removed = removed,
        selected = selected,
        crop = if (left != null && top != null && right != null && bottom != null) CropRect(left, top, right, bottom) else null,
        rotation = rotation,
        strokes = strokes,
    )
}

private fun PenStroke.toEntity(projectId: Long, path: String, position: Int) = PenStrokeEntity(
    id = 0,
    projectId = projectId,
    path = path,
    position = position,
    kind = kind,
    widthPercent = widthPercent,
    color = color,
    blurPercent = blurPercent,
    points = encodePoints(points),
)

private fun PenStrokeEntity.toModel() = PenStroke(
    kind = kind,
    points = decodePoints(points),
    widthPercent = widthPercent,
    color = color,
    blurPercent = blurPercent,
)

private const val BYTES_PER_POINT = Float.SIZE_BYTES * 2

private fun encodePoints(points: List<PenPoint>): ByteArray {
    val buffer = ByteBuffer.allocate(points.size * BYTES_PER_POINT).order(ByteOrder.LITTLE_ENDIAN)
    points.forEach { buffer.putFloat(it.x).putFloat(it.y) }
    return buffer.array()
}

private fun decodePoints(bytes: ByteArray): List<PenPoint> {
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    return List(bytes.size / BYTES_PER_POINT) { PenPoint(x = buffer.getFloat(), y = buffer.getFloat()) }
}
