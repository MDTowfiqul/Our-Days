package app.ourdays.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ourdays.R

/**
 * An accent color for Our Style (premium customisation). Only the primary
 * family changes: backgrounds, surfaces and the tertiary color stay exactly
 * as today, so every accent still looks like the app, just in a new color.
 * The free default (wine) is today's color scheme, unchanged.
 */
data class Accent(
    val id: String,
    @StringRes val name: Int,
    val lightPrimary: Color,
    val darkPrimary: Color,
    /** True = premium-locked; the free default is never locked. */
    val premium: Boolean = false,
)

/** Display name of the accent in the chosen language */
@Composable
fun Accent.label(): String = stringResource(name)

object Accents {
    /** The free default: exactly today's colors */
    const val DEFAULT_ID = "wine"

    val all: List<Accent> = listOf(
        Accent(DEFAULT_ID, R.string.accent_wine, Color(0xFF8B1E31), Color(0xFFFFB2B9)),
        Accent("rose", R.string.accent_rose, Color(0xFFC2185B), Color(0xFFFFB3C6), premium = true),
        Accent("ocean", R.string.accent_ocean, Color(0xFF166B8A), Color(0xFF9AD9E8), premium = true),
        Accent("lavender", R.string.accent_lavender, Color(0xFF6D5DB0), Color(0xFFCFC2F5), premium = true),
        Accent("forest", R.string.accent_forest, Color(0xFF2E7D5B), Color(0xFFA8D8BE), premium = true),
    )

    /** The accent for the stored ID; empty or unknown = free default */
    fun find(id: String): Accent {
        val wanted = id.ifEmpty { DEFAULT_ID }
        return all.firstOrNull { it.id == wanted } ?: all.first { it.id == DEFAULT_ID }
    }
}

/**
 * Stored accent, resolved against premium: a locked accent falls back to
 * the free default. The stored value itself is never rewritten, so unlocking
 * premium later restores the choice.
 */
fun resolveAccentId(stored: String, isPremium: Boolean): String {
    val accent = Accents.find(stored)
    return if (accent.premium && !isPremium) Accents.DEFAULT_ID else accent.id
}

/** True when this accent may be used right now (free, or premium unlocked) */
fun canUseAccent(isPremium: Boolean, id: String): Boolean = !Accents.find(id).premium || isPremium

/**
 * Stored home background, resolved against premium: a locked preset falls
 * back to the first (wine) preset. The stored value itself is never
 * rewritten, so unlocking premium later restores the choice.
 */
fun resolvePresetIndex(stored: Int, isPremium: Boolean): Int {
    val preset = Presets.getOrNull(stored) ?: return 0
    return if (preset.premium && !isPremium) 0 else stored
}

/** True when this home background may be used right now */
fun canUsePreset(isPremium: Boolean, index: Int): Boolean {
    val preset = Presets.getOrNull(index) ?: return false
    return !preset.premium || isPremium
}

/** Mixes [a] toward [b] by [t] (0 = a, 1 = b) */
private fun mix(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

/**
 * The app scheme with this accent's primary family. The free default returns
 * [base] untouched, so free users see exactly today's colors.
 */
fun accentColorScheme(base: ColorScheme, accent: Accent, dark: Boolean): ColorScheme {
    if (accent.id == Accents.DEFAULT_ID) return base
    return if (!dark) {
        val container = mix(accent.lightPrimary, Color.White, 0.82f)
        base.copy(
            primary = accent.lightPrimary,
            onPrimary = if (accent.lightPrimary.luminance() > 0.6f) Color(0xFF22191A) else Color.White,
            primaryContainer = container,
            onPrimaryContainer = mix(accent.lightPrimary, Color.Black, 0.55f),
            secondaryContainer = container,
            onSecondaryContainer = mix(accent.lightPrimary, Color.Black, 0.72f),
        )
    } else {
        val container = mix(accent.lightPrimary, Color.White, 0.82f)
        base.copy(
            primary = accent.darkPrimary,
            onPrimary = mix(accent.darkPrimary, Color.Black, 0.75f),
            primaryContainer = accent.lightPrimary,
            onPrimaryContainer = container,
            secondaryContainer = mix(accent.lightPrimary, Color.Black, 0.35f),
            onSecondaryContainer = container,
        )
    }
}

/**
 * Accent picker for Our Style. A locked accent shows a lock and opens the
 * Premium page instead of switching; the free default is always available.
 */
@Composable
fun AccentDialog(
    selected: String,
    isPremium: Boolean,
    onSelect: (String) -> Unit,
    onLocked: () -> Unit,
    onDismiss: () -> Unit,
) {
    val current = Accents.find(resolveAccentId(selected, isPremium)).id
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Palette, null) },
        title = { Text(stringResource(R.string.accent_title)) },
        text = {
            // Normal column instead of a lazy list: dialogs do not take lazy lists
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Accents.all.forEach { accent ->
                    val locked = accent.premium && !isPremium
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = accent.id == current, role = Role.RadioButton) {
                                if (locked) onLocked() else onSelect(accent.id)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = accent.id == current, onClick = null)
                        Box(
                            Modifier
                                .padding(start = 12.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(accent.lightPrimary)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        )
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(accent.label(), fontWeight = FontWeight.SemiBold)
                            if (locked) {
                                Text(
                                    stringResource(R.string.premium_title),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (locked) {
                            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}
