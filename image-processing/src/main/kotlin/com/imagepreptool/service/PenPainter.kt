package com.imagepreptool.service

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.imagepreptool.model.PenKind
import com.imagepreptool.model.PenStroke

object PenPainter {

    private const val BLUR_PASSES = 3

    /** 線を描いた画像を返す。[strokes] が空なら [image] をそのまま返し、それ以外でも [image] は変更しない */
    fun paint(image: BufferedImage, strokes: List<PenStroke>): BufferedImage {
        if (strokes.isEmpty()) return image
        val canvasType = if (image.type == BufferedImage.TYPE_CUSTOM) BufferedImage.TYPE_INT_ARGB else image.type
        val canvas = BufferedImage(image.width, image.height, canvasType)
        val g = canvas.createGraphics()
        g.drawImage(image, 0, 0, null)
        g.dispose()
        strokes.forEach { stroke ->
            when (stroke.kind) {
                PenKind.Draw -> draw(canvas, stroke)
                PenKind.Blur -> blur(canvas, stroke)
            }
        }
        return canvas
    }

    /** 画像の短辺に対する [percent] をピクセルにする */
    fun percentToPixels(percent: Float, width: Int, height: Int): Float = min(width, height) * percent / 100f

    private fun draw(canvas: BufferedImage, stroke: PenStroke) {
        val g = canvas.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(stroke.color, true)
        g.fill(strokeShape(stroke, canvas.width, canvas.height))
        g.dispose()
    }

    private fun blur(canvas: BufferedImage, stroke: PenStroke) {
        val shape = strokeShape(stroke, canvas.width, canvas.height)
        val radius = percentToPixels(stroke.blurPercent, canvas.width, canvas.height).roundToInt().coerceAtLeast(1)
        val imageBounds = Rectangle(0, 0, canvas.width, canvas.height)
        val paintBounds = shape.bounds.intersection(imageBounds)
        if (paintBounds.isEmpty) return
        // 線の外側の画素もぼかしに混ぜるため、ぼかしの届く範囲まで広げて読む
        val sampleMargin = radius * BLUR_PASSES
        val sampleBounds = Rectangle(
            paintBounds.x - sampleMargin,
            paintBounds.y - sampleMargin,
            paintBounds.width + sampleMargin * 2,
            paintBounds.height + sampleMargin * 2,
        ).intersection(imageBounds)

        val width = sampleBounds.width
        val height = sampleBounds.height
        val pixels = canvas.getRGB(sampleBounds.x, sampleBounds.y, width, height, null, 0, width)
        val blurred = boxBlur(pixels, width, height, radius)

        val tile = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        tile.setRGB(0, 0, width, height, blurred, 0, width)
        // 線の形をアンチエイリアス付きで切り抜き、縁が段々にならないようにする
        val mask = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val maskGraphics = mask.createGraphics()
        maskGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        maskGraphics.color = Color.WHITE
        maskGraphics.fill(AffineTransform.getTranslateInstance(-sampleBounds.x.toDouble(), -sampleBounds.y.toDouble()).createTransformedShape(shape))
        maskGraphics.composite = AlphaComposite.SrcIn
        maskGraphics.drawImage(tile, 0, 0, null)
        maskGraphics.dispose()

        val g = canvas.createGraphics()
        g.drawImage(mask, sampleBounds.x, sampleBounds.y, null)
        g.dispose()
    }

    private fun strokeShape(stroke: PenStroke, width: Int, height: Int): Shape {
        val strokeWidth = percentToPixels(stroke.widthPercent, width, height).coerceAtLeast(1f)
        val first = stroke.points.firstOrNull() ?: return Path2D.Float()
        if (stroke.points.size == 1) {
            val half = strokeWidth / 2
            return Ellipse2D.Float(first.x * width - half, first.y * height - half, strokeWidth, strokeWidth)
        }
        val path = Path2D.Float()
        path.moveTo(first.x * width, first.y * height)
        stroke.points.drop(1).forEach { point -> path.lineTo(point.x * width, point.y * height) }
        return BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(path)
    }

    /** 箱型ぼかしを縦横に [BLUR_PASSES] 回ずつ重ね、ガウスぼかしに近づける */
    private fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
        val buffer = IntArray(pixels.size)
        val result = pixels.copyOf()
        repeat(BLUR_PASSES) {
            for (y in 0 until height) blurLine(result, buffer, start = y * width, length = width, step = 1, radius = radius)
            for (x in 0 until width) blurLine(buffer, result, start = x, length = height, step = width, radius = radius)
        }
        return result
    }

    private fun blurLine(src: IntArray, dst: IntArray, start: Int, length: Int, step: Int, radius: Int) {
        val windowSize = 2 * radius + 1
        val last = length - 1
        var sumA = 0
        var sumR = 0
        var sumG = 0
        var sumB = 0
        for (i in -radius..radius) {
            val p = src[start + i.coerceIn(0, last) * step]
            sumA += p ushr 24
            sumR += (p shr 16) and 0xFF
            sumG += (p shr 8) and 0xFF
            sumB += p and 0xFF
        }
        for (i in 0 until length) {
            dst[start + i * step] = ((sumA / windowSize) shl 24) or
                ((sumR / windowSize) shl 16) or
                ((sumG / windowSize) shl 8) or
                (sumB / windowSize)
            val leaving = src[start + max(i - radius, 0) * step]
            val entering = src[start + min(i + radius + 1, last) * step]
            sumA += (entering ushr 24) - (leaving ushr 24)
            sumR += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
            sumG += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
            sumB += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}
