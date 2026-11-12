package com.mohammad.tvdraw

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Glass-crack geometry: polylines (x0,y0,x1,y1,...) radiating from an impact point. */
object CrackGen {
    fun make(cx: Float, cy: Float, reach: Float, s: Float, rnd: Random = Random.Default): List<FloatArray> {
        val lines = mutableListOf<FloatArray>()
        val n = rnd.nextInt(9, 14)
        val step = 40f * s
        val rays = mutableListOf<List<Float>>()
        for (i in 0 until n) {
            var a = (i + rnd.nextFloat() * 0.7f) * (2 * PI.toFloat() / n)
            val len = reach * (0.45f + rnd.nextFloat() * 0.55f)
            val pts = mutableListOf(cx, cy)
            var x = cx; var y = cy; var d = 0f
            while (d < len) {
                a += (rnd.nextFloat() - 0.5f) * 0.35f
                x += cos(a) * step; y += sin(a) * step; d += step
                pts.add(x); pts.add(y)
                if (d > step * 2 && rnd.nextFloat() < 0.22f) {
                    var ba = a + (if (rnd.nextBoolean()) 1 else -1) * (0.5f + rnd.nextFloat() * 0.4f)
                    val bl = len * (0.15f + rnd.nextFloat() * 0.25f)
                    val b = mutableListOf(x, y)
                    var bx = x; var by = y; var bd = 0f
                    while (bd < bl) {
                        ba += (rnd.nextFloat() - 0.5f) * 0.4f
                        bx += cos(ba) * step; by += sin(ba) * step; bd += step
                        b.add(bx); b.add(by)
                    }
                    lines.add(b.toFloatArray())
                }
            }
            rays.add(pts); lines.add(pts.toFloatArray())
        }
        for (k in intArrayOf(1, 2, 4)) for (i in 0 until n) {
            val p = rays[i]; val q = rays[(i + 1) % n]
            if (rnd.nextFloat() < 0.65f && p.size > 2 * k + 1 && q.size > 2 * k + 1)
                lines.add(floatArrayOf(p[2 * k], p[2 * k + 1], q[2 * k], q[2 * k + 1]))
        }
        return lines
    }
}
