package com.reese.game.core

/** What the game needs from the device. */
interface Platform {
    fun asset(path: String): String
    fun read(name: String): String?
    fun write(name: String, data: String)
    fun haptic() {}
    fun exit() {}
}

enum class Btn { UP, DOWN, LEFT, RIGHT, A, B, START, SELECT }

/** A full-screen state (title, map, scene...). Screens stack; the top one gets input. */
abstract class Screen(val g: Game) {
    open val overlay = false
    open fun update() {}
    abstract fun draw(s: Scr)
    open fun press(b: Btn) {}
    open fun tap(x: Int, y: Int) {}
    open fun drag(x: Int, y: Int) {}
    open fun release() {}
    open fun back() { press(Btn.START) }
}

class Settings {
    var music = 7; var sfx = 8; var textSpeed = 1; var autoDelay = 1; var palette = 0
    fun toJson() = Json.write(mapOf("music" to music, "sfx" to sfx, "text" to textSpeed, "auto" to autoDelay, "pal" to palette))
    fun load(s: String?) {
        val m = s?.let { runCatching { Json.parse(it).obj() }.getOrNull() } ?: return
        music = m["music"].int(7); sfx = m["sfx"].int(8); textSpeed = m["text"].int(1); autoDelay = m["auto"].int(1); palette = m["pal"].int(0)
    }
}

class Game(val pf: Platform, val synth: Synth) {
    companion object {
        const val CW = 180; const val CH = 320          // whole console canvas
        const val SX = 10; const val SY = 24            // screen position on the console

        val PALETTES = listOf(
            "CLASSIC" to intArrayOf(0xFF9BBC0F.toInt(), 0xFF8BAC0F.toInt(), 0xFF306230.toInt(), 0xFF0F380F.toInt()),
            "POCKET" to intArrayOf(0xFFC4CFA1.toInt(), 0xFF8B956D.toInt(), 0xFF4D533C.toInt(), 0xFF1F1F1F.toInt()),
            "SWEETHEART" to intArrayOf(0xFFFFE4EC.toInt(), 0xFFF29BB8.toInt(), 0xFFA34A72.toInt(), 0xFF3D1530.toInt()),
            "MIDNIGHT" to intArrayOf(0xFFE3EEC0.toInt(), 0xFF8FB3A8.toInt(), 0xFF4A6A8A.toInt(), 0xFF1E2244.toInt()),
        )
        // console shell colours
        const val BODY = 0xFFC9C6BE.toInt(); const val BODY_LO = 0xFFA9A59C.toInt(); const val BODY_HI = 0xFFE3E0D8.toInt()
        const val BEZEL = 0xFF5E5E70.toInt(); const val BEZEL_LO = 0xFF48485A.toInt()
        const val DPAD = 0xFF2E2E36.toInt(); const val DPAD_HI = 0xFF4A4A55.toInt()
        const val BTN = 0xFFA0285C.toInt(); const val BTN_HI = 0xFFC4528A.toInt(); const val BTN_DN = 0xFF7A1A44.toInt()
        const val NAVY = 0xFF283080.toInt(); const val LED = 0xFFE03C3C.toInt(); const val LINE2 = 0xFF8C2850.toInt()
        const val SLOT = 0xFF7E7A72.toInt()
    }

    val scr = Scr()
    val story: Story
    var st = GameState()
    lateinit var eng: Engine
    val settings = Settings()
    val gallery = LinkedHashSet<String>()
    val backlog = ArrayList<Pair<String, String>>()
    val held = BooleanArray(Btn.values().size)
    var t = 0
    val stack = ArrayList<Screen>()
    var auto = false
    var skip = false
    var toast = ""; var toastT = 0

    val fb = IntArray(CW * CH)
    private val shellBase = IntArray(CW * CH)
    private var shellDirty = true

    init {
        Art.load(pf.asset("art/sprites.txt"))
        story = Story(pf.asset("script/story.json"))
        settings.load(pf.read("settings.json"))
        pf.read("gallery.json")?.let { runCatching { Json.parse(it).arr().forEach { x -> gallery.add(x.str()) } } }
        applyAudio()
        newState()
        stack.add(TitleScreen(this))
    }

    fun applyAudio() { synth.musicVol = settings.music / 10f; synth.sfxVol = settings.sfx / 10f }
    fun saveSettings() { pf.write("settings.json", settings.toJson()); applyAudio() }
    fun sfx(id: String) = synth.sfx(id)
    fun music(id: String?) = synth.playMusic(id)
    fun showToast(s: String) { toast = s; toastT = 120 }

    fun newState() {
        st = GameState().apply { aff = story.startAff; coins = story.startCoins }
        eng = Engine(story, st)
        backlog.clear()
    }

