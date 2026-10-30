package app.ourdays.data

import kotlin.random.Random

/**
 * AI opponents for the v1 games. Two levels, both real move logic, never random-only:
 * EASY plays a simple heuristic, HARD plays a stronger one. In the spirit of Chogan's
 * leveled AI, implemented natively here.
 */
enum class AiLevel { EASY, HARD }

object GameAi {

    // ---- Dots and Boxes ----

    /** Move for [player] (1 or 2). Null if it is not [player]'s turn or the game is over. */
    fun dotsMove(
        state: DotsBoxesState,
        player: Int,
        level: AiLevel,
        random: Random = Random.Default,
    ): DotsMove? {
        if (state.finished || state.turn != player) return null
        val legal = state.legalMoves()
        if (legal.isEmpty()) return null
        // Completing a box is always good: it scores and keeps the turn.
        val completing = legal.firstOrNull { move ->
            val next = DotsBoxes.apply(state, move)
            next != null && next.boxes.count { it != 0 } > state.boxes.count { it != 0 }
        }
        if (completing != null) return completing
        if (level == AiLevel.EASY) return legal.random(random)
        // HARD: prefer a move that does not leave a three-sided box for the opponent.
        val safe = legal.filter { !createsGift(state, it) }
        if (safe.isNotEmpty()) return safe.random(random)
        // Forced to give boxes away: give away as few as possible.
        return legal.minByOrNull { boxesAtStake(state, it) } ?: legal.random(random)
    }

    /** True if [move] leaves an unclaimed box with exactly three edges: a gift. */
    private fun createsGift(state: DotsBoxesState, move: DotsMove): Boolean {
        val next = DotsBoxes.apply(state, move) ?: return false
        return DotsBoxes.adjacentBoxes(move).any { box ->
            next.boxes[box] == 0 && DotsBoxes.boxEdgeCount(next, box) == 3
        }
    }

    /** How many boxes the opponent would take next after [move] (three-sided boxes next to it). */
    private fun boxesAtStake(state: DotsBoxesState, move: DotsMove): Int {
        val next = DotsBoxes.apply(state, move) ?: return 0
        return DotsBoxes.adjacentBoxes(move).count { box ->
            next.boxes[box] == 0 && DotsBoxes.boxEdgeCount(next, box) == 3
        }
    }

    // ---- Gomoku ----

    /** Move for [player] (1 or 2). Null if it is not [player]'s turn or the game is over. */
    fun gomokuMove(
        state: GomokuState,
        player: Int,
        level: AiLevel,
        random: Random = Random.Default,
    ): GomokuMove? {
        if (state.finished || state.turn != player) return null
        val opponent = 3 - player
        // Win now, or stop the opponent winning next.
        immediateWin(state, player)?.let { return it }
        immediateWin(state, opponent)?.let { return it }
        if (level == AiLevel.EASY) {
            val near = nearMoves(state)
            return if (near.isEmpty()) GomokuMove(centerIndex()) else GomokuMove(near.random(random))
        }
        // HARD: two-ply over the best candidate moves by static score.
        val candidates = rankedCandidates(state, player).take(12)
        if (candidates.isEmpty()) return GomokuMove(centerIndex())
        var best: GomokuMove? = null
        var bestValue = Int.MIN_VALUE
        for (move in candidates) {
            val after = Gomoku.apply(state, move) ?: continue
            if (after.finished && after.winner == player) return move
            val myScore = staticScore(after.cells, player)
            val oppBest = rankedCandidates(after, opponent).take(12).maxOfOrNull { opp ->
                val oppAfter = Gomoku.apply(after, opp)
                if (oppAfter != null && oppAfter.finished && oppAfter.winner == opponent) Int.MAX_VALUE / 2
                else if (oppAfter != null) staticScore(oppAfter.cells, opponent) else 0
            } ?: 0
            val value = myScore - oppBest
            if (value > bestValue) {
                bestValue = value
                best = move
            }
        }
        return best ?: GomokuMove(candidates.first())
    }

    /** A move that wins immediately for [player], if one exists. */
    private fun immediateWin(state: GomokuState, player: Int): GomokuMove? {
        for (index in state.legalMoves()) {
            val cells = state.cells.toMutableList()
            cells[index] = player
            if (Gomoku.hasFive(cells, index, player)) return GomokuMove(index)
        }
        return null
    }

    /** Empty cells next to an existing stone (Chebyshev distance 1). */
    private fun nearMoves(state: GomokuState): List<Int> {
        val size = Gomoku.SIZE
        val out = mutableSetOf<Int>()
        for (index in state.cells.indices) {
            if (state.cells[index] == 0) continue
            val r = index / size
            val c = index % size
            for (dr in -1..1) {
                for (dc in -1..1) {
                    val rr = r + dr
                    val cc = c + dc
                    if (rr in 0 until size && cc in 0 until size) {
                        val at = rr * size + cc
                        if (state.cells[at] == 0) out += at
                    }
                }
            }
        }
        return out.toList()
    }

    /** Candidate moves near stones, best static score first. Empty board: only the centre. */
    private fun rankedCandidates(state: GomokuState, player: Int): List<GomokuMove> {
        val size = Gomoku.SIZE
        val near = if (state.cells.all { it == 0 }) {
            listOf(centerIndex())
        } else {
            val stones = state.cells.indices.filter { state.cells[it] != 0 }
            state.cells.indices.filter { index ->
                state.cells[index] == 0 && stones.any { stone ->
                    val dr = kotlin.math.abs(index / size - stone / size)
                    val dc = kotlin.math.abs(index % size - stone % size)
                    maxOf(dr, dc) <= 2
                }
            }
        }
        return near.map { index ->
            val cells = state.cells.toMutableList()
            cells[index] = player
            GomokuMove(index) to staticScore(cells, player)
        }.sortedByDescending { it.second }.map { it.first }
    }

    private fun centerIndex(): Int {
        val mid = Gomoku.SIZE / 2
        return mid * Gomoku.SIZE + mid
    }

    /**
     * Static board score for [player]: every five-cell window counts. Windows with an
     * opponent stone score nothing for attack; windows with a player stone score nothing
     * for defence. Four in a window dominates three, and so on.
     */
    fun staticScore(cells: List<Int>, player: Int): Int {
        val size = Gomoku.SIZE
        val opponent = 3 - player
        var score = 0
        fun windowScore(window: List<Int>): Int {
            var mine = 0
            var theirs = 0
            window.forEach { cell ->
                if (cell == player) mine++
                if (cell == opponent) theirs++
            }
            var value = 0
            if (theirs == 0) {
                value += when (mine) {
                    4 -> 10_000
                    3 -> 1_000
                    2 -> 100
                    1 -> 10
                    else -> 0
                }
            }
            if (mine == 0) {
                value += when (theirs) {
                    4 -> 9_000
                    3 -> 900
                    2 -> 90
                    1 -> 9
                    else -> 0
                }
            }
            return value
        }
        fun line(r: Int, c: Int, dr: Int, dc: Int) {
            val window = mutableListOf<Int>()
            var rr = r
            var cc = c
            repeat(5) {
                window += cells[rr * size + cc]
                rr += dr
                cc += dc
            }
            score += windowScore(window)
        }
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (c + 4 < size) line(r, c, 0, 1)
                if (r + 4 < size) line(r, c, 1, 0)
                if (r + 4 < size && c + 4 < size) line(r, c, 1, 1)
                if (r + 4 < size && c - 4 >= 0) line(r, c, 1, -1)
            }
        }
        return score
    }
}
