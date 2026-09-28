package com.imagepreptool.model

import java.io.File
import java.nio.file.Path

enum class WorkflowStep(val label: String) {
    Select("Step1: 選択"),
    Edit("Step2: 編集と出力"),
}

enum class ExifTextPosition(val label: String) {
    TopLeft("左上"),
    TopRight("右上"),
    BottomLeft("左下"),
    BottomRight("右下"),
}

enum class OutputFormat(val label: String, val extension: String) {
    KeepOriginal("元の形式", ""),
    Jpeg("JPEG", "jpg"),
    Webp("WebP", "webp"),
    Png("PNG", "png"),
}

data class ImageSelection(
    val file: File,
    val selected: Boolean = true,
)

data class EditOptions(
    val maxWidth: Int? = 1920,
    val maxHeight: Int? = 1080,
    val keepAspectRatio: Boolean = true,
    val outputFormat: OutputFormat = OutputFormat.KeepOriginal,
    val burnExifText: Boolean = true,
    val exifPosition: ExifTextPosition = ExifTextPosition.BottomRight,
    val exifFontSize: Int = 24,
    val exifMargin: Int = 16,
    val customExifLine: String = "",
)

data class ProcessResult(
    val source: File,
    val success: Boolean,
    val outputPath: Path? = null,
    val message: String,
)

data class ExternalToolStatus(
    val name: String,
    val command: String,
    val available: Boolean,
    val detail: String,
)
