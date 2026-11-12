package app.ourdays.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import java.time.LocalDate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ourdays.app.R

val Wine = Color(0xFF8B1E31)

private val Light = lightColorScheme(
    primary = Wine,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9DC),
    onPrimaryContainer = Color(0xFF3F0010),
    secondary = Color(0xFF765659),
    secondaryContainer = Color(0xFFFFD9DC),
    onSecondaryContainer = Color(0xFF2C1517),
    tertiary = Color(0xFF7A5733),
    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF22191A),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF22191A),
    surfaceVariant = Color(0xFFF4DDDE),
    onSurfaceVariant = Color(0xFF524344),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF0F0),
    surfaceContainer = Color(0xFFFCEAEA),
    surfaceContainerHigh = Color(0xFFF6E4E4),
    surfaceContainerHighest = Color(0xFFF0DEDF),
    outlineVariant = Color(0xFFD7C1C2),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB2B9),
    onPrimary = Color(0xFF670020),
    primaryContainer = Color(0xFF8B1E31),
    onPrimaryContainer = Color(0xFFFFD9DC),
    secondary = Color(0xFFE5BDC0),
    secondaryContainer = Color(0xFF5D3F42),
    onSecondaryContainer = Color(0xFFFFD9DC),
    tertiary = Color(0xFFEBBE90),
    background = Color(0xFF1A1112),
    onBackground = Color(0xFFF0DEDF),
    surface = Color(0xFF1A1112),
    onSurface = Color(0xFFF0DEDF),
    surfaceVariant = Color(0xFF524344),
    onSurfaceVariant = Color(0xFFD7C1C2),
    surfaceContainerLowest = Color(0xFF140C0D),
    surfaceContainerLow = Color(0xFF22191A),
    surfaceContainer = Color(0xFF271D1E),
    surfaceContainerHigh = Color(0xFF322828),
    surfaceContainerHighest = Color(0xFF3D3233),
    outlineVariant = Color(0xFF524344),
)

val LabelCaps = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)

/** Farbschemen: „light“ (helles Weiß) ist der Standard, „system“ folgt dem Handy. */
object AppThemes {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"
    const val ROSE = "rose"
    const val OCEAN = "ocean"
    const val FOREST = "forest"

    data class AppTheme(val id: String, @androidx.annotation.StringRes val label: Int)

    val all = listOf(
        AppTheme(SYSTEM, R.string.theme_system),
        AppTheme(LIGHT, R.string.theme_light),
        AppTheme(DARK, R.string.theme_dark),
        AppTheme(ROSE, R.string.theme_rose),
        AppTheme(OCEAN, R.string.theme_ocean),
        AppTheme(FOREST, R.string.theme_forest),
    )

    fun find(id: String): AppTheme = all.firstOrNull { it.id == id } ?: all[1]

    /** Helles Schema mit einer anderen Hauptfarbe, sonst wie das helle Standardschema */
    private fun tinted(primary: Color, primaryContainer: Color, onPrimaryContainer: Color, secondary: Color): androidx.compose.material3.ColorScheme =
        Light.copy(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            secondaryContainer = primaryContainer,
            onSecondaryContainer = onPrimaryContainer,
            tertiary = secondary,
        )

    private val Rose = tinted(
        primary = Color(0xFFC2185B),
        primaryContainer = Color(0xFFFFD9E3),
        onPrimaryContainer = Color(0xFF3F0016),
        secondary = Color(0xFF7A4A5E),
    )
    private val Ocean = tinted(
        primary = Color(0xFF1565C0),
        primaryContainer = Color(0xFFD6E4FF),
        onPrimaryContainer = Color(0xFF001D35),
        secondary = Color(0xFF4A5E7A),
    )
    private val Forest = tinted(
        primary = Color(0xFF2E7D32),
        primaryContainer = Color(0xFFD3EFD4),
        onPrimaryContainer = Color(0xFF00210A),
        secondary = Color(0xFF4A6350),
    )

    @Composable
    fun scheme(id: String): androidx.compose.material3.ColorScheme = when (id) {
        SYSTEM -> if (isSystemInDarkTheme()) Dark else Light
        DARK -> Dark
        ROSE -> Rose
        OCEAN -> Ocean
        FOREST -> Forest
        else -> Light
    }
}

@Composable
fun LoveTheme(theme: String = AppThemes.LIGHT, font: String = "", content: @Composable () -> Unit) {
    val colors = AppThemes.scheme(theme)
    val typography = remember(font) { AppFonts.typography(AppFonts.find(font)) }
    MaterialTheme(colorScheme = colors, typography = typography) {
        // Standard-Textfarbe passend zum Hintergrund, sonst ist Text ohne eigene Farbe im Dunkelmodus schwarz
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}

/** Standardmotive, falls kein eigenes Foto gewählt ist. */
data class Preset(@androidx.annotation.StringRes val name: Int, val colors: List<Color>) {
    val brush: Brush get() = Brush.linearGradient(colors)
}

val Presets = listOf(
    Preset(ourdays.app.R.string.preset_wine, listOf(Color(0xFF4A0B18), Color(0xFF8B1E31), Color(0xFFE0566B))),
    Preset(ourdays.app.R.string.preset_sunset, listOf(Color(0xFF3B1C4A), Color(0xFFC2466B), Color(0xFFF7A26B))),
    Preset(ourdays.app.R.string.preset_lavender, listOf(Color(0xFF2E2A5A), Color(0xFF7B63B8), Color(0xFFE6B7E0))),
    Preset(ourdays.app.R.string.preset_night, listOf(Color(0xFF05070F), Color(0xFF1B2450), Color(0xFF6A4C93))),
    Preset(ourdays.app.R.string.preset_rose, listOf(Color(0xFF8C4A5A), Color(0xFFE8A0A8), Color(0xFFFBE3DA))),
    Preset(ourdays.app.R.string.preset_ocean, listOf(Color(0xFF062B3A), Color(0xFF1C6E8C), Color(0xFF8FD3D1))),
)

/** Im Juni (Pride Month) ist die App ein bisschen bunter: Herzen in Regenbogenfarben und ein Gruß */
val LocalPrideMonth = staticCompositionLocalOf { false }

fun isPrideMonth(day: LocalDate) = day.monthValue == 6

/** Farben der Regenbogenflagge */
val PrideColors = listOf(
    Color(0xFFE40303),
    Color(0xFFFF8C00),
    Color(0xFFFFED00),
    Color(0xFF008026),
    Color(0xFF004DFF),
    Color(0xFF750787),
)

/** Färbt den Inhalt (z. B. ein Herz-Icon) mit einem Regenbogenverlauf */
fun Modifier.rainbow(): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(Brush.linearGradient(PrideColors), blendMode = BlendMode.SrcIn)
    }

/**
 * Farbschema-Auswahl: ein Tipp wechselt das Farbschema sofort in der ganzen App,
 * so sieht man direkt, wie es wirkt.
 */
@Composable
fun ThemeDialog(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val current = AppThemes.find(selected).id
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_title)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                AppThemes.all.forEach { theme ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = theme.id == current, role = Role.RadioButton) { onSelect(theme.id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = theme.id == current, onClick = null)
                        Text(
                            stringResource(theme.label),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}
