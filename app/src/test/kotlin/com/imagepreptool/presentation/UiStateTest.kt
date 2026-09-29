package com.imagepreptool.presentation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.ExternalTools
import com.imagepreptool.model.OutputFormat

class UiStateTest {

    private val listener = ImagePrepViewModel(
        settings = com.imagepreptool.data.InMemorySettingsStore(),
        checkTools = { ExternalTools.None },
    ).snapshotForTest().listener

    @Test
    fun naturalOrderSortsNumbersByValue() {
        val names = listOf("IMG_10.jpg", "IMG_2.jpg", "img_1.jpg", "IMG_3b.jpg")
        assertEquals(
            listOf("img_1.jpg", "IMG_2.jpg", "IMG_3b.jpg", "IMG_10.jpg"),
            names.map(::File).sortedWith(NaturalOrder).map { it.name },
        )
    }

    @Test
    fun webpWithoutCwebpBlocksExport() {
        val state = ImagePrepViewModelState(
            images = listOf(ImageItem(File("/photos/a.jpg"))),
            options = EditOptions(outputFormat = OutputFormat.Webp),
            tools = ExternalTools.None,
        ).toUiState(listener)
        assertFalse(state.canExport)
        assertTrue(state.notices.any { it.blocking && it.action == NoticeAction.ShowTools })
    }

    @Test
    fun toolDependentExportWaitsForToolCheck() {
        val heic = ImagePrepViewModelState(images = listOf(ImageItem(File("/photos/a.heic"))), tools = null).toUiState(listener)
        assertFalse(heic.canExport)
        val jpeg = ImagePrepViewModelState(images = listOf(ImageItem(File("/photos/a.jpg"))), tools = null).toUiState(listener)
        assertTrue(jpeg.canExport)
    }

    @Test
    fun invalidSizeInputBlocksExport() {
        val state = ImagePrepViewModelState(
            images = listOf(ImageItem(File("/photos/a.jpg"))),
            invalidInputs = setOf("longEdge"),
        ).toUiState(listener)
        assertFalse(state.canExport)
        assertTrue(state.notices.any { it.blocking })
    }

    @Test
    fun exportIsDisabledWhileLoading() {
        val loading = ImagePrepViewModelState(images = listOf(ImageItem(File("/photos/a.jpg"))), isLoading = true).toUiState(listener)
        assertFalse(loading.canExport)
    }

    @Test
    fun exportIsReservedBeforePlanning() {
        val vm = ImagePrepViewModel(
            settings = com.imagepreptool.data.InMemorySettingsStore(),
            checkTools = { ExternalTools.None },
        )
        vm.addFilesForTest(listOf(File("/photos/a.jpg")))
        vm.snapshotForTest().listener.requestExport()
        assertEquals(ExportState.Preparing, vm.snapshotForTest().export)
        assertFalse(vm.snapshotForTest().canExport)
        // 準備中は閉じたり別の画像に差し替えたりできない
        vm.snapshotForTest().listener.closeAll()
        vm.snapshotForTest().listener.openFolder(File("/photos/other"))
        assertEquals(listOf(File("/photos/a.jpg")), vm.snapshotForTest().images.map { it.file })
    }

    @Test
    fun heicWithoutDecoderCanBeRemoved() {
        val vm = ImagePrepViewModel(
            settings = com.imagepreptool.data.InMemorySettingsStore(),
            checkTools = { ExternalTools.None },
        )
        vm.addFilesForTest(listOf(File("/photos/a.jpg"), File("/photos/b.heic")))
        vm.setToolsForTest(ExternalTools.None)
        assertTrue(vm.snapshotForTest().notices.any { it.action == NoticeAction.RemoveUnreadable })
        vm.snapshotForTest().listener.removeUnreadable()
        val ui = vm.snapshotForTest()
        assertTrue(ui.canExport)
        assertEquals(1, ui.exportCount)
        assertEquals(File("/photos/output"), ui.outputDirectory)
    }

    @Test
    fun actionsOnSelectedImageApplyToWholeSelection() {
        val files = (1..5).map { File("/photos/$it.jpg") }
        val vm = ImagePrepViewModel(
            settings = com.imagepreptool.data.InMemorySettingsStore(),
            checkTools = { ExternalTools.None },
        )
        vm.addFilesForTest(files)
        vm.snapshotForTest().listener.clickImage(files[1], SelectMode.Single)
        vm.snapshotForTest().listener.clickImage(files[3], SelectMode.Range)
        vm.snapshotForTest().listener.clickImage(files[4], SelectMode.Toggle)
        var state = vm.snapshotForTest()
        // プレビューは最初にクリックした画像のまま
        assertEquals(files[1], state.focusedFile)
        assertEquals(setOf(files[1], files[2], files[3], files[4]), state.selectedFiles)

        // 複数選択中は選択中の画像だけを書き出す
        assertTrue(state.isExportingSelection)
        assertEquals(4, state.exportCount)

        vm.snapshotForTest().listener.removeImage(files[3])
        state = vm.snapshotForTest()
        assertEquals(listOf(files[0]), state.images.map { it.file })
        assertEquals(files[0], state.focusedFile)
        assertFalse(state.isExportingSelection)
        assertEquals(1, state.exportCount)
    }

    @Test
    fun removingUnreadableKeepsRemainingSelection() {
        val files = listOf("a.jpg", "b.heic", "c.jpg", "d.jpg", "e.heic").map { File("/photos/$it") }
        val vm = ImagePrepViewModel(
            settings = com.imagepreptool.data.InMemorySettingsStore(),
            checkTools = { ExternalTools.None },
        )
        vm.addFilesForTest(files)
        vm.setToolsForTest(ExternalTools.None)
        vm.snapshotForTest().listener.clickImage(files[0], SelectMode.Single)
        vm.snapshotForTest().listener.clickImage(files[2], SelectMode.Range)
        vm.snapshotForTest().listener.removeUnreadable()
        val state = vm.snapshotForTest()
        // 選択外の読み込めない画像は残し、選択は削除されなかった画像のまま保つ
        assertEquals(listOf(files[0], files[2], files[3], files[4]), state.images.map { it.file })
        assertTrue(state.isExportingSelection)
        assertEquals(setOf(files[0], files[2]), state.selectedFiles)
    }

    @Test
    fun undoRestoresRemovedImagesAtOriginalPositions() {
        val files = (1..5).map { File("/photos/$it.jpg") }
        val vm = ImagePrepViewModel(
            settings = com.imagepreptool.data.InMemorySettingsStore(),
            checkTools = { ExternalTools.None },
        )
        vm.addFilesForTest(files)
        vm.snapshotForTest().listener.clickImage(files[1], SelectMode.Single)
        vm.snapshotForTest().listener.clickImage(files[3], SelectMode.Toggle)
        vm.snapshotForTest().listener.removeSelection()
        assertEquals(listOf(files[0], files[2], files[4]), vm.snapshotForTest().images.map { it.file })

        vm.snapshotForTest().listener.undoRemoval()
        val state = vm.snapshotForTest()
        assertEquals(files, state.images.map { it.file })
        assertEquals(setOf(files[1], files[3]), state.selectedFiles)
    }
}
