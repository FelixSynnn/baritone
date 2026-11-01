package com.reese.game

import com.reese.game.core.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Runs the real game loop headlessly, drives it with button presses, and saves screenshots. */
class ScreenshotTest {
    class FakePlatform : Platform {
        val files = HashMap<String, String>()
        override fun asset(path: String) = Assets.text(path)
        override fun read(name: String) = files[name]
        override fun write(name: String, data: String) { files[name] = data }
    }

    private val out = File("build/screens").apply { mkdirs() }
    private val pf = FakePlatform()
    private val g = Game(pf, Synth())

    private fun frames(n: Int) = repeat(n) { g.update(); g.render() }
    private fun tap(b: Btn, wait: Int = 4) { g.press(b); g.update(); g.release(b); frames(wait) }
    private fun shot(name: String, scale: Int = 3) {
        val fb = g.render()
        Png.write(File(out, "$name.png"), fb, Game.CW, Game.CH, scale)
    }
    /** Press A until the scene shows a choice (or a different screen appears). */
    private fun advanceToChoice(max: Int = 200): Boolean {
        repeat(max) {
            val s = g.top
            if (s is SceneScreen && hasMenu(s)) return true
            if (s !is SceneScreen) return false
            tap(Btn.A, 3)
        }
        return false
    }
    private fun hasMenu(s: SceneScreen): Boolean {
        val f = SceneScreen::class.java.getDeclaredField("choiceMenu"); f.isAccessible = true
        val f2 = SceneScreen::class.java.getDeclaredField("giftMenu"); f2.isAccessible = true
        return f.get(s) != null || f2.get(s) != null
    }

    @Test fun captureTour() {
        frames(120); shot("01_title")
        tap(Btn.A, 10)                                  // NEW GAME
        assertTrue(g.top is SceneScreen)
        frames(10); shot("02_intro_title_card")
        tap(Btn.A, 30); frames(80); shot("03_intro_room")
        repeat(12) { tap(Btn.A, 3); tap(Btn.A, 3) }
        frames(60); shot("04_intro_dialogue")
        assertTrue(advanceToChoice())
        shot("05_choice")
        tap(Btn.DOWN); tap(Btn.A, 20)
        frames(40); shot("06_reaction")
        // finish intro -> map
        repeat(400) { if (g.top is SceneScreen) { tap(Btn.A, 2) } }
        assertTrue("map after intro, got ${g.top}", g.top is MapScreen)
        frames(170); shot("07_map")
        // shop: shop is selected by default on the map
        tap(Btn.A, 2); frames(120)
        assertTrue(g.top is ShopScreen)
        tap(Btn.A, 4); shot("08_shop")
        tap(Btn.B, 4)
        // go to the cafe (top-left of the bottom row)
        tap(Btn.LEFT); tap(Btn.A, 2); frames(150)
        assertTrue(g.top is SceneScreen)
        frames(30); tap(Btn.A, 30); shot("09_cafe")
        tap(Btn.START, 4); shot("10_pause")
        tap(Btn.DOWN); tap(Btn.A, 4); tap(Btn.DOWN); tap(Btn.A, 4); shot("11_save"); tap(Btn.B); tap(Btn.B)
        assertNotNull(pf.files["save_2.json"])
        advanceToChoice(); shot("12_choice_cafe")
        tap(Btn.A, 10)
        repeat(30) { tap(Btn.A, 2) }
        if (g.top is SceneScreen && hasMenu(g.top as SceneScreen)) { shot("13_gift_prompt") }
        repeat(400) { if (g.top is SceneScreen) tap(Btn.A, 2) }
        assertTrue(g.top is MapScreen)
        // day 2: arcade -> mini-game
        frames(170); tap(Btn.UP); tap(Btn.RIGHT); tap(Btn.A, 2); frames(200)
        var guard = 0
        while (g.top !is MinigameScreen && guard++ < 300) { if (g.top is SceneScreen && hasMenu(g.top as SceneScreen)) tap(Btn.A, 4) else tap(Btn.A, 2) }
        assertTrue(g.top is MinigameScreen)
        frames(200); g.press(Btn.RIGHT); frames(400); shot("14_minigame"); g.release(Btn.RIGHT)
        frames(30 * 60)
        shot("15_minigame_result"); frames(60); tap(Btn.A, 10)
        assertTrue(g.top is SceneScreen)
        // settings + palettes
        g.replace(SettingsScreen(g)); tap(Btn.DOWN); tap(Btn.DOWN); tap(Btn.DOWN); tap(Btn.DOWN); tap(Btn.RIGHT); shot("16_settings_pink")
        g.settings.palette = 0
        // gallery + CG + endings, rendered directly
        g.gallery.addAll(Backgrounds.CGS.map { it.id })
        g.replace(GalleryScreen(g)); frames(2); shot("17_gallery")
        for (cg in Backgrounds.CGS) { g.replace(CgViewScreen(g, cg.id)); frames(3); shot("cg_${cg.id}") }
        g.replace(EndingScreen(g, "best")); frames(80); tap(Btn.A, 400); shot("18_ending_text")
        g.replace(CreditsScreen(g)); frames(400); shot("19_credits")
        // every background with both characters
        for (bg in Backgrounds.IDS) { g.st.bg = bg; g.st.reese = "happy"; g.replace(SceneScreen(g)); frames(1); shot("bg_$bg") }
    }

