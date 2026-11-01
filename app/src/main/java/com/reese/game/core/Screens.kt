package com.reese.game.core

import kotlin.math.sin

/** Simple vertical menu inside a retro window. With wrapW > 0, long items wrap onto two lines. */
class Menu(val g: Game, var items: List<String>, var x: Int, var y: Int, var w: Int, val rowH: Int = 11, val wrapW: Int = 0) {
    var sel = 0
    var enabled: (Int) -> Boolean = { true }
    private fun lines(i: Int) = if (wrapW > 0) Font.wrap(items[i], wrapW).take(2) else listOf(items[i])
    private fun rowHeight(i: Int) = if (wrapW > 0) lines(i).size * 9 + 3 else rowH
    private fun rowTop(i: Int): Int { var t = y + 5; for (j in 0 until i) t += rowHeight(j); return t }
    val h get() = (0 until items.size).sumOf { rowHeight(it) } + 8

    fun draw(s: Scr) {
        s.window(x, y, w, h)
        for (i in items.indices) {
            val ty = rowTop(i)
            if (i == sel && (g.t / 16) % 4 != 3) s.text("\u25b6", x + 5, ty, 3)
            lines(i).forEachIndexed { k, l -> s.text(l, x + 12, ty + k * 9, if (enabled(i)) 3 else 1) }
        }
    }
    /** Returns the chosen index on A, or null. */
    fun press(b: Btn): Int? {
        when (b) {
            Btn.UP -> { sel = (sel - 1 + items.size) % items.size; g.sfx("move") }
            Btn.DOWN -> { sel = (sel + 1) % items.size; g.sfx("move") }
            Btn.A -> { return if (enabled(sel)) { g.sfx("select"); sel } else { g.sfx("back"); null } }
            else -> {}
        }
        return null
    }
    fun tap(tx: Int, ty: Int): Int? {
        if (tx < x || tx >= x + w) return null
        val r = items.indices.firstOrNull { ty >= rowTop(it) - 2 && ty < rowTop(it) + rowHeight(it) - 1 } ?: return null
        if (r != sel) { sel = r; g.sfx("move") }
        return if (enabled(r)) { g.sfx("select"); r } else { g.sfx("back"); null }
    }
}

fun drawHearts(s: Scr, aff: Int, x: Int, y: Int) {
    for (i in 0 until 5) {
        val v = aff - i * 20
        val spr = when { v >= 20 -> "hud_full"; v >= 10 -> "hud_half"; else -> "hud_empty" }
        s.blit(Art[spr], x + i * 8, y)
    }
}

// ======================================================================
// TITLE
// ======================================================================
class TitleScreen(g: Game) : Screen(g) {
    private val menu = Menu(g, listOf("NEW GAME", "CONTINUE", "LOAD", "GALLERY", "SETTINGS"), 44, 64, 72)
    private var t0 = 0
    init { menu.enabled = { i -> (i != 1 && i != 2) || g.hasAnySave() } ; if (g.hasAnySave()) menu.sel = 1 }

    override fun update() { t0++; g.music("title") }

    override fun draw(s: Scr) {
        s.clear(0)
        // falling hearts backdrop
        for (i in 0 until 9) {
            val y = ((t0 / 2 + i * 41) % 170) - 12
            val x = (i * 37 + (sin((t0 + i * 20) / 30.0) * 4).toInt()) % 160
            s.blit(Art["hud_empty"], x, y, shade = byteArrayOf(0, 1, 1, 1))
        }
        s.hline(0, 140, 160, 1); s.dither(0, 141, 160, 3, 1, 2)
        // credit line
        s.textC("ELIJAH CHRISTAN BREEDLOVE in", 80, 6, 2)
        // animated logo: letters drop in, then bob
        val word = "REESE"
        var x = 80 - (Font.width(word) * 3) / 2
        for ((i, c) in word.withIndex()) {
            val drop = maxOf(0, 60 - (t0 - i * 8) * 3)
            val bob = (sin((t0 + i * 10) / 12.0) * 2).toInt()
            val y = 22 - drop + bob
            s.textScaled(c.toString(), x + 2, y + 2, 3, 2)
            s.textScaled(c.toString(), x, y, 3, 3)
            x += Font.advance(c) * 3
        }
        // heart pops after the letters land
        if (t0 > 70) {
            val k = if ((t0 / 20) % 2 == 0) 2 else 1
            val h = Art["heart"]
            s.blitScaled(h, 136 - h.w * k / 2, 26 - h.h * k / 2 + 6, k)
        }
        s.textC("a love story in four shades", 80, 50, 2)
        if (t0 > 40) menu.draw(s)
        s.text("v1.0", 2, 134, 2)
        s.text("♥ 2026", 128, 134, 2)
    }

    override fun press(b: Btn) {
        if (t0 < 40) { t0 = 40; return }
        if (b == Btn.START && menu.sel == 0) { act(0); return }
        menu.press(b)?.let { act(it) }
    }
    override fun tap(x: Int, y: Int) { if (t0 < 40) { t0 = 40; return }; menu.tap(x, y)?.let { act(it) } }
    override fun back() { g.pf.exit() }

    private fun act(i: Int) {
        when (i) {
            0 -> { g.sfx("start"); g.newGame() }
            1 -> { val s = g.latestSlot(); if (s != null) g.load(s) }
            2 -> g.push(SaveLoadScreen(g, false))
            3 -> g.push(GalleryScreen(g))
            4 -> g.push(SettingsScreen(g))
        }
    }
}

// ======================================================================
// STORY SCENE (visual novel)
// ======================================================================
class SceneScreen(g: Game) : Screen(g) {
    private var ev: Ev? = null
    private var pages: List<List<String>> = emptyList()
    private var page = 0
    private var shown = 0f
    private var who = ""
    private var eliExpr = "neutral"
    private var eliX = -40f
    private var reeseX = 64f
    private var shakeT = 0
    private var flashT = 0
    private var doneT = 0
    private var pulled = false
    private var giftMenu: Menu? = null
    private var choiceMenu: Menu? = null
    private var choiceMap: List<Int> = emptyList()
    private var titleT = 0
    private var heartAnim = 0

