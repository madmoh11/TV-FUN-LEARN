package com.mohammad.tvdraw

/**
 * The rhythm workout: moves arrive on the beat and each player answers with their body.
 * Step left, step right, jump, duck. Timing decides the score and a streak multiplies it.
 * Plain Kotlin with no Android code, so the rules can be tested on their own.
 */
class MoveCore(private val rnd: java.util.Random = java.util.Random()) {
    class Cue(val at: Long, val kind: Int)
    class Player(val cid: String, var name: String) {
        var score = 0; var combo = 0; var best = 0
        var perfect = 0; var good = 0; var miss = 0
        var marks = IntArray(0)                    // one per cue: PENDING, PERFECT, GOOD or MISS
        var judgeSeq = 0; var lastJudge = 0; var lastAt = 0L
    }

    val players = LinkedHashMap<String, Player>()
    var level = 0
    var seconds = 60
    var cues = listOf<Cue>(); private set
    val bpm get() = BPM[level]
    val beatMs get() = 60000L / bpm

    fun join(cid: String, name: String): Boolean {
        players[cid]?.let { it.name = name; return true }
        if (players.size >= MAX_PLAYERS) return false
        players[cid] = Player(cid, name); return true
    }
    fun leave(cid: String) { players.remove(cid) }

    /** Lays out the moves of one round. The first comes after four beats, so everybody hears the tempo first. */
    fun begin() {
        val out = ArrayList<Cue>(); val beat = beatMs
        val end = seconds * 1000L
        var b = 4; var last = -1; var run = 0
        while (b * beat < end) {
            var kind: Int
            do { val r = rnd.nextInt(100); kind = if (r < 30) LEFT else if (r < 60) RIGHT else if (r < 82) JUMP else DUCK } while (kind == last && run >= 2)
            run = if (kind == last) run + 1 else 1; last = kind
            out.add(Cue(b * beat, kind))
            b += when (level) { 0 -> 2; 1 -> if (rnd.nextInt(100) < 35) 1 else 2; else -> if (rnd.nextInt(100) < 70) 1 else 2 }
        }
        cues = out
        for (p in players.values) { p.score = 0; p.combo = 0; p.best = 0; p.perfect = 0; p.good = 0; p.miss = 0; p.marks = IntArray(out.size); p.judgeSeq = 0; p.lastJudge = 0 }
    }

    private fun judge(p: Player, i: Int, mark: Int, t: Long) {
        p.marks[i] = mark; p.judgeSeq++; p.lastJudge = mark; p.lastAt = t
        if (mark == MISS) { p.miss++; p.combo = 0; return }
        p.score += (if (mark == PERFECT) 100 else 50) * multiplier(p)      // the streak so far, before this move
        p.combo++; if (p.combo > p.best) p.best = p.combo
        if (mark == PERFECT) p.perfect++ else p.good++
    }
    /** Every ten moves in a row add one to the multiplier, up to four. */
    fun multiplier(p: Player) = 1 + minOf(p.combo / 10, 3)

    /** A player made a move at time t (milliseconds since the round began). A move that fits no waiting cue is ignored. */
    fun act(cid: String, kind: Int, t: Long): Int {
        val p = players[cid] ?: return 0
        var bestI = -1; var bestD = Long.MAX_VALUE
        for (i in cues.indices) { val c = cues[i]
            if (c.at - t > GOOD_MS) break
            if (c.kind != kind || p.marks[i] != PENDING) continue
            val d = Math.abs(c.at - t); if (d <= GOOD_MS && d < bestD) { bestD = d; bestI = i } }
        if (bestI < 0) return 0
        val mark = if (bestD <= PERFECT_MS) PERFECT else GOOD
        judge(p, bestI, mark, t); return mark
    }

    /** Cues whose moment has passed unanswered become misses. */
    fun sweep(t: Long) {
        for (p in players.values) for (i in cues.indices) {
            if (cues[i].at + GOOD_MS >= t) break
            if (p.marks[i] == PENDING) judge(p, i, MISS, t)
        }
    }
    fun over(t: Long) = cues.isEmpty() || t > cues.last().at + GOOD_MS + 900
    fun ranking() = players.values.sortedByDescending { it.score }
    fun winners(): List<Player> { val top = players.values.maxOfOrNull { it.score } ?: 0; return if (top <= 0) emptyList() else players.values.filter { it.score == top } }
    fun place(p: Player) = 1 + players.values.count { it.score > p.score }

    companion object {
        const val LEFT = 0; const val RIGHT = 1; const val JUMP = 2; const val DUCK = 3
        const val PENDING = 0; const val PERFECT = 1; const val GOOD = 2; const val MISS = 3
        const val PERFECT_MS = 150L; const val GOOD_MS = 320L
        const val MAX_PLAYERS = 2
        val BPM = intArrayOf(96, 112, 128)
    }
}
