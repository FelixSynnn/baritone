package com.reese.game.core

/**
 * Every location is drawn in code with the 4 Game Boy shades.
 * The scene view is the top 160x96; the text box covers the rest.
 * `t` is a frame counter (60/s) used for small animations.
 */
object Backgrounds {
    val IDS = listOf("cafe", "park", "arcade", "rooftop", "school", "room", "ramen", "stage", "sunset", "station", "black", "white")

    fun draw(s: Scr, id: String, t: Int) {
        when (id) {
            "cafe" -> cafe(s, t)
            "park" -> park(s, t, false)
            "sunset" -> park(s, t, true)
            "arcade" -> arcade(s, t)
            "rooftop" -> rooftop(s, t)
            "school" -> school(s, t)
            "room" -> room(s, t)
            "ramen" -> ramen(s, t)
            "stage" -> stage(s, t)
            "station" -> station(s, t)
            "white" -> s.clear(0)
            else -> s.clear(3)
        }
    }

    // ---------------- CAFÉ ----------------
    private fun cafe(s: Scr, t: Int) {
        s.clear(0)
        // wallpaper stripes
        for (x in 0 until 160 step 8) s.vline(x, 0, 70, 1)
        s.rect(0, 0, 160, 4, 2)
        // window with street outside
        s.rect(8, 10, 54, 40, 3)
        s.rect(10, 12, 50, 36, 1)
        s.sparse(10, 12, 50, 18, 0, 1)
        s.rect(10, 34, 50, 14, 2)                        // building across the street
        for (i in 0 until 5) s.rect(13 + i * 9, 37, 4, 5, 0)
        s.vline(35, 12, 36, 3); s.hline(10, 30, 50, 3)
        // awning scallops
        for (i in 0 until 7) { s.rect(8 + i * 8, 6, 8, 4, if (i % 2 == 0) 3 else 2); s.pset(11 + i * 8, 10, 3); s.pset(12 + i * 8, 10, 3) }
        // menu board
        s.rect(96, 8, 54, 34, 3); s.frame(95, 7, 56, 36, 2)
        s.text("MENU", 112, 10, 0)
        for (i in 0 until 3) { s.hline(100, 21 + i * 6, 26, 1); s.hline(134, 21 + i * 6, 10, 0) }
        // hanging lamps
        for (lx in listOf(74, 152)) {
            s.vline(lx, 0, 10, 3); s.rect(lx - 4, 10, 9, 4, 3); s.rect(lx - 2, 14, 5, 1, 1)
            if ((t / 40) % 2 == 0) s.pset(lx, 15, 0)
        }
        // counter
        s.rect(0, 70, 160, 3, 3)
        s.rect(0, 73, 160, 30, 2)
        for (x in 4 until 160 step 16) s.vline(x, 76, 24, 3)
        s.hline(0, 102, 160, 3)
        s.dither(0, 103, 160, 41, 1, 2)
        // espresso machine + cups
        s.rect(118, 50, 30, 20, 3); s.rect(121, 53, 24, 6, 1); s.rect(128, 61, 3, 5, 1); s.rect(136, 61, 3, 5, 1)
        s.pset(124, 55, 0); s.pset(127, 55, 0)
        val steam = (t / 12) % 3
        s.pset(129, 47 - steam, 1); s.pset(130, 45 - steam, 1)
        for (cx in listOf(14, 26, 38)) { s.rect(cx, 64, 7, 6, 0); s.frame(cx, 64, 7, 6, 3); s.pset(cx + 7, 66, 3) }
        // plant
        s.rect(80, 58, 10, 12, 3); s.rect(81, 59, 8, 10, 2)
        s.disc(85, 52, 6, 3); s.disc(83, 51, 3, 2); s.disc(88, 54, 2, 2)
    }