    private fun pull() {
        while (true) {
            val e = g.eng.next()
            handleFx()
            g.flushUnlocks()
            ev = e
            when (e) {
                is Ev.SceneEnd -> { ev = null; g.sceneEnded(); return }
                is Ev.Say -> {
                    who = e.who
                    if (e.who == "eli" && e.expr != null) eliExpr = e.expr
                    if (e.who != "eli") { /* keep */ }
                    val lines = Font.wrap(e.text, 146)
                    pages = lines.chunked(3)
                    page = 0; shown = 0f; doneT = 0
                    g.backlog.add(Pair(speakerName(e.who), e.text))
                    if (g.backlog.size > 200) g.backlog.removeAt(0)
                    return
                }
                is Ev.Choice -> {
                    g.skip = false
                    choiceMap = e.options.map { it.first }
                    val w = 152
                    val m = Menu(g, e.options.map { it.second }, 4, 0, w, wrapW = 136)
                    m.y = 95 - m.h
                    choiceMenu = m
                    return
                }
                is Ev.GiftPrompt -> {
                    g.skip = false
                    val names = e.items.map { g.story.item(it)?.name ?: it } + "Not now"
                    val m = Menu(g, names, 60, 0, 96); m.y = 95 - m.h
                    giftMenu = m
                    return
                }
                is Ev.Minigame -> {
                    g.skip = false
                    g.push(MinigameScreen(g) { score -> g.eng.minigameDone(score); pull() })
                    return
                }
                is Ev.Cg -> { titleT = 0; g.sfx("gift"); return }
                is Ev.Title -> { titleT = 0; g.sfx("day"); return }
                is Ev.Ending -> { g.skip = false; g.auto = false; g.replace(EndingScreen(g, e.id)); return }
            }
        }
    }

    private fun clip(s: String, w: Int): String {
        if (Font.width(s) <= w) return s
        var t = s
        while (t.isNotEmpty() && Font.width("$t..") > w) t = t.dropLast(1)
        return "$t.."
    }

    private fun handleFx() {
        for (f in g.eng.fx) when {
            f == "heart_up" -> { g.sfx("heart_up"); heartAnim = 40 }
            f == "heart_down" -> { g.sfx("heart_down"); heartAnim = -40 }
            f == "shake" -> { shakeT = 24; g.sfx("shake") }
            f == "flash" -> flashT = 10
            f.startsWith("sfx:") -> g.sfx(f.substring(4))
        }
        g.eng.fx.clear()
    }

    private fun speakerName(w: String) = when (w) {
        "eli" -> g.story.playerName
        "reese" -> "Reese"
        "" -> ""
        else -> w
    }

    private val typing get() = ev is Ev.Say && shown < pages.getOrNull(page)?.sumOf { it.length + 1 } ?: 0

    override fun update() {
        if (!pulled) { pulled = true; pull(); if (g.top !== this) return }
        g.music(g.st.music)
        titleT++
        if (heartAnim > 0) heartAnim-- else if (heartAnim < 0) heartAnim++
        if (shakeT > 0) shakeT--
        if (flashT > 0) flashT--
        val e = ev
        val speaking = e is Ev.Say && e.who == "eli"
        eliX += ((if (speaking) 12f else -40f) - eliX) * 0.25f
        reeseX += ((if (speaking) 100f else 64f) - reeseX) * 0.2f

        val fast = g.skip || g.held[Btn.B.ordinal]
        if (e is Ev.Say) {
            val total = pages[page].sumOf { it.length + 1 }
            if (shown < total) {
                val before = shown.toInt()
                shown += if (fast) 999f else when (g.settings.textSpeed) { 0 -> 0.5f; 1 -> 1f; 2 -> 2.5f; else -> 999f }
                val after = minOf(shown.toInt(), total)
                if (!fast && after / 3 != before / 3) {
                    g.sfx(when (e.who) { "eli" -> "blip_eli"; "reese" -> "blip_reese"; else -> "blip_other" })
                }
            } else {
                doneT++
                val wait = if (fast) 3 else if (g.auto) 50 + g.settings.autoDelay * 50 else -1
                if (wait > 0 && doneT >= wait) advance()
            }
        } else if (fast && (e is Ev.Title || e is Ev.Cg) && titleT > 20) advance()
    }

    private fun advance() {
        when (val e = ev) {
            is Ev.Say -> {
                if (typing) { shown = 9999f; return }
                if (page < pages.size - 1) { page++; shown = 0f; doneT = 0; return }
                g.eng.advance(); pull()
            }
            is Ev.Title, is Ev.Cg -> { if (titleT > 15) { g.eng.advance(); pull() } }
            else -> {}
        }
    }

    override fun press(b: Btn) {
        when (b) {
            Btn.START -> { g.sfx("select"); g.push(PauseScreen(g, true)); return }
            Btn.SELECT -> { g.auto = !g.auto; g.showToast(if (g.auto) "AUTO ON" else "AUTO OFF"); return }
            else -> {}
        }
        choiceMenu?.let { m -> m.press(b)?.let { pick(it) }; return }
        giftMenu?.let { m -> m.press(b)?.let { giftPick(it) }; if (b == Btn.B) giftPick(m.items.size - 1); return }
        if (b == Btn.A) advance()
    }

    override fun tap(x: Int, y: Int) {
        choiceMenu?.let { m -> m.tap(x, y)?.let { pick(it) }; return }
        giftMenu?.let { m -> m.tap(x, y)?.let { giftPick(it) }; return }
        advance()
    }

    private fun pick(i: Int) {
        val m = choiceMenu ?: return
        choiceMenu = null
        g.backlog.add(Pair("▶", m.items[i]))
        g.eng.choose(choiceMap[i])
        pull()
    }

