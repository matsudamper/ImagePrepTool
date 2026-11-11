package com.imagepreptool.data

import java.util.prefs.Preferences
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.OutputPathMode
import com.imagepreptool.service.RelativeOutputPath

/**
 * プロジェクト機能より前のバージョンが保存した書き出し設定。
 * 一度も書き出していないときの、新しいプロジェクトの初期値に使う
 */
interface SettingsStore {
    fun loadOptions(): EditOptions
    fun loadOutputPathMode(): OutputPathMode
    fun loadRelativeOutputPath(): String
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
                captionStyle = enumOr(prefs.get("captionStyle", null)?.replace("Shadow", "Plain"), d.captionStyle),
                fileNameSuffix = prefs.get("fileNameSuffix", d.fileNameSuffix),
                onlyScaleDown = prefs.getBoolean("onlyScaleDown", d.onlyScaleDown),
            )
        }.getOrDefault(d)
    }

    /** 旧バージョンの「カスタムテキスト」設定があればテンプレートとして引き継ぐ */
    private fun loadCaptionTemplate(default: String): String {
        prefs.get("captionTemplate", null)?.let { return it }
        val legacy = prefs.get("customCaption", "")
        return if (prefs.get("captionSource", null) == "Custom" && legacy.isNotBlank()) legacy else default
    }

    override fun loadOutputPathMode(): OutputPathMode = enumOr(prefs.get("outputPathMode", null), OutputPathMode.Relative)

    override fun loadRelativeOutputPath(): String = prefs.get("relativeOutputPath", RelativeOutputPath.DEFAULT)

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private fun Int.validDimension(default: Int): Int =
        if (this in EditOptions.MIN_DIMENSION..EditOptions.MAX_DIMENSION) this else default
}

class InMemorySettingsStore(
    private val options: EditOptions = EditOptions(),
    private val outputPathMode: OutputPathMode = OutputPathMode.Relative,
    private val relativeOutputPath: String = RelativeOutputPath.DEFAULT,
) : SettingsStore {
    override fun loadOptions() = options
    override fun loadOutputPathMode() = outputPathMode
    override fun loadRelativeOutputPath() = relativeOutputPath
}
