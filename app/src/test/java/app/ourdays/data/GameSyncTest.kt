package app.ourdays.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric because SyncCodec uses org.json (same reason as SyncTest).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameSyncTest {

    private fun dotsGame(id: String, turn: Int, updatedAt: Long): GameState = GameState(
        id = id,
        kind = GameKind.DOTS,
        mode = GameMode.PARTNER,
        player1 = "a",
        player2 = "b",
        turn = turn,
        board = emptyDotsBoard(),
        finished = false,
        winner = 0,
        updatedAt = updatedAt,
        lastMoveBy = "a",
    )

    @Test
    fun gameCodecRoundTrip() {
        val game = dotsGame("g1", 2, 12345L)
        val decoded = GameCodec.decode(GameCodec.encode(listOf(game)))
        assertEquals(listOf(game), decoded)
        val single = GameCodec.fromJson(GameCodec.toJson(game))
        assertEquals(game, single)
    }

    @Test
    fun brokenGameRecordsAreSkipped() {
        assertTrue(GameCodec.decode("not json").isEmpty())
        assertTrue(GameCodec.decode(null).isEmpty())
    }

    @Test
    fun mergeKeepsTheNewerVersion() {
        val local = dotsGame("g1", 1, 1000L)
        val remoteNewer = dotsGame("g1", 2, 2000L)
        val remoteOlder = dotsGame("g1", 2, 500L)
        assertEquals(listOf(remoteNewer), GameMerge.merge(listOf(local), listOf(remoteNewer), "b").games)
        assertEquals(listOf(local), GameMerge.merge(listOf(local), listOf(remoteOlder), "b").games)
    }

    @Test
    fun mergeReportsWhenTheTurnComesToThisPhone() {
        // Partner (a) moved: turn goes 1 -> 2, and this phone is b.
        val local = dotsGame("g1", 1, 1000L)
        val remote = dotsGame("g1", 2, 2000L)
        val result = GameMerge.merge(listOf(local), listOf(remote), "b")
        assertEquals(listOf(remote), result.newlyMyTurn)
        // Already my turn locally: re-importing the same state must not notify again.
        val again = GameMerge.merge(listOf(remote), listOf(remote), "b")
        assertTrue(again.newlyMyTurn.isEmpty())
        // Finished games never notify.
        val finished = remote.copy(finished = true, winner = 2)
        assertTrue(GameMerge.merge(listOf(local), listOf(finished), "b").newlyMyTurn.isEmpty())
    }

    @Test
    fun turnIsMineFollowsPlayerNumbers() {
        val game = dotsGame("g1", 2, 1000L)
        assertTrue(GameMerge.turnIsMine(game, "b"))
        assertTrue(!GameMerge.turnIsMine(game, "a"))
        assertTrue(!GameMerge.turnIsMine(game, "someone-else"))
    }

    @Test
    fun oldSyncFilesWithoutGamesStillDecode() {
        val json = """{"app":"app.ourdays","format":1,"from":null,"people":[],"relationships":[],"moments":[],"deleted":{}}"""
        val decoded = SyncCodec.decode(json)
        assertTrue(decoded.games.isEmpty())
        assertTrue(decoded.quiz.sheets.isEmpty())
        assertTrue(decoded.quiz.wr.isEmpty())
        // And a file with games round-trips through the whole sync codec.
        val withGames = SyncData(
            people = emptyList(),
            relationships = emptyList(),
            moments = emptyList(),
            deleted = emptyMap(),
            from = "a",
            games = listOf(dotsGame("g1", 1, 1000L)),
            quiz = QuizSyncData(
                sheets = listOf(KnowMeSheet("a", mapOf("ideal_weekend" to 0), 1000L)),
                wr = listOf(WrAnswers("2026-10-30", "a", mapOf("tea_coffee" to 1), 1000L)),
            ),
        )
        val back = SyncCodec.decode(SyncCodec.encode(withGames))
        assertEquals(withGames.games, back.games)
        assertEquals(withGames.quiz.sheets, back.quiz.sheets)
        assertEquals(withGames.quiz.wr, back.quiz.wr)
        assertEquals("a", back.from)
    }

    @Test
    fun quizMergeKeepsTheNewerRecordPerKey() {
        val local = QuizSyncData(
            sheets = listOf(KnowMeSheet("a", mapOf("ideal_weekend" to 0), 1000L)),
            wr = listOf(WrAnswers("2026-10-30", "a", mapOf("tea_coffee" to 0), 1000L)),
        )
        val remote = QuizSyncData(
            sheets = listOf(KnowMeSheet("a", mapOf("ideal_weekend" to 2), 2000L)),
            attempts = listOf(KnowMeAttempt("a", "b", mapOf("ideal_weekend" to 1), 2000L)),
            wr = listOf(WrAnswers("2026-10-30", "a", mapOf("tea_coffee" to 1), 500L)),
        )
        val merged = QuizMerge.merge(local, remote)
        assertEquals(2, merged.sheets.first().answers["ideal_weekend"])
        assertEquals(1, merged.attempts.size)
        // The older remote wr record must not overwrite the newer local one.
        assertEquals(0, merged.wr.first().answers["tea_coffee"])
    }
}
