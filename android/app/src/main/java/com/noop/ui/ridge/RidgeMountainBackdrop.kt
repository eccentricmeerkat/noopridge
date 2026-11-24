package com.noop.ui.ridge

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow

/**
 * The Ridge page backdrop (side quest #2, R2c): layered, hazy mountain ridges on a navy sky, faded into the page
 * colour. A port of `docs/ridge-build/design reference/backdrop-reference/ridge-backdrop-generator.js`: the same
 * layer table, draw order and rules, so the look matches, though the shapes differ (different random numbers).
 *
 * Drawn once into a [Bitmap] on a software canvas (blurs need one) and cached by the caller; it is static, so it
 * never redraws while a page scrolls. No image asset ships.
 */
object RidgeMountainBackdrop {

    /** Far to near. base/amp/valley are fractions of the height; rough is tree-line roughness at an 844-tall frame. */
    private data class Layer(
        val base: Float, val amp: Float, val freq: Float, val oct: Int, val ridged: Boolean, val rough: Float,
        val valley: Float, val top: Int, val bot: Int, val mist: Float, val blur: Float,
    )

    private val LAYERS = listOf(
        Layer(0.050f, 0.050f, 1.8f, 4, true, 0f, 0f, 0xFF1A3047.toInt(), 0xFF27425A.toInt(), 0.09f, 1.2f),
        Layer(0.078f, 0.045f, 2.4f, 4, true, 0f, 0f, 0xFF172C42.toInt(), 0xFF223C52.toInt(), 0.08f, 0.9f),
        Layer(0.105f, 0.050f, 2.0f, 4, false, 0f, 0.01f, 0xFF14263A.toInt(), 0xFF1D3549.toInt(), 0.08f, 0.6f),
        Layer(0.135f, 0.050f, 2.6f, 4, false, 0.5f, 0.02f, 0xFF102034.toInt(), 0xFF172C3E.toInt(), 0.07f, 0.3f),
        Layer(0.170f, 0.060f, 1.8f, 4, false, 1.0f, 0.04f, 0xFF0C1B2B.toInt(), 0xFF112434.toInt(), 0.06f, 0f),
        Layer(0.215f, 0.045f, 2.2f, 4, false, 1.0f, 0.05f, 0xFF0A1726.toInt(), 0xFF0E1D2C.toInt(), 0f, 0f),
    )
    private val SKY = intArrayOf(0xFF1F3550.toInt(), 0xFF1E354B.toInt(), 0xFF15273A.toInt())

    /** The page colour the backdrop fades into: Ridge bg-100 (#131F26), per the owner's brief. */
    const val PAGE_BASE: Int = 0xFF131F26.toInt()

    /** mulberry32, as the reference, so a seed means the same sequence on every run. */
    private class Rng(seed: Int) {
        private var a = seed
        fun next(): Float {
            a += 0x6D2B79F5
            var t = (a xor (a ushr 15)) * (1 or a)
            t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
            return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
        }
    }

    private class Noise(rng: Rng) {
        private val v = FloatArray(512) { rng.next() }
        fun at(x: Float): Float {
            val i0 = floor(x).toInt()
            val t = x - i0
            val s = t * t * (3 - 2 * t)
            val a = v[Math.floorMod(i0, 512)]
            val b = v[Math.floorMod(i0 + 1, 512)]
            return a + (b - a) * s
        }
    }

    private fun fbm(n: Noise, x0: Float, oct: Int): Float {
        var a = 1f; var s = 0f; var tot = 0f; var x = x0
        repeat(oct) { s += a * n.at(x); tot += a; x *= 2.03f; a *= 0.5f }
        return s / tot
    }

    private fun ridgedNoise(n: Noise, x0: Float, oct: Int): Float {
        var a = 1f; var s = 0f; var tot = 0f; var x = x0
        repeat(oct) { val r = 1 - abs(2 * n.at(x) - 1); s += a * r * r; tot += a; x *= 2.03f; a *= 0.5f }
        return s / tot
    }

    private fun clamp01(v: Float) = v.coerceIn(0f, 1f)

