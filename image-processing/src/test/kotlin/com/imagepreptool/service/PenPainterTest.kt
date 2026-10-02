package com.imagepreptool.service

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import com.imagepreptool.model.PenKind
import com.imagepreptool.model.PenPoint
import com.imagepreptool.model.PenStroke

class PenPainterTest {

    @Test
    fun noStrokesReturnsSameImage() {
        val image = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)
        assertSame(image, PenPainter.paint(image, listOf()))
    }

    @Test
    fun drawPaintsOnlyAlongStrokeWithoutChangingSource() {
        val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB)
        val stroke = PenStroke(
            kind = PenKind.Draw,
            points = listOf(PenPoint(0.1f, 0.5f), PenPoint(0.9f, 0.5f)),
            widthPercent = 10f,
            color = 0xFFFF0000.toInt(),
            blurPercent = 1f,
        )
        val painted = PenPainter.paint(image, listOf(stroke))
        assertEquals(0xFF0000, painted.getRGB(50, 50) and 0xFFFFFF)
        assertEquals(0x000000, painted.getRGB(50, 10) and 0xFFFFFF)
        assertEquals(0x000000, image.getRGB(50, 50) and 0xFFFFFF)
    }

    @Test
    fun blurMixesNeighborsOnlyInsideStroke() {
        // 縦縞の画像を横切るようにぼかすと、線の上だけ縞が混ざる
        val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 100) for (x in 0 until 100) image.setRGB(x, y, if (x % 2 == 0) 0xFFFFFF else 0x000000)
        val stroke = PenStroke(
            kind = PenKind.Blur,
            points = listOf(PenPoint(0f, 0.5f), PenPoint(1f, 0.5f)),
            widthPercent = 20f,
            color = 0,
            blurPercent = 3f,
        )
        val blurred = PenPainter.paint(image, listOf(stroke))
        val center = blurred.getRGB(50, 50) and 0xFF
        assertNotEquals(0xFF, center)
        assertNotEquals(0x00, center)
        assertEquals(0xFFFFFF, blurred.getRGB(50, 5) and 0xFFFFFF)
        assertEquals(0x000000, blurred.getRGB(51, 5) and 0xFFFFFF)
    }
}
