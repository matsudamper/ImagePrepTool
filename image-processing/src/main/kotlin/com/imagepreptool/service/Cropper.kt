package com.imagepreptool.service

import java.awt.image.BufferedImage
import kotlin.math.roundToInt
import com.imagepreptool.model.CropRect
import com.imagepreptool.model.ImageSize

object Cropper {

    /** 切り抜き後のサイズ。[crop] が null なら [source] のまま */
    fun croppedSize(source: ImageSize, crop: CropRect?): ImageSize {
        val bounds = pixelBounds(source, crop) ?: return source
        return ImageSize(bounds.width, bounds.height)
    }

    /** 切り抜いた画像を返す。戻り値は [image] と画素を共有するので、書き換える前に複製すること */
    fun crop(image: BufferedImage, crop: CropRect?): BufferedImage {
        val bounds = pixelBounds(ImageSize(image.width, image.height), crop) ?: return image
        return image.getSubimage(bounds.x, bounds.y, bounds.width, bounds.height)
    }

    private class PixelBounds(val x: Int, val y: Int, val width: Int, val height: Int)

    private fun pixelBounds(size: ImageSize, crop: CropRect?): PixelBounds? {
        if (crop == null || crop.isFull) return null
        val x = (crop.left.coerceIn(0f, 1f) * size.width).roundToInt().coerceIn(0, size.width - 1)
        val y = (crop.top.coerceIn(0f, 1f) * size.height).roundToInt().coerceIn(0, size.height - 1)
        val right = (crop.right.coerceIn(0f, 1f) * size.width).roundToInt().coerceIn(x + 1, size.width)
        val bottom = (crop.bottom.coerceIn(0f, 1f) * size.height).roundToInt().coerceIn(y + 1, size.height)
        return PixelBounds(x, y, right - x, bottom - y)
    }
}