    private fun withAlpha(c: Int, a: Float): Int = ((a.coerceIn(0f, 1f) * 255).toInt() shl 24) or (c and 0xFFFFFF)

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** An elliptical radial glow: [rx] wide, squashed vertically by [sy], centred at ([cx], [cy]). */
    private fun Canvas.glow(cx: Float, cy: Float, rx: Float, sy: Float, color: Int, alpha: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(0f, 0f, rx, withAlpha(color, alpha), withAlpha(color, 0f), Shader.TileMode.CLAMP)
        save(); translate(cx, cy); scale(1f, sy)
        drawRect(-rx * 2, -rx * 2, rx * 2, rx * 2, p)
        restore()
    }

    /** Render the backdrop at [w]×[h] pixels for [seed]. Pure apart from the bitmap; same seed, same image. */
    fun render(w: Int, h: Int, seed: Int, base: Int = PAGE_BASE): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val W = w.toFloat(); val H = h.toFloat(); val sc = H / 844f

        // 1. Sky, lighter at the top right.
        val sky = Paint()
        sky.shader = LinearGradient(
            0f, 0f, 0f, H, intArrayOf(SKY[0], SKY[1], SKY[2], base, base), floatArrayOf(0f, 0.08f, 0.22f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, W, H, sky)
        c.glow(W * 0.95f, H * 0.02f, W * 0.7f, 1f, rgb(78, 112, 146), 0.26f)

        // 2. Ridge layers, far to near, each followed by mist in its valleys.
        val rngMist = Rng(seed * 7919 + 13)
        LAYERS.forEachIndexed { i, L ->
            val n = Noise(Rng(seed * 131 + i * 977))
            val nr = Noise(Rng(seed * 17 + i * 31))
            val off = i * 3.7f
            val path = Path().apply { moveTo(-10f, H) }
            var x = -10f
            while (x <= W + 10f) {
                val u = x / W
                val s = if (L.ridged) clamp01((ridgedNoise(n, u * L.freq + off, L.oct) - 0.1f) / 0.55f)
                else clamp01((fbm(n, u * L.freq + off, L.oct) - 0.22f) / 0.52f)
                val valley = L.valley * exp(-((u - 0.58f) / 0.22f).pow(2))
                var y = (L.base - L.amp * s + valley) * H
                // The forest edge: fine roughness along the nearer ridgelines.
                if (L.rough > 0f) y += (fbm(nr, x * 0.11f / sc, 3) - 0.5f) * 7f * L.rough * sc
                path.lineTo(x, y)
                x += sc  // about 1 dp, fine enough for the forest edge
            }
            path.lineTo(W + 10f, H); path.close()
            val fill = Paint(Paint.ANTI_ALIAS_FLAG)
            fill.shader = LinearGradient(0f, (L.base - L.amp) * H, 0f, (L.base + 0.14f) * H, L.top, L.bot, Shader.TileMode.CLAMP)
            if (L.blur > 0f) fill.maskFilter = BlurMaskFilter(L.blur * sc * 2f, BlurMaskFilter.Blur.NORMAL)
            c.drawPath(path, fill)
            if (L.mist > 0f) repeat(2) {
                val cx = W * (0.15f + 0.7f * rngMist.next())
                c.glow(cx, (L.base + L.amp * 0.35f) * H, W * 0.6f, 0.16f, rgb(150, 184, 216), L.mist)
            }
        }

        // 3. The lit mist bank on the right, as in the reference.
        c.glow(W * 0.84f, H * 0.19f, W * 0.55f, 0.5f, rgb(130, 165, 200), 0.11f)

        // 4. Fade into the page colour, then a faint light fog at the very bottom.
        val fade = Paint()
        fade.shader = LinearGradient(0f, H * 0.21f, 0f, H * 0.50f, withAlpha(base, 0f), withAlpha(base, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, H * 0.21f, W, H, fade)
        c.glow(W * 0.7f, H, W * 0.8f, 0.4f, rgb(80, 115, 150), 0.13f)

        // 5. Grain: a 96 px tile of faint white specks, repeated.
        val tile = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val rg = Rng(seed + 99)
        val px = IntArray(96 * 96) { ((rg.next() * 9).toInt() shl 24) or 0xFFFFFF }
        tile.setPixels(px, 0, 96, 0, 0, 96, 96)
        val grain = Paint().apply { shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
        c.drawRect(0f, 0f, W, H, grain)
        tile.recycle()
        return bmp
    }
}
