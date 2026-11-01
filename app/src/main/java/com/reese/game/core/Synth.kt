package com.reese.game.core

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * Game Boy flavoured chiptune synth. Everything is generated in code:
 *  - pulse 1: melody, pulse 2: arpeggios, wave: triangle bass, noise: drums
 *  - a separate pulse/noise voice for sound effects.
 * Songs are written as a chord progression + an 8th-note melody (see SONGS).
 */
class Synth(val rate: Int = 22050) {

    // ---------------- song data ----------------
    class Song(val bpm: Int, val chords: String, val melody: String, val drums: String = "k...h...s...h...",
               val bass: String = "pulse", val duty: Double = 0.5, val arp: Boolean = true)

    companion object {
        val SONGS: Map<String, Song> = mapOf(
            // title: hopeful I-V-vi-IV
            "title" to Song(118, "C G Am F C G F F",
                "E5 - G5 - C6 - G5 E5  D5 - G5 - B5 - A5 G5  C5 - E5 - A5 - G5 E5  F5 - A5 - G5 - - .  " +
                "E5 - G5 - C6 - D6 E6  D6 - B5 - G5 - A5 B5  C6 - A5 - F5 - A5 -  G5 - - - . . . .",
                "k...h...s...h.h.", "walk", 0.5),
            "map" to Song(112, "G D Em C",
                "G5 . B5 . D6 - B5 .  A5 . F#5 . A5 - D5 .  G5 . E5 . G5 - B5 .  C6 - B5 - A5 - G5 .",
                "k.h.s.h.k.h.s.hh", "pulse", 0.25),
            "cafe" to Song(92, "Fmaj7 Em7 Dm7 Cmaj7",
                "A5 - C6 - E6 - D6 C6  B5 - G5 - E5 - D5 E5  F5 - A5 - C6 - B5 A5  G5 - E5 - - - . .",
                "k..hs..hk.khs..h", "walk", 0.25),
            "park" to Song(128, "C F C G",
                "C5 E5 G5 - E5 G5 C6 -  A5 - F5 A5 C6 - A5 -  G5 E5 C5 E5 G5 - E5 -  D5 - G5 - B5 - - .",
                "k.h.s.h.k.h.s.h.", "pulse", 0.5),
            "arcade" to Song(150, "Am F C G",
                "A5 A5 C6 A5 E6 - D6 C6  C6 C6 A5 C6 F6 - E6 C6  E6 - D6 C6 G5 - C6 -  B5 - D6 - G6 - - .",
                "k.hhs.hhk.hks.hs", "pulse", 0.25),
            "rooftop" to Song(80, "Am F C E Am F G G",
                "E5 - - A5 C6 - B5 A5  A5 - - - F5 - . .  G5 - - C6 E6 - D6 C6  B5 - - - G#5 - . .  " +
                "E5 - - A5 C6 - D6 E6  F6 - E6 - D6 - C6 -  D6 - - - B5 - - -  G5 - - - . . . .",
                "k.......s.......", "long", 0.5),
            "school" to Song(116, "D Bm G A",
                "F#5 - A5 - D6 - A5 .  F#5 - D5 - B4 - D5 .  G5 - B5 - D6 - B5 .  A5 - G5 - F#5 - E5 .",
                "k.h.s.hhk.h.s.h.", "walk", 0.5),
            "room" to Song(88, "C Am Dm G",
                "E5 - - G5 - - C6 -  C6 - - B5 A5 - E5 -  F5 - - A5 - - D6 -  B5 - - A5 G5 - . .",
                "k.......s.......", "long", 0.25),
            "ramen" to Song(104, "Dm C Bb C",
                "D6 - C6 A5 G5 - A5 .  E5 - G5 - C6 - A5 .  D6 - F6 - D6 C6 A5 -  G5 - E5 - C6 - . .",
                "k..hs.h.k.hhs.h.", "pulse", 0.25),
            "brave" to Song(132, "E C#m A B E C#m A B",
                "B5 - G#5 - E5 - G#5 B5  C#6 - B5 - G#5 - E5 -  A5 - C#6 - E6 - C#6 A5  B5 - - - D#6 - - -  " +
                "E6 - B5 - G#5 - B5 E6  F#6 - E6 - C#6 - B5 -  A5 - B5 - C#6 - E6 -  D#6 - - - B5 - - -",
                "k.h.s.h.k.khs.h.", "walk", 0.5),
            "sunset" to Song(96, "E C#m A B",
                "B5 - - G#5 E5 - G#5 -  C#6 - - B5 G#5 - - -  A5 - - C#6 E6 - C#6 -  B5 - - - - - . .",
                "k.......s.......", "long", 0.25),
            "minigame" to Song(160, "C Am F G",
                "C6 E6 G6 E6 C6 E6 G6 -  A5 C6 E6 C6 A5 C6 E6 -  F5 A5 C6 A5 F6 - E6 -  D6 - B5 - G5 - - .",
                "khhhshhhkhkhshhs", "pulse", 0.25),
            "ending" to Song(90, "F G Em Am F G C C",
                "A5 - C6 - F6 - E6 C6  D6 - B5 - G5 - - -  G5 - B5 - E6 - D6 B5  C6 - A5 - E5 - - -  " +
                "A5 - C6 - F6 - A6 G6  G6 - F6 - D6 - B5 -  C6 - - - E6 - - -  C6 - - - - - . .",
                "k.......s...h...", "walk", 0.5),
            "sad" to Song(70, "Am Em F C",
                "E5 - - - A5 - G5 E5  D5 - - - B4 - . .  C5 - - - F5 - E5 C5  E5 - - - . . . .",
                "................", "long", 0.25),
            "credits" to Song(122, "C G Am Em F C F G",
                "G5 - C6 - E6 - D6 C6  B5 - D6 - G6 - F6 D6  C6 - E6 - A6 - G6 E6  B5 - - - G5 - - -  " +
                "A5 - C6 - F6 - E6 D6  E6 - C6 - G5 - E5 -  F5 - A5 - C6 - F6 -  G6 - - - D6 - B5 -",
                "k.h.s.h.k.h.s.hh", "walk", 0.5),
        )

        private val NOTE_IDX = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)
        fun midi(tok: String): Int? {
            if (tok.isEmpty() || tok[0] !in NOTE_IDX) return null
            var n = NOTE_IDX[tok[0]]!!
            var i = 1
            if (i < tok.length && tok[i] == '#') { n++; i++ } else if (i < tok.length && tok[i] == 'b') { n--; i++ }
            val oct = tok.substring(i).toIntOrNull() ?: 4
            return 12 * (oct + 1) + n
        }
        fun freq(m: Int) = 440.0 * 2.0.pow((m - 69) / 12.0)

