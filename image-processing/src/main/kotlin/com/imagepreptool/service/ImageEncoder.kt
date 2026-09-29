package com.imagepreptool.service

import com.imagepreptool.model.ExternalTool
import com.imagepreptool.model.ExternalTools
import com.imagepreptool.model.OutputFormat
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.file.Files
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

object ImageEncoder {

    /** [format] は解決済み（[OutputFormat.Original] 以外）であること */
    fun write(image: BufferedImage, format: OutputFormat, quality: Int, file: File, tools: ExternalTools) {
        file.parentFile?.mkdirs()
        when (format) {
            OutputFormat.Jpeg -> writeJpeg(image, quality, file)
            OutputFormat.Png -> writePng(image, file)
            OutputFormat.Webp -> writeWebp(image, quality, file, tools)
            OutputFormat.Original -> error("出力形式が解決されていません")
        }
    }

    private fun writeJpeg(image: BufferedImage, quality: Int, file: File) {
        val rgb = if (image.colorModel.hasAlpha()) flattenOnWhite(image) else image
        val writer = ImageIO.getImageWritersByFormatName("jpeg").asSequence().firstOrNull()
            ?: throw IOException("JPEG エンコーダが見つかりません")
        try {
            val param = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality.coerceIn(1, 100) / 100f
            }
            writeAtomically(file) { temp ->
                ImageIO.createImageOutputStream(temp).use { out ->
                    writer.output = out
                    writer.write(null, IIOImage(rgb, null, null), param)
                }
            }
        } finally {
            writer.dispose()
        }
    }

    private fun writePng(image: BufferedImage, file: File) {
        writeAtomically(file) { temp ->
            if (!ImageIO.write(image, "png", temp)) throw IOException("PNG エンコーダが見つかりません")
        }
    }

    private fun writeWebp(image: BufferedImage, quality: Int, file: File, tools: ExternalTools) {
        if (!tools.canWriteWebp) throw IOException("WebP の書き出しには cwebp が必要です")
        val png = Files.createTempFile("imageprep-", ".png").toFile()
        try {
            if (!ImageIO.write(image, "png", png)) throw IOException("一時ファイルを書き出せません")
            writeAtomically(file) { temp ->
                val result = try {
                    ProcessRunner.run(
                        listOf(
                            ExternalTool.Cwebp.command, "-quiet", "-q", quality.coerceIn(1, 100).toString(),
                            "-metadata", "none", png.absolutePath, "-o", temp.absolutePath,
                        ),
                        timeoutSeconds = 180,
                    )
                } catch (e: ExternalCommandException) {
                    throw IOException(e.message, e)
                }
                if (result.exitCode != 0) {
                    val reason = result.output.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "終了コード ${result.exitCode}"
                    throw IOException("cwebp が失敗しました（$reason）")
                }
            }
        } finally {
            png.delete()
        }
    }

    /** 途中で失敗しても壊れたファイルを残さないよう、一時ファイルに書いてから置き換える */
    private fun writeAtomically(file: File, block: (File) -> Unit) {
        val temp = File(file.parentFile, ".${file.name}.tmp")
        try {
            block(temp)
            if (!temp.isFile || temp.length() == 0L) throw IOException("書き出し結果が空です")
            Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temp.delete()
        }
    }

    private fun flattenOnWhite(image: BufferedImage): BufferedImage =
        BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB).also { out ->
            val g = out.createGraphics()
            g.color = Color.WHITE
            g.fillRect(0, 0, image.width, image.height)
            g.drawImage(image, 0, 0, null)
            g.dispose()
        }
}
