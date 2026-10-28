package com.example.mp3

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.model.CoverStyle
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates aesthetic, modern album artwork graphics for MP3 files.
 */
object CoverArtGenerator {

    private const val DEFAULT_SIZE = 600

    fun generateCover(
        title: String,
        artist: String,
        style: CoverStyle,
        size: Int = DEFAULT_SIZE
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        when (style) {
            CoverStyle.NEON_SYNTHWAVE -> drawNeonSynthwave(canvas, size, title, artist)
            CoverStyle.SUNSET_HORIZON -> drawSunsetHorizon(canvas, size, title, artist)
            CoverStyle.COBALT_NIGHT -> drawCobaltNight(canvas, size, title, artist)
            CoverStyle.EMERALD_CHILL -> drawEmeraldChill(canvas, size, title, artist)
            CoverStyle.VINTAGE_RETRO -> drawVintageRetro(canvas, size, title, artist)
            CoverStyle.AURORA_DREAM -> drawAuroraDream(canvas, size, title, artist)
        }

        return bitmap
    }

    fun generateForSong(
        title: String,
        artist: String,
        chosenStyle: CoverStyle? = null,
        size: Int = DEFAULT_SIZE
    ): Pair<Bitmap, ByteArray> {
        val style = chosenStyle ?: run {
            val hash = abs((title + artist).hashCode())
            CoverStyle.fromIndex(hash)
        }
        val bitmap = generateCover(title, artist, style, size)
        val bytes = bitmapToJpegBytes(bitmap)
        return Pair(bitmap, bytes)
    }

    fun bitmapToJpegBytes(bitmap: Bitmap, quality: Int = 90): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    private fun drawNeonSynthwave(canvas: Canvas, size: Int, title: String, artist: String) {
        val bgPaint = Paint().apply {
            shader = RadialGradient(
                size * 0.5f, size * 0.35f, size * 0.75f,
                intArrayOf(Color.parseColor("#4A0E4E"), Color.parseColor("#1B053A"), Color.parseColor("#090014")),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // Visualizer Bars
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val numBars = 16
        val barWidth = (size - 100f) / numBars
        for (i in 0 until numBars) {
            val barHeight = 40f + (sin(i * 0.5 + 1.2) * 45f + 50f).toFloat()
            val left = 50f + i * barWidth + 4f
            val right = left + barWidth - 8f
            val top = size * 0.55f - barHeight
            val bottom = size * 0.55f

            barPaint.shader = LinearGradient(
                left, top, left, bottom,
                Color.parseColor("#00F0FF"),
                Color.parseColor("#FF007F"),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(RectF(left, top, right, bottom), 6f, 6f, barPaint)
        }

        // Glowing Sun / Disc Arc
        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#66FF007F")
        }
        for (r in listOf(size * 0.22f, size * 0.28f, size * 0.34f)) {
            canvas.drawCircle(size * 0.5f, size * 0.35f, r, arcPaint)
        }

        // Modern Vinyl Center Motif
        val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#120024")
        }
        canvas.drawCircle(size * 0.5f, size * 0.35f, size * 0.12f, discPaint)

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00F0FF")
        }
        canvas.drawCircle(size * 0.5f, size * 0.35f, 12f, dotPaint)

        // Text & Badge Overlay
        drawBadge(canvas, "STEREO HIGH FIDELITY", 40f, 50f, Color.parseColor("#FF007F"))
        drawTrackLabels(canvas, size, title, artist, Color.WHITE, Color.parseColor("#00F0FF"))
    }