    // ---------------- PARK ----------------
    private fun park(s: Scr, t: Int, sunset: Boolean) {
        if (sunset) {
            s.clear(1)
            s.dither(0, 0, 160, 18, 2, 1)
            s.sparse(0, 18, 160, 14, 1, 2)
            s.disc(122, 52, 14, 0)                       // setting sun
            for (y in 40 until 60 step 3) s.hline(108, y, 30, 1)
        } else {
            s.clear(0)
            // clouds drifting
            val off = (t / 8) % 200
            for ((cx, cy) in listOf(30 to 12, 110 to 8, 180 to 18)) {
                val x = ((cx + off) % 200) - 20
                s.disc(x, cy, 5, 1); s.disc(x + 6, cy - 2, 6, 1); s.disc(x + 12, cy, 5, 1)
                s.disc(x, cy, 4, 0); s.disc(x + 6, cy - 2, 5, 0); s.disc(x + 12, cy, 4, 0)
            }
        }
        // distant tree line
        for (i in 0 until 12) s.disc(i * 15 + 4, 52 + (i % 3) * 2, 11, 2)
        s.rect(0, 54, 160, 6, 2)
        // grass
        s.rect(0, 60, 160, 84, 1)
        for (y in 62 until 144 step 4) for (x in (y % 8) until 160 step 8) { s.pset(x, y, 2); s.pset(x + 1, y - 1, 2) }
        // path
        for (y in 60 until 144) { val w = 10 + (y - 60) / 2; s.hline(80 - w, y, w * 2, 0) }
        // fountain
        s.rect(56, 60, 48, 10, 3); s.rect(58, 61, 44, 8, 2); s.dither(60, 62, 40, 5, 0, 1)
        s.rect(77, 46, 6, 15, 3); s.rect(73, 44, 14, 3, 3)
        val sp = (t / 6) % 4
        for (k in 0..2) { s.pset(78 - k * 2 - sp / 2, 40 + k + sp, 0); s.pset(81 + k * 2 + sp / 2, 40 + k + sp, 0) }
        s.pset(79, 39, 0); s.pset(80, 38, 0)
        // big tree left
        s.rect(10, 30, 8, 40, 3); s.rect(12, 30, 3, 40, 2)
        s.disc(14, 22, 16, 3); s.disc(12, 20, 12, 2); s.disc(20, 26, 6, 2)
        for (k in 0 until 8) s.pset(4 + k * 3, 14 + (k * 7) % 14, 1)
        // bench right
        s.rect(116, 70, 34, 3, 3); s.rect(116, 64, 34, 3, 3); s.rect(118, 73, 2, 8, 3); s.rect(146, 73, 2, 8, 3)
        s.hline(117, 71, 32, 2); s.hline(117, 65, 32, 2)
        // lamp post
        s.rect(104, 30, 2, 40, 3); s.rect(101, 26, 8, 5, 3); s.rect(102, 27, 6, 3, if (sunset) 0 else 1)
    }

    // ---------------- ARCADE ----------------
    private fun arcade(s: Scr, t: Int) {
        s.clear(3)
        // neon sign
        val on = (t / 50) % 7 != 0
        s.frame(40, 4, 80, 15, if (on) 0 else 2); s.frame(41, 5, 78, 13, 1)
        s.text("NEON PIXEL", 51, 8, if (on) 0 else 2)
        // cabinets
        for (i in 0 until 5) {
            val x = 2 + i * 32
            s.rect(x, 24, 28, 76, 2)
            s.rect(x, 24, 28, 7, 1); s.text(listOf("STAR", "FOX", "BLOK", "KART", "BEAT")[i], x + 3, 24, 3)
            s.rect(x + 3, 33, 22, 20, 3)
            // little animated games on the screens
            val fr = (t / 10 + i * 3) % 16
            when (i % 3) {
                0 -> { s.pset(x + 6 + fr, 40, 0); s.pset(x + 7 + fr, 40, 0); s.rect(x + 12, 48, 5, 2, 1) }
                1 -> { s.rect(x + 5, 45, 18, 1, 1); s.rect(x + 6 + (fr % 12), 41, 3, 4, 0) }
                else -> { for (k in 0 until 4) s.rect(x + 5 + k * 4, 36 + ((fr + k * 5) % 14), 3, 3, if (k % 2 == 0) 0 else 1) }
            }
            s.rect(x + 2, 56, 24, 8, 1)                       // control panel
            s.disc(x + 8, 59, 2, 3); s.pset(x + 8, 57, 3)
            s.disc(x + 17, 60, 1, 0); s.disc(x + 22, 60, 1, 0)
            s.rect(x + 4, 66, 20, 34, 2); s.frame(x + 4, 66, 20, 34, 3)
            s.rect(x + 11, 74, 6, 3, 3)                       // coin slot
        }
        // checker floor
        for (y in 100 until 144 step 4) for (x in 0 until 160 step 4) if (((x + y) / 4) % 2 == 0) s.rect(x, y, 4, 4, 2)
    }

