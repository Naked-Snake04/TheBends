package com.example.thebends.core

class Filter (a: DoubleArray, b: DoubleArray) {
    private var p: Int = 0
    private var q: Int = 0
    private var a: DoubleArray
    private var b: DoubleArray
    private var xMem: DoubleArray
    private var yMem: DoubleArray

    init {
        this.p = a.size
        this.q = b.size
        this.a = a.clone()
        this.b = b.clone()
        xMem = DoubleArray(p)
        yMem = DoubleArray(q)

        for (i in 0..<p step 1) xMem[i] = 0.0
        for (i in 0..<q step 1) yMem[i] = 0.0
    }

    fun filtering(x: Double): Double {
        var x1: Double = x

        if (x1.isNaN()) x1 = 0.0

        // сдвиг
        if (p >= 2) {
            for (i in p - 1 downTo 1) xMem[i] = xMem[i - 1]
        }
        if (q >= 2) {
            for (i in q - 1 downTo 1) yMem[i] = yMem[i - 1]
        }
        xMem[0] = x1
        yMem[0] = 0.0
        for (i in 0 until p) yMem[0] += a[i] * xMem[i]
        for (i in 1 until q) yMem[0] += -1 * b[i] * yMem[i]
        return yMem[0]
    }
}