    private fun giftPick(i: Int) {
        val m = giftMenu ?: return
        giftMenu = null
        val items = (ev as? Ev.GiftPrompt)?.items ?: emptyList()
        if (i < items.size) { g.sfx("gift"); g.eng.gift(items[i]) } else g.eng.gift(null)
        pull()
    }

    /** Called before saving so the save resumes on this exact line. */
    fun canSave() = ev != null && ev !is Ev.Ending

    override fun draw(s: Scr) {
        val e = ev
        if (e is Ev.Title) { drawTitle(s, e); return }
        if (e is Ev.Cg) {
            Backgrounds.drawCG(s, e.id, g.t)
            val name = Backgrounds.CGS.firstOrNull { it.id == e.id }?.title ?: ""
            s.rect(0, 132, 160, 12, 3); s.textC(name, 80, 134, 0)
            if (titleT > 30 && (g.t / 20) % 2 == 0) s.text("▼", 150, 134, 0)
            return
        }
        Backgrounds.draw(s, g.st.bg, g.t)
        if (g.st.rain) Backgrounds.rain(s, g.t)
        // Reese
        g.st.reese?.let { ex ->
            val blink = (g.t % 220) < 7
            val bounce = if (heartAnim > 20 && (heartAnim / 4) % 2 == 0) -1 else 0
            s.blit(Art.portrait("reese", ex, blink), reeseX.toInt(), 48 + bounce)
        }
        // Elijah beside his lines
        if (eliX > -32) s.blit(Art.portrait("eli", eliExpr, (g.t % 260) in 100..106), eliX.toInt(), 48)
        // shake the scene view
        if (shakeT > 0) shiftRows(s, ((shakeT % 4) - 2) * 2)
        // HUD
        s.rect(0, 0, 34, 11, 0); s.frame(0, 0, 34, 11, 3)
        s.text("DAY ${g.st.day}", 3, 2, 3)
        val hx = 118
        s.rect(hx - 2, 0, 44, 10, 0); s.frame(hx - 2, 0, 44, 10, 3)
        drawHearts(s, g.st.aff, hx, 2)
        if (heartAnim != 0) {
            val up = heartAnim > 0
            val k = Math.abs(heartAnim)
            s.text(if (up) "♥+" else "♥-", hx + 14, 12 + (40 - k) / 6, if (up) 3 else 2)
        }
        if (g.auto) s.text("AUTO", 38, 2, if ((g.t / 30) % 2 == 0) 3 else 2)
        if (g.skip || g.held[Btn.B.ordinal]) s.text("SKIP▶▶", 38, 2, 3)
        // text box
        drawBox(s, e)
        choiceMenu?.draw(s)
        giftMenu?.let { s.window(4, it.y - 13, 54, 13); s.text("Give?", 9, it.y - 10, 3); it.draw(s) }
        if (flashT > 0) s.rect(0, 0, 160, 144, 0)
    }

    private fun shiftRows(s: Scr, dx: Int) {
        for (y in 0 until 96) {
            val row = ByteArray(160) { x -> s.px[y * 160 + ((x - dx).coerceIn(0, 159))] }
            System.arraycopy(row, 0, s.px, y * 160, 160)
        }
    }

    private fun drawBox(s: Scr, e: Ev?) {
        s.window(0, 96, 160, 48)
        val say = e as? Ev.Say
        val name = if (say != null) speakerName(say.who) else ""
        if (name.isNotEmpty()) {
            val w = Font.width(name) + 10
            s.rect(4, 86, w, 11, 0); s.frame(4, 86, w, 11, 3)
            s.text(name, 9, 88, 3)
        }
        if (say != null) {
            var left = shown.toInt()
            for ((i, line) in pages[page].withIndex()) {
                if (left <= 0) break
                val part = if (left >= line.length) line else line.substring(0, left)
                s.text(part, 7, 101 + i * Font.LINE, 3)
                left -= line.length + 1
            }
            if (!typing && (g.t / 18) % 2 == 0) s.text("▼", 149, 134, 3)
        } else if (e is Ev.Choice || e is Ev.GiftPrompt) {
            // keep the last line visible under the menu
            val last = g.backlog.lastOrNull { it.first != "▶" }
            if (last != null) Font.wrap(last.second, 146).takeLast(3).forEachIndexed { i, l -> s.text(l, 7, 101 + i * Font.LINE, 2) }
        }
    }

    private fun drawTitle(s: Scr, e: Ev.Title) {
        s.clear(3)
        val lines = Font.wrap(e.text, 150)
        val y0 = 60 - lines.size * 6
        lines.forEachIndexed { i, l -> s.textC(l, 80, y0 + i * 12, 0) }
        if (e.sub.isNotEmpty()) Font.wrap(e.sub, 150).forEachIndexed { i, l -> s.textC(l, 80, y0 + lines.size * 12 + 8 + i * 10, 1) }
        for (i in 0 until 3) s.blit(Art["hud_full"], 66 + i * 10, 120, shade = byteArrayOf(0, 1, 1, 2))
        if (titleT > 30 && (g.t / 20) % 2 == 0) s.text("▼", 150, 132, 1)
    }
}

// ======================================================================
// TOWN MAP
// ======================================================================
class MapScreen(g: Game) : Screen(g) {
    data class Node(val id: String, val x: Int, val y: Int, val icon: String)
    private val nodes = listOf(
        Node("school", 26, 22, "map_school"), Node("park", 80, 20, "map_park"), Node("arcade", 134, 22, "map_arcade"),
        Node("cafe", 26, 74, "map_cafe"), Node("shop", 80, 76, "map_shop"), Node("rooftop", 134, 74, "map_rooftop"),
    )
    private var sel = 4
    private var px = 80f; private var py = 50f
    private var path = ArrayList<Pair<Int, Int>>()
    private var goal: Node? = null
    private var dir = 0; private var walkT = 0
    private var banner = 150
    private val day get() = g.story.days[g.st.day - 1]

    private fun open(n: Node) = n.id == "shop" || day.spots.containsKey(n.id)
    private fun label(n: Node) = if (n.id == "shop") "Pixel Mart" else g.story.locations[n.id] ?: n.id

