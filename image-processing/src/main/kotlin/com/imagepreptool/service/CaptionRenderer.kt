package com.imagepreptool.service

import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.imagepreptool.model.CaptionPosition
import com.imagepreptool.model.CaptionStyle
import com.imagepreptool.model.EditOptions

object CaptionRenderer {

    /** [image] に直接キャプションを描く。改行で複数行。右側に置くときは右揃え */
    fun draw(image: BufferedImage, caption: String, options: EditOptions) {
        val lines = caption.lines().map { it.trim() }.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
        if (lines.isEmpty()) return
        val shortEdge = min(image.width, image.height)
        val percent = options.captionSizePercent.coerceIn(EditOptions.MIN_CAPTION_PERCENT, EditOptions.MAX_CAPTION_PERCENT)
        var fontPx = max(8f, shortEdge * percent / 100f)
        val margin = (fontPx * 1.1f).roundToInt()
        val plate = options.captionStyle == CaptionStyle.Plate

        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

            // 画像幅に収まらなければ文字を小さくする
            var font = Font(Font.SANS_SERIF, Font.PLAIN, 1).deriveFont(fontPx)
            val available = image.width - margin * 2
            val measured = maxLineWidth(g, font, lines) + (if (plate) fontPx * 1.1f else 0f)
            if (measured > available && available > 0) {
                fontPx = max(6f, fontPx * available / measured)
                font = font.deriveFont(fontPx)
            }
            g.font = font
            val metrics = g.fontMetrics
            val lineHeight = (metrics.ascent + metrics.descent) * 1.18f
            val textWidth = maxLineWidth(g, font, lines).toFloat()
            val padH = if (plate) fontPx * 0.55f else 0f
            val padV = if (plate) fontPx * 0.32f else 0f
            val boxWidth = textWidth + padH * 2
            val boxHeight = lineHeight * (lines.size - 1) + metrics.ascent + metrics.descent + padV * 2
            val alignRight = options.captionPosition == CaptionPosition.TopRight || options.captionPosition == CaptionPosition.BottomRight

            val left = if (alignRight) image.width - margin - boxWidth else margin.toFloat()
            val top = when (options.captionPosition) {
                CaptionPosition.TopLeft, CaptionPosition.TopRight -> margin.toFloat()
                CaptionPosition.BottomLeft, CaptionPosition.BottomRight -> image.height - margin - boxHeight
            }

            if (plate) {
                g.color = Color(0, 0, 0, 150)
                val radius = min(boxHeight, fontPx * 1.6f) * 0.35f
                g.fill(RoundRectangle2D.Float(left, top, boxWidth, boxHeight, radius * 2, radius * 2))
            }
            lines.forEachIndexed { index, line ->
                val baseline = top + padV + metrics.ascent + lineHeight * index
                val x = if (alignRight) left + padH + textWidth - metrics.stringWidth(line) else left + padH
                if (!plate) {
                    val offset = max(1f, fontPx * 0.06f)
                    g.color = Color(0, 0, 0, 90)
                    g.drawString(line, x + offset * 1.6f, baseline + offset * 1.6f)
                    g.color = Color(0, 0, 0, 170)
                    g.drawString(line, x + offset, baseline + offset)
                }
                g.color = Color(255, 255, 255, 240)
                g.drawString(line, x, baseline)
            }
        } finally {
            g.dispose()
        }
    }

    private fun maxLineWidth(g: Graphics2D, font: Font, lines: List<String>): Int {
        val metrics = g.getFontMetrics(font)
        return lines.maxOf { metrics.stringWidth(it) }
    }
}
