package com.freekalizer.pitch

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Radix-2 in-place complex FFT (Cooley–Tukey). [re] and [im] length [n] (power of 2). No allocations.
 */
internal object ComplexFft {
    fun transform(re: FloatArray, im: FloatArray, n: Int, inverse: Boolean) {
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
            var bit = n ushr 1
            while (bit <= j) {
                j -= bit
                bit = bit ushr 1
            }
            j += bit
        }
        var len = 2
        while (len <= n) {
            val half = len shr 1
            val ang = (if (inverse) 2f else -2f) * PI.toFloat() / len
            val wmR = cos(ang)
            val wmI = sin(ang)
            for (i in 0 until n step len) {
                var wR = 1f
                var wI = 0f
                for (k in 0 until half) {
                    val idx = i + k + half
                    val uk = re[i + k]
                    val uik = im[i + k]
                    val tr = wR * re[idx] - wI * im[idx]
                    val ti = wR * im[idx] + wI * re[idx]
                    re[i + k] = uk + tr
                    im[i + k] = uik + ti
                    re[idx] = uk - tr
                    im[idx] = uik - ti
                    val nwr = wR * wmR - wI * wmI
                    wI = wR * wmI + wI * wmR
                    wR = nwr
                }
            }
            len = len shl 1
        }
        if (inverse) {
            val scale = 1f / n
            for (i in 0 until n) {
                re[i] *= scale
                im[i] *= scale
            }
        }
    }
}
