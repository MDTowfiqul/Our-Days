package app.ourdays.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Pure logic, no Android: plain JUnit, no Robolectric needed.
class GameQuizTest {

    @Test
    fun dailyChallengeIsIdenticalForTheSameDate() {
        val date = LocalDate.of(2026, 10, 30)
        val first = DailyChallenge.questionsFor(date)
        val second = DailyChallenge.questionsFor(date)
        assertEquals(DailyChallenge.COUNT, first.size)
        assertEquals(first.map { it.id }, second.map { it.id })
        assertEquals(first.map { it.id }.toSet().size, first.size)
        assertTrue(first.all { q -> WR_QUESTIONS.any { it.id == q.id } })
    }

    @Test
    fun dailyChallengeCoversDifferentQuestionsAcrossDates() {
        // Not a strict property (any two dates could theoretically collide), but over a
        // whole week the sets must vary: this guards against a constant "daily" set.
        val sets = (0 until 7).map { DailyChallenge.questionsFor(LocalDate.of(2026, 10, 30).plusDays(it.toLong())).map { q -> q.id } }.toSet()
        assertTrue(sets.size > 1)
    }

    @Test
    fun knowMeScoreCountsMatchesOnly() {
        val sheet = KnowMeSheet("a", mapOf("ideal_weekend" to 0, "awake" to 2, "gift" to 1), 1000L)
        val attempt = KnowMeAttempt("a", "b", mapOf("ideal_weekend" to 0, "awake" to 1, "gift" to 1), 2000L)
        assertEquals(2, knowMeScore(sheet, attempt))
    }

    @Test
    fun wrAgreementCountsSharedAnswersOnly() {
        val a = WrAnswers("2026-10-30", "a", mapOf("tea_coffee" to 0, "rain_sun" to 1, "walk_drive" to 0), 1000L)
        val b = WrAnswers("2026-10-30", "b", mapOf("tea_coffee" to 0, "rain_sun" to 0), 2000L)
        // tea_coffee matches, rain_sun differs, walk_drive unanswered by b: agreement = 1.
        assertEquals(1, wrAgreement(a, b))
    }

    @Test
    fun questionSetsHaveBothLanguages() {
        assertTrue(KNOW_ME_QUESTIONS.size >= 10)
        assertTrue(WR_QUESTIONS.size >= 10)
        KNOW_ME_QUESTIONS.forEach { q ->
            assertTrue(q.en.isNotBlank() && q.bn.isNotBlank())
            assertEquals(4, q.optionsEn.size)
            assertEquals(4, q.optionsBn.size)
            assertNotEquals(q.en, q.bn)
        }
        WR_QUESTIONS.forEach { q ->
            assertTrue(q.en.isNotBlank() && q.bn.isNotBlank())
            assertTrue(q.aEn.isNotBlank() && q.aBn.isNotBlank())
            assertTrue(q.bEn.isNotBlank() && q.bBn.isNotBlank())
        }
    }
}