    override fun update() {
        g.music("map")
        if (banner > 0) banner--
        if (path.isNotEmpty()) {
            val (tx, ty) = path[0]
            val dx = tx - px; val dy = ty - py
            val d = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (d < 1.6f) { px = tx.toFloat(); py = ty.toFloat(); path.removeAt(0) }
            else {
                px += dx / d * 1.5f; py += dy / d * 1.5f
                dir = if (Math.abs(dx) > Math.abs(dy)) (if (dx > 0) 2 else 3) else (if (dy > 0) 0 else 1)
                walkT++
            }
            if (path.isEmpty()) arrive()
        }
    }

    private fun arrive() {
        val n = goal ?: return
        goal = null
        if (n.id == "shop") { g.sfx("door"); g.push(ShopScreen(g)) }
        else { g.sfx("door"); g.startScene(n.id) }
    }

    private fun go(n: Node) {
        if (!open(n)) { g.sfx("back"); g.showToast("${label(n)}: closed today"); return }
        g.sfx("select")
        goal = n
        path.clear()
        val roadY = 50
        if (Math.abs(px - n.x) > 2) { path.add(Pair(px.toInt(), roadY)); path.add(Pair(n.x, roadY)) }
        path.add(Pair(n.x, n.y + 12))
        if (path.size > 0 && path[0] == Pair(px.toInt(), py.toInt())) path.removeAt(0)
    }

    override fun draw(s: Scr) {
        // grass + roads
        s.clear(0)
        for (y in 0 until 102 step 6) for (x in (y % 12) / 2 until 160 step 12) s.pset(x, y, 1)
        s.rect(0, 47, 160, 7, 1); for (x in 0 until 160 step 8) s.hline(x, 50, 4, 2)
        for (n in nodes) s.rect(n.x - 2, minOf(n.y + 8, 50), 5, Math.abs(50 - n.y - 8) + 4, 1)
        // river + trees
        s.dither(0, 92, 160, 10, 1, 2); s.hline(0, 91, 160, 2)
        for ((tx, ty) in listOf(52 to 34, 108 to 34, 52 to 66, 108 to 64, 6 to 40, 152 to 40, 66 to 84, 96 to 86)) { s.disc(tx, ty, 4, 2); s.pset(tx, ty + 5, 3) }
        // locations
        for ((i, n) in nodes.withIndex()) {
            val o = open(n)
            s.blit(Art[n.icon], n.x - 8, n.y - 8, shade = if (o) null else byteArrayOf(0, 1, 1, 2))
            if (!o) { s.line(n.x - 6, n.y - 6, n.x + 6, n.y + 6, 3); s.line(n.x + 6, n.y - 6, n.x - 6, n.y + 6, 3) }
            if (i == sel && path.isEmpty() && (g.t / 15) % 2 == 0) s.text("▼", n.x - 2, n.y - 18, 3)
        }
        // Elijah
        val frame = (walkT / 8) % 2
        val (spr, flip) = when (dir) { 1 -> "eli_walk_u$frame" to false; 2 -> "eli_walk_s$frame" to false; 3 -> "eli_walk_s$frame" to true; else -> "eli_walk_d$frame" to false }
        s.blit(Art[spr], px.toInt() - 8, py.toInt() - 14, flip)
        // info panel
        s.window(0, 102, 160, 42)
        val n = nodes[sel]
        s.text(label(n), 7, 106, 3)
        s.text(if (open(n)) (if (n.id == "shop") "GIFTS" else "OPEN") else "CLOSED", 116, 106, if (open(n)) 3 else 1)
        s.text("${day.name}  DAY ${g.st.day}/7", 7, 118, 2)
        s.text("$${g.st.coins}", 7, 129, 3)
        drawHearts(s, g.st.aff, 112, 130)
        s.text("A: go", 48, 129, 2)
        if (banner > 0) {
            val y = if (banner > 130) (150 - banner) * 2 - 30 else if (banner < 20) -30 + banner * 2 else 10
            s.window(20, y, 120, 26)
            s.textC("DAY ${g.st.day}", 80, y + 5, 3); s.textC(day.name, 80, y + 15, 2)
        }
    }

    private fun moveSel(b: Btn) {
        val c = nodes[sel]
        val cand = nodes.withIndex().filter { (_, n) ->
            when (b) { Btn.LEFT -> n.x < c.x - 4 && Math.abs(n.y - c.y) < 20; Btn.RIGHT -> n.x > c.x + 4 && Math.abs(n.y - c.y) < 20
                Btn.UP -> n.y < c.y - 4 && Math.abs(n.x - c.x) < 20; Btn.DOWN -> n.y > c.y + 4 && Math.abs(n.x - c.x) < 20; else -> false }
        }.minByOrNull { (_, n) -> Math.abs(n.x - c.x) + Math.abs(n.y - c.y) }
        if (cand != null) { sel = cand.index; g.sfx("move") }
    }

    override fun press(b: Btn) {
        if (path.isNotEmpty()) return
        when (b) {
            Btn.UP, Btn.DOWN, Btn.LEFT, Btn.RIGHT -> moveSel(b)
            Btn.A -> go(nodes[sel])
            Btn.START -> { g.sfx("select"); g.push(PauseScreen(g, false)) }
            else -> {}
        }
    }

    override fun tap(x: Int, y: Int) {
        if (path.isNotEmpty()) return
        val hit = nodes.indexOfFirst { Math.abs(it.x - x) < 12 && Math.abs(it.y - y) < 12 }
        if (hit >= 0) { if (hit == sel) go(nodes[hit]) else { sel = hit; g.sfx("move") } }
        else if (y >= 102) go(nodes[sel])
    }
}

// ======================================================================
// SHOP
// ======================================================================
class ShopScreen(g: Game) : Screen(g) {
    private var sel = 0
    private val items get() = g.story.items
    private var msg = ""

    override fun update() { g.music("map") }

