package com.imagepreptool.service

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt
import com.drew.imaging.ImageMetadataReader
import com.drew.imaging.jpeg.JpegSegmentReader
import com.drew.imaging.jpeg.JpegSegmentType
import com.drew.metadata.Directory
import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifReader
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.ExifThumbnailDirectory
import com.drew.metadata.jpeg.JpegDirectory
import com.imagepreptool.model.CaptionField
import com.imagepreptool.model.ImageSize

/** EXIF に埋め込まれた縮小画像。[imageSize] は向き補正前の本体画像サイズ */
class EmbeddedThumbnail(val bytes: ByteArray, val orientation: Int, val imageSize: ImageSize?)

object ExifService {

    /** キャプションに使える撮影情報。取得できない項目は含まれない */
    fun readFields(file: File): Map<CaptionField, String> {
        val fields = linkedMapOf(CaptionField.FileName to file.nameWithoutExtension)
        val metadata = readMetadata(file) ?: return fields
        val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
        val sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)

        val make = ifd0?.string(ExifIFD0Directory.TAG_MAKE)
        val model = ifd0?.string(ExifIFD0Directory.TAG_MODEL)
        val camera = cameraName(make, model)
        fun put(field: CaptionField, value: String?) {
            if (!value.isNullOrBlank()) fields[field] = value
        }
        put(CaptionField.Camera, camera)
        put(CaptionField.Make, make)
        put(CaptionField.Model, model)
        put(CaptionField.Lens, sub?.string(ExifSubIFDDirectory.TAG_LENS_MODEL))
        put(CaptionField.FocalLength, sub?.doubleOrNull(ExifSubIFDDirectory.TAG_FOCAL_LENGTH)?.let { "${formatDecimal(it)}mm" })
        put(CaptionField.FocalLength35, sub?.intOrNull(ExifSubIFDDirectory.TAG_35MM_FILM_EQUIV_FOCAL_LENGTH)?.takeIf { it > 0 }?.let { "${it}mm" })
        put(CaptionField.Aperture, sub?.doubleOrNull(ExifSubIFDDirectory.TAG_FNUMBER)?.let { "f/${formatDecimal(it)}" })
        put(CaptionField.Shutter, sub?.doubleOrNull(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)?.let(::formatExposure))
        put(CaptionField.Iso, sub?.intOrNull(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)?.takeIf { it > 0 }?.let { "ISO $it" })
        put(CaptionField.ExposureBias, sub?.exposureBias()?.let(::formatExposureBias))
        val taken = sub?.dateOrNull(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL) ?: ifd0?.dateOrNull(ExifIFD0Directory.TAG_DATETIME)
        put(CaptionField.Date, taken?.let { format("yyyy/MM/dd", it) })
        put(CaptionField.DateTime, taken?.let { format("yyyy/MM/dd HH:mm", it) })
        put(CaptionField.Artist, ifd0?.string(ExifIFD0Directory.TAG_ARTIST))
        put(CaptionField.Copyright, ifd0?.string(ExifIFD0Directory.TAG_COPYRIGHT))
        return fields
    }

    /** メーカー名とモデル名をまとめる。「NIKON CORPORATION」+「NIKON D850」のような重複は省く */
    internal fun cameraName(make: String?, model: String?): String? {
        if (model == null) return make
        if (make == null) return model
        val makeHead = make.substringBefore(' ')
        return if (model.startsWith(makeHead, ignoreCase = true)) model else "$makeHead $model"
    }

    /** EXIF Orientation（1〜8）。無ければ 1 */
    fun readOrientation(file: File): Int {
        val metadata = readMetadata(file) ?: return 1
        return metadata.orientation()
    }

    fun readEmbeddedThumbnail(file: File): EmbeddedThumbnail? {
        val metadata = readMetadata(file) ?: return null
        val thumbnailDirectory = metadata.getFirstDirectoryOfType(ExifThumbnailDirectory::class.java) ?: return null
        val offset = thumbnailDirectory.intOrNull(ExifThumbnailDirectory.TAG_THUMBNAIL_OFFSET)?.takeIf { it >= 0 } ?: return null
        val length = thumbnailDirectory.intOrNull(ExifThumbnailDirectory.TAG_THUMBNAIL_LENGTH)?.takeIf { it > 0 } ?: return null
        // オフセットは APP1 内の TIFF ヘッダ起点なので、ファイル位置ではなくセグメントから切り出す
        val exifSegment = runCatching { JpegSegmentReader.readSegments(file, listOf(JpegSegmentType.APP1)) }.getOrNull()
            ?.getSegments(JpegSegmentType.APP1)
            ?.firstOrNull(ExifReader::startsWithJpegExifPreamble)
            ?: return null
        val start = ExifReader.JPEG_SEGMENT_PREAMBLE.length + offset
        if (start + length > exifSegment.size) return null
        val bytes = exifSegment.copyOfRange(start, start + length)
        val jpeg = metadata.getFirstDirectoryOfType(JpegDirectory::class.java)
        val width = jpeg?.intOrNull(JpegDirectory.TAG_IMAGE_WIDTH)?.takeIf { it > 0 }
        val height = jpeg?.intOrNull(JpegDirectory.TAG_IMAGE_HEIGHT)?.takeIf { it > 0 }
        val imageSize = if (width != null && height != null) ImageSize(width, height) else null
        return EmbeddedThumbnail(bytes, metadata.orientation(), imageSize)
    }

    private fun readMetadata(file: File): Metadata? =
        runCatching { ImageMetadataReader.readMetadata(file) }.getOrNull()

    private fun Metadata.orientation(): Int =
        getDirectoriesOfType(ExifIFD0Directory::class.java)
            .firstNotNullOfOrNull { it.intOrNull(ExifIFD0Directory.TAG_ORIENTATION) }
            ?.takeIf { it in 1..8 }
            ?: 1

    private fun Directory.string(tag: Int): String? =
        getString(tag)?.trim()?.trimEnd('\u0000')?.takeIf { it.isNotBlank() }

    private fun Directory.doubleOrNull(tag: Int): Double? =
        if (containsTag(tag)) runCatching { getDouble(tag) }.getOrNull()?.takeIf { it > 0 && it.isFinite() } else null

    private fun Directory.exposureBias(): Double? {
        val tag = ExifSubIFDDirectory.TAG_EXPOSURE_BIAS
        return if (containsTag(tag)) runCatching { getDouble(tag) }.getOrNull()?.takeIf { it.isFinite() } else null
    }

    private fun Directory.intOrNull(tag: Int): Int? =
        if (containsTag(tag)) runCatching { getInt(tag) }.getOrNull() else null

    // EXIF の日時はタイムゾーンを持たないので UTC として読み、そのまま UTC で書く
    private fun Directory.dateOrNull(tag: Int): Date? =
        if (containsTag(tag)) runCatching { getDate(tag, TimeZone.getTimeZone("UTC")) }.getOrNull() else null

    private fun format(pattern: String, date: Date): String =
        SimpleDateFormat(pattern).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date)

    internal fun formatExposure(seconds: Double): String {
        if (seconds >= 0.3) return "${formatDecimal(seconds)}s"
        val denominator = (1 / seconds).roundToInt()
        return "1/${denominator}s"
    }

    internal fun formatExposureBias(ev: Double): String = when {
        abs(ev) < 0.05 -> "±0EV"
        ev > 0 -> "+${formatDecimal(ev)}EV"
        else -> "${formatDecimal(ev)}EV"
    }

    internal fun formatDecimal(value: Double): String {
        val rounded = (value * 10).roundToInt() / 10.0
        return if (abs(rounded - rounded.roundToInt()) < 1e-9) rounded.roundToInt().toString() else rounded.toString()
    }
}
