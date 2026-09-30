package com.imagepreptool.ui

import java.awt.Desktop
import java.awt.Window
import java.io.File
import java.io.IOException
import com.imagepreptool.service.ImageLoader
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogParent
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker

object DesktopDialogs {

    suspend fun pickDirectory(parent: Window?, title: String, initial: File? = null): File? = FileKit.openDirectoryPicker(
        directory = initial?.toExistingDirectory(),
        dialogSettings = dialogSettings(parent, title),
    )?.file

    suspend fun pickImages(parent: Window?, initial: File? = null): List<File> = FileKit.openFilePicker(
        type = FileKitType.File(ImageLoader.supportedExtensions),
        mode = FileKitMode.Multiple(),
        directory = initial?.toExistingDirectory(),
        dialogSettings = dialogSettings(parent, "画像を追加"),
    ).orEmpty().map { it.file }

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

    private fun dialogSettings(parent: Window?, title: String) = FileKitDialogSettings(
        title = title,
        parent = parent?.let(FileKitDialogParent::awt),
    )

    private fun File.toExistingDirectory(): PlatformFile? {
        val directory = if (isDirectory) this else parentFile
        return if (directory?.isDirectory == true) PlatformFile(directory) else null
    }
}