    private fun state(id: String) = when { id in g.st.given -> "GIVEN"; id in g.st.inv -> "OWNED"; else -> null }

    override fun draw(s: Scr) {
        s.clear(0)
        s.rect(0, 0, 160, 13, 3); s.text("PIXEL MART", 4, 3, 0); s.text("$${g.st.coins}", 74, 3, 0); s.text("B:exit", 124, 3, 1)
        for ((i, it) in items.withIndex()) {
            val y = 14 + i * 19
            if (i == sel) { s.rect(0, y, 160, 19, 1) }
            s.blit(Art["item_${it.id}"], 4, y + 1)
            s.text(it.name, 24, y + 2, 3)
            val st = state(it.id)
            s.text(st ?: "$${it.price}", 24, y + 10, if (st != null) 2 else 3)
        }
        s.window(0, 110, 160, 34)
        val text = if (msg.isNotEmpty()) msg else items[sel].desc
        Font.wrap(text, 148).take(2).forEachIndexed { i, l -> s.text(l, 6, 115 + i * 10, 3) }
    }

    private fun buy() {
        val it = items[sel]
        when {
            state(it.id) != null -> { g.sfx("back"); msg = "Sold out! (one per customer)" }
            g.st.coins < it.price -> { g.sfx("back"); msg = "Not enough coins. Need $${it.price}." }
            else -> { g.st.coins -= it.price; g.st.inv.add(it.id); g.sfx("buy"); msg = "Bought ${it.name}! Give it to Reese after a date." }
        }
    }

    override fun press(b: Btn) {
        when (b) {
            Btn.UP -> { sel = (sel - 1 + items.size) % items.size; msg = ""; g.sfx("move") }
            Btn.DOWN -> { sel = (sel + 1) % items.size; msg = ""; g.sfx("move") }
            Btn.A -> buy()
            Btn.B, Btn.START -> { g.sfx("back"); g.pop() }
            else -> {}
        }
    }
    override fun back() = press(Btn.B)
    override fun tap(x: Int, y: Int) {
        if (y in 14 until 14 + items.size * 19) {
            val i = (y - 14) / 19
            if (i == sel) buy() else { sel = i; msg = ""; g.sfx("move") }
        } else if (y < 13 && x >= 120) press(Btn.B)
    }
}

// ======================================================================
// MINI-GAME: HEART CATCH
// ======================================================================
class MinigameScreen(g: Game, val done: (Int) -> Unit) : Screen(g) {
    private class Drop(var x: Float, var y: Float, val vy: Float, val kind: Int)
    private val drops = ArrayList<Drop>()
    private var phase = 0     // 0 countdown, 1 play, 2 results
    private var timer = 180
    private var score = 0
    private var bx = 80f
    private var dragX = -1
    private var rng = java.util.Random(g.t.toLong())
    private var cheer = 0
    private var combo = 0
    private val LENGTH = 30 * 60

    override fun update() {
        g.music("minigame")
        if (cheer > 0) cheer--
        when (phase) {
            0 -> { timer--; if (timer % 60 == 0 && timer > 0) g.sfx("move"); if (timer <= 0) { phase = 1; timer = LENGTH; g.sfx("select") } }
            1 -> play()
            2 -> timer++
        }
    }

    private fun play() {
        timer--
        val speed = 2.2f
        if (g.held[Btn.LEFT.ordinal]) bx -= speed
        if (g.held[Btn.RIGHT.ordinal]) bx += speed
        if (dragX >= 0) bx += ((dragX - bx).coerceIn(-3f, 3f))
        bx = bx.coerceIn(30f, 152f)
        val elapsed = LENGTH - timer
        val every = maxOf(12, 30 - elapsed / 120)
        if (elapsed % every == 0) {
            val r = rng.nextInt(100)
            val kind = when { r < 70 -> 0; r < 92 -> 1; else -> 2 }
            drops.add(Drop(30f + rng.nextInt(120), -8f, 0.7f + rng.nextFloat() * 0.8f + elapsed / 1800f, kind))
        }
        val it = drops.iterator()
        while (it.hasNext()) {
            val d = it.next()
            d.y += d.vy
            if (d.y + 6 >= 122 && d.y < 128 && Math.abs(d.x + 4 - bx) < 10) {
                when (d.kind) {
                    0 -> { score += 1; combo++; g.sfx("catch") }
                    1 -> { score = maxOf(0, score - 2); combo = 0; g.sfx("bad") }
                    else -> { score += 3; combo++; g.sfx("heart_up"); cheer = 40 }
                }
                if (combo > 0 && combo % 10 == 0) cheer = 60
                it.remove()
            } else if (d.y > 144) { if (d.kind != 1) combo = 0; it.remove() }
        }
        if (timer <= 0) { phase = 2; timer = 0; g.sfx(if (score >= 30) "heart_up" else "select") }
    }

