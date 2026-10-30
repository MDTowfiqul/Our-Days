package app.ourdays.data

/**
 * Dots and Boxes on a 4x4 dot grid (3x3 = 9 boxes). A quick game at 24 edges, which
 * suits async play between two phones. Pure rules, no Android dependencies.
 *
 * Edge numbering:
 * - horizontal edges: row * 3 + col, row in 0..3, col in 0..2 (12 edges)
 * - vertical edges: row * 4 + col, row in 0..2, col in 0..3 (12 edges)
 * Boxes: row * 3 + col, row/col in 0..2. Owner 0 = unclaimed, 1/2 = player.
 */
data class DotsBoxesState(
    val hEdges: Set<Int> = emptySet(),
    val vEdges: Set<Int> = emptySet(),
    val boxes: List<Int> = List(9) { 0 },
    /** Player to move: 1 or 2 */
    val turn: Int = 1,
    val finished: Boolean = false,
    /** Winner: 1, 2, or 0 for a draw; only meaningful when finished */
    val winner: Int = 0,
) {
    fun score(player: Int): Int = boxes.count { it == player }

    fun legalMoves(): List<DotsMove> {
        val moves = mutableListOf<DotsMove>()
        for (i in 0 until 12) {
            if (i !in hEdges) moves += DotsMove(horizontal = true, index = i)
            if (i !in vEdges) moves += DotsMove(horizontal = false, index = i)
        }
        return moves
    }
}

data class DotsMove(val horizontal: Boolean, val index: Int)

object DotsBoxes {
    const val DOTS = 4
    const val BOX_ROWS = 3
    const val BOX_COLS = 3

    /** Applies [move] for the player whose turn it is. Null if the move is illegal. */
    fun apply(state: DotsBoxesState, move: DotsMove): DotsBoxesState? {
        if (state.finished) return null
        val player = state.turn
        val h = if (move.horizontal) {
            if (move.index !in 0 until 12 || move.index in state.hEdges) return null
            state.hEdges + move.index
        } else {
            state.hEdges
        }
        val v = if (!move.horizontal) {
            if (move.index !in 0 until 12 || move.index in state.vEdges) return null
            state.vEdges + move.index
        } else {
            state.vEdges
        }
        val completed = completedBoxes(h, v, state.boxes)
        val boxes = state.boxes.toMutableList()
        var newly = 0
        completed.forEach { box ->
            if (boxes[box] == 0) {
                boxes[box] = player
                newly++
            }
        }
        val finished = boxes.all { it != 0 }
        val s1 = boxes.count { it == 1 }
        val s2 = boxes.count { it == 2 }
        return DotsBoxesState(
            hEdges = h,
            vEdges = v,
            boxes = boxes,
            turn = if (finished) player else if (newly > 0) player else 3 - player,
            finished = finished,
            winner = if (!finished) 0 else when {
                s1 > s2 -> 1
                s2 > s1 -> 2
                else -> 0
            },
        )
    }

    /** Boxes whose four edges are all present and which are still unclaimed. */
    private fun completedBoxes(h: Set<Int>, v: Set<Int>, boxes: List<Int>): List<Int> {
        val out = mutableListOf<Int>()
        for (r in 0 until BOX_ROWS) {
            for (c in 0 until BOX_COLS) {
                val box = r * BOX_COLS + c
                if (boxes[box] != 0) continue
                val top = r * BOX_COLS + c
                val bottom = (r + 1) * BOX_COLS + c
                val left = r * DOTS + c
                val right = r * DOTS + c + 1
                if (top in h && bottom in h && left in v && right in v) out += box
            }
        }
        return out
    }

    /** Number of edges present around [box] (0..4). */
    fun boxEdgeCount(state: DotsBoxesState, box: Int): Int {
        val r = box / BOX_COLS
        val c = box % BOX_COLS
        var n = 0
        if (r * BOX_COLS + c in state.hEdges) n++
        if ((r + 1) * BOX_COLS + c in state.hEdges) n++
        if (r * DOTS + c in state.vEdges) n++
        if (r * DOTS + c + 1 in state.vEdges) n++
        return n
    }

    /** Boxes next to [move]: the one or two boxes that share this edge. */
    fun adjacentBoxes(move: DotsMove): List<Int> {
        return if (move.horizontal) {
            val r = move.index / BOX_COLS
            val c = move.index % BOX_COLS
            listOfNotNull(
                if (r > 0) (r - 1) * BOX_COLS + c else null,
                if (r < BOX_ROWS) r * BOX_COLS + c else null,
            )
        } else {
            val r = move.index / DOTS
            val c = move.index % DOTS
            listOfNotNull(
                if (c > 0) r * BOX_COLS + (c - 1) else null,
                if (c < BOX_COLS) r * BOX_COLS + c else null,
            )
        }
    }
}
