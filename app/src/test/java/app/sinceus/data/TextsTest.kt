package app.sinceus.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.Period
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TextsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val period = Period.between(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 3, 2))
    private val day = LocalDate.of(2026, 3, 15)

    @Test
    @Config(qualifiers = "bn")
    fun bengali() {
        Locale.setDefault(Locale.forLanguageTag("bn-BD"))
        assertEquals("১ বছর, ২ মাস ও ১ দিন", Texts.period(context, period))
        assertEquals("৫ মাস", Texts.period(context, Period.ofMonths(5), dative = true))
        assertEquals("১তম বছরপূর্তি", Texts.milestoneTitle(context, Milestone(MilestoneKind.YEARS, 1, day)))
        assertEquals(
            "আজ তোমরা ঠিক ৫ মাস ধরে একসাথে আছো।",
            Texts.milestoneMessage(context, Milestone(MilestoneKind.MONTHS, 5, day)),
        )
        assertEquals("১,০০০ দিন", Texts.milestoneTitle(context, Milestone(MilestoneKind.DAYS, 1000, day)))
        assertEquals("২ বছর আগে আজ: Erstes Date", Texts.momentMessage(context, Moment("a", "Erstes Date", day), 2))
        val moment = Milestone(MilestoneKind.MOMENT, 2, day, "Erstes Date")
        assertEquals("Erstes Date (২ বছর)", Texts.milestoneTitle(context, moment))
        assertEquals("২ বছর আগে আজ: Erstes Date", Texts.milestoneMessage(context, moment))
    }

    @Test
    @Config(qualifiers = "en")
    fun english() {
        Locale.setDefault(Locale.US)
        assertEquals("1 year, 2 months and 1 day", Texts.period(context, period))
        assertEquals("1-year anniversary", Texts.milestoneTitle(context, Milestone(MilestoneKind.YEARS, 1, day)))
        assertEquals(
            "Today you have been together for exactly 5 months.",
            Texts.milestoneMessage(context, Milestone(MilestoneKind.MONTHS, 5, day)),
        )
        assertEquals("1,000 days", Texts.milestoneTitle(context, Milestone(MilestoneKind.DAYS, 1000, day)))
        assertEquals("Today 1 year ago: First date", Texts.momentMessage(context, Moment("a", "First date", day), 1))
        assertEquals("First date (1 year)", Texts.milestoneTitle(context, Milestone(MilestoneKind.MOMENT, 1, day, "First date")))
    }
}