    // ---------------- ROOFTOP AT NIGHT ----------------
    private fun rooftop(s: Scr, t: Int) {
        s.clear(3)
        // stars (some twinkle)
        var seed = 7
        for (i in 0 until 46) {
            seed = (seed * 1103515245 + 12345) and 0x7fffffff
            val x = seed % 160; val y = (seed / 160) % 54
            val tw = (i + t / 20) % 9 == 0
            s.pset(x, y, if (tw) 1 else 0)
            if (i % 11 == 0 && !tw) { s.pset(x - 1, y, 1); s.pset(x + 1, y, 1); s.pset(x, y - 1, 1); s.pset(x, y + 1, 1) }
        }
        // moon
        s.disc(130, 16, 9, 0); s.disc(134, 13, 8, 3)
        // skyline
        val bld = listOf(0 to 40, 18 to 30, 34 to 46, 52 to 26, 70 to 38, 90 to 22, 108 to 34, 126 to 44, 144 to 28)
        for ((x, h) in bld) {
            s.rect(x, 74 - h, 18, h, 2)
            for (wy in 74 - h + 3 until 72 step 5) for (wx in x + 2 until x + 16 step 4)
                if ((wx * 7 + wy * 3) % 5 < 2) s.rect(wx, wy, 2, 2, if ((wx + wy) % 3 == 0) 0 else 1)
        }
        s.rect(92, 48, 1, 4, 1); if ((t / 30) % 2 == 0) s.pset(92, 47, 0)  // antenna light
        // railing
        s.rect(0, 74, 160, 2, 1)
        for (x in 0 until 160 step 10) s.rect(x, 74, 2, 16, 1)
        s.rect(0, 82, 160, 1, 1)
        // floor
        s.rect(0, 90, 160, 54, 2)
        for (x in 0 until 160 step 20) s.vline(x, 90, 54, 3)
        s.hline(0, 90, 160, 3)
        // string lights
        for (x in 0 until 160 step 4) {
            val y = 60 + ((x - 80) * (x - 80)) / 400
            s.pset(x, y, 1)
            if (x % 12 == 0) s.pset(x, y + 1, if ((x / 12 + t / 30) % 2 == 0) 0 else 1)
        }
    }

    // ---------------- SCHOOL HALLWAY ----------------
    private fun school(s: Scr, t: Int) {
        s.clear(0)
        // ceiling
        s.rect(0, 0, 160, 10, 1)
        for (x in 10 until 160 step 40) { s.rect(x, 2, 20, 3, 0); s.frame(x - 1, 1, 22, 5, 2) }
        // back wall with door
        s.rect(60, 10, 40, 60, 1); s.rect(70, 30, 20, 40, 2); s.frame(70, 30, 20, 40, 3); s.rect(74, 34, 12, 10, 0)
        s.text("EXIT", 70, 14, 3)
        // lockers left / right
        for (i in 0 until 6) {
            val x = i * 10
            s.rect(x, 18, 9, 60, 2); s.frame(x, 18, 9, 60, 3)
            for (v in 0 until 3) s.hline(x + 2, 22 + v * 2, 5, 3)
            s.pset(x + 7, 48, 0)
            val x2 = 100 + i * 10
            s.rect(x2 + 1, 18, 9, 60, 2); s.frame(x2 + 1, 18, 9, 60, 3)
            for (v in 0 until 3) s.hline(x2 + 3, 22 + v * 2, 5, 3)
            s.pset(x2 + 3, 48, 0)
        }
        // bulletin board on the door wall
        s.rect(62, 46, 6, 10, 0); s.rect(92, 44, 6, 8, 0)
        // floor with tiles
        s.rect(0, 78, 160, 66, 1)
        for (y in 78 until 144 step 6) s.hline(0, y, 160, 2)
        for (k in -8..8) s.line(80 + k * 6, 78, 80 + k * 24, 144, 2)
        // shine on floor
        val sh = (t / 4) % 160
        s.hline(sh, 90, 6, 0); s.hline(sh + 3, 96, 4, 0)
    }

