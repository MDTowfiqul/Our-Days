package app.ourdays.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure rules, no Android: plain JUnit, no Robolectric needed.
class GameDotsBoxesTest {

    @Test
    fun emptyBoardHas24MovesAndPlayerOneStarts() {
        val state = DotsBoxesState()
        assertEquals(24, state.legalMoves().size)
        assertEquals(1, state.turn)
        assertFalse(state.finished)
    }

    @Test
    fun completingABoxKeepsTheTurn() {
        // Box (0,0) has top (h0), bottom (h3) and left (v0); right edge is v1.
        val state = DotsBoxesState(hEdges = setOf(0, 3), vEdges = setOf(0), turn = 1)
        val next = DotsBoxes.apply(state, DotsMove(horizontal = false, index = 1))
        assertTrue(next != null)
        assertEquals(1, next!!.boxes[0])
        assertEquals(1, next.turn)
        assertEquals(1, next.score(1))
    }

    @Test
    fun illegalMovesAreRejected() {
        val state = DotsBoxesState(hEdges = setOf(0), turn = 1)
        assertNull(DotsBoxes.apply(state, DotsMove(horizontal = true, index = 0)))
        assertNull(DotsBoxes.apply(state, DotsMove(horizontal = true, index = 99)))
        val finished = DotsBoxesState(boxes = List(9) { 1 }, finished = true, winner = 1)
        assertNull(DotsBoxes.apply(finished, DotsMove(horizontal = true, index = 1)))
    }

    @Test
    fun fullGameFinishesWithNineBoxesClaimed() {
        var state = DotsBoxesState()
        var guard = 0
        while (!state.finished && guard < 30) {
            val move = state.legalMoves().first()
            state = DotsBoxes.apply(state, move)!!
            guard++
        }
        assertTrue(state.finished)
        assertEquals(9, state.score(1) + state.score(2))
        val expectedWinner = when {
            state.score(1) > state.score(2) -> 1
            state.score(2) > state.score(1) -> 2
            else -> 0
        }
        assertEquals(expectedWinner, state.winner)
    }
}
