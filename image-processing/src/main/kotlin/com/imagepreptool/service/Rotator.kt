package com.imagepreptool.service

import java.awt.image.BufferedImage
import com.imagepreptool.model.ImageSize
import com.imagepreptool.model.Rotation

object Rotator {

    private const val EXIF_ROTATE_180 = 3
    private const val EXIF_ROTATE_CLOCKWISE_90 = 6
    private const val EXIF_ROTATE_CLOCKWISE_270 = 8

    fun rotatedSize(source: ImageSize, rotation: Rotation): ImageSize =
        if (rotation.swapsDimensions) ImageSize(source.height, source.width) else source

    /** 回転した画像を返す。[Rotation.None] なら [image] そのもの */
    fun rotate(image: BufferedImage, rotation: Rotation): BufferedImage = when (rotation) {
        Rotation.None -> image
        Rotation.Clockwise90 -> ImageLoader.applyOrientation(image, EXIF_ROTATE_CLOCKWISE_90)
        Rotation.Clockwise180 -> ImageLoader.applyOrientation(image, EXIF_ROTATE_180)
        Rotation.Clockwise270 -> ImageLoader.applyOrientation(image, EXIF_ROTATE_CLOCKWISE_270)
    }
}
