package com.mohammad.tvdraw

import kotlin.math.abs

/**
 * Rules of the math tug of war, with no Android code so they can be tested on their own.
 * Two teams of up to six players in total answer sums on their phones; every correct answer pulls the rope.
 * pos runs from -LIMIT (left team wins) to +LIMIT (right team wins).
 */
class TugCore {
    class Player(val cid: String, var name: String, var team: Int) {
        var correct = 0; var wrong = 0; var streak = 0; var boost = 0f; var totalMs = 0L
        val avgMs get() = if (correct == 0) 0L else totalMs / correct
    }

    val players = LinkedHashMap<String, Player>()
    var level = 0                 // 0 easy, 1 medium, 2 hard, 3 starts easy and gets harder
    var duration = 90
    var pos = 0f; private set
    var timeLeft = 90f; private set
    var sudden = false; private set
    var over = false; private set
    var winner = -1; private set   // 0 left, 1 right
    var reason = 0; private set
    var pulls = 0; private set     // counts every pull, for sounds and the shake on the TV
    var lastPull = 0f; private set // strength of the last pull, signed like pos
    private var botCarry = 0f; private var botSudden = 0f

    fun count(team: Int) = players.values.count { it.team == team }

    /** Returns false when the game is full. Joining again moves the player to the other team. */
    fun join(cid: String, name: String, team: Int): Boolean {
        val p = players[cid]
        if (p != null) { p.team = team; if (name.isNotBlank()) p.name = name; return true }
        if (players.size >= MAX_PLAYERS) return false
        players[cid] = Player(cid, name.ifBlank { "P" + (players.size + 1) }, team)
        return true
    }
    fun leave(cid: String) { players.remove(cid) }

    fun reset() {
        pos = 0f; timeLeft = duration.toFloat(); sudden = false; over = false; winner = -1; reason = 0; pulls = 0; lastPull = 0f
        botCarry = 0f; botSudden = 0f
        for (p in players.values) { p.correct = 0; p.wrong = 0; p.streak = 0; p.boost = 0f; p.totalMs = 0L }
    }

    /** The level questions should have right now: for "gets harder" it follows the clock. */
    fun currentLevel(): Int {
        if (level < 3) return level
        val gone = 1f - timeLeft / duration
        return if (sudden || gone >= 2f / 3) 2 else if (gone >= 1f / 3) 1 else 0
    }

    /** A smaller team pulls harder, so one fast player can stand against two. */
    private fun handicap(team: Int): Float {
        val mine = count(team).coerceAtLeast(1); val theirs = count(1 - team).coerceAtLeast(1)
        return if (theirs > mine) theirs.toFloat() / mine else 1f
    }

    private fun pull(team: Int, amount: Float) {
        val signed = if (team == 0) -amount else amount
        pos = (pos + signed).coerceIn(-LIMIT, LIMIT); lastPull = signed; pulls++
        if (sudden) end(team, SUDDEN) else if (abs(pos) >= LIMIT) end(team, KNOCKOUT)
    }
    private fun end(team: Int, why: Int) { over = true; winner = team; reason = why }

    /** A correct answer worth points (1 easy, 2 medium, 3 or 4 hard), answered in ms milliseconds. */
    fun hit(cid: String, points: Int, ms: Int) {
        val p = players[cid] ?: return
        if (over) return
        val pts = points.coerceIn(1, MAX_POINTS[currentLevel()])
        p.correct++; p.totalMs += ms.coerceIn(0, 60000); p.streak++
        val doubled = p.boost > 0f
        if (p.streak >= STREAK && !doubled) { p.boost = BOOST_SECONDS; p.streak = 0 }
        pull(p.team, pts * (if (doubled) 2f else 1f) * handicap(p.team))
    }

    /** A wrong answer: the streak is lost and the rope slips a little towards the other team. */
    fun miss(cid: String) {
        val p = players[cid] ?: return
        if (over) return
        p.wrong++; p.streak = 0
        if (!sudden) { pos = (pos + (if (p.team == 0) SLIP else -SLIP)).coerceIn(-LIMIT + 0.01f, LIMIT - 0.01f) }
    }

    fun step(dt: Float) {
        if (over) return
        for (p in players.values) if (p.boost > 0f) p.boost = (p.boost - dt).coerceAtLeast(0f)
        // A team with nobody on it is played by the computer, which pulls steadily.
        val empty = if (count(0) == 0 && count(1) > 0) 0 else if (count(1) == 0 && count(0) > 0) 1 else -1
        if (empty >= 0) {
            if (sudden) { botSudden += dt; if (botSudden >= BOT_SUDDEN[currentLevel()]) { pull(empty, 1f); return } }
            else { botCarry += BOT_RATE[currentLevel()] * dt
                if (botCarry >= 1f) { botCarry -= 1f; pull(empty, 1f); if (over) return } }
        }
        if (sudden) return
        timeLeft -= dt
        if (timeLeft <= 0f) {
            timeLeft = 0f
            if (abs(pos) < TIE) { sudden = true; pos = 0f } else end(if (pos < 0) 0 else 1, TIME)
        }
    }

    fun mostCorrect() = players.values.filter { it.correct > 0 }.maxByOrNull { it.correct }
    /** Fastest average answer; needs three correct answers so one lucky tap does not count. */
    fun fastest() = (players.values.filter { it.correct >= 3 }.ifEmpty { players.values.filter { it.correct > 0 } }).minByOrNull { it.avgMs }

    companion object {
        const val MAX_PLAYERS = 6
        const val LIMIT = 20f
        const val SLIP = 0.5f
        const val TIE = 0.25f
        const val STREAK = 5
        const val BOOST_SECONDS = 5f
        const val KNOCKOUT = 1; const val TIME = 2; const val SUDDEN = 3
        private val MAX_POINTS = intArrayOf(1, 2, 4)
        private val BOT_RATE = floatArrayOf(0.28f, 0.5f, 0.75f)       // points a second
        private val BOT_SUDDEN = floatArrayOf(7f, 5f, 3.5f)           // seconds until the computer answers in sudden death
    }
}
