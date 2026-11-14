package com.mohammad.tvdraw

/**
 * Rules of the space race, with no Android code so they can be tested on their own.
 * Every player answers vocabulary questions at their own pace; each correct answer moves their rocket
 * towards 100 %. The first rocket to arrive wins.
 */
class SpaceCore(private val random: java.util.Random = java.util.Random()) {
    class Racer(val cid: String, var name: String, var rocket: Int, var mastered: Int, val robot: Boolean = false) {
        var progress = 0f; var streak = 0; var turbo = false; var correct = 0; var wrong = 0
        internal var wait = 0f
    }

    val racers = LinkedHashMap<String, Racer>()
    var length = 10                 // words needed to finish: 10, 15 or 20
    var category = 0                // 0 = all galaxies, 1..10 = one galaxy
    var roboMode = AUTO             // Robo-X joins: only a lone player, always, or never
    var roboSpeed = 0               // 0 easy, 1 medium, 2 hard
    var over = false; private set
    var winner: Racer? = null; private set
    val missed = LinkedHashSet<Int>()      // ids of the words answered wrongly in this race, for the review board
    var boosts = 0; private set            // counts doubled moves, for sounds on the TV

    val humans get() = racers.values.count { !it.robot }
    val step get() = 100f / length

    fun join(cid: String, name: String, rocket: Int, mastered: Int): Boolean {
        val r = racers[cid]
        if (r != null) { if (name.isNotBlank()) r.name = name; r.rocket = rocket; r.mastered = mastered; return true }
        if (humans >= MAX_PLAYERS) return false
        racers[cid] = Racer(cid, name.ifBlank { "P" + (humans + 1) }, rocket, mastered)
        return true
    }
    fun leave(cid: String) { racers.remove(cid) }

    /** Puts every rocket back on the start line and adds or removes Robo-X as the settings say. */
    fun reset() {
        val wantRobot = roboMode == ALWAYS || (roboMode == AUTO && humans == 1)
        if (wantRobot && racers[ROBOT] == null) racers[ROBOT] = Racer(ROBOT, "Robo-X", -1, 0, true)
        if (!wantRobot) racers.remove(ROBOT)
        over = false; winner = null; missed.clear(); boosts = 0
        for (r in racers.values) { r.progress = 0f; r.streak = 0; r.turbo = false; r.correct = 0; r.wrong = 0; r.wait = roboWait() }
    }

    private fun roboWait() = ROBO_SECONDS[roboSpeed.coerceIn(0, 2)] * (0.75f + random.nextFloat() * 0.5f)

    private fun advance(r: Racer, amount: Float) {
        r.progress = (r.progress + amount).coerceAtMost(100f)
        if (r.progress >= 100f && !over) { over = true; winner = r }
    }

    /** A correct answer given in ms milliseconds. golden marks the special question that appears every fifth time. */
    fun hit(cid: String, ms: Int, golden: Boolean) {
        val r = racers[cid] ?: return
        if (over || r.robot) return
        val fast = ms < FAST_MS
        var times = 1
        if (r.turbo) { times++; r.turbo = false; r.streak = 0 } else r.streak++
        if (golden && fast) times++
        if (times > 1) boosts++
        if (r.streak >= TURBO_STREAK) r.turbo = true
        r.correct++
        advance(r, step * times + (if (fast) SPEED_BONUS else 0f))
    }

    /** A wrong answer: the streak and any waiting turbo are lost, and the word goes on the review board. */
    fun miss(cid: String, wordId: Int) {
        val r = racers[cid] ?: return
        if (over || r.robot) return
        r.wrong++; r.streak = 0; r.turbo = false
        if (wordId > 0) missed.add(wordId)
    }

    fun step(dt: Float) {
        if (over) return
        val robo = racers[ROBOT] ?: return
        robo.wait -= dt
        if (robo.wait <= 0f) { robo.wait = roboWait(); robo.correct++; advance(robo, step) }
    }

    /** Rockets from first to last. */
    fun ranking() = racers.values.sortedWith(compareByDescending<Racer> { it === winner }.thenByDescending { it.progress })

    companion object {
        const val MAX_PLAYERS = 6
        const val ROBOT = "robo-x"
        const val AUTO = 0; const val ALWAYS = 1; const val NEVER = 2
        const val FAST_MS = 2500
        const val SPEED_BONUS = 2f
        const val TURBO_STREAK = 3
        private val ROBO_SECONDS = floatArrayOf(6.5f, 4.5f, 3f)      // seconds Robo-X needs for one word
    }
}
