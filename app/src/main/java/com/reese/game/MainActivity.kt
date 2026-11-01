package com.reese.game

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.reese.game.core.Btn
import com.reese.game.core.Game
import com.reese.game.core.Platform
import com.reese.game.core.Synth
import java.io.File

class MainActivity : Activity() {
    private lateinit var view: GameView
    private lateinit var audio: AudioOut
    private lateinit var game: Game

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val synth = Synth()
        audio = AudioOut(synth)
        view = GameView(this)
        game = Game(AndroidPlatform(this, view), synth)
        view.game = game
        setContentView(view)
        hideBars()
    }

    private fun hideBars() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideBars() }
    override fun onResume() { super.onResume(); audio.start(); view.resume() }
    override fun onPause() { view.pause(); audio.stop(); super.onPause() }

    /** Android back button opens the pause menu (or goes back in menus). */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { game.backKey() }

    // Physical controllers / keyboards work too.
    private fun keyToBtn(code: Int): Btn? = when (code) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> Btn.UP
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> Btn.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> Btn.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> Btn.RIGHT
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_DPAD_CENTER -> Btn.A
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_X -> Btn.B
        KeyEvent.KEYCODE_BUTTON_START -> Btn.START
        KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_TAB -> Btn.SELECT
        else -> null
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val b = keyToBtn(keyCode) ?: return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0) game.press(b)
        return true
    }
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val b = keyToBtn(keyCode) ?: return super.onKeyUp(keyCode, event)
        game.release(b)
        return true
    }
}

class AndroidPlatform(private val act: Activity, private val view: View) : Platform {
    override fun asset(path: String): String = act.assets.open(path).bufferedReader().use { it.readText() }
    override fun read(name: String): String? {
        val f = File(act.filesDir, name)
        return if (f.exists()) runCatching { f.readText() }.getOrNull() else null
    }
    override fun write(name: String, data: String) {
        // write-then-rename so a crash never leaves a half-written save
        val tmp = File(act.filesDir, "$name.tmp")
        tmp.writeText(data)
        tmp.renameTo(File(act.filesDir, name))
    }
    override fun haptic() { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    override fun exit() { act.finish() }
}

/** Draws the 180x320 console canvas, integer-scaled with nearest-neighbor. */
class GameView(ctx: Context) : View(ctx), Choreographer.FrameCallback {
    var game: Game? = null
    private val bmp = Bitmap.createBitmap(Game.CW, Game.CH, Bitmap.Config.ARGB_8888)
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private val gridPaint = Paint().apply { color = Color.argb(28, 0, 0, 0); strokeWidth = 1f }
    private val dst = Rect()
    private var running = false
    private var lastNs = 0L
    private var acc = 0.0
    private var scale = 1f
    private var offX = 0f
    private var offY = 0f

    init { isFocusable = true }

    fun resume() { if (!running) { running = true; lastNs = 0; Choreographer.getInstance().postFrameCallback(this) } }
    fun pause() { running = false; Choreographer.getInstance().removeFrameCallback(this) }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val g = game
        if (g != null) {
            if (lastNs == 0L) lastNs = frameTimeNanos
            acc += (frameTimeNanos - lastNs) / 1e9
            lastNs = frameTimeNanos
            if (acc > 0.25) acc = 0.25
            // fixed 60 Hz logic regardless of display refresh rate
            while (acc >= 1.0 / 60) { g.update(); acc -= 1.0 / 60 }
            invalidate()
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val fit = minOf(w / Game.CW.toFloat(), h / Game.CH.toFloat())
        // integer scale keeps pixels perfectly square when possible
        scale = if (fit >= 2f) Math.floor(fit.toDouble()).toFloat() else fit
        val dw = Game.CW * scale; val dh = Game.CH * scale
        offX = (w - dw) / 2f; offY = (h - dh) / 2f
        dst.set(offX.toInt(), offY.toInt(), (offX + dw).toInt(), (offY + dh).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        val fb = g.render()
        bmp.setPixels(fb, 0, Game.CW, 0, 0, Game.CW, Game.CH)
        canvas.drawColor(Game.BODY)
        canvas.drawBitmap(bmp, null, dst, paint)
        // faint LCD pixel grid on the screen area for that handheld feel
        if (scale >= 4f) {
            val x0 = offX + Game.SX * scale; val y0 = offY + Game.SY * scale
            val x1 = x0 + 160 * scale; val y1 = y0 + 144 * scale
            var x = x0
            while (x <= x1) { canvas.drawLine(x, y0, x, y1, gridPaint); x += scale }
            var y = y0
            while (y <= y1) { canvas.drawLine(x0, y, x1, y, gridPaint); y += scale }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val g = game ?: return true
        fun cx(i: Int) = (e.getX(i) - offX) / scale
        fun cy(i: Int) = (e.getY(i) - offY) / scale
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                g.touchDown(e.getPointerId(i), cx(i), cy(i))
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) g.touchMove(e.getPointerId(i), cx(i), cy(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> g.touchUp(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> for (i in 0 until e.pointerCount) g.touchUp(e.getPointerId(i))
        }
        return true
    }
}

/** Streams the synth to the speaker on a background thread. */
class AudioOut(private val synth: Synth) {
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({
            val rate = synth.rate
            val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(min, 4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            val buf = ShortArray(512)
            track.play()
            while (running) {
                synth.render(buf, buf.size)
                track.write(buf, 0, buf.size)
            }
            track.stop(); track.release()
        }, "reese-audio")
        thread!!.priority = Thread.MAX_PRIORITY
        thread!!.start()
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }
}
