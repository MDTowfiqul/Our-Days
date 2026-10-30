package app.ourdays.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure rules, no Android: plain JUnit, no Robolectric needed.
class GameGomokuTest {

    private fun cell(r: Int, c: Int) = r * Gomoku.SIZE + c

    /** Plays [moves] as (player, index) pairs in order, asserting each is legal. */
    private fun play(moves: List<Pair<Int, Int>>): GomokuState {
        var state = GomokuState()
        moves.forEach { (player, index) ->
            assertEquals(player, state.turn)
            state = Gomoku.apply(state, GomokuMove(index))!!
        }
        return state
    }

    @Test
    fun horizontalFiveWins() {
        val moves = listOf(
            1 to cell(6, 0), 2 to cell(0, 0),
            1 to cell(6, 1), 2 to cell(0, 1),
            1 to cell(6, 2), 2 to cell(0, 2),
            1 to cell(6, 3), 2 to cell(0, 3),
            1 to cell(6, 4),
        )
        val state = play(moves)
        assertTrue(state.finished)
        assertEquals(1, state.winner)
    }

    @Test
    fun verticalFiveWins() {
        val moves = listOf(
            1 to cell(0, 5), 2 to cell(0, 0),
            1 to cell(1, 5), 2 to cell(0, 1),
            1 to cell(2, 5), 2 to cell(0, 2),
            1 to cell(3, 5), 2 to cell(0, 3),
            1 to cell(4, 5),
        )
        val state = play(moves)
        assertTrue(state.finished)
        assertEquals(1, state.winner)
    }

    @Test
    fun diagonalFiveWins() {
        val moves = listOf(
            1 to cell(0, 0), 2 to cell(0, 9),
            1 to cell(1, 1), 2 to cell(0, 10),
            1 to cell(2, 2), 2 to cell(0, 11),
            1 to cell(3, 3), 2 to cell(0, 12),
            1 to cell(4, 4),
        )
        val state = play(moves)
        assertTrue(state.finished)
        assertEquals(1, state.winner)
    }

    @Test
    fun fourInARowDoesNotWin() {
        val state = play(
            listOf(
                1 to cell(6, 0), 2 to cell(0, 0),
                1 to cell(6, 1), 2 to cell(0, 1),
                1 to cell(6, 2), 2 to cell(0, 2),
                1 to cell(6, 3),
            ),
        )
        assertFalse(state.finished)
        assertEquals(0, state.winner)
    }

    @Test
    fun illegalMovesAreRejected() {
        val state = GomokuState(cells = List(Gomoku.SIZE * Gomoku.SIZE) { if (it == 0) 1 else 0 }, turn = 1)
        assertNull(Gomoku.apply(state, GomokuMove(0)))
        assertNull(Gomoku.apply(state, GomokuMove(-1)))
        assertNull(Gomoku.apply(state, GomokuMove(Gomoku.SIZE * Gomoku.SIZE)))
        val finished = GomokuState(finished = true, winner = 1)
        assertNull(Gomoku.apply(finished, GomokuMove(5)))
    }

    @Test
    fun hasFiveChecksAllDirections() {
        fun boardWith(vararg stones: Pair<Int, Int>): List<Int> {
            val cells = MutableList(Gomoku.SIZE * Gomoku.SIZE) { 0 }
            stones.forEach { (index, player) -> cells[index] = player }
            return cells
        }
        // Four only: no win
        assertFalse(Gomoku.hasFive(boardWith(cell(6, 0) to 1, cell(6, 1) to 1, cell(6, 2) to 1, cell(6, 3) to 1), cell(6, 3), 1))
        // Five, anti-diagonal
        val anti = boardWith(cell(0, 4) to 2, cell(1, 3) to 2, cell(2, 2) to 2, cell(3, 1) to 2, cell(4, 0) to 2)
        assertTrue(Gomoku.hasFive(anti, cell(4, 0), 2))
        // Broken line: no win
        val broken = boardWith(cell(6, 0) to 1, cell(6, 1) to 1, cell(6, 2) to 2, cell(6, 3) to 1, cell(6, 4) to 1)
        assertFalse(Gomoku.hasFive(broken, cell(6, 4), 1))
    }
}
