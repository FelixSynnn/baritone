package com.reese.game.core

/** Everything that gets saved. */
class GameState {
    var aff = 20
    var day = 1
    var coins = 20
    var score = 0
    var best = 0
    val flags = LinkedHashSet<String>()
    val traits = LinkedHashMap<String, Int>()
    val inv = ArrayList<String>()
    val given = LinkedHashSet<String>()
    var phase = "intro"          // intro | map | scene | ending
    var scene: String? = null
    var pc = 0
    val stack = ArrayList<Pair<String, Int>>()
    var bg = "black"
    var music: String? = null
    var reese: String? = null    // shown expression, or null if hidden
    var rain = false
    var ending: String? = null
    var location = ""
    var giftToday = false

    fun dominantTrait(): String {
        val best = traits.maxByOrNull { it.value } ?: return "none"
        // ties go to the trait chosen most recently? keep stable: first max in insertion order
        return best.key
    }

    fun toJson(): String = Json.write(mapOf(
        "aff" to aff, "day" to day, "coins" to coins, "score" to score, "best" to best,
        "flags" to flags.toList(), "traits" to traits, "inv" to inv, "given" to given.toList(),
        "phase" to phase, "scene" to scene, "pc" to pc,
        "stack" to stack.map { listOf(it.first, it.second) },
        "bg" to bg, "music" to music, "reese" to reese, "rain" to rain, "ending" to ending,
        "location" to location, "giftToday" to giftToday,
    ))

    companion object {
        fun fromJson(text: String): GameState {
            val m = Json.parse(text).obj()
            return GameState().apply {
                aff = m["aff"].int(20); day = m["day"].int(1); coins = m["coins"].int(0)
                score = m["score"].int(0); best = m["best"].int(0)
                m["flags"].arr().forEach { flags.add(it.str()) }
                m["traits"].obj().forEach { (k, v) -> traits[k] = v.int() }
                m["inv"].arr().forEach { inv.add(it.str()) }
                m["given"].arr().forEach { given.add(it.str()) }
                phase = m["phase"].str("map"); scene = m["scene"] as? String; pc = m["pc"].int(0)
                m["stack"].arr().forEach { val l = it.arr(); stack.add(Pair(l[0].str(), l[1].int())) }
                bg = m["bg"].str("black"); music = m["music"] as? String; reese = m["reese"] as? String
                rain = m["rain"] == true; ending = m["ending"] as? String
                location = m["location"].str(""); giftToday = m["giftToday"] == true
            }
        }
    }
}

/** Blocking things the UI must show before the script continues. */
sealed class Ev {
    data class Say(val who: String, val expr: String?, val text: String) : Ev()
    data class Choice(val options: List<Pair<Int, String>>) : Ev()
    data class Minigame(val id: String) : Ev()
    data class GiftPrompt(val items: List<String>) : Ev()
    data class Cg(val id: String) : Ev()
    data class Title(val text: String, val sub: String) : Ev()
    data class Ending(val id: String) : Ev()
    object SceneEnd : Ev()
}

class Item(val id: String, val name: String, val price: Int, val desc: String)
class EndingInfo(val id: String, val title: String, val text: String)
class Day(val name: String, val spots: Map<String, String>, val forced: String?)

/** The parsed story.json. */
class Story(text: String) {
    val root = Json.parse(text).obj()
    val meta = root["meta"].obj()
    val fullName = meta["player_full_name"].str("Elijah Christan Breedlove")
    val playerName = meta["player_name"].str("Elijah")
    val nickname = meta["nickname"].str("Eli")
    val nicknameAt = meta["nickname_at"].int(70)
    val startAff = meta["start_affection"].int(20)
    val startCoins = meta["start_coins"].int(20)
    val allowance = meta["daily_coins"].int(10)
    val locations: Map<String, String> = root["locations"].obj().mapValues { it.value.str() }
    val days: List<Day> = root["days"].arr().map { d ->
        val o = d.obj()
        Day(o["name"].str(), o["spots"].obj().mapValues { it.value.str() }, o["forced"] as? String)
    }
    val items: List<Item> = root["shop"].arr().map { val o = it.obj(); Item(o["id"].str(), o["name"].str(), o["price"].int(), o["desc"].str()) }
    val endings: List<EndingInfo> = root["endings"].arr().map { val o = it.obj(); EndingInfo(o["id"].str(), o["title"].str(), o["text"].str()) }
    val scenes: Map<String, List<Map<String, Any?>>> = root["scenes"].obj().mapValues { e -> e.value.arr().map { it.obj() } }
    private val labels = HashMap<String, Map<String, Int>>()

