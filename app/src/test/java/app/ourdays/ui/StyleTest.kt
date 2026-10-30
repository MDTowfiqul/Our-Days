package app.ourdays.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StyleTest {

    @Test
    fun accentIdsAreUnique() {
        val ids = Accents.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun emptyOrUnknownAccentFallsBackToWine() {
        assertEquals(Accents.DEFAULT_ID, Accents.find("").id)
        assertEquals(Accents.DEFAULT_ID, Accents.find("gibt-es-nicht").id)
        assertEquals("ocean", Accents.find("ocean").id)
    }

    @Test
    fun onlyWineIsFree() {
        assertFalse(Accents.find(Accents.DEFAULT_ID).premium)
        Accents.all.filter { it.id != Accents.DEFAULT_ID }.forEach { assertTrue(it.id, it.premium) }
    }

    @Test
    fun lockedAccentResolvesToDefaultButUnlockedKeepsChoice() {
        assertEquals(Accents.DEFAULT_ID, resolveAccentId("ocean", false))
        assertEquals("ocean", resolveAccentId("ocean", true))
        assertEquals(Accents.DEFAULT_ID, resolveAccentId("", true))
        assertEquals(Accents.DEFAULT_ID, resolveAccentId("unknown", false))
    }

    @Test
    fun canUseAccentFollowsPremium() {
        assertTrue(canUseAccent(false, "wine"))
        assertFalse(canUseAccent(false, "forest"))
        assertTrue(canUseAccent(true, "forest"))
    }

    @Test
    fun premiumPresetsAreOnlyTheNewOnes() {
        val premium = Presets.mapIndexedNotNull { i, p -> if (p.premium) i else null }
        assertEquals(listOf(6, 7, 8, 9), premium)
        assertEquals(10, Presets.size)
    }

    @Test
    fun lockedPresetResolvesToFirstButUnlockedKeepsChoice() {
        assertEquals(0, resolvePresetIndex(7, false))
        assertEquals(7, resolvePresetIndex(7, true))
        assertEquals(3, resolvePresetIndex(3, false))
        assertEquals(0, resolvePresetIndex(99, false))
        assertEquals(0, resolvePresetIndex(-1, true))
    }

    @Test
    fun canUsePresetFollowsPremium() {
        assertTrue(canUsePreset(false, 0))
        assertFalse(canUsePreset(false, 8))
        assertTrue(canUsePreset(true, 8))
        assertFalse(canUsePreset(false, 99))
    }

    @Test
    fun freeAccentSchemeIsUnchanged() {
        val base = lightColorScheme()
        assertEquals(base, accentColorScheme(base, Accents.find("wine"), false))
    }

    @Test
    fun accentSchemeSwapsOnlyThePrimaryFamily() {
        val base = lightColorScheme()
        val ocean = Accents.find("ocean")
        val scheme = accentColorScheme(base, ocean, false)
        assertEquals(ocean.lightPrimary, scheme.primary)
        assertEquals(Color.White, scheme.onPrimary)
        assertNotEquals(base.primaryContainer, scheme.primaryContainer)
        assertEquals(base.background, scheme.background)
        assertEquals(base.surface, scheme.surface)
        assertEquals(base.tertiary, scheme.tertiary)
    }

    @Test
    fun darkAccentSchemeUsesTheDarkPrimary() {
        val base = darkColorScheme()
        val forest = Accents.find("forest")
        val scheme = accentColorScheme(base, forest, true)
        assertEquals(forest.darkPrimary, scheme.primary)
        assertEquals(forest.lightPrimary, scheme.primaryContainer)
        assertEquals(base.background, scheme.background)
    }
}
