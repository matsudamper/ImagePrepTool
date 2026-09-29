package com.imagepreptool.data

import java.io.File
import java.util.prefs.Preferences
import com.imagepreptool.model.EditOptions

/** 前回の設定を次回起動時に復元する */
interface SettingsStore {
    fun loadOptions(): EditOptions
    fun saveOptions(options: EditOptions)
    fun loadCustomOutputDir(): File?
    fun saveCustomOutputDir(dir: File?)
    fun loadRecentFolders(): List<File>
    fun saveRecentFolders(folders: List<File>)
}

class PreferencesSettingsStore(
    private val prefs: Preferences = Preferences.userRoot().node("com/imagepreptool"),
) : SettingsStore {

    override fun loadOptions(): EditOptions {
        val d = EditOptions()
        return runCatching {
            EditOptions(
                resizeMode = enumOr(prefs.get("resizeMode", null), d.resizeMode),
                longEdge = prefs.getInt("longEdge", d.longEdge).validDimension(d.longEdge),
                fitWidth = prefs.getInt("fitWidth", d.fitWidth).validDimension(d.fitWidth),
                fitHeight = prefs.getInt("fitHeight", d.fitHeight).validDimension(d.fitHeight),
                outputFormat = enumOr(prefs.get("outputFormat", null), d.outputFormat),
                quality = prefs.getInt("quality", d.quality).coerceIn(1, 100),
                captionEnabled = prefs.getBoolean("captionEnabled", d.captionEnabled),
                captionTemplate = loadCaptionTemplate(d.captionTemplate),
                captionPosition = enumOr(prefs.get("captionPosition", null), d.captionPosition),
                captionSizePercent = prefs.getFloat("captionSizePercent", d.captionSizePercent)
                    .coerceIn(EditOptions.MIN_CAPTION_PERCENT, EditOptions.MAX_CAPTION_PERCENT),
                captionStyle = enumOr(prefs.get("captionStyle", null), d.captionStyle),
                fileNameSuffix = prefs.get("fileNameSuffix", d.fileNameSuffix),
            )
        }.getOrDefault(d)
    }

    override fun saveOptions(options: EditOptions) = safely {
        prefs.put("resizeMode", options.resizeMode.name)
        prefs.putInt("longEdge", options.longEdge)
        prefs.putInt("fitWidth", options.fitWidth)
        prefs.putInt("fitHeight", options.fitHeight)
        prefs.put("outputFormat", options.outputFormat.name)
        prefs.putInt("quality", options.quality)
        prefs.putBoolean("captionEnabled", options.captionEnabled)
        prefs.put("captionTemplate", options.captionTemplate)
        prefs.put("captionPosition", options.captionPosition.name)
        prefs.putFloat("captionSizePercent", options.captionSizePercent)
        prefs.put("captionStyle", options.captionStyle.name)
        prefs.put("fileNameSuffix", options.fileNameSuffix)
    }

    /** 旧バージョンの「カスタムテキスト」設定があればテンプレートとして引き継ぐ */
    private fun loadCaptionTemplate(default: String): String {
        prefs.get("captionTemplate", null)?.let { return it }
        val legacy = prefs.get("customCaption", "")
        return if (prefs.get("captionSource", null) == "Custom" && legacy.isNotBlank()) legacy else default
    }

    override fun loadCustomOutputDir(): File? = prefs.get("customOutputDir", null)?.let(::File)

    override fun saveCustomOutputDir(dir: File?) = safely {
        if (dir == null) prefs.remove("customOutputDir") else prefs.put("customOutputDir", dir.absolutePath)
    }

    override fun loadRecentFolders(): List<File> =
        prefs.get("recentFolders", "").lines().filter { it.isNotBlank() }.map(::File).filter { it.isDirectory }

    override fun saveRecentFolders(folders: List<File>) = safely {
        prefs.put("recentFolders", folders.joinToString("\n") { it.absolutePath })
    }

    private inline fun safely(block: () -> Unit) {
        runCatching { block() }
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private fun Int.validDimension(default: Int): Int =
        if (this in EditOptions.MIN_DIMENSION..EditOptions.MAX_DIMENSION) this else default
}

class InMemorySettingsStore(
    private var options: EditOptions = EditOptions(),
    private var customOutputDir: File? = null,
    private var recent: List<File> = emptyList(),
) : SettingsStore {
    override fun loadOptions() = options
    override fun saveOptions(options: EditOptions) {
        this.options = options
    }
    override fun loadCustomOutputDir() = customOutputDir
    override fun saveCustomOutputDir(dir: File?) {
        customOutputDir = dir
    }
    override fun loadRecentFolders() = recent
    override fun saveRecentFolders(folders: List<File>) {
        recent = folders
    }
}
