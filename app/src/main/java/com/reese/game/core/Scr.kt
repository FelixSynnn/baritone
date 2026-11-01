package com.reese.game.core

/** A sprite: w*h shade indices, -1 = transparent. */
class Sprite(val w: Int, val h: Int, val px: ByteArray) {
    companion object {
        fun parse(rows: List<String>): Sprite {
            val h = rows.size
            val w = rows.maxOf { it.length }
            val px = ByteArray(w * h) { -1 }
            for (y in 0 until h) for (x in rows[y].indices) {
                val c = rows[y][x]
                if (c in '0'..'3') px[y * w + x] = (c - '0').toByte()
            }
            return Sprite(w, h, px)
        }
    }
}

/**
 * The Game Boy screen: 160x144 pixels, each holding a shade 0..3
 * (0 = lightest, 3 = darkest). The palette is applied when the frame is shown.
 */
class Scr(val w: Int = W, val h: Int = H) {
    companion object { const val W = 160; const val H = 144 }

    val px = ByteArray(w * h)
    /** Clip rectangle (used for scrolling lists). */
    var cx0 = 0; var cy0 = 0; var cx1 = w; var cy1 = h
    /** Shade remap applied to every draw (e.g. fades / inverted text). */
    var remap: ByteArray? = null

    fun clip(x: Int, y: Int, ww: Int, hh: Int) { cx0 = maxOf(0, x); cy0 = maxOf(0, y); cx1 = minOf(w, x + ww); cy1 = minOf(h, y + hh) }
    fun noclip() { cx0 = 0; cy0 = 0; cx1 = w; cy1 = h }

    fun clear(c: Int) { px.fill(c.toByte()) }

    fun pset(x: Int, y: Int, c: Int) {
        if (x < cx0 || y < cy0 || x >= cx1 || y >= cy1 || c < 0) return
        val m = remap
        px[y * w + x] = if (m != null) m[c] else c.toByte()
    }
    fun pget(x: Int, y: Int): Int = if (x < 0 || y < 0 || x >= w || y >= h) 0 else px[y * w + x].toInt()

    fun rect(x: Int, y: Int, ww: Int, hh: Int, c: Int) {
        for (j in maxOf(y, cy0) until minOf(y + hh, cy1)) for (i in maxOf(x, cx0) until minOf(x + ww, cx1)) pset(i, j, c)
    }
    fun hline(x: Int, y: Int, len: Int, c: Int) = rect(x, y, len, 1, c)
    fun vline(x: Int, y: Int, len: Int, c: Int) = rect(x, y, 1, len, c)
    fun frame(x: Int, y: Int, ww: Int, hh: Int, c: Int) {
        hline(x, y, ww, c); hline(x, y + hh - 1, ww, c); vline(x, y, hh, c); vline(x + ww - 1, y, hh, c)
    }

    /** Two-shade checkerboard, the classic Game Boy "in-between" shade. */
    fun dither(x: Int, y: Int, ww: Int, hh: Int, a: Int, b: Int) {
        for (j in y until y + hh) for (i in x until x + ww) pset(i, j, if ((i + j) and 1 == 0) a else b)
    }
    /** Sparse dither: every 4th pixel gets shade b. */
    fun sparse(x: Int, y: Int, ww: Int, hh: Int, a: Int, b: Int) {
        for (j in y until y + hh) for (i in x until x + ww) pset(i, j, if (i % 2 == 0 && (j + (i / 2)) % 2 == 0) b else a)
    }

    fun line(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
        var x = x0; var y = y0
        val dx = Math.abs(x1 - x0); val dy = -Math.abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1; val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            pset(x, y, c)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    fun disc(cx: Int, cy: Int, r: Int, c: Int) {
        for (j in -r..r) for (i in -r..r) if (i * i + j * j <= r * r + r / 2) pset(cx + i, cy + j, c)
    }
    fun ring(cx: Int, cy: Int, r: Int, c: Int) {
        for (j in -r..r) for (i in -r..r) {
            val d = i * i + j * j
            if (d <= r * r + r && d >= (r - 1) * (r - 1) + (r - 1)) pset(cx + i, cy + j, c)
        }
    }

    fun blit(s: Sprite, x: Int, y: Int, flip: Boolean = false, shade: ByteArray? = null) {
        for (j in 0 until s.h) for (i in 0 until s.w) {
            val c = s.px[j * s.w + (if (flip) s.w - 1 - i else i)].toInt()
            if (c >= 0) pset(x + i, y + j, if (shade != null) shade[c].toInt() else c)
        }
    }
    /** Draw a sprite scaled up by an integer factor. */
    fun blitScaled(s: Sprite, x: Int, y: Int, k: Int) {
        for (j in 0 until s.h) for (i in 0 until s.w) {
            val c = s.px[j * s.w + i].toInt()
            if (c >= 0) rect(x + i * k, y + j * k, k, k, c)
        }
    }

    fun copyFrom(o: Scr) { System.arraycopy(o.px, 0, px, 0, px.size) }

    // ---------- UI helpers ----------
    /** Classic double-line RPG window. */
    fun window(x: Int, y: Int, ww: Int, hh: Int) {
        rect(x, y, ww, hh, 0)
        frame(x, y, ww, hh, 3)
        frame(x + 2, y + 2, ww - 4, hh - 4, 3)
        // rounded corners
        pset(x, y, -1); pset(x + ww - 1, y, -1)
        pset(x + 1, y + 1, 3); pset(x + ww - 2, y + 1, 3); pset(x + 1, y + hh - 2, 3); pset(x + ww - 2, y + hh - 2, 3)
    }
    fun text(s: String, x: Int, y: Int, c: Int = 3): Int = Font.draw(this, s, x, y, c)
    fun textC(s: String, cx: Int, y: Int, c: Int = 3) = Font.draw(this, s, cx - Font.width(s) / 2, y, c)
    fun textScaled(s: String, x: Int, y: Int, k: Int, c: Int) = Font.drawScaled(this, s, x, y, k, c)
    /** Text with a 1px outline in shade o. */
    fun textOutlined(s: String, x: Int, y: Int, c: Int, o: Int) {
        for (dy in -1..1) for (dx in -1..1) if (dx != 0 || dy != 0) Font.draw(this, s, x + dx, y + dy, o)
        Font.draw(this, s, x, y, c)
    }
}