    override fun draw(s: Scr) {
        s.clear(3)
        // playfield frame (the arcade cabinet screen)
        s.rect(26, 0, 134, 144, 3)
        for (i in 0 until 30) s.pset(28 + (i * 47) % 130, (i * 31 + g.t / 4) % 130, 2)
        s.vline(25, 0, 144, 1); s.vline(24, 0, 144, 2)
        s.hline(26, 130, 134, 1)
        // sidebar: player 2 (Reese) cheering
        s.rect(0, 0, 24, 144, 2)
        s.text("P2", 6, 4, 0)
        s.blit(Art[if (cheer > 0 && (g.t / 8) % 2 == 0) "reese_chibi1" else "reese_chibi0"], 4, 16)
        if (cheer > 0) { s.window(0, 34, 24, 13); s.text("GO!", 4, 37, 3) }
        s.text("SC", 4, 60, 0); s.text("$score", 4, 70, 0)
        s.text("T", 4, 88, 0); s.text("${maxOf(0, timer / 60)}", 4, 98, 0)
        s.text("BEST", 1, 114, 1); s.text("${maxOf(g.st.best, score)}", 4, 124, 1)
        // drops
        for (d in drops) s.blit(Art[when (d.kind) { 0 -> "heart"; 1 -> "heart_broken"; else -> "sparkle" }], d.x.toInt(), d.y.toInt(),
            shade = if (d.kind == 0) byteArrayOf(0, 0, 1, 0) else if (d.kind == 2) byteArrayOf(0, 0, 0, 1) else byteArrayOf(1, 1, 1, 1))
        // basket + Elijah
        s.blit(Art["basket"], bx.toInt() - 7, 120, shade = byteArrayOf(0, 1, 1, 1))
        s.blit(Art["eli_walk_u${(g.t / 10) % 2}"], bx.toInt() - 8, 126, shade = byteArrayOf(1, 1, 0, 1))
        when (phase) {
            0 -> { s.window(54, 40, 80, 40); s.textC("HEART CATCH", 94, 46, 3); s.textScaled("${timer / 60 + 1}", 89, 58, 2, 3) }
            2 -> {
                s.window(34, 30, 120, 70)
                s.textC("TIME UP!", 94, 36, 3)
                s.textC("SCORE  $score", 94, 50, 3)
                s.textC("BEST  ${maxOf(g.st.best, score)}", 94, 62, 2)
                s.textC(when { score >= 30 -> "Reese: \"Whoa!!\""; score >= 15 -> "Reese: \"Not bad!\""; else -> "Reese: \"Rematch?\"" }, 94, 74, 3)
                if (timer > 40 && (g.t / 20) % 2 == 0) s.textC("A: continue", 94, 88, 2)
            }
        }
        if (phase == 1 && timer > LENGTH - 90) s.textC("◀ ▶ or drag!", 94, 60, 0)
    }

    private fun finish() { if (phase == 2 && timer > 40) { g.pop(); done(score) } }
    override fun press(b: Btn) { if (b == Btn.A) finish() else if (b == Btn.START && phase == 1) { /* no pause mid-round */ } }
    override fun back() {}
    override fun tap(x: Int, y: Int) { if (phase == 2) finish() else dragX = x }
    override fun drag(x: Int, y: Int) { dragX = x }
    override fun release() { dragX = -1 }
}

// ======================================================================
// PAUSE
// ======================================================================
class PauseScreen(g: Game, val inScene: Boolean) : Screen(g) {
    override val overlay = true
    private val menu = Menu(g, emptyList(), 30, 14, 100)
    init { refresh() }
    private fun refresh() {
        menu.items = listOf("RESUME", "SAVE", "LOAD", "BACKLOG", "AUTO: " + (if (g.auto) "ON" else "OFF"), "SKIP TO CHOICE", "SETTINGS", "QUIT TO TITLE")
        menu.enabled = { i -> inScene || i !in 3..5 }
    }
    override fun draw(s: Scr) {
        s.dither(0, 0, 160, 144, 3, -1)
        menu.draw(s)
        s.window(30, 112, 100, 22); s.textC("DAY ${g.st.day}  ♥${g.st.aff}  $${g.st.coins}", 80, 119, 3)
    }
    private fun act(i: Int) {
        when (i) {
            0 -> g.pop()
            1 -> g.push(SaveLoadScreen(g, true))
            2 -> g.push(SaveLoadScreen(g, false))
            3 -> g.push(BacklogScreen(g))
            4 -> { g.auto = !g.auto; refresh() }
            5 -> { g.skip = true; g.pop() }
            6 -> g.push(SettingsScreen(g))
            7 -> { g.skip = false; g.auto = false; g.replace(TitleScreen(g)) }
        }
    }
    override fun press(b: Btn) {
        if (b == Btn.START || b == Btn.B) { g.sfx("back"); g.pop(); return }
        menu.press(b)?.let { act(it) }
    }
    override fun back() = press(Btn.B)
    override fun tap(x: Int, y: Int) { val r = menu.tap(x, y); if (r != null) act(r) else if (y > 110 || x < 30 || x > 130) g.pop() }
}

// ======================================================================
// SAVE / LOAD
// ======================================================================
class SaveLoadScreen(g: Game, val saving: Boolean) : Screen(g) {
    private var sel = if (saving) 1 else 0
    private var msg = ""
    override fun draw(s: Scr) {
        s.clear(0)
        s.rect(0, 0, 160, 13, 3); s.text(if (saving) "SAVE GAME" else "LOAD GAME", 4, 3, 0)
        for (i in 0..3) {
            val y = 18 + i * 26
            val info = g.slotInfo(i)
            val disabled = (saving && i == 0) || (!saving && info == null)
            s.window(4, y, 152, 24)
            if (i == sel) s.text("▶", 8, y + 5, 3)
            s.text(if (i == 0) "AUTOSAVE" else "SLOT $i", 16, y + 5, if (disabled) 1 else 3)
            s.text(info ?: "- empty -", 16, y + 14, if (disabled) 1 else 2)
        }
        s.text(if (msg.isNotEmpty()) msg else "A: ${if (saving) "save" else "load"}   B: back", 8, 128, 2)
    }
    private fun act() {
        if (saving) {
            if (sel == 0) { g.sfx("back"); msg = "Autosave is automatic."; return }
            g.saveTo(sel, true); g.sfx("select"); msg = "Saved to slot $sel!"
        } else {
            if (g.slotInfo(sel) == null) { g.sfx("back"); return }
            g.sfx("select"); g.load(sel)
        }
    }
    override fun press(b: Btn) {
        when (b) {
            Btn.UP -> { sel = (sel + 3) % 4; g.sfx("move") }
            Btn.DOWN -> { sel = (sel + 1) % 4; g.sfx("move") }
            Btn.A -> act()
            Btn.B, Btn.START -> { g.sfx("back"); g.pop() }
            else -> {}
        }
    }
    override fun back() = press(Btn.B)
    override fun tap(x: Int, y: Int) {
        val i = (y - 18) / 26
        if (y >= 18 && i in 0..3) { if (i == sel) act() else { sel = i; g.sfx("move") } } else if (y > 120) press(Btn.B)
    }
}

