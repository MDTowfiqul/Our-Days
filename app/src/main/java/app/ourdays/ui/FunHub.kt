package app.ourdays.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ourdays.data.LoveSettings
import app.ourdays.data.Pairing
import app.ourdays.data.focusUrl
import app.ourdays.data.gamesIndexUrl
import app.ourdays.data.roomCodeForPairing
import app.ourdays.data.watchUrl
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import ourdays.app.R

/** Die vier Bereiche der Spaß-Seite */
enum class FunSection(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val iconAsset: String,
    val accent: Color,
) {
    BY_TYPE(R.string.fun_hub_by_type, R.string.fun_hub_by_type_sub, "lottie/heart.json", Color(0xFFFF4D8D)),
    GAMES(R.string.fun_hub_games, R.string.fun_hub_games_sub, "lottie/target.json", Color(0xFF2196F3)),
    FOCUS(R.string.fun_hub_focus, R.string.fun_hub_focus_sub, "lottie/clock.json", Color(0xFF2E7D32)),
    WATCH(R.string.fun_hub_watch, R.string.fun_hub_watch_sub, "lottie/youtube.json", Color(0xFFC2185B)),
}

/** Vorlage je Beziehungstyp: eigene Farbe und eigene Worte, gleiche Abläufe */
enum class FunType(
    val accent: Color,
    @StringRes val missTitle: Int,
    @StringRes val missSubtitle: Int,
    @StringRes val sendLabel: Int,
    val sendIsKiss: Boolean,
    @StringRes val planDate: Int,
    @StringRes val label: Int,
    val notes: List<Int>,
) {
    COUPLE(
        Color(0xFFFF4D8D),
        R.string.fun_miss_title, R.string.fun_miss_subtitle, R.string.fun_kiss, true,
        R.string.fun_plan_date, R.string.fun_who_partner,
        listOf(
            R.string.fun_note_1, R.string.fun_note_2, R.string.fun_note_3, R.string.fun_note_4,
            R.string.fun_note_5, R.string.fun_note_6, R.string.fun_note_7, R.string.fun_note_8,
        ),
    ),
    FAMILY(
        Color(0xFFFFB703),
        R.string.fun_miss_title_family, R.string.fun_miss_subtitle_family, R.string.fun_love, false,
        R.string.fun_plan_date_family, R.string.fun_who_family,
        listOf(R.string.fun_note_family_1, R.string.fun_note_family_2, R.string.fun_note_family_3, R.string.fun_note_family_4),
    ),
    FRIENDS(
        Color(0xFF00B8A9),
        R.string.fun_miss_title_friends, R.string.fun_miss_subtitle_friends, R.string.fun_love, false,
        R.string.fun_plan_date_friends, R.string.fun_who_friends,
        listOf(R.string.fun_note_friends_1, R.string.fun_note_friends_2, R.string.fun_note_friends_3, R.string.fun_note_friends_4),
    ),
    OTHERS(
        Color(0xFFAE52FF),
        R.string.fun_miss_title_others, R.string.fun_miss_subtitle_others, R.string.fun_love, false,
        R.string.fun_plan_date_others, R.string.fun_who_others,
        listOf(R.string.fun_note_others_1, R.string.fun_note_others_2, R.string.fun_note_others_3, R.string.fun_note_others_4),
    ),
}

/** Spaß-Tab: erst die vier Bereiche, dann der gewählte Bereich */
@Composable
fun FunTab(
    settings: LoveSettings,
    funActions: FunActions,
    pairedIds: Set<String>,
    pairings: Map<String, Pairing> = emptyMap(),
) {
    var section by remember { mutableStateOf<FunSection?>(null) }
    var type by remember { mutableStateOf<FunType?>(null) }
    BackHandler(enabled = type != null) { type = null }
    BackHandler(enabled = section != null && type == null) { section = null }
    when (val s = section) {
        null -> FunHub(settings) { section = it }
        FunSection.BY_TYPE -> {
            val t = type
            if (t == null) FunTypePicker { type = it } else FunScreen(settings, funActions, pairedIds, funType = t)
        }
        FunSection.GAMES -> {
            val pairing = pairings[settings.relationship.id]
            if (pairing == null) {
                FunNotPaired()
            } else {
                GameWebView(gamesIndexUrl(roomCodeForPairing(pairing))) { section = null }
            }
        }
        FunSection.FOCUS -> {
            val pairing = pairings[settings.relationship.id]
            if (pairing == null) {
                FunNotPaired()
            } else {
                GameWebView(focusUrl(roomCodeForPairing(pairing)), titleRes = FunSection.FOCUS.title) { section = null }
            }
        }
        FunSection.WATCH -> {
            val pairing = pairings[settings.relationship.id]
            if (pairing == null) {
                FunNotPaired()
            } else {
                GameWebView(watchUrl(roomCodeForPairing(pairing)), titleRes = FunSection.WATCH.title) { section = null }
            }
        }
    }
}

/** Die vier Bereichs-Karten mit animiertem Icon (useanimations, Lottie) */
@Composable
fun FunHub(settings: LoveSettings, onSection: (FunSection) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(stringResource(R.string.fun_hub_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            settings.namesOf(settings.relationship),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        FunSection.entries.forEach { section ->
            Card(
                onClick = { onSection(section) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionIcon(section.iconAsset)
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(stringResource(section.title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(section.subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(72.dp))
    }
}

/** Für wen ist das — dann die Vorlage dieses Typs */
@Composable
private fun FunTypePicker(onPick: (FunType) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(stringResource(R.string.fun_who_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        FunType.entries.forEach { type ->
            Card(
                onClick = { onPick(type) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(type.accent),
                    )
                    Text(
                        stringResource(type.label),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(72.dp))
    }
}

/** Ohne Kopplung gibt es keinen Raum-Code — erst koppeln, dann spielen */
@Composable
private fun FunNotPaired() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(96.dp))
        Text(
            stringResource(R.string.game_not_paired),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Animiertes Icon aus assets/lottie. Die useanimations-Icons sind schwarz
 * gezeichnet, darum liegen sie auf einem weißen Kreis — so lesen sie sich
 * im hellen wie im dunklen Design.
 */
@Composable
private fun SectionIcon(asset: String, sizeDp: Int = 56) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset(asset))
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        LottieAnimation(
            composition,
            iterations = LottieConstants.IterateForever,
            modifier = Modifier.size((sizeDp * 0.72f).dp),
        )
    }
}
