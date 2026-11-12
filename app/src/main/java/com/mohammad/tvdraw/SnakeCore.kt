package com.mohammad.tvdraw

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** One snake. The body is the trail the head has left behind, oldest point first. */
class Serpent(val side: Int) {
    var playing = false; var alive = true
    var x = 0f; var y = 0f
    var heading = 0f                      // radians, direction of travel
    var want = Float.NaN                  // direction the player is steering toward
    var len = 0f                          // body length in pixels
    var score = 0
    val px = ArrayList<Float>(); val py = ArrayList<Float>()
    var acc = 0f                          // distance travelled since the last trail point
}
class Pellet(val x: Float, val y: Float, val gold: Boolean, val born: Float, val life: Float)

/**
 * Snake rules with no drawing in them, so they can be tested exactly.
 * The field wraps: leaving one edge re-enters at the opposite one, and every distance is measured around the wrap.
 * A snake's head moves at a fixed speed and can only turn so fast, so it sweeps a real arc and cannot fold onto its own neck.
 */
class SnakeCore(val w: Float, val h: Float, val k: Float, private val rnd: Random = Random.Default) {
    companion object { const val SELF = 1; const val HIT_OTHER = 2; const val HEAD_ON = 3; const val TIME = 4; const val DRAW = 2 }
    val s = arrayOf(Serpent(0), Serpent(1))
    val food = ArrayList<Pellet>()
    var level = 0
    var time = 0f
    var over = false; var winner = -1; var reason = 0
    val crashed = BooleanArray(2)
    val eats = IntArray(2)                               // counts up each time that side eats (for sounds / vibration)
    var lastEatGold = false
    private var spawnIn = 0f

    val r get() = 22f * k                                // body radius
    val step get() = 5f * k                              // spacing of trail points
    fun speed() = floatArrayOf(230f, 310f, 400f)[level] * k
    fun turnRate() = floatArrayOf(3.4f, 4.0f, 4.6f)[level]
    fun foodLife() = floatArrayOf(10f, 7f, 4.8f)[level]
    fun foodCount() = intArrayOf(5, 4, 3)[level]
    private val hitDist get() = r * 1.5f                 // head touching a body
    private val neck get() = r * 3.4f                    // the part right behind a head that it can never reach

