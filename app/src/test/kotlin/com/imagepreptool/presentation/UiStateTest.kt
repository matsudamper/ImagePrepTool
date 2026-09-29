package com.imagepreptool.presentation

import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.ExternalTools
import com.imagepreptool.model.OutputFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiStateTest {

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
        ).toUiState()
        assertFalse(state.canExport)
        assertTrue(state.notices.any { it.blocking && it.action == NoticeAction.ShowTools })
    }

    @Test
    fun heicWithoutDecoderCanBeExcluded() {
        val state = ImagePrepViewModelState(
            images = listOf(ImageItem(File("/photos/a.jpg")), ImageItem(File("/photos/b.heic"))),
            tools = ExternalTools.None,
        )
        assertTrue(state.toUiState().notices.any { it.action == NoticeAction.ExcludeUnreadable })
        val excluded = state.copy(
            images = state.images.map { if (!state.canRead(it.file, ExternalTools.None)) it.copy(included = false) else it },
        )
        val ui = excluded.toUiState()
        assertTrue(ui.canExport)
        assertEquals(1, ui.includedCount)
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
        vm.clickImage(files[1], SelectMode.Single)
        vm.clickImage(files[3], SelectMode.Range)
        vm.clickImage(files[4], SelectMode.Toggle)
        var state = vm.snapshotForTest()
        // プレビューは最初にクリックした画像のまま
        assertEquals(files[1], state.focusedFile)
        assertEquals(setOf(files[1], files[2], files[3], files[4]), state.selectedFiles)

        vm.toggleIncluded(files[2])
        state = vm.snapshotForTest()
        assertEquals(listOf(true, false, false, false, false), state.images.map { it.included })

        // 選択外の画像はその 1 枚だけ
        vm.toggleIncluded(files[0])
        assertEquals(listOf(false, false, false, false, false), vm.snapshotForTest().images.map { it.included })

        vm.removeImage(files[3])
        state = vm.snapshotForTest()
        assertEquals(listOf(files[0]), state.images.map { it.file })
        assertEquals(files[0], state.focusedFile)
    }
}
