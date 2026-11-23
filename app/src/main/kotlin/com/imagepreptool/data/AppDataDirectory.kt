package com.imagepreptool.data

import java.io.File

/** OS ごとのアプリのデータを置くフォルダ */
object AppDataDirectory {
    private const val APP_NAME = "ImagePrepTool"

    fun resolve(): File {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val home = System.getProperty("user.home")
        return when {
            os.contains("windows") -> File(System.getenv("APPDATA") ?: File(home, "AppData/Roaming").path, APP_NAME)
            os.contains("mac") -> File(home, "Library/Application Support/$APP_NAME")
            else -> File(System.getenv("XDG_DATA_HOME") ?: File(home, ".local/share").path, APP_NAME)
        }
    }
}