        /** Chord symbol -> root midi (octave 3) + intervals. */
        fun chord(sym: String): Pair<Int, IntArray> {
            var i = 1
            var root = NOTE_IDX[sym[0]] ?: 0
            if (i < sym.length && sym[i] == '#') { root++; i++ } else if (i < sym.length && sym[i] == 'b') { root--; i++ }
            val q = sym.substring(i)
            val iv = when (q) {
                "m" -> intArrayOf(0, 3, 7, 12)
                "m7" -> intArrayOf(0, 3, 7, 10)
                "maj7" -> intArrayOf(0, 4, 7, 11)
                "7" -> intArrayOf(0, 4, 7, 10)
                else -> intArrayOf(0, 4, 7, 12)
            }
            return Pair(48 + root, iv)
        }

        // sound effects: list of (midi note or -1 for noise, length in ms)
        val SFX: Map<String, List<Pair<Int, Int>>> = mapOf(
            "blip_eli" to listOf(57 to 22),
            "blip_reese" to listOf(76 to 22),
            "blip_other" to listOf(67 to 22),
            "move" to listOf(81 to 18),
            "select" to listOf(84 to 40, 91 to 60),
            "back" to listOf(76 to 40, 69 to 60),
            "heart_up" to listOf(72 to 70, 76 to 70, 79 to 70, 84 to 160),
            "heart_down" to listOf(67 to 90, 63 to 90, 60 to 90, 59 to 220),
            "catch" to listOf(88 to 30, 93 to 40),
            "bad" to listOf(-1 to 140),
            "buy" to listOf(83 to 60, 88 to 200),
            "gift" to listOf(79 to 60, 83 to 60, 86 to 60, 91 to 200),
            "start" to listOf(72 to 80, 79 to 80, 84 to 80, 88 to 80, 91 to 240),
            "day" to listOf(67 to 90, 72 to 90, 76 to 180),
            "shake" to listOf(-1 to 200),
            "door" to listOf(60 to 50, 64 to 50),
        )
    }

    // ---------------- state ----------------
    var musicVol = 0.7f
    var sfxVol = 0.8f

    private var song: Song? = null
    var songId: String? = null; private set
    private var chordList: List<Pair<Int, IntArray>> = emptyList()
    private var melody: List<String> = emptyList()
    private var step = 0
    private var stepPos = 0.0
    private var samplesPerStep = 1000.0

    private class Voice { var f = 0.0; var ph = 0.0; var vol = 0.0; var decay = 0.9999; var duty = 0.5; var age = 0 }
    private val p1 = Voice(); private val p2 = Voice(); private val tri = Voice()
    private var drum = ' '; private var drumT = 0
    private var lfsr = 0x7fff
    private var noiseVal = 1.0; private var noiseCnt = 0

    private var sfxQueue: List<Pair<Int, Int>> = emptyList()
    private var sfxIdx = 0; private var sfxLeft = 0
    private val sv = Voice()

    @Synchronized fun playMusic(id: String?) {
        if (id == songId) return
        songId = id
        val s = id?.let { SONGS[it] }
        song = s
        if (s == null) { p1.vol = 0.0; p2.vol = 0.0; tri.vol = 0.0; return }
        chordList = s.chords.split(' ').filter { it.isNotBlank() }.map { chord(it) }
        val mel = s.melody.split(' ').filter { it.isNotBlank() }.toMutableList()
        val need = chordList.size * 8
        while (mel.size < need) mel.add(".")
        melody = mel.take(need)
        samplesPerStep = rate * 60.0 / (s.bpm * 4.0)
        step = -1; stepPos = samplesPerStep
    }

    @Synchronized fun sfx(id: String) {
        val q = SFX[id] ?: return
        sfxQueue = q; sfxIdx = 0; sfxLeft = 0
    }

    private fun noteOn(v: Voice, m: Int, vol: Double, decayMs: Double, duty: Double) {
        v.f = freq(m); v.vol = vol; v.duty = duty; v.age = 0
        v.decay = Math.exp(Math.log(0.05) / (decayMs / 1000.0 * rate))
    }

    private fun tick() {
        val s = song ?: return
        step++
        val total = chordList.size * 16
        val st = step % total
        val ci = st / 16
        val (root, iv) = chordList[ci]
        val inBar = st % 16
        // melody on 8ths
        if (st % 2 == 0) {
            val tok = melody[st / 2]
            when {
                tok == "-" -> {}
                tok == "." -> p1.decay = Math.exp(Math.log(0.05) / (0.03 * rate))
                else -> midi(tok)?.let { noteOn(p1, it, 1.0, 900.0, s.duty) }
            }
        }
        // arpeggio on 16ths
        if (s.arp) noteOn(p2, root + 12 + iv[inBar % iv.size], 0.6, 110.0, 0.125)
        // bass
        when (s.bass) {
            "long" -> if (inBar == 0 || inBar == 8) noteOn(tri, root - 12 + (if (inBar == 8) 7 else 0), 1.0, 1400.0, 0.5)
            "walk" -> if (inBar % 4 == 0) noteOn(tri, root - 12 + intArrayOf(0, 7, 12, 7)[inBar / 4], 1.0, 380.0, 0.5)
            else -> if (inBar % 2 == 0) noteOn(tri, root - 12 + (if (inBar % 4 == 2) 12 else 0), 1.0, 200.0, 0.5)
        }
        // drums
        val d = s.drums[inBar % s.drums.length]
        if (d != '.') { drum = d; drumT = 0 }
    }

    private fun square(v: Voice): Double {
        if (v.vol < 0.001) return 0.0
        // gentle vibrato on held notes
        val vib = if (v.age > rate / 4) 1.0 + 0.004 * sin(v.age * 2 * PI * 5.5 / rate) else 1.0
        v.ph += v.f * vib / rate
        if (v.ph >= 1) v.ph -= 1.0
        v.age++
        val out = if (v.ph < v.duty) v.vol else -v.vol
        v.vol *= v.decay
        return out
    }
    private fun triangle(v: Voice): Double {
        if (v.vol < 0.001) return 0.0
        v.ph += v.f / rate
        if (v.ph >= 1) v.ph -= 1.0
        val out = (if (v.ph < 0.5) 4 * v.ph - 1 else 3 - 4 * v.ph) * v.vol
        // 4-bit quantize like the Game Boy wave channel
        v.vol *= v.decay
        return Math.round(out * 8) / 8.0
    }
    private fun noise(period: Int): Double {
        noiseCnt++
        if (noiseCnt >= period) {
            noiseCnt = 0
            val bit = (lfsr xor (lfsr shr 1)) and 1
            lfsr = (lfsr shr 1) or (bit shl 14)
            noiseVal = if (lfsr and 1 == 0) 1.0 else -1.0
        }
        return noiseVal
    }
    private fun drums(): Double {
        if (drum == ' ') return 0.0
        val tsec = drumT.toDouble() / rate
        drumT++
        return when (drum) {
            'k' -> {
                if (tsec > 0.12) { drum = ' '; 0.0 } else {
                    val f = 140 * Math.exp(-tsec * 30) + 45
                    sin(2 * PI * f * tsec) * Math.exp(-tsec * 25) * 1.6
                }
            }
            's' -> if (tsec > 0.15) { drum = ' '; 0.0 } else noise(2) * Math.exp(-tsec * 28) * 0.9
            'h' -> if (tsec > 0.04) { drum = ' '; 0.0 } else noise(1) * Math.exp(-tsec * 90) * 0.5
            else -> 0.0
        }
    }

    private fun sfxSample(): Double {
        if (sfxIdx >= sfxQueue.size && sfxLeft <= 0) return 0.0
        if (sfxLeft <= 0) {
            val (m, ms) = sfxQueue[sfxIdx++]
            sfxLeft = ms * rate / 1000
            if (m >= 0) noteOn(sv, m, 1.0, ms * 2.0, 0.25) else { sv.f = -1.0; sv.vol = 1.0; sv.decay = Math.exp(Math.log(0.05) / (ms / 1000.0 * rate)) }
        }
        sfxLeft--
        return if (sv.f < 0) { val o = noise(3) * sv.vol; sv.vol *= sv.decay; o } else square(sv)
    }

    /** Fill a buffer with 16-bit mono samples. */
    @Synchronized fun render(out: ShortArray, n: Int) {
        for (i in 0 until n) {
            var mus = 0.0
            if (song != null) {
                stepPos += 1.0
                if (stepPos >= samplesPerStep) { stepPos -= samplesPerStep; tick() }
                mus = square(p1) * 0.20 + square(p2) * 0.07 + triangle(tri) * 0.26 + drums() * 0.14
            }
            val fx = sfxSample() * 0.22
            var v = mus * musicVol + fx * sfxVol
            if (v > 1) v = 1.0 else if (v < -1) v = -1.0
            out[i] = (v * 30000).toInt().toShort()
        }
    }
}
