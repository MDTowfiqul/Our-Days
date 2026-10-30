package app.ourdays.data

/**
 * Gomoku (five in a row) on a 13x13 board. Pure rules, no Android dependencies.
 * Cells: 0 = empty, 1 = player 1, 2 = player 2. Index = row * SIZE + col.
 */
data class GomokuState(
    val cells: List<Int> = List(Gomoku.SIZE * Gomoku.SIZE) { 0 },
    val turn: Int = 1,
    val finished: Boolean = false,
    /** Winner: 1, 2, or 0 for a draw; only meaningful when finished */
    val winner: Int = 0,
    val lastMove: Int = -1,
) {
    fun legalMoves(): List<Int> = cells.indices.filter { cells[it] == 0 }
}

data class GomokuMove(val index: Int)

object Gomoku {
    const val SIZE = 13
    const val WIN = 5

    /** Applies [move] for the player whose turn it is. Null if the move is illegal. */
    fun apply(state: GomokuState, move: GomokuMove): GomokuState? {
        if (state.finished) return null
        if (move.index !in state.cells.indices || state.cells[move.index] != 0) return null
        val cells = state.cells.toMutableList()
        cells[move.index] = state.turn
        val win = hasFive(cells, move.index, state.turn)
        val full = cells.all { it != 0 }
        return GomokuState(
            cells = cells,
            turn = if (win || full) state.turn else 3 - state.turn,
            finished = win || full,
            winner = if (win) state.turn else 0,
            lastMove = move.index,
        )
    }

    /** True if placing [player] at [index] completes five in a row. */
    fun hasFive(cells: List<Int>, index: Int, player: Int): Boolean {
        val r = index / SIZE
        val c = index % SIZE
        val dirs = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        for ((dr, dc) in dirs) {
            var count = 1
            var rr = r + dr
            var cc = c + dc
            while (rr in 0 until SIZE && cc in 0 until SIZE && cells[rr * SIZE + cc] == player) {
                count++
                rr += dr
                cc += dc
            }
            rr = r - dr
            cc = c - dc
            while (rr in 0 until SIZE && cc in 0 until SIZE && cells[rr * SIZE + cc] == player) {
                count++
                rr -= dr
                cc -= dc
            }
            if (count >= WIN) return true
        }
        return false
    }
}