    @Test fun saveAndLoadResumesSameLine() {
        g.newGame(); frames(5)
        repeat(20) { tap(Btn.A, 3) }
        val before = g.st.toJson()
        g.saveTo(2, true)
        repeat(10) { tap(Btn.A, 3) }
        assertTrue(g.load(2))
        assertEquals(Json.parse(before).obj()["pc"], Json.parse(g.st.toJson()).obj()["pc"])
    }

    @Test fun synthRendersEverySong() {
        val s = Synth()
        val buf = ShortArray(22050)
        for (id in Synth.SONGS.keys) {
            s.playMusic(id); s.render(buf, buf.size)
            assertTrue("song $id is silent", buf.any { it.toInt() != 0 })
        }
        for (id in Synth.SFX.keys) { s.playMusic(null); s.sfx(id); s.render(buf, 4000); assertTrue("sfx $id", buf.take(4000).any { it.toInt() != 0 }) }
        // write a short preview of the title theme
        s.playMusic(null); s.playMusic("title")
        val pcm = ShortArray(22050 * 8); s.render(pcm, pcm.size)
        val f = File(out, "title_theme.wav")
        f.outputStream().use { o ->
            val data = java.nio.ByteBuffer.allocate(44 + pcm.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            data.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
                .putInt(22050).putInt(44100).putShort(2).putShort(16).put("data".toByteArray()).putInt(pcm.size * 2)
            pcm.forEach { data.putShort(it) }
            o.write(data.array())
        }
    }
}

/** Minimal PNG writer (no java.awt on the Android unit-test classpath). */
object Png {
    fun write(f: File, argb: IntArray, w: Int, h: Int, k: Int) {
        val W = w * k; val H = h * k
        val raw = java.io.ByteArrayOutputStream()
        for (y in 0 until H) {
            raw.write(0)
            for (x in 0 until W) { val c = argb[(y / k) * w + x / k]; raw.write(c shr 16 and 255); raw.write(c shr 8 and 255); raw.write(c and 255) }
        }
        val o = java.io.DataOutputStream(f.outputStream())
        o.write(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
        fun chunk(type: String, data: ByteArray) {
            o.writeInt(data.size); val t = type.toByteArray(); o.write(t); o.write(data)
            val crc = java.util.zip.CRC32(); crc.update(t); crc.update(data); o.writeInt(crc.value.toInt())
        }
        val hdr = java.nio.ByteBuffer.allocate(13).putInt(W).putInt(H).put(8).put(2).put(0).put(0).put(0).array()
        chunk("IHDR", hdr)
        val def = java.io.ByteArrayOutputStream()
        java.util.zip.DeflaterOutputStream(def).use { it.write(raw.toByteArray()) }
        chunk("IDAT", def.toByteArray())
        chunk("IEND", ByteArray(0))
        o.close()
    }
}