    private fun drawSunsetHorizon(canvas: Canvas, size: Int, title: String, artist: String) {
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, size.toFloat(),
                intArrayOf(Color.parseColor("#210B2C"), Color.parseColor("#8E1339"), Color.parseColor("#E65100"), Color.parseColor("#FFD54F")),
                floatArrayOf(0f, 0.4f, 0.75f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // Sliced Sun
        val sunCenterY = size * 0.45f
        val sunRadius = size * 0.26f
        val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFE082")
        }
        canvas.drawCircle(size * 0.5f, sunCenterY, sunRadius, sunPaint)

        // Horizon Lines cutting the sun
        val cutPaint = Paint().apply {
            color = Color.parseColor("#8E1339")
            strokeWidth = 5f
        }
        for (y in (sunCenterY - sunRadius * 0.4f).toInt()..(sunCenterY + sunRadius).toInt() step 20) {
            canvas.drawLine(size * 0.2f, y.toFloat(), size * 0.8f, y.toFloat(), cutPaint)
        }

        // Mountain Contour Path
        val path = Path().apply {
            moveTo(0f, size * 0.65f)
            lineTo(size * 0.3f, size * 0.52f)
            lineTo(size * 0.5f, size * 0.60f)
            lineTo(size * 0.75f, size * 0.48f)
            lineTo(size.toFloat(), size * 0.65f)
            lineTo(size.toFloat(), size.toFloat())
            lineTo(0f, size.toFloat())
            close()
        }
        val mountainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#15031F")
        }
        canvas.drawPath(path, mountainPaint)

