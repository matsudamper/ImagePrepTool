package com.imagepreptool.service

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.ImageOutputStream
import com.imagepreptool.model.ExternalTool
import com.imagepreptool.model.ExternalTools
import com.imagepreptool.model.OutputFormat

object ImageEncoder {

    /**
     * [format] は解決済み（[OutputFormat.Original] 以外）であること。
     * [replaceExisting] が false のとき、書き出し中に同名ファイルが作られていたら置き換えずに失敗する
     */
    fun write(image: BufferedImage, format: OutputFormat, quality: Int, file: File, tools: ExternalTools, replaceExisting: Boolean) {
        file.parentFile?.mkdirs()
        when (format) {
            OutputFormat.Jpeg -> writeJpeg(image, quality, file, replaceExisting)
            OutputFormat.Png -> writePng(image, file, replaceExisting)
            OutputFormat.Webp -> writeWebp(image, quality, file, tools, replaceExisting)
            OutputFormat.Original -> error("出力形式が解決されていません")
        }
    }

    private fun writeJpeg(image: BufferedImage, quality: Int, file: File, replaceExisting: Boolean) {
        writeAtomically(file, replaceExisting) { temp ->
            ImageIO.createImageOutputStream(temp).use { out -> encodeJpeg(image, quality, out) }
        }
    }

    private fun encodeJpeg(image: BufferedImage, quality: Int, out: ImageOutputStream) {
        val rgb = if (image.colorModel.hasAlpha()) flattenOnWhite(image) else image
        val writer = ImageIO.getImageWritersByFormatName("jpeg").asSequence().firstOrNull()
            ?: throw IOException("JPEG エンコーダが見つかりません")
        try {
            val param = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality.coerceIn(1, 100) / 100f
            }
            writer.output = out
            writer.write(null, IIOImage(rgb, null, null), param)
        } finally {
            writer.dispose()
        }
    }

    private fun writePng(image: BufferedImage, file: File, replaceExisting: Boolean) {
        writeAtomically(file, replaceExisting) { temp ->
            if (!ImageIO.write(image, "png", temp)) throw IOException("PNG エンコーダが見つかりません")
        }
    }

    private fun writeWebp(image: BufferedImage, quality: Int, file: File, tools: ExternalTools, replaceExisting: Boolean) {
        if (!tools.canWriteWebp) throw IOException("WebP の書き出しには cwebp が必要です")
        writeAtomically(file, replaceExisting) { temp -> encodeWebp(image, quality, temp) }
    }

    private fun encodeWebp(image: BufferedImage, quality: Int, output: File) {
        val png = Files.createTempFile("imageprep-", ".png").toFile().apply { deleteOnExit() }
        try {
            if (!ImageIO.write(image, "png", png)) throw IOException("一時ファイルを書き出せません")
            val result = try {
                ProcessRunner.run(
                    listOf(
                        ExternalTool.Cwebp.command, "-quiet", "-q", quality.coerceIn(1, 100).toString(),
                        "-metadata", "none", png.absolutePath, "-o", output.absolutePath,
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
        } finally {
            png.delete()
        }
    }

    /**
     * 書き出しと同じ形式・品質でエンコードし、読み戻した画像とエンコード後のバイト数を返す。
     * WebP で cwebp が無いなど、書き出しと同じエンコードができない場合は null
     * @throws IOException エンコードや読み戻しに失敗した場合
     */
    fun encodeForPreview(image: BufferedImage, format: OutputFormat, quality: Int, tools: ExternalTools): EncodedPreview? =
        when (format) {
            OutputFormat.Jpeg -> {
                val bytes = ByteArrayOutputStream().also { buffer ->
                    ImageIO.createImageOutputStream(buffer).use { out -> encodeJpeg(image, quality, out) }
                }.toByteArray()
                EncodedPreview(readBack(bytes.inputStream()), bytes.size.toLong())
            }
            OutputFormat.Png -> {
                val buffer = ByteArrayOutputStream()
                if (!ImageIO.write(image, "png", buffer)) throw IOException("PNG エンコーダが見つかりません")
                // 可逆なので読み戻さずにそのまま使う
                EncodedPreview(image, buffer.size().toLong())
            }
            OutputFormat.Webp -> if (tools.canWriteWebp) encodeWebpForPreview(image, quality) else null
            OutputFormat.Original -> error("出力形式が解決されていません")
        }

    private fun encodeWebpForPreview(image: BufferedImage, quality: Int): EncodedPreview {
        val webp = Files.createTempFile("imageprep-", ".webp").toFile().apply { deleteOnExit() }
        try {
            encodeWebp(image, quality, webp)
            return EncodedPreview(readBack(webp.inputStream()), webp.length())
        } finally {
            webp.delete()
        }
    }

    private fun readBack(input: InputStream): BufferedImage =
        input.use { ImageIO.read(it) } ?: throw IOException("エンコードした画像を読み戻せません")

    /** 途中で失敗しても壊れたファイルを残さないよう、一時ファイルに書いてから置き換える */
    private fun writeAtomically(file: File, replaceExisting: Boolean, block: (File) -> Unit) {
        // 既存ファイルや同時に動く書き出しとぶつからないよう、処理ごとに一意な名前にする。
        // createTempFile は 0600 で作られ移動後も残るため、通常の権限（umask 依存）で新規作成する
        val temp = generateSequence { File(file.absoluteFile.parentFile, ".imageprep-${UUID.randomUUID()}.tmp") }
            .first { it.createNewFile() }
            .apply { deleteOnExit() }
        try {
            block(temp)
            if (!temp.isFile || temp.length() == 0L) throw IOException("書き出し結果が空です")
            // キャンセル後に完成したファイルは置かない
            if (Thread.currentThread().isInterrupted) throw InterruptedException("キャンセルされました")
            try {
                if (replaceExisting) {
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } else {
                    Files.move(temp.toPath(), file.toPath())
                }
            } catch (e: FileAlreadyExistsException) {
                throw IOException("書き出し中に同名のファイルが作られたため、上書きせずに中止しました", e)
            }
        } finally {
            temp.delete()
        }
    }

    /** JPEG は透明を持てないため白背景に合成する（プレビューでも同じ見た目にするため公開） */
    fun flattenOnWhite(image: BufferedImage): BufferedImage =
        BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB).also { out ->
            val g = out.createGraphics()
            g.color = Color.WHITE
            g.fillRect(0, 0, image.width, image.height)
            g.drawImage(image, 0, 0, null)
            g.dispose()
        }
}

/** 書き出し時と同じエンコードを通した画像と、そのバイト数 */
class EncodedPreview(val image: BufferedImage, val byteSize: Long)
