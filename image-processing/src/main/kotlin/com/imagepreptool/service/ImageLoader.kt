package com.imagepreptool.service

import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.file.Files
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import kotlin.math.max
import com.imagepreptool.model.ExternalTool
import com.imagepreptool.model.ExternalTools
import com.imagepreptool.model.ImageSize

/** 読み込んだ画像。[size] は向き補正後の元画像サイズ（[image] は縮小済みのことがある） */
class LoadedImage(val image: BufferedImage, val size: ImageSize)

class ImageLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

object ImageLoader {

    val standardExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    val heifExtensions = setOf("heic", "heif", "hif")
    val supportedExtensions = standardExtensions + heifExtensions

    init {
        // 配布版でも TwelveMonkeys のプラグインを確実に登録する
        ImageIO.scanForPlugins()
        ImageIO.setUseCache(false)
    }

    fun isSupported(file: File): Boolean = file.extension.lowercase() in supportedExtensions

    fun isHeif(file: File): Boolean = file.extension.lowercase() in heifExtensions

    /**
     * 画像を読み込み、EXIF の向きを反映した RGB / ARGB 画像にする。
     * @param maxDimension 指定すると長辺がこの値程度になるよう縮小して読む（サムネイル・プレビュー用）
     */
    fun load(file: File, tools: ExternalTools, maxDimension: Int? = null): LoadedImage {
        if (!file.isFile) throw ImageLoadException("ファイルが見つかりません")
        if (isHeif(file)) return loadHeif(file, tools, maxDimension)

        val orientation = ExifService.readOrientation(file)
        val (decoded, rawSize) = try {
            decode(file, maxDimension)
        } catch (e: ImageLoadException) {
            throw e
        } catch (e: Exception) {
            throw ImageLoadException("画像を読み込めません（${e.message ?: e.javaClass.simpleName}）", e)
        }
        val oriented = applyOrientation(normalize(decoded), orientation)
        val size = if (orientation >= 5) ImageSize(rawSize.height, rawSize.width) else rawSize
        return LoadedImage(fitWithin(oriented, maxDimension), size)
    }

    private fun decode(file: File, maxDimension: Int?): Pair<BufferedImage, ImageSize> {
        ImageIO.createImageInputStream(file).use { stream ->
            stream ?: throw ImageLoadException("ファイルを開けません")
            val reader: ImageReader = ImageIO.getImageReaders(stream).asSequence().firstOrNull()
                ?: throw ImageLoadException("対応していない画像形式です")
            try {
                reader.setInput(stream, true, true)
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                val param = reader.defaultReadParam
                if (maxDimension != null) {
                    val step = max(1, max(width, height) / maxDimension)
                    if (step > 1) param.setSourceSubsampling(step, step, 0, 0)
                }
                return reader.read(0, param) to ImageSize(width, height)
            } finally {
                reader.dispose()
            }
        }
    }

    private fun loadHeif(file: File, tools: ExternalTools, maxDimension: Int?): LoadedImage {
        val decoder = tools.heifDecoder
            ?: throw ImageLoadException("HEIC の読み込みには heif-dec または magick が必要です")
        val temp = Files.createTempFile("imageprep-heif-", if (decoder == ExternalTool.Magick) ".png" else ".jpg").toFile()
        try {
            val command = when (decoder) {
                ExternalTool.Magick -> listOf(decoder.command, file.absolutePath, temp.absolutePath)
                else -> listOf(decoder.command, "-q", "95", file.absolutePath, temp.absolutePath)
            }
            val result = try {
                ProcessRunner.run(command, timeoutSeconds = 120)
            } catch (e: IOException) {
                throw ImageLoadException("${decoder.command} を実行できません", e)
            } catch (e: ExternalCommandException) {
                throw ImageLoadException(e.message.orEmpty(), e)
            }
            if (result.exitCode != 0 || temp.length() == 0L) {
                val reason = result.output.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "終了コード ${result.exitCode}"
                throw ImageLoadException("HEIC を変換できません（$reason）")
            }
            // デコーダ側で回転は適用済みなので EXIF の向きは使わない
            val image = normalize(ImageIO.read(temp) ?: throw ImageLoadException("HEIC の変換結果を読み込めません"))
            return LoadedImage(fitWithin(image, maxDimension), ImageSize(image.width, image.height))
        } finally {
            temp.delete()
        }
    }

    /** 色空間や画素形式をそろえる（INT_RGB か INT_ARGB） */
    internal fun normalize(image: BufferedImage): BufferedImage {
        val type = if (image.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        if (image.type == type) return image
        return BufferedImage(image.width, image.height, type).also { out ->
            val g = out.createGraphics()
            g.drawImage(image, 0, 0, null)
            g.dispose()
        }
    }

    internal fun applyOrientation(image: BufferedImage, orientation: Int): BufferedImage {
        if (orientation !in 2..8) return image
        val w = image.width.toDouble()
        val h = image.height.toDouble()
        // x' = m00 x + m01 y + m02, y' = m10 x + m11 y + m12
        val transform = when (orientation) {
            2 -> AffineTransform(-1.0, 0.0, 0.0, 1.0, w, 0.0)
            3 -> AffineTransform(-1.0, 0.0, 0.0, -1.0, w, h)
            4 -> AffineTransform(1.0, 0.0, 0.0, -1.0, 0.0, h)
            5 -> AffineTransform(0.0, 1.0, 1.0, 0.0, 0.0, 0.0)
            6 -> AffineTransform(0.0, 1.0, -1.0, 0.0, h, 0.0)
            7 -> AffineTransform(0.0, -1.0, -1.0, 0.0, h, w)
            else -> AffineTransform(0.0, -1.0, 1.0, 0.0, 0.0, w)
        }
        val swap = orientation >= 5
        val out = BufferedImage(if (swap) image.height else image.width, if (swap) image.width else image.height, image.type)
        val g = out.createGraphics()
        g.drawImage(image, transform, null)
        g.dispose()
        return out
    }

    private fun fitWithin(image: BufferedImage, maxDimension: Int?): BufferedImage {
        if (maxDimension == null) return image
        val size = Resizer.fitWithin(ImageSize(image.width, image.height), maxDimension, maxDimension)
        return Resizer.resize(image, size)
    }
}
