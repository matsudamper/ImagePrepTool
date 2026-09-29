package com.imagepreptool.service

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Directory
import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

object ExifService {

    /** 撮影情報を 1 行にまとめる。取得できなければ null */
    fun buildCaption(file: File): String? {
        val metadata = readMetadata(file) ?: return null
        val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
        val sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
        val make = ifd0?.string(ExifIFD0Directory.TAG_MAKE)
        val model = ifd0?.string(ExifIFD0Directory.TAG_MODEL)
        val camera = when {
            model == null -> make
            make == null || model.startsWith(make, ignoreCase = true) -> model
            // "NIKON CORPORATION" などは先頭の単語だけ使う
            else -> "${make.substringBefore(' ')} $model"
        }
        val parts = listOfNotNull(
            camera,
            sub?.string(ExifSubIFDDirectory.TAG_LENS_MODEL),
            sub?.doubleOrNull(ExifSubIFDDirectory.TAG_FOCAL_LENGTH)?.let { "${it.roundToInt()}mm" },
            sub?.doubleOrNull(ExifSubIFDDirectory.TAG_FNUMBER)?.let { "f/${formatDecimal(it)}" },
            sub?.doubleOrNull(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)?.let(::formatExposure),
            sub?.intOrNull(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)?.let { "ISO $it" },
        ).distinct()
        return parts.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
    }

    /** EXIF Orientation（1〜8）。無ければ 1 */
    fun readOrientation(file: File): Int {
        val metadata = readMetadata(file) ?: return 1
        return metadata.getDirectoriesOfType(ExifIFD0Directory::class.java)
            .firstNotNullOfOrNull { it.intOrNull(ExifIFD0Directory.TAG_ORIENTATION) }
            ?.takeIf { it in 1..8 }
            ?: 1
    }

    private fun readMetadata(file: File): Metadata? =
        runCatching { ImageMetadataReader.readMetadata(file) }.getOrNull()

    private fun Directory.string(tag: Int): String? =
        getString(tag)?.trim()?.trimEnd('\u0000')?.takeIf { it.isNotBlank() }

    private fun Directory.doubleOrNull(tag: Int): Double? =
        if (containsTag(tag)) runCatching { getDouble(tag) }.getOrNull()?.takeIf { it > 0 && it.isFinite() } else null

    private fun Directory.intOrNull(tag: Int): Int? =
        if (containsTag(tag)) runCatching { getInt(tag) }.getOrNull() else null

    internal fun formatExposure(seconds: Double): String {
        if (seconds >= 0.3) return "${formatDecimal(seconds)}s"
        val denominator = (1 / seconds).roundToInt()
        return "1/${denominator}s"
    }

    internal fun formatDecimal(value: Double): String {
        val rounded = (value * 10).roundToInt() / 10.0
        return if (abs(rounded - rounded.roundToInt()) < 1e-9) rounded.roundToInt().toString() else rounded.toString()
    }
}
