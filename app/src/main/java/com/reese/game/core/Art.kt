package com.reese.game.core

/** Loads the text sprite sheet (assets/art/sprites.txt). */
object Art {
    private val sprites = HashMap<String, Sprite>()

    fun load(text: String) {
        sprites.clear()
        var name: String? = null
        val rows = ArrayList<String>()
        fun flush() { name?.let { sprites[it] = Sprite.parse(rows.toList()) }; rows.clear() }
        for (raw in text.lines()) {
            val line = raw.trimEnd()
            if (line.startsWith("#")) continue
            if (line.startsWith("== ")) {
                flush(); name = line.substring(3).trim().split(' ')[0]
            } else if (line.isNotEmpty() && name != null) rows.add(line)
        }
        flush()
    }

    operator fun get(name: String): Sprite = sprites[name] ?: sprites["heart"] ?: Sprite(1, 1, byteArrayOf(-1))
    fun has(name: String) = sprites.containsKey(name)

    val REESE_EXPR = listOf("neutral", "happy", "blush", "surprised", "sad", "annoyed")
    val ELI_EXPR = listOf("neutral", "happy", "nervous", "determined")

    /** Portrait sprite for a character + expression (+ blink frame). */
    fun portrait(who: String, expr: String, blink: Boolean): Sprite {
        val list = if (who == "reese") REESE_EXPR else ELI_EXPR
        val e = if (expr in list) expr else "neutral"
        val base = "${who}_$e"
        return get(if (blink) "${base}_blink" else base)
    }
}
