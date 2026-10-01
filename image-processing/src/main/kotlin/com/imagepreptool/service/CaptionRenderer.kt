package com.imagepreptool.service

import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import kotlin.math.min
import com.imagepreptool.model.CaptionPosition
import com.imagepreptool.model.CaptionStyle
import com.imagepreptool.model.EditOptions

/**
 * 寸法はすべて文字サイズ（＝画像の短辺に対する割合）の倍率で決める。
 * 画素数の下限や整数丸めを入れると、画像サイズによって余白や行間の見た目の比率が変わってしまう
 */
object CaptionRenderer {
    private const val MARGIN_RATIO = 1.1f
    private const val LINE_HEIGHT_RATIO = 1.3f
    private const val PLATE_PADDING_H_RATIO = 0.55f
    private const val PLATE_PADDING_V_RATIO = 0.25f
    private const val PLATE_CORNER_RADIUS_RATIO = 0.5f

    /** [image] に直接キャプションを描く。改行で複数行。右側に置くときは右揃え */
    fun draw(image: BufferedImage, caption: String, options: EditOptions) {
        val lines = caption.lines().map { it.trim() }.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
        if (lines.isEmpty()) return
        val shortEdge = min(image.width, image.height)
        val percent = options.captionSizePercent.coerceIn(EditOptions.MIN_CAPTION_PERCENT, EditOptions.MAX_CAPTION_PERCENT)
        val preferredFontPx = shortEdge * percent / 100f
        val margin = preferredFontPx * MARGIN_RATIO
        val plate = options.captionStyle == CaptionStyle.Plate
        val plateWidthRatio = if (plate) PLATE_PADDING_H_RATIO * 2 else 0f

        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

            // 画像幅に収まらなければ文字を小さくする
            var fontPx = preferredFontPx
            var font = Font(Font.SANS_SERIF, Font.PLAIN, 1).deriveFont(fontPx)
            val available = image.width - margin * 2
            val measured = maxLineWidth(g, font, lines) + fontPx * plateWidthRatio
            if (measured > available && available > 0) {
                fontPx *= available / measured
                font = font.deriveFont(fontPx)
            }
            g.font = font
            val lineMetrics = font.getLineMetrics(lines.first(), g.fontRenderContext)
            val lineHeight = fontPx * LINE_HEIGHT_RATIO
            val baselineInLine = (lineHeight - lineMetrics.ascent - lineMetrics.descent) / 2 + lineMetrics.ascent
            val textWidth = maxLineWidth(g, font, lines)
            val padH = if (plate) fontPx * PLATE_PADDING_H_RATIO else 0f
            val padV = if (plate) fontPx * PLATE_PADDING_V_RATIO else 0f
            val boxWidth = textWidth + padH * 2
            val boxHeight = lineHeight * lines.size + padV * 2
            val alignRight = options.captionPosition == CaptionPosition.TopRight || options.captionPosition == CaptionPosition.BottomRight

            val left = if (alignRight) image.width - margin - boxWidth else margin
            val top = when (options.captionPosition) {
                CaptionPosition.TopLeft, CaptionPosition.TopRight -> margin
                CaptionPosition.BottomLeft, CaptionPosition.BottomRight -> image.height - margin - boxHeight
            }

            if (plate) {
                g.color = Color(0, 0, 0, 150)
                val diameter = fontPx * PLATE_CORNER_RADIUS_RATIO * 2
                g.fill(RoundRectangle2D.Float(left, top, boxWidth, boxHeight, diameter, diameter))
            }
            lines.forEachIndexed { index, line ->
                val baseline = top + padV + lineHeight * index + baselineInLine
                val x = if (alignRight) left + padH + textWidth - lineWidth(g, font, line) else left + padH
                g.color = Color(255, 255, 255, 240)
                g.drawString(line, x, baseline)
            }
        } finally {
            g.dispose()
        }
    }

    private fun maxLineWidth(g: Graphics2D, font: Font, lines: List<String>): Float =
        lines.maxOf { lineWidth(g, font, it) }

    private fun lineWidth(g: Graphics2D, font: Font, line: String): Float =
        font.getStringBounds(line, g.fontRenderContext).width.toFloat()
}
