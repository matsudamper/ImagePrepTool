package com.imagepreptool.ui

import java.awt.Component
import java.awt.Desktop
import java.io.File
import java.io.IOException
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import com.imagepreptool.service.ImageLoader

object DesktopDialogs {

    fun pickDirectory(parent: Component?, title: String, initial: File? = null): File? {
        val chooser = JFileChooser(initial?.takeIf { it.exists() } ?: initial?.parentFile).apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
            approveButtonText = "このフォルダを選択"
        }
        return if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    fun pickImages(parent: Component?, initial: File? = null): List<File> {
        val chooser = JFileChooser(initial).apply {
            dialogTitle = "画像を追加"
            fileSelectionMode = JFileChooser.FILES_ONLY
            isMultiSelectionEnabled = true
            fileFilter = FileNameExtensionFilter(
                "画像ファイル（${ImageLoader.supportedExtensions.sorted().joinToString(", ")}）",
                *ImageLoader.supportedExtensions.toTypedArray(),
            )
            approveButtonText = "追加"
        }
        return if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) chooser.selectedFiles.toList() else emptyList()
    }

    /** エクスプローラーなどでフォルダを開く。失敗したらメッセージを返す */
    fun openFolder(dir: File): String? = try {
        if (!dir.isDirectory) {
            "フォルダが見つかりません"
        } else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(dir)
            null
        } else {
            "この環境ではフォルダを開けません"
        }
    } catch (e: IOException) {
        e.message ?: "フォルダを開けません"
    }

    /** ファイルを選択した状態でエクスプローラーを開く（Windows 以外はフォルダを開く） */
    fun revealFile(file: File): String? {
        if (System.getProperty("os.name").lowercase().contains("win")) {
            return try {
                ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
                null
            } catch (e: IOException) {
                e.message
            }
        }
        return openFolder(file.parentFile)
    }
}
