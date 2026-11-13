@file:OptIn(ExperimentalTextApi::class)

package app.ourdays.ui

import androidx.annotation.StringRes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import ourdays.app.R

/**
 * Eine Schrift zur Auswahl in den Einstellungen: [heading] für Überschriften und die großen Zahlen,
 * [body] für den übrigen Text (null = Systemschrift). Schreibschriften wirken kleiner, daher
 * werden ihre Überschriften um [scale] vergrößert. Alle Schriften stehen unter der SIL Open Font License.
 */
class AppFont(
    val id: String,
    val name: String,
    @StringRes val group: Int,
    val heading: FontFamily,
    val body: FontFamily? = null,
    val scale: Float = 1f,
    /** Übersetzter Name statt [name], z. B. für die klassische Schrift */
    @StringRes val nameRes: Int? = null,
)

/** Anzeigename der Schrift in der gewählten Sprache */
@Composable
fun AppFont.label(): String = nameRes?.let { stringResource(it) } ?: name

object AppFonts {
    /** Schrift für alle, die noch keine eigene gewählt haben */
    const val DEFAULT_ID = "funnel"

    /** Die frühere Schrift der App (Systemschrift mit Serifen-Überschriften) */
    const val CLASSIC_ID = "standard"

    private val weights = listOf(400, 500, 600, 700)

    /** Variable Schrift: eine Datei, alle Stärken über die Achse "wght" */
    private fun variable(res: Int) = FontFamily(
        weights.map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) },
    )

    /** Schrift mit nur einer Stärke, fett wird von Android berechnet */
    private fun single(res: Int) = FontFamily(Font(res))

    val all: List<AppFont> by lazy {
        listOf(
            AppFont(CLASSIC_ID, "", R.string.font_group_standard, FontFamily.Serif, nameRes = R.string.font_classic),
            // Rund und weich
            AppFont("fredoka", "Fredoka", R.string.font_group_round, variable(R.font.fredoka), variable(R.font.fredoka)),
            // Modern und klar
            AppFont("funnel", "Funnel", R.string.font_group_modern, variable(R.font.funnel_display), variable(R.font.funnel_sans)),
            AppFont(
                "poppins",
                "Poppins",
                R.string.font_group_modern,
                FontFamily(
                    Font(R.font.poppins_regular, FontWeight.Normal),
                    Font(R.font.poppins_semibold, FontWeight.SemiBold),
                    Font(R.font.poppins_semibold, FontWeight.Bold),
                ),
                FontFamily(Font(R.font.poppins_regular, FontWeight.Normal), Font(R.font.poppins_semibold, FontWeight.SemiBold)),
            ),
            AppFont("urbanist", "Urbanist", R.string.font_group_modern, variable(R.font.urbanist), variable(R.font.urbanist)),
            AppFont("familjen_grotesk", "Familjen Grotesk", R.string.font_group_modern, variable(R.font.familjen_grotesk), variable(R.font.familjen_grotesk)),
            AppFont("host_grotesk", "Host Grotesk", R.string.font_group_modern, variable(R.font.host_grotesk), variable(R.font.host_grotesk)),
            // Edel, mit Serifen
            AppFont("abril_fatface", "Abril Fatface", R.string.font_group_serif, single(R.font.abril_fatface), variable(R.font.funnel_sans)),
            AppFont("italiana", "Italiana", R.string.font_group_serif, single(R.font.italiana), variable(R.font.funnel_sans), 1.05f),
            AppFont("gloock", "Gloock", R.string.font_group_serif, single(R.font.gloock), variable(R.font.funnel_sans)),
            // Schreibschrift und Handschrift
            AppFont("kalam", "Kalam", R.string.font_group_script, single(R.font.kalam), variable(R.font.funnel_sans)),
        )
    }

    /**
     * Schriftdateien für die Widgets (Überschrift, Text), die nur Bilder statt Compose-Schriften kennen.
     * null = Systemschrift.
     */
    private val files: Map<String, Pair<Int?, Int?>> = mapOf(
            CLASSIC_ID to (null to null),
            "fredoka" to (R.font.fredoka to R.font.fredoka),
            "funnel" to (R.font.funnel_display to R.font.funnel_sans),
            "poppins" to (R.font.poppins_semibold to R.font.poppins_regular),
            "urbanist" to (R.font.urbanist to R.font.urbanist),
            "familjen_grotesk" to (R.font.familjen_grotesk to R.font.familjen_grotesk),
            "host_grotesk" to (R.font.host_grotesk to R.font.host_grotesk),
            "abril_fatface" to (R.font.abril_fatface to R.font.funnel_sans),
            "italiana" to (R.font.italiana to R.font.funnel_sans),
            "gloock" to (R.font.gloock to R.font.funnel_sans),
            "kalam" to (R.font.kalam to R.font.funnel_sans),
    )

    fun files(font: AppFont): Pair<Int?, Int?> = files[font.id] ?: (null to null)

    /** Die Schrift zur gespeicherten ID; leer oder unbekannt = Standard */
    fun find(id: String): AppFont {
        val wanted = id.ifEmpty { DEFAULT_ID }
        return all.firstOrNull { it.id == wanted } ?: all.first { it.id == DEFAULT_ID }
    }

    /** Material-Schriftstile mit dieser Schrift */
    fun typography(font: AppFont): Typography {
        val base = Typography()
        fun TextStyle.head(weight: FontWeight? = null) = copy(
            fontFamily = font.heading,
            fontWeight = weight ?: fontWeight,
            fontSize = fontSize * font.scale,
            lineHeight = lineHeight * font.scale,
        )
        fun TextStyle.text() = if (font.body == null) this else copy(fontFamily = font.body)
        return base.copy(
            displayLarge = base.displayLarge.head(FontWeight.SemiBold),
            displayMedium = base.displayMedium.head(FontWeight.SemiBold),
            displaySmall = base.displaySmall.head(),
            headlineLarge = base.headlineLarge.head(),
            headlineMedium = base.headlineMedium.head(),
            headlineSmall = base.headlineSmall.head(),
            titleLarge = base.titleLarge.head(),
            titleMedium = base.titleMedium.text(),
            titleSmall = base.titleSmall.text(),
            bodyLarge = base.bodyLarge.text(),
            bodyMedium = base.bodyMedium.text(),
            bodySmall = base.bodySmall.text(),
            labelLarge = base.labelLarge.text(),
            labelMedium = base.labelMedium.text(),
            labelSmall = base.labelSmall.text(),
        )
    }
}
