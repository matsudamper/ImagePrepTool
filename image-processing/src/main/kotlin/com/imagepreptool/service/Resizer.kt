package com.imagepreptool.service

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.ImageSize
import com.imagepreptool.model.ResizeMode

object Resizer {

    /** 設定から出力サイズを求める。拡大はしない */
    fun targetSize(source: ImageSize, options: EditOptions): ImageSize = when (options.resizeMode) {
        ResizeMode.None -> source
        ResizeMode.LongEdge -> fitWithin(source, options.longEdge, options.longEdge)
        ResizeMode.Fit -> fitWithin(source, options.fitWidth, options.fitHeight)
    }

    fun fitWithin(source: ImageSize, maxWidth: Int, maxHeight: Int): ImageSize {
        if (maxWidth <= 0 || maxHeight <= 0) return source
        val scale = min(1.0, min(maxWidth.toDouble() / source.width, maxHeight.toDouble() / source.height))
        if (scale >= 1.0) return source
        return ImageSize(
            (source.width * scale).roundToInt().coerceAtLeast(1),
            (source.height * scale).roundToInt().coerceAtLeast(1),
        )
    }

    /** 縮小時は半分ずつ段階的に縮めてジャギーを抑える */
    fun resize(image: BufferedImage, target: ImageSize): BufferedImage {
        if (image.width == target.width && image.height == target.height) return image
        var current = image
        while (current.width / 2 >= target.width && current.height / 2 >= target.height) {
            current = draw(current, max(current.width / 2, 1), max(current.height / 2, 1), bicubic = false)
        }
        return draw(current, target.width, target.height, bicubic = true)
    }

    private fun draw(image: BufferedImage, width: Int, height: Int, bicubic: Boolean): BufferedImage {
        val out = BufferedImage(width, height, if (image.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            if (bicubic) RenderingHints.VALUE_INTERPOLATION_BICUBIC else RenderingHints.VALUE_INTERPOLATION_BILINEAR,
        )
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(image, 0, 0, width, height, null)
        g.dispose()
        return out
    }
}
