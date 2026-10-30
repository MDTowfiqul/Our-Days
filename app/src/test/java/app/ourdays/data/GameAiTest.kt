package app.ourdays.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

// Pure logic, no Android: plain JUnit, no Robolectric needed.
class GameAiTest {

    private val rng = Random(42)

    @Test
    fun dotsEasyReturnsALegalMove() {
        val move = GameAi.dotsMove(DotsBoxesState(), 1, AiLevel.EASY, rng)
        assertNotNull(move)
        assertTrue(DotsBoxesState().legalMoves().contains(move))
    }

    @Test
    fun dotsHardTakesACompletingBox() {
        // Box (0,0) has top (h0), bottom (h3) and left (v0); only the right edge (v1) is open.
        val state = DotsBoxesState(hEdges = setOf(0, 3), vEdges = setOf(0), turn = 1)
        assertEquals(DotsMove(horizontal = false, index = 1), GameAi.dotsMove(state, 1, AiLevel.HARD, rng))
    }

    @Test
    fun dotsHardAvoidsGivingABoxAway() {
        // Box (0,0) has top (h0) and bottom (h3): playing either side (v0 or v1)
        // leaves a three-sided box for the opponent. Other moves are safe.
        val state = DotsBoxesState(hEdges = setOf(0, 3), vEdges = emptySet(), turn = 1)
        val move = GameAi.dotsMove(state, 1, AiLevel.HARD, rng)
        assertNotNull(move)
        assertTrue(move != DotsMove(horizontal = false, index = 0))
        assertTrue(move != DotsMove(horizontal = false, index = 1))
    }

    @Test
    fun gomokuEasyBlocksOpponentWin() {
        val cells = MutableList(Gomoku.SIZE * Gomoku.SIZE) { 0 }
        for (c in 0..3) cells[6 * Gomoku.SIZE + c] = 2
        val state = GomokuState(cells = cells, turn = 1)
        assertEquals(GomokuMove(6 * Gomoku.SIZE + 4), GameAi.gomokuMove(state, 1, AiLevel.EASY, rng))
    }

    @Test
    fun gomokuHardTakesImmediateWin() {
        val cells = MutableList(Gomoku.SIZE * Gomoku.SIZE) { 0 }
        for (c in 0..3) cells[6 * Gomoku.SIZE + c] = 1
        val state = GomokuState(cells = cells, turn = 1)
        assertEquals(GomokuMove(6 * Gomoku.SIZE + 4), GameAi.gomokuMove(state, 1, AiLevel.HARD, rng))
    }

    @Test
    fun gomokuAiOpensInTheCentreOnEmptyBoard() {
        val centre = 6 * Gomoku.SIZE + 6
        assertEquals(GomokuMove(centre), GameAi.gomokuMove(GomokuState(), 1, AiLevel.EASY, rng))
        assertEquals(GomokuMove(centre), GameAi.gomokuMove(GomokuState(), 1, AiLevel.HARD, rng))
    }
}