    // ---------------- ELIJAH'S ROOM ----------------
    private fun room(s: Scr, t: Int) {
        s.clear(1)
        s.sparse(0, 0, 160, 76, 1, 0)
        // window at night
        s.rect(100, 8, 44, 34, 3); s.frame(98, 6, 48, 38, 2); s.vline(122, 8, 34, 2); s.hline(100, 25, 44, 2)
        s.pset(108, 14, 0); s.pset(134, 12, 0); s.pset(116, 32, 1); s.disc(138, 34, 3, 0)
        // poster
        s.rect(14, 10, 30, 38, 0); s.frame(14, 10, 30, 38, 3)
        s.text("BRAVE", 16, 14, 3); s.text("MODE", 18, 23, 3); s.text("♪♪", 21, 34, 2)
        // desk with keyboard + handheld + headphones
        s.rect(4, 70, 90, 4, 3); s.rect(4, 74, 90, 2, 2)
        s.rect(8, 76, 4, 30, 3); s.rect(86, 76, 4, 30, 3)
        s.rect(10, 62, 46, 8, 3); for (k in 0 until 11) s.rect(12 + k * 4, 64, 3, 2, 0)
        s.rect(62, 56, 14, 14, 2); s.frame(62, 56, 14, 14, 3); s.rect(65, 58, 8, 6, 0)
        if ((t / 20) % 2 == 0) s.pset(67, 60, 3)
        s.ring(84, 62, 5, 3)
        // bed
        s.rect(100, 70, 58, 30, 2); s.rect(100, 66, 58, 6, 0); s.frame(100, 66, 58, 34, 3); s.rect(104, 62, 18, 8, 0); s.frame(104, 62, 18, 8, 3)
        // floor
        s.rect(0, 100, 160, 44, 2)
        for (x in 0 until 160 step 16) s.vline(x, 100, 44, 3)
    }

    // ---------------- KENJI'S RAMEN ----------------
    private fun ramen(s: Scr, t: Int) {
        s.clear(2)
        // noren curtains
        for (i in 0 until 5) {
            val x = 10 + i * 28
            s.rect(x, 0, 26, 24, 0); s.frame(x, 0, 26, 24, 3)
        }
        s.text("KENJI'S", 50, 8, 3)
        // paper lanterns
        for (lx in listOf(16, 144)) {
            val sw = if ((t / 30) % 2 == 0) 0 else 1
            s.vline(lx, 24, 6, 3); s.rect(lx - 5 + sw, 30, 11, 14, 0); s.frame(lx - 5 + sw, 30, 11, 14, 3)
            s.hline(lx - 4 + sw, 35, 9, 1); s.hline(lx - 4 + sw, 39, 9, 1)
        }
        // shelf with bowls
        s.rect(30, 34, 100, 2, 3)
        for (k in 0 until 6) { s.rect(34 + k * 16, 28, 10, 6, 0); s.frame(34 + k * 16, 28, 10, 6, 3) }
        // steaming pot
        s.rect(60, 46, 40, 20, 3); s.rect(62, 48, 36, 4, 1)
        for (k in 0 until 3) { val o = (t / 10 + k * 4) % 12; s.pset(68 + k * 10, 44 - o, 0); s.pset(69 + k * 10, 42 - o, 0) }
        // counter
        s.rect(0, 70, 160, 4, 3); s.rect(0, 74, 160, 70, 1)
        for (x in 0 until 160 step 12) s.vline(x, 74, 70, 2)
        // two bowls on the counter
        for (bx in listOf(40, 104)) {
            s.rect(bx, 64, 18, 6, 0); s.frame(bx, 64, 18, 6, 3); s.hline(bx + 2, 65, 14, 1)
            s.line(bx + 12, 54, bx + 16, 64, 3); s.line(bx + 14, 54, bx + 17, 63, 3)
        }
    }

