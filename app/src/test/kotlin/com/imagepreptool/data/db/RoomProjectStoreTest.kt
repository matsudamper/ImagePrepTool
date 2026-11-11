package com.imagepreptool.data.db

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import com.imagepreptool.data.ExportSettings
import com.imagepreptool.data.FileStamp
import com.imagepreptool.data.ProjectContent
import com.imagepreptool.data.ProjectImage
import com.imagepreptool.model.CropRect
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.OutputFormat
import com.imagepreptool.model.OutputPathMode
import com.imagepreptool.model.PenKind
import com.imagepreptool.model.PenPoint
import com.imagepreptool.model.PenStroke
import com.imagepreptool.model.PenTool
import com.imagepreptool.model.Rotation
import com.imagepreptool.presentation.ImageSortKey
import com.imagepreptool.presentation.ImageSortOrder

class RoomProjectStoreTest {

    private val directory = Files.createTempDirectory("imageprep-db-test").toFile()
    private val database = AppDatabase.open(File(directory, "test.db"))
    private val store = RoomProjectStore(database)

    private val exportSettings = ExportSettings(
        options = EditOptions(outputFormat = OutputFormat.Png, longEdge = 1200),
        outputPathMode = OutputPathMode.Absolute,
        relativeOutputPath = "out",
        absoluteOutputDir = File("/exports"),
    )

    @AfterTest
    fun tearDown() {
        database.close()
        directory.deleteRecursively()
    }

    @Test
    fun savedContentIsLoadedAsIs() = runBlocking {
        val image = File(directory, "a.jpg").apply { writeBytes(ByteArray(10)) }
        val id = store.createProject("旅行", exportSettings, nowMillis = 1)
        val stroke = PenStroke(PenKind.Blur, listOf(PenPoint(0.1f, 0.2f), PenPoint(0.3f, 0.4f)), 2f, 0xFF000000.toInt(), 1f)
        val content = ProjectContent(
            exportSettings = exportSettings,
            sortOrder = ImageSortOrder(ImageSortKey.Captured, ascending = false),
            focusedFile = image,
            isSelectionMode = true,
            images = listOf(
                ProjectImage(image, removed = false, selected = true, CropRect(0.1f, 0.1f, 0.9f, 0.8f), Rotation.Clockwise90, listOf(stroke)),
                ProjectImage(File(directory, "b.jpg"), removed = true, selected = false, crop = null, rotation = Rotation.None, strokes = listOf()),
            ),
            editStamps = mapOf(image to FileStamp.of(image)),
        )

        store.saveProject(id, previous = null, current = content)

        val loaded = store.loadProject(id)
        assertEquals(content, loaded?.content)
        assertEquals("旅行", loaded?.name)
        assertEquals(FileStamp.of(image), loaded?.stamps?.get(image))
    }

    @Test
    fun onlyChangedImagesAreRewritten() = runBlocking {
        val id = store.createProject("p", exportSettings, nowMillis = 1)
        val images = (1..3).map { ProjectImage(File(directory, "$it.jpg"), false, false, null, Rotation.None, listOf()) }
        val first = ProjectContent(exportSettings, ImageSortOrder(ImageSortKey.Name, true), null, false, images, editStamps = mapOf())
        store.saveProject(id, previous = null, current = first)

        val stroke = PenStroke(PenKind.Draw, listOf(PenPoint(0.5f, 0.5f)), 1f, 0xFFFFFFFF.toInt(), 1f)
        val second = first.copy(
            images = listOf(images[0], images[2].copy(strokes = listOf(stroke))),
            editStamps = mapOf(images[2].file to FileStamp(size = 1, modifiedAtMillis = 2)),
        )
        store.saveProject(id, previous = first, current = second)

        assertEquals(second, store.loadProject(id)?.content)
        assertEquals(2, store.listProjects().single().imageCount)
    }

    @Test
    fun savingWithoutPreviousContentReplacesWhatIsStored() = runBlocking {
        val id = store.createProject("p", exportSettings, nowMillis = 1)
        val stroke = PenStroke(PenKind.Draw, listOf(PenPoint(0.5f, 0.5f)), 1f, 0xFFFFFFFF.toInt(), 1f)
        val images = (1..2).map { ProjectImage(File(directory, "$it.jpg"), false, false, null, Rotation.None, listOf(stroke)) }
        val stamps = images.associate { it.file to FileStamp(size = 1, modifiedAtMillis = 2) }
        val first = ProjectContent(exportSettings, ImageSortOrder(ImageSortKey.Name, true), null, false, images, stamps)
        store.saveProject(id, previous = null, current = first)

        // 線を消し、画像を 1 枚外した内容を、前回の内容が分からない状態で保存する
        val second = first.copy(images = listOf(images[0].copy(strokes = listOf())), editStamps = mapOf())
        store.saveProject(id, previous = null, current = second)

        assertEquals(second, store.loadProject(id)?.content)
    }

    @Test
    fun stampOfEditTimeIsSavedEvenIfFileChangedBeforeSaving() = runBlocking {
        val image = File(directory, "a.jpg").apply { writeBytes(ByteArray(10)) }
        val stampWhenEdited = FileStamp.of(image)
        val id = store.createProject("p", exportSettings, nowMillis = 1)
        val edited = ProjectImage(image, false, false, CropRect(0.1f, 0.1f, 0.9f, 0.9f), Rotation.None, listOf())
        val first = ProjectContent(exportSettings, ImageSortOrder(ImageSortKey.Name, true), null, false, listOf(edited), mapOf(image to stampWhenEdited))
        // 編集した後、保存されるまでの間に外で差し替えられた
        image.writeBytes(ByteArray(20))
        store.saveProject(id, previous = null, current = first)
        assertEquals(stampWhenEdited, store.loadProject(id)?.stamps?.get(image))

        // その後、選択だけが変わった
        val second = first.copy(images = listOf(edited.copy(selected = true)))
        store.saveProject(id, previous = first, current = second)

        assertEquals(stampWhenEdited, store.loadProject(id)?.stamps?.get(image))
    }

    @Test
    fun deletingProjectForgetsItAsLastOpened() = runBlocking {
        val id = store.createProject("p", exportSettings, nowMillis = 1)
        store.saveLastProjectId(id)
        store.deleteProject(id)
        assertNull(store.loadProject(id))
        assertNull(store.loadLastProjectId())
    }

    @Test
    fun appWideSettingsAreKept() = runBlocking {
        val tool = PenTool(PenKind.Blur, widthPercent = 3f, color = 0xFF1E88E5.toInt(), blurPercent = 2f)
        store.saveLastExportSettings(exportSettings)
        store.savePenTool(tool)
        assertEquals(exportSettings, store.loadLastExportSettings())
        assertEquals(tool, store.loadPenTool())
    }
}