// ======================================================================
// SETTINGS
// ======================================================================
class SettingsScreen(g: Game) : Screen(g) {
    private var sel = 0
    private val rows = listOf("MUSIC", "SOUND FX", "TEXT SPEED", "AUTO DELAY", "PALETTE", "BACK")
    private fun bar(v: Int) = "♥".repeat(v / 2) + "♡".repeat(5 - v / 2)
    private fun value(i: Int) = when (i) {
        0 -> bar(g.settings.music) + " ${g.settings.music}"
        1 -> bar(g.settings.sfx) + " ${g.settings.sfx}"
        2 -> listOf("SLOW", "NORMAL", "FAST", "INSTANT")[g.settings.textSpeed]
        3 -> listOf("SHORT", "MEDIUM", "LONG")[g.settings.autoDelay]
        4 -> Game.PALETTES[g.settings.palette].first
        else -> ""
    }
    private fun change(i: Int, d: Int) {
        val st = g.settings
        when (i) {
            0 -> st.music = (st.music + d).coerceIn(0, 10)
            1 -> st.sfx = (st.sfx + d).coerceIn(0, 10)
            2 -> st.textSpeed = (st.textSpeed + d + 4) % 4
            3 -> st.autoDelay = (st.autoDelay + d + 3) % 3
            4 -> st.palette = (st.palette + d + Game.PALETTES.size) % Game.PALETTES.size
            else -> return
        }
        g.saveSettings(); g.sfx("move")
    }
    override fun draw(s: Scr) {
        s.clear(0)
        s.rect(0, 0, 160, 13, 3); s.text("SETTINGS", 4, 3, 0)
        for ((i, r) in rows.withIndex()) {
            val y = 18 + i * 18
            if (i == sel) s.rect(0, y - 2, 160, 16, 1)
            s.text(r, 8, y, 3)
            val v = value(i)
            if (v.isNotEmpty()) { s.text("◀", 72, y + 1, 2); s.text(v, 80, y + 1, 3); s.text("▶", 153, y + 1, 2) }
        }
        s.text("◀▶ change   B back", 8, 128, 2)
        // palette preview swatch
        for (k in 0..3) s.rect(120 + k * 8, 128, 8, 8, k)
    }
    override fun press(b: Btn) {
        when (b) {
            Btn.UP -> { sel = (sel + rows.size - 1) % rows.size; g.sfx("move") }
            Btn.DOWN -> { sel = (sel + 1) % rows.size; g.sfx("move") }
            Btn.LEFT -> change(sel, -1)
            Btn.RIGHT, Btn.A -> if (sel == rows.size - 1) { g.sfx("back"); g.pop() } else change(sel, 1)
            Btn.B, Btn.START -> { g.sfx("back"); g.pop() }
            else -> {}
        }
    }
    override fun back() = press(Btn.B)
    override fun tap(x: Int, y: Int) {
        val i = (y - 16) / 18
        if (y < 16 || i !in rows.indices) { if (y > 120) press(Btn.B); return }
        sel = i
        if (i == rows.size - 1) { press(Btn.B); return }
        change(i, if (x < 116) -1 else 1)
    }
}

// ======================================================================
// BACKLOG
// ======================================================================
class BacklogScreen(g: Game) : Screen(g) {
    private val lines = ArrayList<Pair<Int, String>>()   // shade, text
    private var scroll = 0
    private var lastDragY = -1
    init {
        for ((who, text) in g.backlog) {
            if (who == "▶") { lines.add(Pair(2, "▶ $text")); continue }
            val wrapped = Font.wrap(if (who.isEmpty()) text else "$who: $text", 148)
            wrapped.forEach { lines.add(Pair(3, it)) }
            lines.add(Pair(0, ""))
        }
    }
    private val visible = 12
    override fun draw(s: Scr) {
        s.clear(0)
        s.rect(0, 0, 160, 13, 3); s.text("BACKLOG", 4, 3, 0); s.text("B: back", 120, 3, 1)
        val start = maxOf(0, lines.size - visible - scroll)
        for (i in 0 until visible) {
            val l = lines.getOrNull(start + i) ?: break
            s.text(l.second, 6, 16 + i * 10, l.first)
        }
        if (lines.isEmpty()) s.textC("Nothing yet.", 80, 70, 2)
        if (scroll < lines.size - visible) s.text("▲", 150, 16, 2)
        if (scroll > 0) s.text("▼", 150, 134, 2)
    }
    override fun press(b: Btn) {
        when (b) {
            Btn.UP -> scroll = minOf(maxOf(0, lines.size - visible), scroll + 1)
            Btn.DOWN -> scroll = maxOf(0, scroll - 1)
            Btn.LEFT -> scroll = minOf(maxOf(0, lines.size - visible), scroll + visible)
            Btn.RIGHT -> scroll = maxOf(0, scroll - visible)
            Btn.B, Btn.START, Btn.A -> { g.sfx("back"); g.pop() }
            else -> {}
        }
    }
    override fun back() = press(Btn.B)
    override fun tap(x: Int, y: Int) { lastDragY = y; if (y < 14 && x > 110) press(Btn.B) }
    override fun drag(x: Int, y: Int) {
        if (lastDragY < 0) { lastDragY = y; return }
        val d = (y - lastDragY) / 6
        if (d != 0) { scroll = (scroll + d).coerceIn(0, maxOf(0, lines.size - visible)); lastDragY = y }
    }
    override fun release() { lastDragY = -1 }
}

