package app.sinceus.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.drawToBitmap
import app.sinceus.data.LoveSettings
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Rendert die Screens als PNG nach app/build/screenshots, um das Design ohne
 * Gerät prüfen zu können: ./gradlew testDebugUnitTest
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// 1233 x 2460 px: Seitenverhältnis max. 2:1, wie vom Play Store verlangt
@Config(sdk = [35], qualifiers = "w411dp-h820dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val settings = LoveSettings.couple("Alex", "Sam", LocalDate.of(2025, 3, 15)).copy(onboardingDone = true)
    private val today = LocalDate.of(2025, 8, 15)

    @Test
    fun home() = shot("home") { HomeScreen(settings, today, {}, {}) }

    @Test
    fun homeBengali() = shot("home", lang = "bn") { HomeScreen(settings, today, {}, {}) }

    @Test
    fun homeDistanceBengali() = shot("home_distance", lang = "bn") {
        val far = settings.relationships.map { it.copy(distance = true, farZone = "America/New_York", nextMeeting = today.plusDays(12)) }
        HomeScreen(settings.copy(relationships = far), today, {}, {})
    }

    @Test
    fun liveBengali() = shot("live", lang = "bn") {
        LiveScreen(settings, active = false, now = java.time.LocalDateTime.of(2025, 8, 15, 12, 46, 33))
    }

    @Test
    fun settingsBengali() = shot("settings", lang = "bn") {
        SettingsScreen(settings, true)
    }

    @Test
    fun settingsPhotoBengali() = shot("settings_photo", lang = "bn") {
        SettingsScreen(
            settings, true,
            initialPage = SettingsPage.Photo,
        )
    }

    @Test
    fun settingsAbout() = shot("settings_about") {
        SettingsScreen(
            settings, true,
            initialPage = SettingsPage.About,
        )
    }

    @Test
    fun settingsResetBengali() = shot("settings_reset", lang = "bn") {
        SettingsScreen(
            settings, true,
            initialPage = SettingsPage.Reset,
        )
    }

    private val poly = settings.copy(
        people = listOf(
            app.sinceus.data.Person("a", "Alex"),
            app.sinceus.data.Person("s", "Sam", "they/them"),
            app.sinceus.data.Person("k", "Kim", "sie/ihr"),
        ),
        relationships = listOf(
            app.sinceus.data.Relationship("r1", listOf("a", "s"), LocalDate.of(2025, 3, 15)),
            app.sinceus.data.Relationship("r2", listOf("a", "k"), LocalDate.of(2024, 6, 1), label = "Nesting-Partner*in"),
        ),
        selected = app.sinceus.data.ALL_RELATIONSHIPS,
    )

    @Test
    fun homePolyBengali() = shot("home_poly", lang = "bn") { HomeScreen(poly, today, {}, {}) }

    @Test
    fun homePolyOneBengali() = shot("home_poly_one", lang = "bn") { HomeScreen(poly.copy(selected = "r2"), today, {}, {}) }

    @Test
    fun settingsPeopleBengali() = shot("settings_people", lang = "bn") {
        SettingsScreen(poly, true, initialPage = SettingsPage.Couple)
    }

    @Test
    fun settingsPeopleCoupleBengali() = shot("settings_people_couple", lang = "bn") {
        SettingsScreen(settings, true, initialPage = SettingsPage.Couple)
    }

    private val polyEnglish = poly.copy(
        people = poly.people.map { if (it.id == "k") it.copy(pronouns = "she/her") else it },
        relationships = poly.relationships.map { if (it.id == "r2") it.copy(label = "Nesting partner") else it },
    )

    @Test
    fun homePoly() = shot("home_poly") { HomeScreen(polyEnglish, today, {}, {}) }

    @Test
    fun sync() = shot("sync") { SettingsScreen(settings, true, initialPage = SettingsPage.Sync) }

    @Test
    fun syncBengali() = shot("sync", lang = "bn") { SettingsScreen(settings, true, initialPage = SettingsPage.Sync) }

    @Test
    fun privacy() = shot("privacy") {
        SettingsScreen(settings.copy(discreet = true), true, appLock = true, initialPage = SettingsPage.Privacy)
    }

    @Test
    fun privacyBengali() = shot("privacy", lang = "bn") {
        SettingsScreen(settings.copy(discreet = true), true, appLock = true, initialPage = SettingsPage.Privacy)
    }

    @Test
    fun homePrideBengali() = shot("home_pride", lang = "bn") {
        androidx.compose.runtime.CompositionLocalProvider(LocalPrideMonth provides true) {
            HomeScreen(settings, today, {}, {})
        }
    }

    @Test
    fun liveFocusedBengali() = shot(
        "live_focused",
        lang = "bn",
        before = { compose.onNodeWithText("৩৩ সেকেন্ড").performClick() },
    ) {
        LiveScreen(settings, active = false, now = java.time.LocalDateTime.of(2025, 8, 15, 12, 46, 33))
    }

    @Test
    fun liveFocusEndsAfterAFewSeconds() {
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("bn-BD"))
        org.robolectric.RuntimeEnvironment.setQualifiers("+bn")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LoveTheme { LiveScreen(settings, active = false, now = java.time.LocalDateTime.of(2025, 8, 15, 12, 46, 33)) }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("৩৩ সেকেন্ড").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("৩৩ সেকেন্ড").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(4000)
        compose.onNodeWithText("৩৩ সেকেন্ড").assertExists()
    }

    @Test
    fun homeWithoutLiveAndMoments() = shot("home_only_overview", lang = "bn") {
        HomeScreen(settings.copy(showLive = false, showMoments = false), today, {}, {})
    }

    @Test
    fun homeDark() = shot("home_dark", dark = true) { HomeScreen(settings.copy(presetIndex = 3), today, {}, {}) }

    @Test
    fun settings() = shot("settings") {
        SettingsScreen(settings, true)
    }

    @Test
    fun live() = shot("live") {
        LiveScreen(settings, active = false, now = java.time.LocalDateTime.of(2025, 8, 15, 12, 46, 33))
    }

    @Test
    fun onboardingWelcome() = onboarding(0)

    @Test
    fun onboardingDarkBengali() = shot("onboarding_1_dark", dark = true, lang = "bn") {
        OnboardingScreen(
            settings, false, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {},
            step = 1, onStep = {},
        )
    }

    @Test
    fun onboardingNames() = onboarding(1)

    @Test
    fun onboardingDate() = onboarding(2)

    @Test
    fun onboardingPhoto() = onboarding(3)

    @Test
    fun onboardingNotify() = onboarding(4)

    private val withMoments = settings.copy(
        moments = listOf(
            app.sinceus.data.Moment("1", "Kennengelernt", LocalDate.of(2025, 1, 20), "Auf der Geburtstagsfeier von Mia"),
            app.sinceus.data.Moment("2", "Erstes Date", LocalDate.of(2025, 2, 14), "Kino und danach Pizza"),
            app.sinceus.data.Moment("3", "Erster Urlaub", LocalDate.of(2025, 7, 5), "Eine Woche an der Ostsee"),
        ),
    )

    @Test
    fun momentsEmpty() = shot("moments_empty", lang = "bn") { MomentsScreen(settings, today, {}, {}) }

    @Test
    fun moments() = shot("moments", lang = "bn") { MomentsScreen(withMoments, today, {}, {}) }

    @Test
    fun momentsCalendar() = shot(
        "moments_calendar",
        lang = "bn",
        before = { compose.onNodeWithText("ক্যালেন্ডার").performClick() },
    ) { MomentsScreen(withMoments, today.withDayOfMonth(20).minusMonths(5), {}, {}) }

    @Test
    fun momentsEnglish() = shot("moments") {
        MomentsScreen(
            settings.copy(
                moments = listOf(
                    app.sinceus.data.Moment("1", "How we met", LocalDate.of(2025, 1, 20), "At Mia's birthday party"),
                    app.sinceus.data.Moment("2", "First date", LocalDate.of(2025, 2, 14), "Movies and pizza afterwards"),
                    app.sinceus.data.Moment("3", "First trip", LocalDate.of(2025, 7, 5), "A week by the sea"),
                ),
            ),
            today,
            {},
            {},
        )
    }

    @Test
    fun momentEditor()= shot("moment_editor", lang = "bn") {
        MomentEditorScreen(withMoments.moments[1], null, today, {}, { _, _, _ -> }, {})
    }

    @Test
    fun homeWithMomentsTab() = shot("home_moments_tab", lang = "bn") { HomeScreen(withMoments, today, {}, {}) }

    @Test
    fun licenses() = shot("licenses", lang = "bn") { LicensesScreen(onBack = {}) }

    @Test
    fun editor() = shot("editor") { PhotoEditorScreen(settings, {}, {}, { _, _, _ -> }) }

    @Test
    fun onboardingPhotoBengali() = shot("onboarding_3", lang = "bn") {
        OnboardingScreen(
            settings, false, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {},
            step = 3, onStep = {},
        )
    }

    private fun onboarding(step: Int) = shot("onboarding_$step") {
        OnboardingScreen(
            settings, false, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {},
            step = step, onStep = {},
        )
    }

    private fun shot(
        name: String,
        dark: Boolean = false,
        lang: String = "en",
        before: () -> Unit = {},
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        java.util.Locale.setDefault(if (lang == "bn") java.util.Locale.forLanguageTag("bn-BD") else java.util.Locale.US)
        org.robolectric.RuntimeEnvironment.setQualifiers("+$lang")
        if (dark) org.robolectric.RuntimeEnvironment.setQualifiers("+night")
        compose.setContent { LoveTheme(content = content) }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        before()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val bitmap = compose.activity.window.decorView.drawToBitmap()
        val dir = File("build/screenshots/$lang").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
