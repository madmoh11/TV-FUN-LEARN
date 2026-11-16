package com.mohammad.tvdraw

/**
 * A quiz the whole room answers together: one question on the TV, every phone answers it,
 * then the right answer is shown. Flags and capitals are picked from four choices, spelling is typed.
 * Plain Kotlin with no Android code, so the rules can be tested on their own.
 */
class QuizCore(private val rnd: java.util.Random = java.util.Random()) {
    /** key = what the TV shows (a flag code, a word id); prompt = text that goes with it; answer = the right text */
    class Item(val key: String, val prompt: String, val answer: String)
    class Question(val item: Item, val options: List<String>, val right: Int)
    class Player(val cid: String, var name: String) {
        var score = 0; var rights = 0
        var answered = false; var correct = false; var gained = 0
    }

    val players = LinkedHashMap<String, Player>()
    var mode = FLAGS
    var rounds = 10
    var level = 0
    var round = 0; private set
    var questions = listOf<Question>(); private set
    val current get() = questions.getOrNull(round)
    val typed get() = mode == SPELL
    val allAnswered get() = players.isNotEmpty() && players.values.all { it.answered }

    fun join(cid: String, name: String): Boolean {
        players[cid]?.let { it.name = name; return true }
        if (players.size >= MAX_PLAYERS) return false
        players[cid] = Player(cid, name); return true
    }
    fun leave(cid: String) { players.remove(cid) }

    /** Starts a new game from these items. False when there are too few to ask. */
    fun begin(items: List<Item>): Boolean {
        val pool = items.distinctBy { it.answer }
        if (pool.size < (if (typed) 1 else 4)) return false
        val picked = pool.shuffled(rnd).take(rounds)
        questions = picked.map { it ->
            if (typed) Question(it, emptyList(), -1)
            else { val wrong = pool.filter { o -> o.answer != it.answer }.shuffled(rnd).take(3).map { o -> o.answer }
                val opts = (wrong + it.answer).shuffled(rnd); Question(it, opts, opts.indexOf(it.answer)) }
        }
        round = 0
        for (p in players.values) { p.score = 0; p.rights = 0 }
        clearAnswers()
        return true
    }
    private fun clearAnswers() { for (p in players.values) { p.answered = false; p.correct = false; p.gained = 0 } }

    /** One answer per player and question: a choice number, or the typed word. Faster right answers earn more. */
    fun answer(cid: String, value: String, ms: Long, limitMs: Long): Boolean {
        val p = players[cid] ?: return false; val q = current ?: return false
        if (p.answered) return false
        p.answered = true
        p.correct = if (typed) norm(value) == norm(q.item.answer) else value.toIntOrNull() == q.right
        if (p.correct) { p.gained = RIGHT_POINTS + Math.round(SPEED_POINTS * (1f - ms.coerceIn(0, limitMs).toFloat() / limitMs)); p.score += p.gained; p.rights++ }
        return true
    }

    /** Moves to the next question. False when that was the last one. */
    fun next(): Boolean {
        if (round + 1 >= questions.size) return false
        round++; clearAnswers(); return true
    }

    fun ranking() = players.values.sortedByDescending { it.score }
    /** Everyone who shares the top score, when it is above zero. */
    fun winners(): List<Player> { val top = players.values.maxOfOrNull { it.score } ?: 0; return if (top <= 0) emptyList() else players.values.filter { it.score == top } }
    fun place(p: Player) = 1 + players.values.count { it.score > p.score }

    companion object {
        const val FLAGS = 0; const val CAPITALS = 1; const val SPELL = 2
        const val MAX_PLAYERS = 6; const val RIGHT_POINTS = 10; const val SPEED_POINTS = 5
        /** Spelling is compared without regard to case or spacing. */
        fun norm(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")
    }
}
