package app.ourdays.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.ourdays.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * End-to-end: the "How to pair & sync" card on the first-install
 * "Sync with My Partner" screen must open the guide. Regression test
 * for the missing onGuide argument on that screen's SyncActions.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h820dp-xxhdpi")
class MainActivityGuideTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun partnerSyncGuideOpens() {
        // The intro plays ~2s on every cold start.
        compose.mainClock.advanceTimeBy(2500)
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Sync with My Partner").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Sync with My Partner").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("How to pair & sync").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("One phone shows the code").assertExists()
    }
}
