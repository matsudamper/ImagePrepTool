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

/** キャプションのテンプレートに埋め込める撮影情報 */
enum class CaptionField(val key: String, val label: String) {
    Camera("camera", "カメラ"),
    Make("make", "メーカー"),
    Model("model", "機種"),
    Lens("lens", "レンズ"),
    FocalLength("focal", "焦点距離"),
    FocalLength35("focal35", "35mm 換算焦点距離"),
    Aperture("aperture", "F 値"),
    Shutter("shutter", "シャッター速度"),
    Iso("iso", "ISO 感度"),
    ExposureBias("ev", "露出補正"),
    Date("date", "撮影日"),
    DateTime("datetime", "撮影日時"),
    Artist("artist", "撮影者"),
    Copyright("copyright", "著作権"),
    FileName("filename", "ファイル名"),
    ;

    val token: String get() = "{$key}"

    companion object {
        fun fromKey(key: String): CaptionField? = entries.firstOrNull { it.key == key }
    }
}

enum class CaptionPosition(val label: String) {
    TopLeft("左上"),
    TopRight("右上"),
    BottomLeft("左下"),
    BottomRight("右下"),
}

enum class CaptionStyle(val label: String) {
    Plate("背景付き"),
    Plain("影なし"),
}

enum class OutputPathMode(val label: String) {
    Absolute("絶対パス"),
    Relative("相対パス"),
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
    /** 指定サイズより小さい画像は拡大しない */
    val onlyScaleDown: Boolean = true,
    val outputFormat: OutputFormat = OutputFormat.Jpeg,
    val quality: Int = 85,
    val captionEnabled: Boolean = true,
    /** `{camera}` などの項目を撮影情報に置き換える。改行で複数行にできる */
    val captionTemplate: String = DEFAULT_CAPTION_TEMPLATE,
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
        const val DEFAULT_CAPTION_TEMPLATE = "{camera}  ·  {lens}\n{focal}  ·  {aperture}  ·  {shutter}  ·  {iso}"
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
    Magick("magick", "-version", "HEIC / HEIF の読み込み"),
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

    /** 画面で揃えるべきツール。HEIF は使えるデコーダがあればそれ、無ければ winget で入る magick だけを示す */
    val requiredStatuses: List<ExternalToolStatus>
        get() = listOf(ExternalTool.Cwebp, heifDecoder ?: ExternalTool.Magick)
            .mapNotNull { tool -> statuses.firstOrNull { it.tool == tool } }

    companion object {
        val None = ExternalTools(emptyList())
    }
}

/**
 * 切り抜く範囲。向き補正後の画像に対する割合（0〜1）で持ち、プレビュー用に縮小した画像にも同じ範囲を当てられるようにする
 */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isFull: Boolean get() = left <= 0f && top <= 0f && right >= 1f && bottom >= 1f

    /** 画像を時計回りに 90° 回したときに同じ部分を指す範囲 */
    fun rotatedClockwise(): CropRect = CropRect(left = 1f - bottom, top = left, right = 1f - top, bottom = right)

    /** 画像を反時計回りに 90° 回したときに同じ部分を指す範囲 */
    fun rotatedCounterClockwise(): CropRect = CropRect(left = top, top = 1f - right, right = bottom, bottom = 1f - left)

    companion object {
        val Full = CropRect(0f, 0f, 1f, 1f)
    }
}

/** 向き補正後の画像に加える時計回りの回転 */
enum class Rotation {
    None,
    Clockwise90,
    Clockwise180,
    Clockwise270,
    ;

    val swapsDimensions: Boolean get() = this == Clockwise90 || this == Clockwise270

    fun rotatedClockwise(): Rotation = entries[(ordinal + 1) % entries.size]

    fun rotatedCounterClockwise(): Rotation = entries[(ordinal + entries.size - 1) % entries.size]
}

/** 一覧の並べ替えに使う日時（エポックミリ秒）。取得できないものは null */
data class FileDates(
    /** EXIF の撮影日時。タイムゾーンを持たないため UTC として読んだ値で、ファイルの日時とは比べられない */
    val capturedAtMillis: Long?,
    val modifiedAtMillis: Long?,
    val createdAtMillis: Long?,
)

enum class PenKind(val label: String) {
    Draw("ペン"),
    Blur("ぼかし"),
}

/** 回転後・切り抜き前の画像に対する割合（0〜1）の位置 */
data class PenPoint(val x: Float, val y: Float) {
    fun rotatedClockwise(): PenPoint = PenPoint(x = 1f - y, y = x)

    fun rotatedCounterClockwise(): PenPoint = PenPoint(x = y, y = 1f - x)
}

/**
 * ペンで描いた 1 本の線。太さとぼかしの強さは画像の短辺に対する割合（%）で持ち、縮小したプレビューでも同じ見た目にする
 */
data class PenStroke(
    val kind: PenKind,
    val points: List<PenPoint>,
    val widthPercent: Float,
    /** ARGB。[PenKind.Draw] のときだけ使う */
    val color: Int,
    /** [PenKind.Blur] のときだけ使う */
    val blurPercent: Float,
) {
    /** 画像を時計回りに 90° 回したときに同じ部分をなぞる線 */
    fun rotatedClockwise(): PenStroke = copy(points = points.map(PenPoint::rotatedClockwise))

    /** 画像を反時計回りに 90° 回したときに同じ部分をなぞる線 */
    fun rotatedCounterClockwise(): PenStroke = copy(points = points.map(PenPoint::rotatedCounterClockwise))

    companion object {
        const val MIN_WIDTH_PERCENT = 0.2f
        const val MAX_WIDTH_PERCENT = 15f
        const val MIN_BLUR_PERCENT = 0.1f
        const val MAX_BLUR_PERCENT = 5f
    }
}

/** ペン編集で次に描く線の設定。画像ではなく道具の好みなので、プロジェクトをまたいで引き継ぐ */
data class PenTool(
    val kind: PenKind,
    val widthPercent: Float,
    /** ARGB */
    val color: Int,
    val blurPercent: Float,
)
