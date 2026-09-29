package com.imagepreptool.service

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.ExifTextPosition
import com.imagepreptool.model.OutputFormat
import com.imagepreptool.model.ProcessResult

class ImageProcessor {

    fun processBatch(
        sources: List<File>,
        outputDir: File,
        options: EditOptions,
        onProgress: (Int, Int, ProcessResult) -> Unit = { _, _, _ -> },
    ): List<ProcessResult> {
        if (!outputDir.exists()) outputDir.mkdirs()
        return sources.mapIndexed { index, source ->
            val result = runCatching { processOne(source, outputDir.toPath(), options) }
                .getOrElse { ProcessResult(source, false, null, it.message ?: "不明なエラー") }
            onProgress(index + 1, sources.size, result)
            result
        }
    }

    fun processOne(source: File, outputDir: Path, options: EditOptions): ProcessResult {
        val caption = if (options.burnExifText) {
            ExifService.buildCaption(source, options.customExifLine)
        } else {
            ""
        }

        val loaded = loadImage(source)
            ?: return convertWithExternalTools(source, outputDir, options, caption)

        var image = loaded
        options.maxWidth?.let { mw ->
            options.maxHeight?.let { mh ->
                image = resize(image, mw, mh, options.keepAspectRatio)
            }
        }

        if (caption.isNotBlank()) {
            image = drawCaption(image, caption, options.exifPosition, options.exifFontSize, options.exifMargin)
        }

        val targetFormat = resolveOutputFormat(source, options.outputFormat)
        val outputFile = outputDir.resolve(buildOutputName(source, targetFormat)).toFile()

        when (targetFormat) {
            OutputFormat.Webp -> {
                val tempPng = Files.createTempFile("imageprep-", ".png").toFile()
                try {
                    ImageIO.write(image, "png", tempPng)
                    runExternal(
                        ExternalToolChecker.resolveCommand("cwebp"),
                        listOf("-q", "85", tempPng.absolutePath, "-o", outputFile.absolutePath),
                    )
                } finally {
                    tempPng.delete()
                }
            }
            OutputFormat.Jpeg -> ImageIO.write(image, "jpg", outputFile)
            OutputFormat.Png -> ImageIO.write(image, "png", outputFile)
            OutputFormat.KeepOriginal -> {
                val ext = source.extension.lowercase()
                when (ext) {
                    "jpg", "jpeg" -> ImageIO.write(image, "jpg", outputFile)
                    "png" -> ImageIO.write(image, "png", outputFile)
                    "webp" -> {
                        val tempPng = Files.createTempFile("imageprep-", ".png").toFile()
                        try {
                            ImageIO.write(image, "png", tempPng)
                            runExternal(
                                ExternalToolChecker.resolveCommand("cwebp"),
                                listOf("-q", "85", tempPng.absolutePath, "-o", outputFile.absolutePath),
                            )
                        } finally {
                            tempPng.delete()
                        }
                    }
                    else -> ImageIO.write(image, ext.ifBlank { "png" }, outputFile)
                }
            }
        }

        return ProcessResult(source, true, outputFile.toPath(), "出力: ${outputFile.name}")
    }

    private fun convertWithExternalTools(
        source: File,
        outputDir: Path,
        options: EditOptions,
        caption: String,
    ): ProcessResult {
        val ext = source.extension.lowercase()
        val target = resolveOutputFormat(source, options.outputFormat)
        val outputFile = outputDir.resolve(buildOutputName(source, target)).toFile()

        when {
            ext in heifExtensions -> {
                if (!ExternalToolChecker.isAvailable("heif-convert") && !ExternalToolChecker.isAvailable("magick")) {
                    return ProcessResult(source, false, null, "HEIF 変換には heif-convert または magick が必要です")
                }
                val tempJpeg = Files.createTempFile("imageprep-heif-", ".jpg").toFile()
                try {
                    if (ExternalToolChecker.isAvailable("heif-convert")) {
                        runExternal(
                            ExternalToolChecker.resolveCommand("heif-convert"),
                            listOf(source.absolutePath, tempJpeg.absolutePath),
                        )
                    } else {
                        runExternal(
                            ExternalToolChecker.resolveCommand("magick"),
                            listOf(source.absolutePath, tempJpeg.absolutePath),
                        )
                    }
                    return processOne(tempJpeg, outputDir, options.copy(outputFormat = target))
                } finally {
                    tempJpeg.delete()
                }
            }
            ext == "webp" && ExternalToolChecker.isAvailable("dwebp") -> {
                val tempPng = Files.createTempFile("imageprep-webp-", ".png").toFile()
                try {
                    runExternal(
                        ExternalToolChecker.resolveCommand("dwebp"),
                        listOf(source.absolutePath, "-o", tempPng.absolutePath),
                    )
                    var image = ImageIO.read(tempPng) ?: error("dwebp 出力を読み込めません")
                    applyPostLoad(image, source, outputDir, options, caption)?.let { return it }
                    return ProcessResult(source, false, null, "WebP の後処理に失敗しました")
                } finally {
                    tempPng.delete()
                }
            }
            else -> return ProcessResult(source, false, null, "画像を読み込めません: ${source.name}")
        }
    }

