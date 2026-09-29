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
}
