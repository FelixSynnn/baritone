package com.reese.game

import com.reese.game.core.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Finds the assets folder whether tests run from the project root or the app module. */
object Assets {
    val dir: File = listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.exists() }
    fun text(p: String) = File(dir, p).readText()
}

class StoryTest {
    private val story = Story(Assets.text("script/story.json"))

    // ------------------------------------------------------------------
    // Script lint: every label, scene, sprite, song, background and CG used by the script exists.
    // ------------------------------------------------------------------
    @Test fun scriptIsValid() {
        Art.load(Assets.text("art/sprites.txt"))
        val problems = ArrayList<String>()
        val songs = Synth.SONGS.keys
        val bgs = Backgrounds.IDS.toSet()
        val cgs = Backgrounds.CGS.map { it.id }.toSet()
        val eng = Engine(story, GameState())
        for ((name, cmds) in story.scenes) {
            val labels = story.labelsOf(name)
            fun checkGo(go: String?) {
                if (go == null) return
                if (go.startsWith("scene:")) { if (!story.scenes.containsKey(go.substring(6))) problems += "$name: bad scene $go" }
                else if (go !in labels) problems += "$name: missing label '$go'"
            }
            for ((i, c) in cmds.withIndex()) {
                checkGo(c["go"] as? String)
                (c["call"] as? String)?.let { if (!story.scenes.containsKey(it)) problems += "$name[$i]: bad call $it" }
                (c["bg"] as? String)?.let { if (it !in bgs) problems += "$name[$i]: bad bg $it" }
                (c["music"] as? String)?.let { if (it !in songs) problems += "$name[$i]: bad music $it" }
                (c["cg"] as? String)?.let { if (it !in cgs) problems += "$name[$i]: bad cg $it" }
                (c["sfx"] as? String)?.let { if (it !in Synth.SFX) problems += "$name[$i]: bad sfx $it" }
                (c["ending"] as? String)?.let { e -> if (story.endings.none { it.id == e }) problems += "$name[$i]: bad ending $e"; if ("end_$e" !in cgs) problems += "no CG for ending $e" }
                (c["show"] as? String)?.let { if (it !in Art.REESE_EXPR) problems += "$name[$i]: bad Reese expression $it" }
                (c["if"] as? String)?.let { runCatching { eng.cond(it) }.onFailure { e -> problems += "$name[$i]: bad condition $it ($e)" } }
                val who = c["say"] as? String
                val e = c["e"] as? String
                if (who == "reese" && e != null && e !in Art.REESE_EXPR) problems += "$name[$i]: bad Reese expression $e"
                if (who == "eli" && e != null && e !in Art.ELI_EXPR) problems += "$name[$i]: bad Elijah expression $e"
                (c["choice"] as? List<*>)?.let { opts ->
                    if (opts.isEmpty() || opts.size > 4) problems += "$name[$i]: choice needs 1-4 options"
                    opts.forEach { o -> checkGo(o.obj()["go"] as? String); if (Font.wrap(o.obj()["t"].str(), 136).size > 2) problems += "$name[$i]: option too long: ${o.obj()["t"]}" }
                }
            }
        }
        for ((di, d) in story.days.withIndex()) {
            d.forced?.let { if (!story.scenes.containsKey(it)) problems += "day ${di + 1}: missing forced scene $it" }
            for ((loc, sc) in d.spots) {
                if (loc !in story.locations) problems += "day ${di + 1}: unknown location $loc"
                if (!story.scenes.containsKey(sc)) problems += "day ${di + 1}: missing scene $sc"
            }
        }
        for (it in story.items) {
            if (!story.scenes.containsKey("gift_${it.id}")) problems += "item ${it.id} has no gift scene"
            if (!Art.has("item_${it.id}")) problems += "item ${it.id} has no icon"
        }
        assertEquals(7, story.days.size)
        assertEquals(5, story.items.size)
        assertEquals(5, story.endings.size)
        for (e in Art.REESE_EXPR) { assertTrue(Art.has("reese_$e")); assertTrue(Art.has("reese_${e}_blink")) }
        for (e in Art.ELI_EXPR) { assertTrue(Art.has("eli_$e")); assertTrue(Art.has("eli_${e}_blink")) }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    // ------------------------------------------------------------------
    // Full playthroughs, one per ending.
    // ------------------------------------------------------------------
    class Run(val story: Story, val spots: List<String>, val prefer: (String) -> Int = { 0 }, val worst: Boolean = false,
              val score: Int = 0, val buy: List<String> = emptyList()) {
        val st = GameState().apply { aff = story.startAff; coins = story.startCoins }
        val eng = Engine(story, st)
        val log = ArrayList<String>()
        var ending: String? = null
        var minigames = 0

        fun pick(opts: List<Pair<Int, String>>): Int {
            val cmd = story.scenes[st.scene]!![st.pc]
            val raw = (cmd["choice"] as List<*>).map { it.obj() }
            return opts.maxByOrNull { (i, t) ->
                val aff = raw[i]["aff"].int(0)
                prefer(t) * 1000 + (if (worst) -aff else aff) * 10 - i
            }!!.first
        }

        fun runScene(): Boolean {
            while (true) {
                when (val e = eng.next()) {
                    is Ev.Say -> { log += "${e.who}: ${e.text}"; eng.advance() }
                    is Ev.Choice -> { val p = pick(e.options); log += "> " + e.options.first { it.first == p }.second; eng.choose(p) }
                    is Ev.Minigame -> { minigames++; eng.minigameDone(score) }
                    is Ev.GiftPrompt -> eng.gift(e.items.firstOrNull())
                    is Ev.Cg, is Ev.Title -> eng.advance()
                    is Ev.Ending -> { ending = e.id; return true }
                    Ev.SceneEnd -> return false
                }
                eng.fx.clear()
            }
        }

        fun go(): Run {
            eng.start("intro"); runScene()
            for (day in 1..7) {
                st.day = day; st.giftToday = false
                if (day > 1) st.coins += story.allowance
                for (b in buy) { val it = story.item(b)!!; if (b !in st.inv && b !in st.given && st.coins >= it.price) { st.coins -= it.price; st.inv += b } }
                val d = story.days[day - 1]
                val sc = d.forced ?: d.spots[spots[day - 1]] ?: error("day $day has no ${spots[day - 1]}")
                st.phase = "scene"
                eng.start(sc)
                log += "== DAY $day: $sc (aff ${st.aff})"
                if (runScene()) return this
            }
            return this
        }
        override fun toString() = "ending=$ending aff=${st.aff} flags=${st.flags} given=${st.given}\n" + log.joinToString("\n")
    }

    private val goodRoute = listOf("cafe", "arcade", "park", "cafe", "rooftop", "park", "")

    @Test fun secretEnding() {
        val r = Run(story, goodRoute, score = 40, buy = listOf("ramen", "tape")).go()
        assertEquals(r.toString(), "secret", r.ending)
        assertTrue(r.st.aff >= 90)
        assertEquals(2, r.st.given.size)
        println("SECRET: aff=${r.st.aff} flags=${r.st.flags}")
    }

    @Test fun bestEnding() {
        // a solid route without the secret requirements (no arcade high score, no gifts)
        val r = Run(story, listOf("cafe", "school", "park", "cafe", "rooftop", "park", ""), score = 0).go()
        assertEquals(r.toString(), "best", r.ending)
        assertTrue("nickname unlocked", r.log.any { it.contains("Eli") && it.startsWith("reese") })
        println("BEST: aff=${r.st.aff}")
    }

    @Test fun distanceEnding() {
        // promise made on day 3, then broken on day 6 by going to the cafe
        val r = Run(story, listOf("cafe", "arcade", "park", "cafe", "rooftop", "cafe", ""), score = 40, buy = listOf("tape")).go()
        assertEquals(r.toString(), "distance", r.ending)
        assertTrue("broke_promise" in r.st.flags)
    }

    @Test fun friendsEndingByStayingQuiet() {
        val r = Run(story, goodRoute, prefer = { if (it.startsWith("Say nothing")) 1 else 0 }, score = 20).go()
        assertEquals(r.toString(), "friends", r.ending)
    }

    @Test fun friendsEndingWithMediumAffection() {
        // neutral-ish route: pick the goofy/low options, no promise
        val r = Run(story, listOf("park", "school", "school", "school", "school", "arcade", ""),
            prefer = { t -> if (t.contains("No promises") || t.contains("bail") || t.contains("Check your phone")) 1 else 0 }, score = 10).go()
        assertTrue(r.toString(), r.st.aff in 35..74)
        assertEquals(r.toString(), "friends", r.ending)
    }

    @Test fun missedConnectionEnding() {
        val r = Run(story, listOf("park", "school", "cafe", "school", "school", "arcade", ""), worst = true, score = 0).go()
        assertEquals(r.toString(), "missed", r.ending)
        assertTrue(r.st.aff < 35)
    }

    @Test fun everyDayLocationIsPlayable() {
        // visit every location on every day at least once across runs; none may crash or end early
        for (d in 0 until 6) for (loc in story.days[d].spots.keys) {
            val spots = MutableList(7) { i -> story.days.getOrNull(i)?.spots?.keys?.firstOrNull() ?: "" }
            spots[d] = loc
            val r = Run(story, spots, score = 20, buy = listOf("milk", "cat", "roses")).go()
            assertNotNull("day ${d + 1} $loc", r.ending)
        }
    }

    @Test fun conditionsAndNickname() {
        val st = GameState()
        val e = Engine(story, st)
        st.aff = 50; st.flags += "a"
        assertTrue(e.cond("aff>=50 & a"))
        assertFalse(e.cond("aff>50 | !a"))
        assertTrue(e.cond("aff<10 | a & aff==50"))
        assertEquals("Elijah", e.nick())
        st.aff = 70
        assertEquals("Eli", e.nick())
        st.traits["goofy"] = 2; st.traits["sweet"] = 1
        assertTrue(e.cond("trait:goofy"))
    }

    @Test fun saveRoundTrip() {
        val r = Run(story, goodRoute, score = 40, buy = listOf("ramen", "tape"))
        r.eng.start("intro"); r.runScene()
        r.st.flags += "x"; r.st.stack += Pair("d1_cafe", 3)
        val back = GameState.fromJson(r.st.toJson())
        assertEquals(r.st.toJson(), back.toJson())
    }
}