    private fun applyPostLoad(
        image: BufferedImage,
        source: File,
        outputDir: Path,
        options: EditOptions,
        caption: String,
    ): ProcessResult? {
        var current = image
        options.maxWidth?.let { mw ->
            options.maxHeight?.let { mh ->
                current = resize(current, mw, mh, options.keepAspectRatio)
            }
        }
        if (caption.isNotBlank()) {
            current = drawCaption(current, caption, options.exifPosition, options.exifFontSize, options.exifMargin)
        }
        val target = resolveOutputFormat(source, options.outputFormat)
        val outputFile = outputDir.resolve(buildOutputName(source, target)).toFile()
        when (target) {
            OutputFormat.Jpeg -> ImageIO.write(current, "jpg", outputFile)
            OutputFormat.Png -> ImageIO.write(current, "png", outputFile)
            OutputFormat.Webp -> {
                val tempPng = Files.createTempFile("imageprep-", ".png").toFile()
                try {
                    ImageIO.write(current, "png", tempPng)
                    runExternal(
                        ExternalToolChecker.resolveCommand("cwebp"),
                        listOf("-q", "85", tempPng.absolutePath, "-o", outputFile.absolutePath),
                    )
                } finally {
                    tempPng.delete()
                }
            }
            OutputFormat.KeepOriginal -> ImageIO.write(current, "png", outputFile)
        }
        return ProcessResult(source, true, outputFile.toPath(), "出力: ${outputFile.name}")
    }

    private fun loadImage(source: File): BufferedImage? =
        runCatching { ImageIO.read(source) }.getOrNull()

    private fun resize(image: BufferedImage, maxWidth: Int, maxHeight: Int, keepAspect: Boolean): BufferedImage {
        if (maxWidth <= 0 || maxHeight <= 0) return image
        val scale = if (keepAspect) {
            minOf(maxWidth.toDouble() / image.width, maxHeight.toDouble() / image.height, 1.0)
        } else {
            1.0
        }
        if (scale >= 1.0 && keepAspect) return image
        val targetW = if (keepAspect) (image.width * scale).roundToInt().coerceAtLeast(1) else maxWidth
        val targetH = if (keepAspect) (image.height * scale).roundToInt().coerceAtLeast(1) else maxHeight
        val result = BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB)
        val g = result.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(image, 0, 0, targetW, targetH, null)
        g.dispose()
        return result
    }

    private fun drawCaption(
        image: BufferedImage,
        caption: String,
        position: ExifTextPosition,
        fontSize: Int,
        margin: Int,
    ): BufferedImage {
        val rgbImage = if (image.type == BufferedImage.TYPE_INT_RGB) {
            image
        } else {
            val converted = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
            val gConvert = converted.createGraphics()
            gConvert.drawImage(image, 0, 0, null)
            gConvert.dispose()
            converted
        }
        val g = rgbImage.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        val font = Font(Font.SANS_SERIF, Font.PLAIN, fontSize.coerceIn(8, 96))
        g.font = font
        val metrics = g.fontMetrics
        val textW = metrics.stringWidth(caption)
        val textH = metrics.height
        val (x, y) = when (position) {
            ExifTextPosition.TopLeft -> margin to margin + metrics.ascent
            ExifTextPosition.TopRight -> (rgbImage.width - textW - margin) to margin + metrics.ascent
            ExifTextPosition.BottomLeft -> margin to (rgbImage.height - margin - metrics.descent)
            ExifTextPosition.BottomRight -> (rgbImage.width - textW - margin) to (rgbImage.height - margin - metrics.descent)
        }
        g.color = Color(0, 0, 0, 160)
        g.fillRoundRect(x - 6, y - metrics.ascent - 4, textW + 12, textH + 4, 8, 8)
        g.composite = AlphaComposite.SrcOver
        g.color = Color.WHITE
        g.drawString(caption, x, y)
        g.dispose()
        return rgbImage
    }

    private fun resolveOutputFormat(source: File, selected: OutputFormat): OutputFormat {
        if (selected != OutputFormat.KeepOriginal) return selected
        return when (source.extension.lowercase()) {
            "jpg", "jpeg" -> OutputFormat.Jpeg
            "webp" -> OutputFormat.Webp
            "png" -> OutputFormat.Png
            else -> OutputFormat.Png
        }
    }

    private fun buildOutputName(source: File, format: OutputFormat): String {
        val ext = when (format) {
            OutputFormat.KeepOriginal -> source.extension.ifBlank { "png" }
            else -> format.extension
        }
        return "${source.nameWithoutExtension}_prepped.$ext"
    }

    private fun runExternal(command: String, args: List<String>) {
        val process = ProcessBuilder(listOf(command) + args)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(120, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            error("$command がタイムアウトしました")
        }
        if (process.exitValue() != 0) {
            val output = process.inputStream.bufferedReader().readText()
            error("$command 失敗: $output")
        }
    }

    companion object {
        private val heifExtensions = setOf("heif", "heic", "hif")
    }
}
