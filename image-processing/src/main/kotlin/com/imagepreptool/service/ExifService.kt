package com.imagepreptool.service

import java.io.File
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory

object ExifService {

    fun buildCaption(file: File, customLine: String): String {
        if (customLine.isNotBlank()) return customLine.trim()
        return try {
            val metadata = ImageMetadataReader.readMetadata(file)
            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            val sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            val parts = buildList {
                ifd0?.getString(ExifIFD0Directory.TAG_MAKE)?.let { add(it.trim()) }
                ifd0?.getString(ExifIFD0Directory.TAG_MODEL)?.let { add(it.trim()) }
                sub?.getString(ExifSubIFDDirectory.TAG_LENS_MODEL)?.let { add(it.trim()) }
                sub?.getDescription(ExifSubIFDDirectory.TAG_FNUMBER)?.let { add("f/$it") }
                sub?.getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)?.let { add("${it}s") }
                sub?.getDescription(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)?.let { add("ISO $it") }
                sub?.getDescription(ExifSubIFDDirectory.TAG_FOCAL_LENGTH)?.let { add("${it}mm") }
            }.filter { it.isNotBlank() }.distinct()
            if (parts.isEmpty()) file.nameWithoutExtension else parts.joinToString(" · ")
        } catch (_: Exception) {
            file.nameWithoutExtension
        }
    }
}
