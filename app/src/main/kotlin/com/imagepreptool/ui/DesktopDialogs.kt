package com.imagepreptool.ui

import java.io.File
import javax.swing.JFileChooser

object DesktopDialogs {

    fun pickDirectoryWithAwt(title: String, initial: File? = null): File? =
        JFileChooser(initial).apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }.let { chooser ->
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile
            } else {
                null
            }
        }
}
