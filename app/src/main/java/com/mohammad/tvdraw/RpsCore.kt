package com.mohammad.tvdraw

/**
 * Rock, paper, scissors for one to six players. Everyone chooses in secret and all hands are shown at once.
 * A hand earns one point for every hand it beats. A lone player plays against the TV.
 * Plain Kotlin with no Android code, so the rules can be tested on their own.
 */
class RpsCore(private val rnd: java.util.Random = java.util.Random()) {
    class Player(val cid: String, var name: String, val robot: Boolean = false) {
        var score = 0; var wins = 0
        var pick = NONE; var gained = 0; var lost = 0
    }
    val players = LinkedHashMap<String, Player>()
    var rounds = 5
    var round = 0; private set
    private var robotPick = NONE
    val humans get() = players.values.filter { !it.robot }
    val allPicked get() = humans.isNotEmpty() && humans.all { it.pick != NONE }

    fun join(cid: String, name: String): Boolean {
        players[cid]?.let { it.name = name; return true }
        if (humans.size >= MAX_PLAYERS) return false
        players[cid] = Player(cid, name); return true
    }
    fun leave(cid: String) { players.remove(cid) }

    /** Starts a new game. With one player alone, the TV joins under the name given. */
    fun begin(tvName: String) {
        players.remove(TV)
        if (humans.size == 1) players[TV] = Player(TV, tvName, true)
        round = 0
        for (p in players.values) { p.score = 0; p.wins = 0 }
        deal()
    }
    private fun deal() { for (p in players.values) { p.pick = NONE; p.gained = 0; p.lost = 0 }; robotPick = rnd.nextInt(3) }

    /** One hand per player and round. */
    fun pick(cid: String, hand: Int): Boolean {
        val p = players[cid] ?: return false
        if (p.robot || p.pick != NONE || hand !in 0..2) return false
        p.pick = hand; return true
    }

    /** Shows the hands: the TV's hand was chosen before anyone picked, and every hand is scored against every other. */
    fun reveal() {
        players[TV]?.pick = robotPick
        val all = players.values.toList()
        for (a in all) for (b in all) { if (a === b) continue
            if (beats(a.pick, b.pick)) { a.gained++; b.lost++ } }
        for (p in all) { p.score += p.gained; if (p.gained > 0 && p.lost == 0) p.wins++ }
    }

    /** Moves to the next round. False when that was the last one. */
    fun next(): Boolean { if (round + 1 >= rounds) return false; round++; deal(); return true }

    fun ranking() = players.values.sortedByDescending { it.score }
    fun winners(): List<Player> { val top = players.values.maxOfOrNull { it.score } ?: 0; return if (top <= 0) emptyList() else players.values.filter { it.score == top } }
    fun place(p: Player) = 1 + players.values.count { it.score > p.score }

    companion object {
        const val NONE = -1; const val ROCK = 0; const val PAPER = 1; const val SCISSORS = 2
        const val MAX_PLAYERS = 6; const val TV = "tv"
        /** Paper wraps rock, scissors cut paper, rock breaks scissors. A hand that was never shown loses to any hand. */
        fun beats(a: Int, b: Int) = a != NONE && (b == NONE || (a - b + 3) % 3 == 1)
    }
}