    fun wrapX(v: Float): Float { var a = v % w; if (a < 0) a += w; return a }
    fun wrapY(v: Float): Float { var a = v % h; if (a < 0) a += h; return a }
    /** Distance between two points on the wrapping field. */
    fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        var dx = abs(ax - bx); if (dx > w / 2) dx = w - dx
        var dy = abs(ay - by); if (dy > h / 2) dy = h - dy
        return hypot(dx, dy)
    }

    /** Signed shortest step from a to b along one axis of the wrapping field. */
    private fun delta(a: Float, b: Float, size: Float): Float { var d = b - a; if (d > size / 2) d -= size; if (d < -size / 2) d += size; return d }
    /** True when point (px,py) lies in front of this snake's head: you can only crash into what you are driving at. */
    private fun ahead(a: Serpent, px: Float, py: Float): Boolean {
        val dx = delta(a.x, px, w); val dy = delta(a.y, py, h); val d = hypot(dx, dy)
        return d < 0.001f || (dx * cos(a.heading) + dy * sin(a.heading)) / d > 0.5f          // within 60 degrees of where it is heading
    }

    fun reset(p0: Boolean, p1: Boolean) {
        food.clear(); time = 0f; over = false; winner = -1; reason = 0; spawnIn = 0f
        crashed[0] = false; crashed[1] = false
        val play = booleanArrayOf(p0, p1)
        for (sn in s) {
            sn.playing = play[sn.side]; sn.alive = true; sn.score = 0; sn.acc = 0f; sn.want = Float.NaN
            sn.len = 260f * k
            sn.x = w * (if (sn.side == 0) 0.22f else 0.78f); sn.y = h * (if (sn.side == 0) 0.36f else 0.64f)
            sn.heading = if (sn.side == 0) 0f else Math.PI.toFloat()
            sn.px.clear(); sn.py.clear()
            val n = (sn.len / step).toInt()
            for (i in n downTo 1) { sn.px.add(wrapX(sn.x - cos(sn.heading) * step * i)); sn.py.add(wrapY(sn.y - sin(sn.heading) * step * i)) }
        }
    }

    /** Joystick direction for one snake; a tiny push is ignored so a resting thumb does not steer. */
    fun steer(side: Int, x: Float, y: Float) { if (hypot(x, y) > 0.25f) s[side].want = atan2(y, x) }

    fun step(dt: Float) {
        if (over || dt <= 0f) return
        val n = maxOf(1, ceil(speed() * dt / (r * 0.3f)).toInt())          // never move more than a fraction of a body width at once
        for (i in 0 until n) { advance(dt / n); if (over) return }
    }

    private fun advance(dt: Float) {
        time += dt
        for (sn in s) {
            if (!sn.playing || !sn.alive) continue
            if (!sn.want.isNaN()) {
                var d = (sn.want - sn.heading) % (2 * Math.PI.toFloat())
                if (d > Math.PI) d -= 2 * Math.PI.toFloat(); if (d < -Math.PI) d += 2 * Math.PI.toFloat()
                val max = turnRate() * dt
                sn.heading += d.coerceIn(-max, max)
            }
            val v = speed() * dt
            sn.x = wrapX(sn.x + cos(sn.heading) * v); sn.y = wrapY(sn.y + sin(sn.heading) * v)
            sn.acc += v
            while (sn.acc >= step) {                                   // drop trail points exactly one spacing apart, whatever the speed
                sn.acc -= step
                sn.px.add(wrapX(sn.x - cos(sn.heading) * sn.acc)); sn.py.add(wrapY(sn.y - sin(sn.heading) * sn.acc))
            }
            val keep = (sn.len / step).toInt()
            while (sn.px.size > keep) { sn.px.removeAt(0); sn.py.removeAt(0) }
        }
        // food
        val it = food.iterator()
        while (it.hasNext()) { val f = it.next()
            if (time - f.born > f.life) { it.remove(); continue }
            for (sn in s) if (sn.playing && sn.alive && dist(sn.x, sn.y, f.x, f.y) < r * 1.25f + 16f * k) {
                val pts = if (f.gold) 3 else 1
                sn.score += pts; sn.len += 55f * k * pts; eats[sn.side]++; lastEatGold = f.gold; it.remove(); break
            }
        }
        spawnIn -= dt
        if (food.size < foodCount() && spawnIn <= 0f) { spawn(); spawnIn = 0.45f }
        // collisions: the snake whose HEAD runs into a body is the one that crashed
        var hit0 = 0; var hit1 = 0
        for (a in s) {
            if (!a.playing || !a.alive) continue
            var why = 0
            val skip = ceil(neck / step).toInt()
            for (i in 0 until a.px.size - skip) if (dist(a.x, a.y, a.px[i], a.py[i]) < hitDist) { why = SELF; break }
            val b = s[1 - a.side]
            if (why == 0 && b.playing && b.alive) {
                // touching the other snake only counts against the one driving INTO it; being rammed from the side or behind is not your crash
                if (dist(a.x, a.y, b.x, b.y) < r * 2f && ahead(a, b.x, b.y)) why = HEAD_ON
                else for (i in 0 until b.px.size) if (dist(a.x, a.y, b.px[i], b.py[i]) < hitDist && ahead(a, b.px[i], b.py[i])) { why = HIT_OTHER; break }
            }
            if (a.side == 0) hit0 = why else hit1 = why
        }
        if (hit0 != 0 || hit1 != 0) finish(hit0, hit1)
    }

    private fun finish(hit0: Int, hit1: Int) {
        over = true
        crashed[0] = hit0 != 0; crashed[1] = hit1 != 0
        if (crashed[0]) s[0].alive = false; if (crashed[1]) s[1].alive = false
        val two = s[0].playing && s[1].playing
        reason = if (hit0 != 0) hit0 else hit1
        winner = if (!two) -1 else if (crashed[0] && crashed[1]) DRAW else if (crashed[0]) 1 else 0
        if (crashed[0] && crashed[1]) reason = HEAD_ON else if (reason == HEAD_ON) reason = HIT_OTHER      // only one of them drove into the other
    }

    /** Time ran out: the longer snake wins. */
    fun timeUp() {
        if (over) return
        over = true; reason = TIME
        val two = s[0].playing && s[1].playing
        winner = if (!two) -1 else if (s[0].score > s[1].score) 0 else if (s[1].score > s[0].score) 1 else DRAW
    }

    private fun spawn() {
        for (tries in 0 until 30) {
            val x = 70f * k + rnd.nextFloat() * (w - 140f * k); val y = 150f * k + rnd.nextFloat() * (h - 220f * k)
            var free = true
            for (sn in s) { if (!sn.playing) continue
                if (dist(x, y, sn.x, sn.y) < r * 7f) { free = false; break }                 // not right in front of a head
                var i = 0; while (i < sn.px.size) { if (dist(x, y, sn.px[i], sn.py[i]) < r * 3f) { free = false; break }; i += 3 }
                if (!free) break }
            for (f in food) if (dist(x, y, f.x, f.y) < 90f * k) free = false
            if (!free) continue
            val gold = rnd.nextFloat() < 0.12f
            food.add(Pellet(x, y, gold, time, foodLife() * (if (gold) 0.6f else 0.75f + rnd.nextFloat() * 0.5f)))
            return
        }
    }
}