        drawBadge(canvas, "AUDIO TRACK", 40f, 50f, Color.parseColor("#FFD54F"))
        drawTrackLabels(canvas, size, title, artist, Color.WHITE, Color.parseColor("#FFB74D"))
    }

    private fun drawCobaltNight(canvas: Canvas, size: Int, title: String, artist: String) {
        val bgPaint = Paint().apply {
            shader = RadialGradient(
                size * 0.5f, size * 0.5f, size * 0.75f,
                Color.parseColor("#0D1B2A"),
                Color.parseColor("#020813"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // Vinyl Grooves
        val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#1B263B")
            strokeWidth = 2f
        }
        for (r in (size * 0.16f).toInt()..(size * 0.46f).toInt() step 12) {
            canvas.drawCircle(size * 0.5f, size * 0.4f, r.toFloat(), groovePaint)
        }

        // Gold center label
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2979FF")
        }
        canvas.drawCircle(size * 0.5f, size * 0.4f, size * 0.14f, labelPaint)

        val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#020813")
        }
        canvas.drawCircle(size * 0.5f, size * 0.4f, 16f, holePaint)

        drawBadge(canvas, "OFFICIAL RELEASE", 40f, 50f, Color.parseColor("#2979FF"))
        drawTrackLabels(canvas, size, title, artist, Color.WHITE, Color.parseColor("#90CAF9"))
    }

    private fun drawEmeraldChill(canvas: Canvas, size: Int, title: String, artist: String) {
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                Color.parseColor("#00281F"),
                Color.parseColor("#00120D"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // Organic waves
        val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#4400E676")
        }
        for (i in 0..5) {
            val yOffset = size * 0.3f + i * 25f
            val wavePath = Path().apply {
                moveTo(0f, yOffset)
                cubicTo(
                    size * 0.25f, yOffset - 40f,
                    size * 0.75f, yOffset + 40f,
                    size.toFloat(), yOffset
                )
            }
            canvas.drawPath(wavePath, wavePaint)
        }

        // Geometric disc
        val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                size * 0.5f, size * 0.4f, size * 0.25f,
                Color.parseColor("#00E676"),
                Color.parseColor("#004D40"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(size * 0.5f, size * 0.4f, size * 0.15f, discPaint)

        drawBadge(canvas, "LO-FI & CHILL", 40f, 50f, Color.parseColor("#00E676"))
        drawTrackLabels(canvas, size, title, artist, Color.WHITE, Color.parseColor("#A7FFEB"))
    }

    private fun drawVintageRetro(canvas: Canvas, size: Int, title: String, artist: String) {
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, size.toFloat(),
                Color.parseColor("#2D1B13"),
                Color.parseColor("#190C07"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // Framed border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.parseColor("#80FFB74D")
        }
        canvas.drawRect(30f, 30f, size - 30f, size - 30f, borderPaint)
        canvas.drawRect(36f, 36f, size - 36f, size - 36f, borderPaint)

        // Cassette reel shapes
        val reelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3E2723")
        }
        val reelStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#FFB74D")
        }
        canvas.drawCircle(size * 0.35f, size * 0.4f, 50f, reelPaint)
        canvas.drawCircle(size * 0.35f, size * 0.4f, 50f, reelStroke)
        canvas.drawCircle(size * 0.65f, size * 0.4f, 50f, reelPaint)
        canvas.drawCircle(size * 0.65f, size * 0.4f, 50f, reelStroke)

        drawBadge(canvas, "VINTAGE CASSETTE", 50f, 65f, Color.parseColor("#FFB74D"))
        drawTrackLabels(canvas, size, title, artist, Color.parseColor("#FFE082"), Color.parseColor("#D7CCC8"))
    }

    private fun drawAuroraDream(canvas: Canvas, size: Int, title: String, artist: String) {
        val bgPaint = Paint().apply {
            shader = RadialGradient(
                size * 0.3f, size * 0.3f, size.toFloat(),
                intArrayOf(Color.parseColor("#311B92"), Color.parseColor("#004D40"), Color.parseColor("#000000")),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // Stars
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }
        for (i in 0 until 40) {
            val sx = ((i * 137.5) % size).toFloat()
            val sy = ((i * 93.3) % (size * 0.65f)).toFloat()
            val rad = 1.5f + (i % 3)
            canvas.drawCircle(sx, sy, rad, starPaint)
        }

        // Swirling Nebula Glow
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                size * 0.6f, size * 0.4f, size * 0.35f,
                Color.parseColor("#667C4DFF"),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(size * 0.6f, size * 0.4f, size * 0.35f, glowPaint)

        drawBadge(canvas, "CELESTIAL SOUNDS", 40f, 50f, Color.parseColor("#B388FF"))
        drawTrackLabels(canvas, size, title, artist, Color.WHITE, Color.parseColor("#80D8FF"))
    }

    private fun drawBadge(canvas: Canvas, text: String, x: Float, y: Float, accentColor: Int) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#33000000")
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.15f
        }

        val bounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds)
        val padH = 16f
        val padV = 8f
        val rect = RectF(x, y - bounds.height() - padV, x + bounds.width() + padH * 2, y + padV)

        canvas.drawRoundRect(rect, 8f, 8f, bgPaint)
        canvas.drawRoundRect(rect, 8f, 8f, strokePaint)
        canvas.drawText(text, x + padH, y, textPaint)
    }

    private fun drawTrackLabels(
        canvas: Canvas,
        size: Int,
        title: String,
        artist: String,
        titleColor: Int,
        artistColor: Int
    ) {
        // Dark translucent gradient backdrop behind text for readability
        val textBg = Paint().apply {
            shader = LinearGradient(
                0f, size * 0.65f, 0f, size.toFloat(),
                Color.TRANSPARENT,
                Color.parseColor("#D9000000").toInt(),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, size * 0.65f, size.toFloat(), size.toFloat(), textBg)

        // Title
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = titleColor
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(8f, 0f, 4f, Color.BLACK)
        }
        val displayTitle = truncateText(titlePaint, title, size - 80f)
        canvas.drawText(displayTitle, 40f, size - 80f, titlePaint)

        // Artist
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = artistColor
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            setShadowLayer(6f, 0f, 2f, Color.BLACK)
        }
        val displayArtist = truncateText(artistPaint, artist, size - 80f)
        canvas.drawText(displayArtist, 40f, size - 40f, artistPaint)
    }

    private fun truncateText(paint: Paint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var truncated = text
        while (truncated.isNotEmpty() && paint.measureText("$truncated...") > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return "$truncated..."
    }
}
