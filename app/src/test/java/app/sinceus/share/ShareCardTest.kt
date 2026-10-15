package app.sinceus.share

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import app.sinceus.data.LoveSettings
import org.junit.Assert.assertEquals
import app.sinceus.data.Moment
import app.sinceus.data.YearReview
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.util.Locale

/** Rendert das Teilen-Bild nach app/build/screenshots/share. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ShareCardTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val settings = LoveSettings.couple("Alex", "Sam", LocalDate.of(2025, 3, 15))
    private val today = LocalDate.of(2025, 8, 15)

    @Test
    @Config(qualifiers = "bn")
    fun bengali() = save("bn", Locale.forLanguageTag("bn-BD"))

    @Test
    @Config(qualifiers = "en")
    fun english() = save("en", Locale.US)

    @Test
    @Config(qualifiers = "bn")
    fun longNamesStayInside() {
        Locale.setDefault(Locale.forLanguageTag("bn-BD"))
        val bitmap = ShareCard.render(context, LoveSettings.couple("Maximiliane-Sophie", "Konstantin-Alexander", LocalDate.of(2025, 3, 15)), today)
        assertEquals(ShareCard.WIDTH, bitmap.width)
        save("bn_long", Locale.forLanguageTag("bn-BD"), LoveSettings.couple("Maximiliane-Sophie", "Konstantin-Alexander", LocalDate.of(2025, 3, 15)))
    }

    private fun save(name: String, locale: Locale, s: LoveSettings = settings) {
        Locale.setDefault(locale)
        val bitmap = ShareCard.render(context, s, today)
        assertEquals(ShareCard.HEIGHT, bitmap.height)
        val dir = File("build/screenshots/share").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    @Config(qualifiers = "bn")
    fun momentAndYearCards() {
        Locale.setDefault(Locale.forLanguageTag("bn-BD"))
        val moment = Moment("m", "Unser erster gemeinsamer Urlaub am Meer in Portugal", LocalDate.of(2025, 6, 1))
        val card = ShareCard.renderMoment(context, settings, moment, today)
        assertEquals(ShareCard.WIDTH, card.width)
        val review = YearReview(1, LocalDate.of(2025, 3, 15), LocalDate.of(2026, 3, 14), listOf(moment))
        val year = ShareCard.renderYear(context, settings, review)
        assertEquals(ShareCard.HEIGHT, year.height)
        val dir = File("build/screenshots/share").apply { mkdirs() }
        File(dir, "moment_de.png").outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(dir, "year_de.png").outputStream().use { year.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun wrapKeepsLinesInside() {
        val fits = { s: String -> s.length <= 10 }
        assertEquals(listOf("Kurz"), ShareCard.wrap("Kurz", 3, fits))
        assertEquals(listOf("Erstes", "Date am", "Meer"), ShareCard.wrap("Erstes Date am Meer", 3, fits))
        val cut = ShareCard.wrap("eins zwei drei vier fünf sechs sieben", 2, fits)
        assertEquals(2, cut.size)
        assertEquals(true, cut.all(fits))
        assertEquals(true, cut.last().endsWith("…"))
    }
}
