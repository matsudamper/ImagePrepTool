package com.imagepreptool.service

import java.awt.image.BufferedImage
import java.io.File
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.ExternalTools
import com.imagepreptool.model.ImageSize
import com.imagepreptool.model.ProcessResult

class ImageProcessor(private val tools: ExternalTools) {

    fun caption(source: File, options: EditOptions): String? {
        if (!options.captionEnabled) return null
        return CaptionTemplate.render(options.captionTemplate, ExifService.readFields(source)).takeIf { it.isNotBlank() }
    }

    /** リサイズとキャプション描画。[image] は変更しない */
    fun render(image: BufferedImage, caption: String?, options: EditOptions, target: ImageSize): BufferedImage {
        val resized = Resizer.resize(image, target)
        val canvas = if (resized === image) copy(image) else resized
        if (caption != null) CaptionRenderer.draw(canvas, caption, options)
        return canvas
    }

    /**
     * 1 枚を書き出す。失敗は [ProcessResult] で返す。
     * @throws InterruptedException スレッドが割り込まれた（キャンセルされた）場合。途中のファイルは残さない
     */
    fun export(item: PlannedOutput, options: EditOptions): ProcessResult {
        if (item.skip) return ProcessResult(item.source, null, ProcessResult.Status.Skipped, "同名のファイルがあるためスキップしました")
        return try {
            val loaded = ImageLoader.load(item.source, tools)
            val rotatedSize = Rotator.rotatedSize(loaded.size, item.rotation)
            val target = Resizer.targetSize(Cropper.croppedSize(rotatedSize, item.crop), options)
            val rotated = Rotator.rotate(loaded.image, item.rotation)
            val rendered = render(Cropper.crop(rotated, item.crop), caption(item.source, options), options, target)
            // 同名確認で「上書き」が選ばれた項目だけ既存ファイルを置き換える
            ImageEncoder.write(rendered, item.format, options.quality, item.target, tools, replaceExisting = item.exists)
            ProcessResult(item.source, item.target, ProcessResult.Status.Success, "$target · ${item.format.label}")
        } catch (e: OutOfMemoryError) {
            ProcessResult(item.source, null, ProcessResult.Status.Failed, "メモリが不足しました")
        } catch (e: InterruptedException) {
            throw e
        } catch (e: java.io.InterruptedIOException) {
            throw InterruptedException("キャンセルされました").apply { initCause(e) }
        } catch (e: Exception) {
            ProcessResult(item.source, null, ProcessResult.Status.Failed, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun copy(image: BufferedImage): BufferedImage =
        BufferedImage(image.width, image.height, image.type).also { out ->
            val g = out.createGraphics()
            g.drawImage(image, 0, 0, null)
            g.dispose()
        }
}
