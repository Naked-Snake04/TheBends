package com.example.thebends

import android.content.Context
import com.example.thebends.core.Audio
import com.example.thebends.databinding.ActivityMainBinding

class Tuner (context: Context, private val binding: ActivityMainBinding) : Runnable {
    private val second: Long = 1000000000L
    private val updateRate: Float = 30.0F
    private val updateInterval: Float = second / updateRate
    private lateinit var tunerThread: Thread
    private var running: Boolean

    private val audio: Audio

    private var signal: Float = 0F
    private var polyNotes = BooleanArray(6)
    private var polyCents = FloatArray(6)

    private var sampleRate: Int = 0
    private var chromaticFreq: Float = 0F

    init {
        audio = Audio(context)
        running = false
    }

    fun start() {
        if (running) {
            return
        }
        audio.start()
        running = true
        tunerThread = Thread(this)
        tunerThread.start()
    }

    fun stop() {
        if (!running)
            return
        audio.stop()
        running = false
        val t: Thread = tunerThread

        while (t.isAlive)
            Thread.yield()
    }
    override fun run() {
        var upd: Int = 0
        var updl: Int = 0
        var count: Long = 0
        var delta: Float = 0F

        var lastTime: Long = getTime()
        while (running) {
            val nowTime = getTime()
            val elapsedTime = nowTime - lastTime
            lastTime = nowTime

            count += elapsedTime
            delta += elapsedTime / updateRate
            while (delta >= 1) {
                update()
            }
        }
    }

    private fun update() {
        if (audio != null) {
            val newSignal: Float = audio.getSignalRMS()
            signal = ((signal * 9.0F) + newSignal) / 10.0F

            polyNotes = audio.getPolyNotes().copyOf(audio.getPolyNotes().size)
            polyCents = audio.getPolyCents().copyOf(audio.getPolyCents().size)

            sampleRate = audio.getSampleRate()

            chromaticFreq = audio.getChromaticFreq()
            val chromaticCent: Float = audio.getChromaticCent()

            binding.cent.text = chromaticFreq.toString()
            binding.cent.text = chromaticCent.toString()
        }
    }

    private fun getTime(): Long {
        return System.nanoTime()
    }
}