    // ---------------- OPEN-MIC STAGE ----------------
    private fun stage(s: Scr, t: Int) {
        s.clear(3)
        // spotlight cone
        for (y in 0 until 96) { val w = 6 + y / 2; s.dither(80 - w, y, w * 2, 1, 2, 3) }
        s.rect(40, 84, 80, 4, 1)
        // stage
        s.rect(0, 88, 160, 56, 2); s.hline(0, 88, 160, 0)
        for (x in 0 until 160 step 12) s.vline(x, 89, 55, 3)
        // mic stand
        s.vline(80, 54, 34, 1); s.rect(78, 52, 5, 4, 0); s.hline(74, 87, 13, 1)
        // speakers
        for (sx in listOf(8, 132)) {
            s.rect(sx, 50, 20, 38, 2); s.frame(sx, 50, 20, 38, 1)
            val pulse = if ((t / 8) % 2 == 0) 6 else 5
            s.ring(sx + 10, 62, pulse, 1); s.disc(sx + 10, 78, 3, 1)
        }
        // crowd silhouettes
        for (i in 0 until 10) { val x = i * 17 + 4; val bob = if ((t / 15 + i) % 2 == 0) 0 else 1; s.disc(x, 124 + bob, 6, 3); s.rect(x - 8, 130 + bob, 16, 14, 3) }
        s.textC("OPEN MIC NIGHT", 80, 14, 1)
    }

    // ---------------- TRAIN STATION ----------------
    private fun station(s: Scr, t: Int) {
        s.clear(0)
        s.sparse(0, 0, 160, 40, 0, 1)
        s.rect(40, 12, 80, 11, 3); s.textC("MAPLE BAY STN", 80, 14, 0)
        // train
        val tx = 0
        s.rect(tx, 30, 160, 40, 2); s.frame(tx, 30, 160, 40, 3)
        for (k in 0 until 6) { s.rect(tx + 6 + k * 26, 36, 18, 14, 1); s.frame(tx + 6 + k * 26, 36, 18, 14, 3) }
        s.hline(0, 56, 160, 0); s.hline(0, 58, 160, 3)
        // platform
        s.rect(0, 70, 160, 74, 1); s.hline(0, 70, 160, 3)
        for (x in 0 until 160 step 4) s.pset(x, 74, 3)
        for (y in 78 until 144 step 8) s.hline(0, y, 160, 2)
        // clock
        s.disc(146, 18, 7, 0); s.ring(146, 18, 7, 3); s.line(146, 18, 146, 13, 3); s.line(146, 18, 150, 18, 3)
        if ((t / 60) % 2 == 0) s.pset(146, 18, 2)
    }

    // ================= CG SCENES (gallery) =================
    data class CG(val id: String, val title: String, val bg: String)
    val CGS = listOf(
        CG("first_meet", "First Meeting", "cafe"),
        CG("umbrella", "Shared Umbrella", "park"),
        CG("ramen_night", "Midnight Ramen", "ramen"),
        CG("high_score", "New High Score", "arcade"),
        CG("fountain_song", "The Fountain Song", "sunset"),
        CG("end_best", "END: Player 2", "rooftop"),
        CG("end_secret", "END: New Game+", "arcade"),
        CG("end_distance", "END: Save File", "station"),
        CG("end_friends", "END: Co-op Mode", "arcade"),
        CG("end_missed", "END: Missed Connection", "rooftop"),
    )

    private fun dim(s: Scr) {
        // pull everything one shade toward light for a soft backdrop
        for (i in s.px.indices) if (s.px[i] > 0 && ((i % 160 + i / 160) and 1) == 0) s.px[i] = (s.px[i] - 1).toByte()
    }