// ======================================================================
// GALLERY
// ======================================================================
class GalleryScreen(g: Game) : Screen(g) {
    private var sel = 0
    private val cgs = Backgrounds.CGS
    override fun update() { g.music("title") }
    override fun draw(s: Scr) {
        s.clear(0)
        s.rect(0, 0, 160, 13, 3); s.text("GALLERY", 4, 3, 0)
        s.text("${cgs.count { it.id in g.gallery }}/${cgs.size}", 130, 3, 0)
        for ((i, cg) in cgs.withIndex()) {
            val col = i % 2; val row = i / 2
            val x = 2 + col * 79; val y = 16 + row * 23
            val got = cg.id in g.gallery
            s.window(x, y, 77, 22)
            if (i == sel) s.frame(x + 1, y + 1, 75, 20, 2)
            if (got) {
                val name = cg.title.removePrefix("END: ")
                val ls = Font.wrap(name, 60).take(2)
                ls.forEachIndexed { k, l -> s.text(l, x + 5, y + (if (ls.size == 1) 8 else 3) + k * 9, 3) }
                if (cg.id.startsWith("end_")) s.blit(Art["hud_full"], x + 66, y + 4)
            } else { s.text("? ? ?", x + 26, y + 8, 1) }
        }
        s.text("A: view  B: back", 8, 133, 2)
    }
    private fun open() {
        val cg = cgs[sel]
        if (cg.id in g.gallery) { g.sfx("select"); g.push(CgViewScreen(g, cg.id)) } else g.sfx("back")
    }
    override fun press(b: Btn) {
        when (b) {
            Btn.UP -> sel = (sel - 2 + cgs.size) % cgs.size
            Btn.DOWN -> sel = (sel + 2) % cgs.size
            Btn.LEFT, Btn.RIGHT -> sel = sel xor 1
            Btn.A -> { open(); return }
            Btn.B, Btn.START -> { g.sfx("back"); g.pop(); return }
            else -> return
        }
        g.sfx("move")
    }
    override fun back() = press(Btn.B)
    override fun tap(x: Int, y: Int) {
        if (y > 130) { press(Btn.B); return }
        val row = (y - 16) / 23; val col = if (x < 80) 0 else 1
        val i = row * 2 + col
        if (y >= 16 && i in cgs.indices) { if (i == sel) open() else { sel = i; g.sfx("move") } }
    }
}

class CgViewScreen(g: Game, val id: String) : Screen(g) {
    override fun draw(s: Scr) {
        Backgrounds.drawCG(s, id, g.t)
        val title = Backgrounds.CGS.firstOrNull { it.id == id }?.title ?: ""
        s.rect(0, 132, 160, 12, 3); s.textC(title, 80, 134, 0)
    }
    override fun press(b: Btn) { g.sfx("back"); g.pop() }
    override fun back() = press(Btn.B)
    override fun tap(x: Int, y: Int) = press(Btn.A)
}

// ======================================================================
// ENDINGS + CREDITS
// ======================================================================
class EndingScreen(g: Game, val id: String) : Screen(g) {
    private var phase = 0
    private var tt = 0
    private val info = g.story.endings.firstOrNull { it.id == id }
    private val index = g.story.endings.indexOfFirst { it.id == id } + 1
    private val lines = Font.wrap(info?.text ?: "", 146)
    // No autosave here: the autosave stays just before the finale so players can retry for other endings.
    init { g.flushUnlocks() }
    override fun update() {
        tt++
        g.music(if (id == "missed" || id == "distance") "sad" else "ending")
    }
    override fun draw(s: Scr) {
        if (phase == 0) {
            Backgrounds.drawCG(s, "end_$id", g.t)
            if (tt > 60) { s.rect(0, 132, 160, 12, 3); s.textC("ENDING $index/${g.story.endings.size}", 80, 134, 0) }
            return
        }
        s.clear(3)
        s.textC("ENDING $index/${g.story.endings.size}", 80, 8, 1)
        s.textC(info?.title ?: id, 80, 20, 0)
        s.hline(20, 31, 120, 1)
        val chars = tt * 1
        var left = chars
        lines.forEachIndexed { i, l ->
            if (left > 0) { s.text(if (left >= l.length) l else l.substring(0, left), 7, 38 + i * 10, 0); left -= l.length + 1 }
        }
        if (left > 0 && (g.t / 20) % 2 == 0) s.text("▼", 150, 134, 1)
    }
    override fun press(b: Btn) {
        if (b != Btn.A && b != Btn.START) return
        if (phase == 0) { if (tt > 40) { phase = 1; tt = 0; g.sfx("select") } }
        else if (tt < lines.sumOf { it.length + 1 }) tt = 9999
        else { g.sfx("select"); g.replace(CreditsScreen(g)) }
    }
    override fun back() {}
    override fun tap(x: Int, y: Int) = press(Btn.A)
}

class CreditsScreen(g: Game) : Screen(g) {
    private var tt = 0
    private val lines = listOf(
        "♥ REESE ♥", "", "a 4-shade love story", "", "", "STARRING", "", "Elijah Christan Breedlove", "as himself", "",
        "Reese", "as Player 2", "", "", "WITH", "", "Bit the cat", "Kenji (ramen)", "Ms. Okafor (teacher)", "", "",
        "MADE WITH", "", "Kotlin + Android Canvas", "a home-made chiptune synth", "and hand-placed pixels", "", "",
        "ENDINGS FOUND", "", "${g.gallery.count { it.startsWith("end_") }} / ${g.story.endings.size}", "", "",
        "Thanks for playing!", "", "", "THE END?"
    )
    override fun update() { tt++; g.music("credits") }
    override fun draw(s: Scr) {
        s.clear(3)
        for (i in 0 until 6) s.pset((i * 53 + tt / 3) % 160, (i * 37) % 144, 1)
        val off = 150 - tt / 2
        lines.forEachIndexed { i, l -> val y = off + i * 12; if (y in -10..150) s.textC(l, 80, y, if (l.uppercase() == l && l.isNotBlank()) 1 else 0) }
        if (off + lines.size * 12 < 60) { if ((g.t / 30) % 2 == 0) s.textC("press A", 80, 120, 1) }
    }
    override fun press(b: Btn) {
        val endY = 150 - tt / 2 + lines.size * 12
        if (endY < 60 || b == Btn.START) g.replace(TitleScreen(g)) else tt += 120
    }
    override fun back() = g.replace(TitleScreen(g))
    override fun tap(x: Int, y: Int) = press(Btn.A)
}
