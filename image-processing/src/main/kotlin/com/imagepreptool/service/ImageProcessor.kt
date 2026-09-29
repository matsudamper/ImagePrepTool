package com.imagepreptool.service

import com.imagepreptool.model.CaptionSource
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.ExternalTools
import com.imagepreptool.model.ImageSize
import com.imagepreptool.model.ProcessResult
import java.awt.image.BufferedImage
import java.io.File

class ImageProcessor(private val tools: ExternalTools) {

    fun caption(source: File, options: EditOptions): String? {
        if (!options.captionEnabled) return null
        return when (options.captionSource) {
            CaptionSource.Custom -> options.customCaption.trim().takeIf { it.isNotEmpty() }
            CaptionSource.Exif -> ExifService.buildCaption(source)
        }
    }

    /** リサイズとキャプション描画。[image] は変更しない */
    fun render(image: BufferedImage, caption: String?, options: EditOptions, target: ImageSize): BufferedImage {
        val resized = Resizer.resize(image, target)
        val canvas = if (resized === image) copy(image) else resized
        if (caption != null) CaptionRenderer.draw(canvas, caption, options)
        return canvas
    }

    fun export(item: PlannedOutput, options: EditOptions): ProcessResult {
        if (item.skip) return ProcessResult(item.source, null, ProcessResult.Status.Skipped, "同名のファイルがあるためスキップしました")
        return try {
            val loaded = ImageLoader.load(item.source, tools)
            val target = Resizer.targetSize(loaded.size, options)
            val rendered = render(loaded.image, caption(item.source, options), options, target)
            ImageEncoder.write(rendered, item.format, options.quality, item.target, tools)
            ProcessResult(item.source, item.target, ProcessResult.Status.Success, "${target} · ${item.format.label}")
        } catch (e: OutOfMemoryError) {
            ProcessResult(item.source, null, ProcessResult.Status.Failed, "メモリが不足しました")
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