    fun drawCG(s: Scr, id: String, t: Int) {
        val cg = CGS.firstOrNull { it.id == id } ?: CGS[0]
        draw(s, cg.bg, t)
        dim(s)
        val bob = if ((t / 30) % 2 == 0) 0 else 1
        fun big(who: String, e: String, x: Int, flip: Boolean = false) {
            val p = Art.portrait(who, e, false)
            if (flip) {
                for (j in 0 until p.h) for (i in 0 until p.w) {
                    val c = p.px[j * p.w + (p.w - 1 - i)].toInt()
                    if (c >= 0) s.rect(x + i * 2, 30 + j * 2 + bob, 2, 2, c)
                }
            } else s.blitScaled(p, x, 30 + bob, 2)
        }
        when (id) {
            "first_meet" -> { big("eli", "nervous", 4); big("reese", "surprised", 92); s.blitScaled(Art["item_tape"], 64, 56, 2) ; sparkles(s, t, 74, 50) }
            "umbrella" -> {
                rain(s, t)
                big("eli", "happy", 16); big("reese", "blush", 80)
                // umbrella
                for (y in 0 until 14) { val w = (y * 5); s.hline(80 - w / 2 - 6, 14 + y, w + 12, if (y == 13) 3 else 2) }
                s.vline(80, 26, 50, 3); s.hline(52, 27, 56, 3)
            }
            "ramen_night" -> { big("reese", "happy", 80); big("eli", "happy", 16); s.blitScaled(Art["item_ramen"], 64, 92, 2); steam(s, t, 80, 88) }
            "high_score" -> {
                big("eli", "determined", 4); big("reese", "happy", 92)
                s.window(36, 6, 88, 20); s.textC("NEW HIGH SCORE!", 80, 12, if ((t / 15) % 2 == 0) 3 else 2)
                hearts(s, t)
            }
            "fountain_song" -> { big("eli", "determined", 10); big("reese", "blush", 86); notes(s, t) }
            "end_best" -> { big("eli", "happy", 18); big("reese", "blush", 78); bigHeart(s, t, 80, 18); sparkles(s, t, 40, 20); sparkles(s, t, 120, 26) }
            "end_secret" -> {
                s.window(30, 4, 100, 24); s.textC("LANTERN FOX 2", 80, 8, 3); s.textC("music by ELI", 80, 17, 2)
                big("eli", "happy", 6); big("reese", "happy", 90); hearts(s, t)
            }
            "end_distance" -> { big("reese", "sad", 48); s.blit(Art["heart"], 120, 40 + bob); s.textC("see you soon", 80, 132, 3) }
            "end_friends" -> { big("eli", "happy", 10); big("reese", "happy", 86); s.window(50, 8, 60, 16); s.textC("CO-OP!", 80, 12, 3) }
            "end_missed" -> {
                big("eli", "nervous", 64)
                for (i in 0 until 12) s.pset((i * 37 + t / 3) % 160, (i * 23 + t) % 96, 1)
            }
        }
    }

    fun rain(s: Scr, t: Int) {
        for (i in 0 until 70) {
            val x = (i * 53 + t * 2) % 168 - 4
            val y = (i * 29 + t * 5) % 150 - 6
            s.line(x, y, x - 2, y + 5, if (i % 3 == 0) 0 else 1)
        }
    }
    fun sparkles(s: Scr, t: Int, x: Int, y: Int) {
        if ((t / 12) % 3 != 0) s.blit(Art["sparkle"], x + ((t / 36) % 3) * 3, y)
    }
    fun hearts(s: Scr, t: Int) {
        for (i in 0 until 6) { val y = 140 - ((t + i * 37) % 150); s.blit(Art["heart"], 6 + i * 26, y) }
    }
    fun notes(s: Scr, t: Int) {
        for (i in 0 until 5) { val y = 90 - ((t / 2 + i * 22) % 90); s.text("♪", 60 + i * 9 + ((t / 10 + i) % 3), y, 3) }
    }
    fun steam(s: Scr, t: Int, x: Int, y: Int) {
        for (k in 0 until 3) { val o = (t / 6 + k * 5) % 15; s.pset(x - 6 + k * 6 + (o % 2), y - o, 0) }
    }
    fun bigHeart(s: Scr, t: Int, cx: Int, cy: Int) {
        val k = if ((t / 20) % 2 == 0) 3 else 2
        val h = Art["heart"]
        s.blitScaled(h, cx - h.w * k / 2 + 1, cy - h.h * k / 2, k)
    }
}
