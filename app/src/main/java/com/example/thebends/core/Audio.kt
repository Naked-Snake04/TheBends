package com.example.thebends.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat

import com.example.thebends.R
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

public class Audio (context: Context): Runnable {
    var numberPolyNotes = IntArray(6)

    private val bufferSize = 2048 * 2 // буфер audioRecoder
    private val samples = 8192 // Размер БПФ
    private val analysisStep = 2048 // размер шага окна

    private var sampleRate : Int = 0 // Частота дискретизации
    private var signalRMS = 0f // Среднее квадратичное сигнала

    private var thread: Thread? = null
    private var running = false
    private var context: Context
    private lateinit var audioRecord: AudioRecord

    private var xReal: DoubleArray
    private var xImage: DoubleArray
    private var amps: DoubleArray
    private var dx: DoubleArray

    private final val maxFreq : Double = 2100.0
    private var maxLevel : Double = 0.0
    private var buff: DoubleArray

    public fun getSampleRate() = sampleRate
    public fun getSignalRMS() = signalRMS

    private var fft: FFT

    init {
        this.context = context
        buff = DoubleArray(samples)
        xReal = DoubleArray(samples)
        xImage = DoubleArray(samples)
        amps = DoubleArray(samples/2)
        dx = DoubleArray(samples/2)

        fft = FFT(samples)

    }

    fun start() {
        if (running) return
        running = true
        thread = Thread("Audio")
        thread!!.start()
    }

    override fun run() {
        processAudio()
    }

    private fun processAudio() {
        val resources : Resources = context.resources;
        // Частота дискретизации
        val sampleRates : IntArray = resources.getIntArray(R.array.sample_rates)

        var size : Int = 0
        var state : Int = 0
        run outer@ {// для использования break в конце
        sampleRates.forEach {
            // проверяем частоту дискретизации
            size = AudioRecord.getMinBufferSize(it,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT)

            // Если частота дискретизации имеет неправильное значение - продолжаем
            if (size == AudioRecord.ERROR_BAD_VALUE) {
                return@forEach
            }

            // выходим если поймали ошибку
            if (size == AudioRecord.ERROR) {
                thread = null
                return
            }

            if (context.let { it1 ->
                    ActivityCompat.checkSelfPermission(
                        it1,
                        Manifest.permission.RECORD_AUDIO
                    )
                } != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            // Создаем объект AudioRecord
            audioRecord = AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                it,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(size, bufferSize)
            )

            // Проверяем состояние
            state = audioRecord!!.state

            if (state != AudioRecord.STATE_INITIALIZED) {
                audioRecord!!.release()
                return@forEach
            }

            this.sampleRate = it
            return@outer
        }
        }

        if (size == AudioRecord.ERROR_BAD_VALUE) {
            thread = null
            return
        }

        if (state != AudioRecord.STATE_INITIALIZED) {
            audioRecord!!.release()
            thread = null
            return
        }

        // создаём буффер для вводимых данных

        // Create buffer for input data
        var data = ShortArray(analysisStep)
        var dataDecimate = DoubleArray(analysisStep)

        // Начинаем запись
        audioRecord!!.startRecording()

        while (thread != null) {
            size = audioRecord!!.read(data, 0, analysisStep)
            if (size == 0) {
                thread = null;
                break
            }

            var sum : Double = 0.0
            for (i in 0..analysisStep) {
                dataDecimate[i] = data[i] / 32768.0
                val v: Double = data[i] / 32768.0
                sum += v * v
            }

            signalRMS = sqrt(sum / analysisStep).toFloat()

            amps!![0] = 0.0
            var max : Int = 0
            val maxF : Int = ((maxFreq * samples / sampleRate).toInt())

            for (i in 1..maxF) {
                dx!![i] = amps[i] - amps[i-1]
                if (amps[i] > amps[max]) max = i
            }
            maxLevel = (20.0 * ln(amps[max])/ln(10.0))


        }
    }
}