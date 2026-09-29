package com.imagepreptool.model

import java.io.File

enum class ResizeMode(val label: String) {
    None("元のサイズ"),
    LongEdge("長辺"),
    Fit("幅×高さ"),
}

enum class OutputFormat(val label: String, val extension: String, val lossy: Boolean) {
    Original("元の形式", "", lossy = true),
    Jpeg("JPEG", "jpg", lossy = true),
    Png("PNG", "png", lossy = false),
    Webp("WebP", "webp", lossy = true),
}

enum class CaptionSource(val label: String) {
    Exif("撮影情報"),
    Custom("テキスト"),
}

enum class CaptionPosition(val label: String) {
    TopLeft("左上"),
    TopRight("右上"),
    BottomLeft("左下"),
    BottomRight("右下"),
}

enum class CaptionStyle(val label: String) {
    Plate("背景付き"),
    Shadow("影付き"),
}

enum class ConflictPolicy(val label: String) {
    Rename("別名で保存"),
    Overwrite("上書き"),
    Skip("スキップ"),
}

data class EditOptions(
    val resizeMode: ResizeMode = ResizeMode.LongEdge,
    val longEdge: Int = 2048,
    val fitWidth: Int = 1920,
    val fitHeight: Int = 1080,
    val outputFormat: OutputFormat = OutputFormat.Jpeg,
    val quality: Int = 85,
    val captionEnabled: Boolean = true,
    val captionSource: CaptionSource = CaptionSource.Exif,
    val customCaption: String = "",
    val captionPosition: CaptionPosition = CaptionPosition.BottomRight,
    /** 画像の短辺に対する文字の高さ（%） */
    val captionSizePercent: Float = 2.5f,
    val captionStyle: CaptionStyle = CaptionStyle.Plate,
    val fileNameSuffix: String = "",
) {
    companion object {
        const val MIN_DIMENSION = 16
        const val MAX_DIMENSION = 16384
        const val MIN_CAPTION_PERCENT = 1f
        const val MAX_CAPTION_PERCENT = 8f
    }
}

data class ImageSize(val width: Int, val height: Int) {
    override fun toString(): String = "$width × $height"
}

data class ProcessResult(
    val source: File,
    val output: File?,
    val status: Status,
    val message: String,
) {
    enum class Status { Success, Skipped, Failed }
}

enum class ExternalTool(val command: String, val versionArg: String, val purpose: String) {
    Cwebp("cwebp", "-version", "WebP 形式での書き出し"),
    HeifDec("heif-dec", "--version", "HEIC / HEIF の読み込み"),
    HeifConvert("heif-convert", "--version", "HEIC / HEIF の読み込み（旧名）"),
    Magick("magick", "-version", "HEIC / HEIF の読み込み（代替）"),
}

data class ExternalToolStatus(
    val tool: ExternalTool,
    val available: Boolean,
    val detail: String,
)

data class ExternalTools(val statuses: List<ExternalToolStatus>) {
    fun isAvailable(tool: ExternalTool): Boolean = statuses.any { it.tool == tool && it.available }

    val canWriteWebp: Boolean get() = isAvailable(ExternalTool.Cwebp)

    /** HEIF のデコードに使えるツール（優先順） */
    val heifDecoder: ExternalTool?
        get() = listOf(ExternalTool.HeifDec, ExternalTool.HeifConvert, ExternalTool.Magick)
            .firstOrNull(::isAvailable)

    companion object {
        val None = ExternalTools(emptyList())
    }
}
