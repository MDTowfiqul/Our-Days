package app.ourdays.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

// Robolectric wegen org.json (in reinen JVM-Tests nur als Attrappe vorhanden)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatePlanTest {
    private val today = LocalDate.of(2026, 10, 30)

    private fun plan(
        id: String,
        date: LocalDate,
        updated: Long = 1000,
        remind: Int = 0,
    ) = DatePlan(
        id = id,
        title = "Plan $id",
        date = date,
        time = LocalTime.of(19, 30),
        note = "Note",
        place = "Park",
        remindBefore = remind,
        relationshipId = null,
        updatedAt = updated,
        addedBy = null,
    )

    @Test
    fun codecRoundtripKeepsAllFields() {
        val original = plan("d1", LocalDate.of(2026, 11, 5), updated = 12345, remind = 7)
        assertEquals(listOf(original), DatePlanCodec.decode(DatePlanCodec.encode(listOf(original))))
    }

    @Test
    fun codecSkipsBadEntriesAndEmptyInput() {
        assertEquals(emptyList<DatePlan>(), DatePlanCodec.decode(null))
        assertEquals(emptyList<DatePlan>(), DatePlanCodec.decode(""))
        assertEquals(emptyList<DatePlan>(), DatePlanCodec.decode("kein json"))
        val good = plan("ok", today)
        val mixed = """[{"id":"ok","title":"Plan ok","date":${today.toEpochDay()},"time":-1,"note":"","place":"","remind":0,"rel":null,"updated":1,"by":null},{"id":"kaputt"},{"title":"ohne id"}]"""
        val expected = DatePlan("ok", "Plan ok", today, null, "", "", 0, null, 1, null)
        assertEquals(listOf(expected), DatePlanCodec.decode(mixed))
        assertEquals(good, DatePlanCodec.decode(DatePlanCodec.encode(listOf(good))).first())
    }

    @Test
    fun upcomingPastAndNext() {
        val plans = listOf(
            plan("past", today.minusDays(5)),
            plan("today", today),
            plan("soon", today.plusDays(3)),
            plan("later", today.plusDays(10)),
        )
        assertEquals(listOf("today", "soon", "later"), DatePlanMath.upcoming(plans, today).map { it.id })
        assertEquals(listOf("past"), DatePlanMath.past(plans, today).map { it.id })
        assertEquals("today", DatePlanMath.next(plans, today)?.id)
        assertNull(DatePlanMath.next(listOf(plan("past", today.minusDays(1))), today))
        assertEquals(3L, DatePlanMath.daysUntil(plan("soon", today.plusDays(3)), today))
        assertEquals(-5L, DatePlanMath.daysUntil(plan("past", today.minusDays(5)), today))
    }

    @Test
    fun remindersOnDayAndAdvanceDay() {
        val withAdvance = plan("a", today.plusDays(5), remind = 1)
        val dayOnly = plan("b", today.plusDays(5), remind = 0)
        assertEquals(listOf("a"), DatePlanMath.remindersOn(listOf(withAdvance, dayOnly), today.plusDays(4)).map { it.id })
        assertEquals(
            listOf("a", "b"),
            DatePlanMath.remindersOn(listOf(withAdvance, dayOnly), today.plusDays(5)).map { it.id },
        )
        assertTrue(DatePlanMath.remindersOn(listOf(withAdvance, dayOnly), today.plusDays(3)).isEmpty())
    }

    @Test
    fun syncCodecKeepsDatesAndPremium() {
        val dates = listOf(plan("d1", today.plusDays(2), updated = 500))
        val premium = PremiumEntitlement("GPA.1234", 9000, "play")
        val data = SyncData(
            people = emptyList(),
            relationships = emptyList(),
            moments = emptyList(),
            deleted = emptyMap(),
            from = null,
            dates = dates,
            deletedDates = mapOf("gone" to 700L),
            premium = premium,
        )
        val decoded = SyncCodec.decode(SyncCodec.encode(data))
        assertEquals(dates, decoded.dates)
        assertEquals(mapOf("gone" to 700L), decoded.deletedDates)
        assertEquals(premium, decoded.premium)
        // Alte Dateien ohne die neuen Felder lesen sich als leer / ohne Premium
        val oldStyle = SyncCodec.decode(SyncCodec.encode(data.copy(dates = emptyList(), deletedDates = emptyMap(), premium = null)))
        assertEquals(emptyList<DatePlan>(), oldStyle.dates)
        assertNull(oldStyle.premium)
    }

    @Test
    fun mergePrefersNewerDatesAndKeepsDeletions() {
        val local = listOf(plan("a", today.plusDays(1), updated = 100))
        val remote = SyncData(
            people = emptyList(),
            relationships = emptyList(),
            moments = emptyList(),
            deleted = emptyMap(),
            from = null,
            dates = listOf(
                plan("a", today.plusDays(2), updated = 200),
                plan("b", today.plusDays(3), updated = 50),
            ),
            deletedDates = mapOf("c" to 300L),
            premium = PremiumEntitlement("GPA.9999", 8000, "play"),
        )
        val merged = SyncMerge.merge(
            people = emptyList(),
            relationships = emptyList(),
            moments = emptyList(),
            deleted = emptyMap(),
            remote = remote,
            dates = local + plan("c", today.plusDays(4), updated = 100),
            deletedDates = emptyMap(),
            premium = PremiumEntitlement("GPA.1111", 1000, "play"),
        )
        assertEquals(listOf("a", "b"), merged.dates.map { it.id })
        assertEquals(today.plusDays(2), merged.dates.first { it.id == "a" }.date)
        assertEquals(mapOf("c" to 300L), merged.deletedDates)
        assertEquals("GPA.9999", merged.premium?.orderId)
    }
}