    fun labelsOf(scene: String): Map<String, Int> = labels.getOrPut(scene) {
        val m = HashMap<String, Int>()
        scenes[scene]?.forEachIndexed { i, c -> (c["label"] as? String)?.let { m[it] = i } }
        m
    }
    fun item(id: String) = items.firstOrNull { it.id == id }
}

/**
 * Runs the script. Call next() to get the next blocking event; the UI then
 * calls advance()/choose()/minigameDone()/gift() to continue.
 */
class Engine(val story: Story, val st: GameState) {
    /** Non-blocking effects for the UI: "heart_up", "heart_down", "shake", "flash", sfx ids... */
    val fx = ArrayList<String>()
    /** Gallery unlocks requested by the script (cg ids / ending ids). */
    val unlocks = ArrayList<String>()
    private var resuming = false
    private var lastChoice: List<Map<String, Any?>> = emptyList()

    fun start(scene: String) {
        st.scene = scene; st.pc = 0; st.stack.clear(); st.phase = if (st.phase == "intro") "intro" else "scene"
    }

    /** Resume after loading a save: the current command's effects were already applied. */
    fun resume() { resuming = true }

    fun nick(): String = if (st.aff >= story.nicknameAt || "nickname" in st.flags) story.nickname else story.playerName

    fun fmt(s: String): String = s.replace("{eli}", nick()).replace("{full}", story.fullName)
        .replace("{coins}", st.coins.toString()).replace("{score}", st.score.toString())
        .replace("{Eli}", nick())

    // ---------------- conditions ----------------
    fun cond(expr: String?): Boolean {
        if (expr.isNullOrBlank()) return true
        return expr.split('|').any { alt -> alt.split('&').all { term(it.trim()) } }
    }

    private val cmpRe = Regex("^([a-z_]+)\\s*(>=|<=|==|!=|>|<)\\s*(-?\\d+)$")
    private fun term(t: String): Boolean {
        if (t.isEmpty()) return true
        if (t.startsWith("!")) return !term(t.substring(1).trim())
        if (t.startsWith("trait:")) return st.dominantTrait() == t.substring(6)
        if (t.startsWith("has:")) return t.substring(4) in st.inv
        if (t.startsWith("gave:")) return t.substring(5) in st.given
        val m = cmpRe.find(t)
        if (m != null) {
            val (name, op, num) = m.destructured
            val v = value(name); val n = num.toInt()
            return when (op) { ">=" -> v >= n; "<=" -> v <= n; ">" -> v > n; "<" -> v < n; "==" -> v == n; else -> v != n }
        }
        return t in st.flags
    }
    private fun value(name: String): Int = when (name) {
        "aff" -> st.aff; "day" -> st.day; "coins" -> st.coins; "score" -> st.score; "best" -> st.best
        "gifts" -> st.given.size
        else -> st.traits[name] ?: 0
    }

    // ---------------- effects ----------------
    private fun addAff(n: Int) {
        if (n == 0) return
        st.aff = (st.aff + n).coerceIn(0, 100)
        fx.add(if (n > 0) "heart_up" else "heart_down")
    }

    private fun strs(v: Any?): List<String> = when (v) { null -> emptyList(); is List<*> -> v.map { it.str() }; else -> listOf(v.str()) }

    /** Apply the side effects found on a command or choice option. */
    fun effects(c: Map<String, Any?>) {
        c["aff"]?.let { addAff(it.int()) }
        strs(c["set"]).forEach { st.flags.add(it) }
        strs(c["unset"]).forEach { st.flags.remove(it) }
        strs(c["trait"]).forEach { st.traits[it] = (st.traits[it] ?: 0) + 1 }
        c["coins"]?.let { st.coins = maxOf(0, st.coins + it.int()) }
        strs(c["item"]).forEach { st.inv.add(it) }
        strs(c["unlock"]).forEach { unlocks.add(it) }
        c["bg"]?.let { st.bg = it.str() }
        if (c.containsKey("music")) st.music = c["music"] as? String
        if (c.containsKey("show")) st.reese = c["show"] as? String
        if (c["hide"] == true) st.reese = null
        c["fx"]?.let { strs(it).forEach { f -> when (f) { "rain" -> st.rain = true; "norain" -> st.rain = false; else -> fx.add(f) } } }
        strs(c["sfx"]).forEach { fx.add("sfx:$it") }
    }

