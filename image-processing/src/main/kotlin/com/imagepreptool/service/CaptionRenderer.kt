package com.imagepreptool.service

import com.imagepreptool.model.CaptionPosition
import com.imagepreptool.model.CaptionStyle
import com.imagepreptool.model.EditOptions
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object CaptionRenderer {

    /** [image] に直接キャプションを描く */
    fun draw(image: BufferedImage, caption: String, options: EditOptions) {
        val text = caption.trim()
        if (text.isEmpty()) return
        val shortEdge = min(image.width, image.height)
        val percent = options.captionSizePercent.coerceIn(EditOptions.MIN_CAPTION_PERCENT, EditOptions.MAX_CAPTION_PERCENT)
        var fontPx = max(8f, shortEdge * percent / 100f)
        val margin = (fontPx * 1.1f).roundToInt()

        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

            val padX = { size: Float -> if (options.captionStyle == CaptionStyle.Plate) size * 0.55f else 0f }
            // 画像幅に収まらなければ文字を小さくする
            var font = Font(Font.SANS_SERIF, Font.PLAIN, 1).deriveFont(fontPx)
            val available = image.width - margin * 2
            val measured = g.getFontMetrics(font).stringWidth(text) + padX(fontPx) * 2
            if (measured > available && available > 0) {
                fontPx = max(6f, fontPx * available / measured)
                font = font.deriveFont(fontPx)
            }
            g.font = font
            val metrics = g.fontMetrics
            val textWidth = metrics.stringWidth(text)
            val padH = padX(fontPx)
            val padV = if (options.captionStyle == CaptionStyle.Plate) fontPx * 0.32f else 0f
            val boxWidth = textWidth + padH * 2
            val boxHeight = metrics.ascent + metrics.descent + padV * 2

            val left = when (options.captionPosition) {
                CaptionPosition.TopLeft, CaptionPosition.BottomLeft -> margin.toFloat()
                CaptionPosition.TopRight, CaptionPosition.BottomRight -> image.width - margin - boxWidth
            }
            val top = when (options.captionPosition) {
                CaptionPosition.TopLeft, CaptionPosition.TopRight -> margin.toFloat()
                CaptionPosition.BottomLeft, CaptionPosition.BottomRight -> image.height - margin - boxHeight
            }
            val baseline = top + padV + metrics.ascent
            val textX = left + padH

            when (options.captionStyle) {
                CaptionStyle.Plate -> {
                    g.color = Color(0, 0, 0, 150)
                    val radius = boxHeight * 0.35f
                    g.fill(RoundRectangle2D.Float(left, top, boxWidth, boxHeight, radius * 2, radius * 2))
                }
                CaptionStyle.Shadow -> {
                    val offset = max(1f, fontPx * 0.06f)
                    g.color = Color(0, 0, 0, 90)
                    g.drawString(text, textX + offset * 1.6f, baseline + offset * 1.6f)
                    g.color = Color(0, 0, 0, 170)
                    g.drawString(text, textX + offset, baseline + offset)
                }
            }
            g.color = Color(255, 255, 255, 240)
            g.drawString(text, textX, baseline)
        } finally {
            g.dispose()
        }
    }
}