    // ---------------- screen stack ----------------
    val top: Screen get() = stack.last()
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.size - 1) }
    fun replace(s: Screen) { stack.clear(); stack.add(s) }

    // ---------------- game flow ----------------
    fun newGame() {
        newState()
        st.phase = "intro"
        eng.start("intro")
        replace(SceneScreen(this))
    }

    /** Called when a story scene finishes. */
    fun sceneEnded() {
        flushUnlocks()
        if (st.phase == "ending") return
        if (st.phase == "intro") st.day = 1 else st.day++
        startDay()
    }

    fun startDay() {
        st.giftToday = false
        st.reese = null; st.rain = false
        if (st.day > 1) st.coins += story.allowance
        val d = story.days.getOrNull(st.day - 1)
        if (d == null) { replace(TitleScreen(this)); return }
        if (d.forced != null) {
            st.phase = "scene"; st.location = "rooftop"
            eng.start(d.forced)
            autosave(false)
            replace(SceneScreen(this))
        } else {
            st.phase = "map"; st.scene = null
            autosave(false)
            replace(MapScreen(this))
        }
    }

    fun startScene(loc: String) {
        val d = story.days[st.day - 1]
        val sc = d.spots[loc] ?: return
        st.location = loc
        st.phase = "scene"
        eng.start(sc)
        autosave(false)
        replace(SceneScreen(this))
    }

    fun flushUnlocks() {
        if (eng.unlocks.isEmpty()) return
        var changed = false
        for (u in eng.unlocks) if (gallery.add(u)) changed = true
        eng.unlocks.clear()
        if (changed) pf.write("gallery.json", Json.write(gallery.toList()))
    }

    // ---------------- saves ----------------
    fun slotName(i: Int) = if (i == 0) "save_auto.json" else "save_$i.json"

    /** midCommand = the current command's effects were already applied. */
    fun autosave(midCommand: Boolean) = saveTo(0, midCommand)

    fun saveTo(slot: Int, midCommand: Boolean) {
        val m = Json.parse(st.toJson()).obj().toMutableMap()
        m["applied"] = midCommand
        m["stamp"] = System.currentTimeMillis()
        pf.write(slotName(slot), Json.write(m))
    }

    fun slotInfo(slot: Int): String? {
        val txt = pf.read(slotName(slot)) ?: return null
        val m = runCatching { Json.parse(txt).obj() }.getOrNull() ?: return null
        val day = m["day"].int(1)
        val loc = when (m["phase"].str()) { "intro" -> "PROLOGUE"; "map" -> "TOWN MAP"; "ending" -> "ENDING"; else -> story.locations[m["location"].str()]?.uppercase() ?: "STORY" }
        return "DAY $day  ♥${m["aff"].int()}  $loc"
    }

    fun load(slot: Int): Boolean {
        val txt = pf.read(slotName(slot)) ?: return false
        val m = runCatching { Json.parse(txt).obj() }.getOrNull() ?: return false
        st = GameState.fromJson(txt)
        eng = Engine(story, st)
        backlog.clear()
        auto = false; skip = false
        when (st.phase) {
            "map" -> replace(MapScreen(this))
            else -> {
                if (st.scene == null) { startDay(); return true }
                if (m["applied"] == true) eng.resume()
                replace(SceneScreen(this))
            }
        }
        return true
    }

    fun hasAnySave() = (0..3).any { pf.read(slotName(it)) != null }

    fun latestSlot(): Int? = (0..3).mapNotNull { i ->
        pf.read(slotName(i))?.let { txt -> runCatching { Json.parse(txt).obj()["stamp"].let { (it as? Number)?.toLong() ?: 0L } }.getOrNull()?.let { i to it } }
    }.maxByOrNull { it.second }?.first

    // ---------------- input ----------------
    private val pointerBtn = HashMap<Int, Btn?>()
    private var screenPointer = -1

    fun press(b: Btn) {
        held[b.ordinal] = true; shellDirty = true
        top.press(b)
    }
    fun release(b: Btn) { held[b.ordinal] = false; shellDirty = true }
    fun backKey() { top.back() }

    fun touchDown(id: Int, cx: Float, cy: Float) {
        val x = cx.toInt(); val y = cy.toInt()
        if (x in SX until SX + Scr.W && y in SY until SY + Scr.H) {
            screenPointer = id
            top.tap(x - SX, y - SY)
            return
        }
        val b = hitButton(cx, cy)
        pointerBtn[id] = b
        if (b != null) { pf.haptic(); press(b) }
    }
    fun touchMove(id: Int, cx: Float, cy: Float) {
        if (id == screenPointer) { top.drag(cx.toInt() - SX, cy.toInt() - SY); return }
        if (!pointerBtn.containsKey(id)) return
        val old = pointerBtn[id]
        val b = hitButton(cx, cy)
        // sliding a thumb across the d-pad changes direction
        if (b != old && (b == null || b.ordinal <= Btn.RIGHT.ordinal) && (old == null || old.ordinal <= Btn.RIGHT.ordinal)) {
            old?.let { release(it) }
            pointerBtn[id] = b
            if (b != null) { pf.haptic(); press(b) }
        }
    }
    fun touchUp(id: Int) {
        if (id == screenPointer) { screenPointer = -1; top.release(); return }
        pointerBtn.remove(id)?.let { release(it) }
    }

    // button geometry (console canvas coords)
    private val DX = 38f; private val DY = 240f
    private val AX = 152f; private val AY = 228f
    private val BX = 124f; private val BY = 242f
    private fun hitButton(x: Float, y: Float): Btn? {
        val dx = x - DX; val dy = y - DY
        if (Math.abs(dx) < 26 && Math.abs(dy) < 26 && (Math.abs(dx) > 3 || Math.abs(dy) > 3)) {
            return if (Math.abs(dx) > Math.abs(dy)) (if (dx < 0) Btn.LEFT else Btn.RIGHT) else (if (dy < 0) Btn.UP else Btn.DOWN)
        }
        fun near(cx: Float, cy: Float, r: Float) = (x - cx) * (x - cx) + (y - cy) * (y - cy) < r * r
        if (near(AX, AY, 16f)) return Btn.A
        if (near(BX, BY, 16f)) return Btn.B
        if (y in 274f..296f) {
            if (x in 44f..78f) return Btn.SELECT
            if (x in 94f..128f) return Btn.START
        }
        return null
    }

    // ---------------- frame ----------------
    fun update() {
        t++
        if (toastT > 0) toastT--
        top.update()
    }

    fun render(): IntArray {
        scr.clear(0)
        // draw from the deepest non-overlay screen up
        var first = stack.size - 1
        while (first > 0 && stack[first].overlay) first--
        for (i in first until stack.size) stack[i].draw(scr)
        if (toastT > 0) {
            val w = Font.width(toast) + 10
            scr.window(80 - w / 2, 4, w, 15); scr.textC(toast, 80, 8, 3)
        }
        if (shellDirty) { drawShell(); shellDirty = false }
        System.arraycopy(shellBase, 0, fb, 0, fb.size)
        val pal = PALETTES[settings.palette.coerceIn(0, PALETTES.size - 1)].second
        for (y in 0 until Scr.H) {
            val row = (SY + y) * CW + SX
            val src = y * Scr.W
            for (x in 0 until Scr.W) fb[row + x] = pal[scr.px[src + x].toInt() and 3]
        }
        // power LED pulses with the heart meter when in a scene
        val ledOn = (t / 30) % 8 != 0
        fill(fb, 14, 15, 3, 3, if (ledOn) LED else 0xFF5A1A1A.toInt())
        return fb
    }

    // ---------------- console shell ----------------
    private fun fill(a: IntArray, x: Int, y: Int, w: Int, h: Int, c: Int) {
        for (j in maxOf(0, y) until minOf(CH, y + h)) for (i in maxOf(0, x) until minOf(CW, x + w)) a[j * CW + i] = c
    }
    private fun disc(a: IntArray, cx: Int, cy: Int, r: Int, c: Int) {
        for (j in -r..r) for (i in -r..r) if (i * i + j * j <= r * r + r / 2) { val x = cx + i; val y = cy + j; if (x in 0 until CW && y in 0 until CH) a[y * CW + x] = c }
    }
    private val txtScr = Scr(CW, 16)
    private fun shellText(a: IntArray, s: String, x: Int, y: Int, c: Int, k: Int = 1, italic: Boolean = false) {
        txtScr.clear(0)
        if (k == 1) txtScr.text(s, 0, 0, 3) else txtScr.textScaled(s, 0, 0, k, 3)
        for (j in 0 until 16) for (i in 0 until CW) if (txtScr.px[j * CW + i].toInt() == 3) {
            val sx = x + i + (if (italic) (16 - j) / 4 else 0); val sy = y + j
            if (sx in 0 until CW && sy in 0 until CH) a[sy * CW + sx] = c
        }
    }

    private fun drawShell() {
        val a = shellBase
        a.fill(BODY)
        // edges / rounded corners
        fill(a, 0, 0, CW, 1, BODY_HI); fill(a, 0, 0, 1, CH, BODY_HI)
        fill(a, CW - 2, 0, 2, CH, BODY_LO); fill(a, 0, CH - 2, CW, 2, BODY_LO)
        // top groove
        fill(a, 0, 5, CW, 1, BODY_LO); fill(a, 0, 6, CW, 1, BODY_HI)
        fill(a, 14, 0, 1, 5, BODY_LO); fill(a, CW - 15, 0, 1, 5, BODY_LO)
        // bezel with the big rounded bottom-right corner
        val bx = 4; val by = 10; val bw = CW - 8; val bh = 166
        for (j in 0 until bh) for (i in 0 until bw) {
            val r = 20
            val cxr = bw - r; val cyr = bh - r
            val inCorner = i >= cxr && j >= cyr && (i - cxr) * (i - cxr) + (j - cyr) * (j - cyr) > r * r
            val small = 4
            val tl = (i < small && j < small && (small - i) * (small - i) + (small - j) * (small - j) > small * small)
            val tr = (i >= bw - small && j < small && (i - (bw - small - 1)) * (i - (bw - small - 1)) + (small - j) * (small - j) > small * small)
            val bl = (i < small && j >= bh - small && (small - i) * (small - i) + (j - (bh - small - 1)) * (j - (bh - small - 1)) > small * small)
            if (!inCorner && !tl && !tr && !bl) a[(by + j) * CW + bx + i] = if (j > bh - 3 || i > bw - 3) BEZEL_LO else BEZEL
        }
        // the coloured lines + caption above the screen
        fill(a, 10, 18, 34, 1, LINE2); fill(a, 10, 20, 34, 1, NAVY)
        fill(a, 136, 18, 34, 1, LINE2); fill(a, 136, 20, 34, 1, NAVY)
        shellText(a, "4-SHADE LOVE MATRIX", 48, 14, BODY_LO)
        // screen recess shadow
        fill(a, SX - 1, SY - 1, Scr.W + 2, 1, BEZEL_LO); fill(a, SX - 1, SY - 1, 1, Scr.H + 2, BEZEL_LO)
        // logo
        shellText(a, "Reese", 10, 182, NAVY, 2, italic = true)
        shellText(a, "POCKET", 76, 190, NAVY)
        shellText(a, "♥", 116, 190, BTN)
        // d-pad
        val dpPress = { b: Btn -> held[b.ordinal] }
        val cx = DX.toInt(); val cy = DY.toInt()
        fill(a, cx - 18, cy - 6, 36, 13, DPAD); fill(a, cx - 6, cy - 18, 13, 36, DPAD)
        fill(a, cx - 17, cy - 5, 34, 1, DPAD_HI); fill(a, cx - 5, cy - 17, 1, 34, DPAD_HI)
        disc(a, cx, cy, 3, DPAD_HI); disc(a, cx, cy, 2, DPAD)
        if (dpPress(Btn.UP)) fill(a, cx - 5, cy - 17, 11, 10, DPAD_HI)
        if (dpPress(Btn.DOWN)) fill(a, cx - 5, cy + 8, 11, 10, DPAD_HI)
        if (dpPress(Btn.LEFT)) fill(a, cx - 17, cy - 5, 10, 11, DPAD_HI)
        if (dpPress(Btn.RIGHT)) fill(a, cx + 8, cy - 5, 10, 11, DPAD_HI)
        // little arrows on the pad
        for (k in 0 until 3) { fill(a, cx - k, cy - 14 + k, 2 * k + 1, 1, BODY_LO); fill(a, cx - k, cy + 14 - k, 2 * k + 1, 1, BODY_LO)
            fill(a, cx - 14 + k, cy - k, 1, 2 * k + 1, BODY_LO); fill(a, cx + 14 - k, cy - k, 1, 2 * k + 1, BODY_LO) }
        // A / B
        for ((b, p) in listOf(Btn.A to (AX to AY), Btn.B to (BX to BY))) {
            val x = p.first.toInt(); val y = p.second.toInt()
            disc(a, x + 1, y + 1, 11, BODY_LO)
            disc(a, x, y, 11, if (held[b.ordinal]) BTN_DN else BTN)
            if (!held[b.ordinal]) { disc(a, x - 3, y - 4, 3, BTN_HI) }
            shellText(a, b.name, x - 2, y + 14, NAVY)
        }
        // select / start pills
        for ((b, x) in listOf(Btn.SELECT to 50, Btn.START to 100)) {
            val c = if (held[b.ordinal]) DPAD else SLOT
            fill(a, x, 280, 20, 6, BODY_LO); fill(a, x + 1, 281, 18, 4, c)
            shellText(a, b.name, x + 10 - Font.width(b.name) / 2, 289, NAVY)
        }
        // speaker grille
        for (k in 0 until 6) for (j in 0 until 30) {
            val x = 136 + k * 6 + j / 3; val y = 304 - j
            if (x < CW - 4) { a[y * CW + x] = SLOT; a[y * CW + x + 1] = BODY_LO }
        }
        shellText(a, "♥ PHONES", 12, 306, BODY_LO)
    }
}
