package com.imagepreptool.presentation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.imagepreptool.data.InMemoryProjectStore
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.ExternalTools
import com.imagepreptool.model.FileDates
import com.imagepreptool.model.OutputFormat
import com.imagepreptool.model.OutputPathMode

class UiStateTest {

    private val listener = ImagePrepViewModel(
        projectStore = InMemoryProjectStore(),
        initialFiles = listOf(),
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
    fun capturedDateSortKeepsImagesWithoutDateLast() {
        val old = File("/photos/b.jpg")
        val new = File("/photos/a.jpg")
        val noDate = File("/photos/c.jpg")
        val other = File("/other/z.jpg")
        val dates = mapOf(
            old to FileDates(capturedAtMillis = 1, modifiedAtMillis = null, createdAtMillis = null),
            new to FileDates(capturedAtMillis = 2, modifiedAtMillis = null, createdAtMillis = null),
            noDate to FileDates(capturedAtMillis = null, modifiedAtMillis = null, createdAtMillis = null),
        )
        val base = ImagePrepViewModelState(images = listOf(noDate, new, old, other).map(::ImageItem), fileDates = dates)

        val ascending = base.copy(sortOrder = ImageSortOrder(ImageSortKey.Captured, ascending = true))
        assertEquals(listOf(old, new, noDate, other), ascending.orderedImages.map { it.file })
        val descending = base.copy(sortOrder = ImageSortOrder(ImageSortKey.Captured, ascending = false))
        assertEquals(listOf(new, old, noDate, other), descending.orderedImages.map { it.file })
    }

    @Test
    fun webpWithoutCwebpBlocksExport() {
        val state = ImagePrepViewModelState(
            images = listOf(ImageItem(File("/photos/a.jpg"))),
            selection = setOf(File("/photos/a.jpg")),
            options = EditOptions(outputFormat = OutputFormat.Webp),
            tools = ExternalTools.None,
        ).toUiState(listener) { NoProjectListener }
        assertFalse(state.canExport)
        assertTrue(state.notices.any { it.blocking })
    }

    @Test
    fun toolDependentExportWaitsForToolCheck() {
        val heic = ImagePrepViewModelState(images = listOf(ImageItem(File("/photos/a.heic"))), selection = setOf(File("/photos/a.heic")), tools = null).toUiState(listener) { NoProjectListener }
        assertFalse(heic.canExport)
        val jpeg = ImagePrepViewModelState(images = listOf(ImageItem(File("/photos/a.jpg"))), selection = setOf(File("/photos/a.jpg")), tools = null).toUiState(listener) { NoProjectListener }
        assertTrue(jpeg.canExport)
    }

    @Test
    fun invalidSizeInputBlocksExport() {
        val state = ImagePrepViewModelState(
            images = listOf(ImageItem(File("/photos/a.jpg"))),
            invalidInputs = setOf("longEdge"),
        ).toUiState(listener) { NoProjectListener }
        assertFalse(state.canExport)
        assertTrue(state.notices.any { it.blocking })
    }

    @Test
    fun absoluteModeWithoutSelectedFolderBlocksExport() {
        val unselected = ImagePrepViewModelState(
            images = listOf(ImageItem(File("/photos/a.jpg"))),
            selection = setOf(File("/photos/a.jpg")),
            outputPathMode = OutputPathMode.Absolute,
        ).toUiState(listener) { NoProjectListener }
        assertEquals(null, unselected.outputDirectory)
        assertFalse(unselected.canExport)
        val selected = ImagePrepViewModelState(
            images = listOf(ImageItem(File("/photos/a.jpg"))),
            selection = setOf(File("/photos/a.jpg")),
            outputPathMode = OutputPathMode.Absolute,
            selectedOutputDir = File("/export"),
        ).toUiState(listener) { NoProjectListener }
        assertEquals(File("/export"), selected.outputDirectory)
        assertTrue(selected.canExport)
    }

    @Test
    fun exportIsDisabledWhileLoading() {
        val loading = ImagePrepViewModelState(images = listOf(ImageItem(File("/photos/a.jpg"))), isLoading = true).toUiState(listener) { NoProjectListener }
        assertFalse(loading.canExport)
    }

    @Test
    fun exportIsReservedBeforePlanning() {
        val vm = ImagePrepViewModel(
            projectStore = InMemoryProjectStore(),
            initialFiles = listOf(),
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
            projectStore = InMemoryProjectStore(),
            initialFiles = listOf(),
            settings = com.imagepreptool.data.InMemorySettingsStore(),
            checkTools = { ExternalTools.None },
        )
        vm.addFilesForTest(listOf(File("/photos/a.jpg"), File("/photos/b.heic")))
        vm.setToolsForTest(ExternalTools.None)
        vm.snapshotForTest().listener.selectAll()
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
            projectStore = InMemoryProjectStore(),
            initialFiles = listOf(),
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
            projectStore = InMemoryProjectStore(),
            initialFiles = listOf(),
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
    fun selectionExportSurvivesRemovalDownToOneImage() {
        val files = listOf("a.jpg", "b.heic", "c.jpg").map { File("/photos/$it") }
        val vm = ImagePrepViewModel(
            projectStore = InMemoryProjectStore(),
            initialFiles = listOf(),
            settings = com.imagepreptool.data.InMemorySettingsStore(),
            checkTools = { ExternalTools.None },
        )
        vm.addFilesForTest(files)
        vm.setToolsForTest(ExternalTools.None)
        vm.snapshotForTest().listener.clickImage(files[0], SelectMode.Single)
        vm.snapshotForTest().listener.clickImage(files[1], SelectMode.Toggle)
        vm.snapshotForTest().listener.removeUnreadable()
        var state = vm.snapshotForTest()
        // 選択が 1 枚に減っても、選択外の画像を書き出し対象にしない
        assertTrue(state.isExportingSelection)
        assertEquals(1, state.exportCount)

        vm.snapshotForTest().listener.undoRemoval()
        state = vm.snapshotForTest()
        assertEquals(files, state.images.map { it.file })
        assertEquals(setOf(files[0], files[1]), state.selectedFiles)
        assertEquals(files[0], state.focusedFile)
        assertTrue(state.isExportingSelection)
    }

    @Test
    fun shiftArrowExtendsSelectionFromAnchor() {
        val files = (1..5).map { File("/photos/$it.jpg") }
        val vm = ImagePrepViewModel(
            projectStore = InMemoryProjectStore(),
            initialFiles = listOf(),
            settings = com.imagepreptool.data.InMemorySettingsStore(),
            checkTools = { ExternalTools.None },
        )
        vm.addFilesForTest(files)
        vm.snapshotForTest().listener.clickImage(files[1], SelectMode.Single)
        vm.snapshotForTest().listener.extendSelection(1)
        vm.snapshotForTest().listener.extendSelection(1)
        var state = vm.snapshotForTest()
        assertEquals(setOf(files[1], files[2], files[3]), state.selectedFiles)
        assertEquals(files[3], state.focusedFile)
        assertTrue(state.isExportingSelection)

        // 起点を越えて戻ると反対側へ広がる
        vm.snapshotForTest().listener.extendSelection(-3)
        state = vm.snapshotForTest()
        assertEquals(setOf(files[0], files[1]), state.selectedFiles)
        assertEquals(files[0], state.focusedFile)
    }

    @Test
    fun undoRestoresRemovedImagesAtOriginalPositions() {
        val files = (1..5).map { File("/photos/$it.jpg") }
        val vm = ImagePrepViewModel(
            projectStore = InMemoryProjectStore(),
            initialFiles = listOf(),
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
        // 削除前の選択に戻る
        assertEquals(setOf(files[1], files[3]), state.selectedFiles)
        assertEquals(files[1], state.focusedFile)
    }

    @Test
    fun imagesFromMultipleFoldersAreGroupedAndExportedTogether() {
        val files = listOf("/trip/1.jpg", "/trip/2.jpg", "/misc/1.jpg").map(::File)
        val state = ImagePrepViewModelState(
            images = files.map(::ImageItem),
            selection = files.toSet(),
        ).toUiState(listener) { NoProjectListener }
        assertEquals(listOf(File("/trip"), File("/misc")), state.imageGroups.map { it.folder })
        assertEquals(listOf(2, 1), state.imageGroups.map { it.images.size })
        // 既定の出力先は先頭フォルダの output で、複数フォルダをまとめて書き出すことを知らせる
        assertEquals(File("/trip/output"), state.outputDirectory)
        assertTrue(state.notices.any { !it.blocking && it.text.contains("2 つのフォルダ") })
        assertTrue(state.canExport)
    }

    @Test
    fun undoingFolderRemovalRestoresFolderGroup() {
        val files = listOf("/trip/1.jpg", "/trip/2.jpg", "/misc/1.jpg").map(::File)
        val vm = ImagePrepViewModel(
            projectStore = InMemoryProjectStore(),
            initialFiles = listOf(),
            settings = com.imagepreptool.data.InMemorySettingsStore(),
            checkTools = { ExternalTools.None },
        )
        vm.addFilesForTest(files)
        vm.snapshotForTest().listener.removeFolder(File("/trip"))
        assertEquals(listOf(files[2]), vm.snapshotForTest().images.map { it.file })
        vm.snapshotForTest().listener.undoRemoval()
        assertEquals(files, vm.snapshotForTest().images.map { it.file })
    }
}

private object NoProjectListener : ProjectItem.Listener {
    override fun open() = Unit
    override fun delete() = Unit
}
