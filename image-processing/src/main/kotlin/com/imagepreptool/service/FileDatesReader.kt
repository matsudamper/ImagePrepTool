package com.imagepreptool.service

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import com.imagepreptool.model.FileDates

object FileDatesReader {

    fun read(file: File): FileDates {
        val attributes = runCatching { Files.readAttributes(file.toPath(), BasicFileAttributes::class.java) }.getOrNull()
        return FileDates(
            capturedAtMillis = ExifService.readCapturedAt(file)?.time,
            modifiedAtMillis = attributes?.lastModifiedTime()?.toMillis(),
            createdAtMillis = attributes?.creationTime()?.toMillis(),
        )
    }
}