    // ---------------- stepping ----------------
    fun next(): Ev {
        var guard = 0
        while (true) {
            if (++guard > 10000) throw IllegalStateException("script loop in ${st.scene}")
            val sc = st.scene ?: return Ev.SceneEnd
            val cmds = story.scenes[sc] ?: throw IllegalStateException("missing scene '$sc'")
            if (st.pc >= cmds.size) {
                if (st.stack.isNotEmpty()) { val (s, p) = st.stack.removeAt(st.stack.size - 1); st.scene = s; st.pc = p; continue }
                return Ev.SceneEnd
            }
            val c = cmds[st.pc]
            val wasResuming = resuming
            resuming = false
            if (!wasResuming && !cond(c["if"] as? String)) { st.pc++; continue }
            if (!wasResuming) effects(c)

            val go = c["go"] as? String
            if (go != null) { jump(go); continue }
            val call = c["call"] as? String
            if (call != null) { st.stack.add(Pair(sc, st.pc + 1)); st.scene = call; st.pc = 0; continue }
            if (c["end"] == true) { st.stack.clear(); return Ev.SceneEnd }

            c["say"]?.let { who ->
                val w = who.str()
                val e = c["e"] as? String
                if (w == "reese" && e != null) st.reese = e
                if (w == "reese" && st.reese == null) st.reese = "neutral"
                return Ev.Say(w, e, fmt(c["t"].str()))
            }
            val opts = c["choice"] as? List<*>
            if (opts != null) {
                lastChoice = opts.map { it.obj() }
                val visible = lastChoice.withIndex().filter { cond(it.value["if"] as? String) }.map { Pair(it.index, fmt(it.value["t"].str())) }
                if (visible.isEmpty()) { st.pc++; continue }
                return Ev.Choice(visible)
            }
            c["minigame"]?.let { return Ev.Minigame(it.str()) }
            if (c["giftprompt"] == true) {
                val items = st.inv.distinct()
                if (items.isEmpty() || st.giftToday) { st.pc++; continue }
                return Ev.GiftPrompt(items)
            }
            c["cg"]?.let { unlocks.add(it.str()); return Ev.Cg(it.str()) }
            c["title"]?.let { return Ev.Title(fmt(it.str()), fmt(c["sub"].str())) }
            c["ending"]?.let { st.ending = it.str(); st.phase = "ending"; unlocks.add("end_" + it.str()); return Ev.Ending(it.str()) }
            st.pc++
        }
    }

    private fun jump(label: String) {
        if (label.startsWith("scene:")) { st.scene = label.substring(6); st.pc = 0; return }
        val idx = story.labelsOf(st.scene!!)[label] ?: throw IllegalStateException("missing label '$label' in ${st.scene}")
        st.pc = idx
    }

    fun advance() { st.pc++ }

    fun choose(optIndex: Int) {
        val sc = st.scene ?: return
        val c = story.scenes[sc]!![st.pc]
        val opt = (c["choice"] as List<*>)[optIndex].obj()
        effects(opt)
        val go = opt["go"] as? String
        if (go != null) jump(go) else st.pc++
    }

    fun minigameDone(score: Int) {
        st.score = score
        if (score > st.best) st.best = score
        st.coins += score / 4
        st.pc++
    }

    /** Give a gift (or null to skip). Runs the gift's reaction scene, e.g. "gift_ramen". */
    fun gift(item: String?) {
        st.pc++
        if (item == null) return
        st.inv.remove(item)
        st.given.add(item)
        st.giftToday = true
        val sc = "gift_$item"
        if (story.scenes.containsKey(sc)) { st.stack.add(Pair(st.scene!!, st.pc)); st.scene = sc; st.pc = 0 }
    }
}
