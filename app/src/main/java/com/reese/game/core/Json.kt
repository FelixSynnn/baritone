package com.reese.game.core

/**
 * Small dependency-free JSON reader/writer, so the story engine runs the same
 * on Android and in plain JVM unit tests.
 * Objects -> LinkedHashMap<String, Any?>, arrays -> List<Any?>,
 * numbers -> Double, plus String / Boolean / null.
 */
object Json {
    fun parse(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value()
        p.ws()
        if (p.i < text.length) p.fail("trailing characters")
        return v
    }

    fun write(v: Any?): String = StringBuilder().also { w(it, v) }.toString()

    private fun w(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> str(sb, v)
            is Boolean -> sb.append(v)
            is Int, is Long -> sb.append(v)
            is Number -> {
                val d = v.toDouble()
                if (d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 1e15) sb.append(d.toLong()) else sb.append(d)
            }
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(',')
                    first = false
                    str(sb, k.toString()); sb.append(':'); w(sb, value)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (x in v) { if (!first) sb.append(','); first = false; w(sb, x) }
                sb.append(']')
            }
            else -> str(sb, v.toString())
        }
    }

    private fun str(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
        }
        sb.append('"')
    }

    private class Parser(val s: String) {
        var i = 0
        fun fail(msg: String): Nothing {
            val line = s.substring(0, minOf(i, s.length)).count { it == '\n' } + 1
            throw IllegalArgumentException("JSON error line $line: $msg")
        }
        fun ws() {
            while (i < s.length) {
                val c = s[i]
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++
                else if (c == '/' && i + 1 < s.length && s[i + 1] == '/') { // allow // comments
                    while (i < s.length && s[i] != '\n') i++
                } else break
            }
        }
        fun value(): Any? {
            if (i >= s.length) fail("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> string()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else fail("unexpected '$c'")
            }
        }
        fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) fail("expected $word")
            i += word.length; return v
        }
        fun num(): Double {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(st, i).toDoubleOrNull() ?: fail("bad number")
        }
        fun string(): String {
            i++
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) fail("unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        val e = s[i++]
                        when (e) {
                            'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                            'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun arr(): List<Any?> {
            i++
            val out = ArrayList<Any?>()
            ws()
            if (s[i] == ']') { i++; return out }
            while (true) {
                ws(); out.add(value()); ws()
                when (s.getOrNull(i)) {
                    ',' -> { i++; ws(); if (s.getOrNull(i) == ']') { i++; return out } }
                    ']' -> { i++; return out }
                    else -> fail("expected , or ]")
                }
            }
        }
        fun obj(): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            ws()
            if (s[i] == '}') { i++; return out }
            while (true) {
                ws()
                if (s.getOrNull(i) != '"') fail("expected key")
                val k = string(); ws()
                if (s.getOrNull(i) != ':') fail("expected :")
                i++; ws()
                out[k] = value(); ws()
                when (s.getOrNull(i)) {
                    ',' -> { i++; ws(); if (s.getOrNull(i) == '}') { i++; return out } }
                    '}' -> { i++; return out }
                    else -> fail("expected , or }")
                }
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun Any?.obj(): Map<String, Any?> = (this as? Map<String, Any?>) ?: emptyMap()
fun Any?.arr(): List<Any?> = (this as? List<Any?>) ?: emptyList()
fun Any?.int(def: Int = 0): Int = (this as? Number)?.toInt() ?: (this as? String)?.toIntOrNull() ?: def
fun Any?.str(def: String = ""): String = (this as? String) ?: this?.toString() ?: def
