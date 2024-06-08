package com.example.thebends.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

public class FFT (n: Int) {
    private var n: Int = 0
    private var m: Int = 0
    private var scaleWindow: Double = 0.0
    private var cos: DoubleArray
    private var sin: DoubleArray
    private var window: DoubleArray
    companion object {
        init {
            System.loadLibrary("fft-lib")
//            System.loadLibrary("fft-lib")
        }
    }
    init {
        this.n = n
        this.m = (ln(n.toDouble()) / ln(2.0)).toInt()
        if (n != (1 shl m)) // проверяем на степень двойки
            throw RuntimeException("Длина БПФ должна быть степенью 2.")

        cos = DoubleArray(n / 2)
        sin = DoubleArray(n / 2)

        for (i in 0..< n/2 step 1) {
            cos[i] = cos(-2* PI * i/n)
            sin[i] = sin(-2 * PI * i/n)
        }

        window = DoubleArray(n)

        val a: Double = (n-1)/2.0
        val r = 7.0
        var scale = 0.0

        for (i in 0..<n step 1) {
            window[i] = exp(-0.5 * (r * (i - a) / n).pow(2.0))
            scale += window[i]
        }

        scaleWindow = n / scale
    }

    fun calcAmpSpectrum(real: DoubleArray, image: DoubleArray, ampSpectrum: DoubleArray) {
        mulWindow(real, image)
        fft(real, image)
        calcMagnitude(real, image, ampSpectrum)
    }

    private external fun fft(x: DoubleArray, y: DoubleArray)
    private external fun calcMagnitude(x: DoubleArray, y: DoubleArray, amps: DoubleArray)
    private external fun mulWindow(x: DoubleArray, y: DoubleArray)
}
