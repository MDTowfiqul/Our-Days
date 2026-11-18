@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package app.ourdays.ui

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.ourdays.data.LoveSettings
import app.ourdays.data.Surprise
import app.ourdays.data.SurpriseOccasions
import app.ourdays.data.missSpan
import kotlinx.coroutines.delay
import ourdays.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/** Aktionen der Spaß-Seite, kommen aus dem ViewModel (in Tests leer) */
data class FunActions(
    val onSetLastMet: (LocalDate, String) -> Unit = { _, _ -> },
    val onSendKiss: (String) -> Unit = {},
    val onSetMissLevel: (Int, String) -> Unit = { _, _ -> },
    val onSendSurprise: (Surprise, String) -> Unit = { _, _ -> },
    val onMarkSurpriseRevealed: (String) -> Unit = {},
    val onDeleteSurprise: (String) -> Unit = {},
)

/** Text über das Teilen-Menü verschicken (z. B. per Messenger) */
private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun formatDate(date: LocalDate): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(date)

private fun formatTime(time: LocalTime): String =
    DateTimeFormatter.ofPattern("h:mm a").format(time)

/** Spaß-Seite: „Ich vermisse dich“-Karte, Pläne und Überraschungen */
@Composable
fun FunScreen(
    settings: LoveSettings,
    actions: FunActions = FunActions(),
    pairedIds: Set<String> = emptySet(),
    now: LocalDateTime? = null,
) {
    val relId = settings.relationship.id
    var showDateWizard by remember { mutableStateOf(false) }
    var showTripWizard by remember { mutableStateOf(false) }
    var showSurprise by remember { mutableStateOf(false) }
    var reveal by remember { mutableStateOf<Surprise?>(null) }
    val surprises = settings.surprisesFor(relId)
    val unrevealed = surprises.filter { !it.revealed }
    val revealed = surprises.filter { it.revealed }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        MissYouCard(settings, relId, actions, now)
        Text(
            stringResource(R.string.fun_plan_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        FunActionCard(Icons.Rounded.CalendarMonth, stringResource(R.string.fun_plan_date), stringResource(R.string.fun_plan_date_sub)) { showDateWizard = true }
        FunActionCard(Icons.Rounded.FlightTakeoff, stringResource(R.string.fun_plan_trip), stringResource(R.string.fun_plan_trip_sub)) { showTripWizard = true }
        FunActionCard(Icons.Rounded.CardGiftcard, stringResource(R.string.fun_surprise_send), stringResource(R.string.fun_surprise_send_sub)) { showSurprise = true }
        if (unrevealed.isNotEmpty()) {
            Text(
                stringResource(R.string.fun_surprise_for_you),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            unrevealed.forEach { s ->
                FunActionCard(Icons.Rounded.Mail, stringResource(R.string.fun_reveal_title), occasionTitle(s.occasion)) { reveal = s }
            }
        }
        if (revealed.isNotEmpty()) {
            Text(
                stringResource(R.string.fun_surprise_keepsakes),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            revealed.forEach { s ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(occasionTitle(s.occasion), style = MaterialTheme.typography.titleMedium)
                            Text(s.message, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { actions.onDeleteSurprise(s.id) }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(72.dp))
    }
    if (showDateWizard) DateWizard(relId) { showDateWizard = false }
    if (showTripWizard) TripWizard(relId) { showTripWizard = false }
    if (showSurprise) SurpriseComposer(relId, pairedIds.contains(relId), actions) { showSurprise = false }
    reveal?.let { s ->
        SurpriseReveal(
            surprise = s,
            settings = settings,
            onKeep = { actions.onMarkSurpriseRevealed(s.id); reveal = null },
            onDismiss = { reveal = null },
        )
    }
}

@Composable
private fun occasionTitle(occasion: String): String = stringResource(
    when (occasion) {
        SurpriseOccasions.PROPOSAL -> R.string.fun_occasion_proposal
        SurpriseOccasions.BIRTHDAY -> R.string.fun_occasion_birthday
        SurpriseOccasions.EID -> R.string.fun_occasion_eid
        SurpriseOccasions.ANNIVERSARY -> R.string.fun_occasion_anniversary
        else -> R.string.fun_occasion_just_because
    },
)

@Composable
private fun FunActionCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(Modifier.padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Dunkle Karte im Stil der „Ich vermisse dich“-Seite: Foto, Zähler, Schieberegler, Küsse, Notizen */
@Composable
private fun MissYouCard(settings: LoveSettings, relId: String, actions: FunActions, now: LocalDateTime? = null) {
    val lastMet = settings.lastMetFor(relId)
    val kisses = settings.kissesFor(relId)
    val savedLevel = settings.missLevelFor(relId) ?: 3
    var current by remember { mutableStateOf(now ?: LocalDateTime.now()) }
    LaunchedEffect(lastMet, now) {
        if (lastMet == null || now != null) return@LaunchedEffect
        while (true) {
            current = LocalDateTime.now()
            delay(1000 - System.currentTimeMillis() % 1000)
        }
    }
    var sliderLevel by remember(savedLevel) { mutableStateOf(savedLevel) }
    var noteIndex by remember { mutableStateOf(0) }
    var kissBurst by remember { mutableStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    val notes = listOf(
        R.string.fun_note_1, R.string.fun_note_2, R.string.fun_note_3, R.string.fun_note_4,
        R.string.fun_note_5, R.string.fun_note_6, R.string.fun_note_7, R.string.fun_note_8,
    )
    val missTexts = listOf(
        R.string.fun_miss_1, R.string.fun_miss_2, R.string.fun_miss_3, R.string.fun_miss_4, R.string.fun_miss_5,
    )
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF0F0C20)),
    ) {
        CouplePhoto(
            settings,
            Modifier
                .matchParentSize()
                .blur(28.dp)
                .alpha(0.5f),
            photoPath = settings.photoFor(relId),
        )
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f)))
        Box(Modifier.matchParentSize()) { FloatingHearts() }
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.fun_miss_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.fun_miss_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (lastMet == null) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.padding(top = 20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                ) {
                    Text(stringResource(R.string.fun_miss_set_date), color = Color.White)
                }
            } else {
                val span = missSpan(lastMet, current)
                Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimerBox(span.days.toString(), R.string.fun_miss_days)
                    TimerBox(span.hours.toString().padStart(2, '0'), R.string.fun_miss_hours)
                    TimerBox(span.minutes.toString().padStart(2, '0'), R.string.fun_miss_mins)
                    TimerBox(span.seconds.toString().padStart(2, '0'), R.string.fun_miss_secs)
                }
                TextButton(onClick = { showDatePicker = true }) {
                    Text(
                        stringResource(R.string.fun_miss_last_met, formatDate(lastMet)),
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
            Text(
                stringResource(R.string.fun_miss_how_much),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp),
            )
            Slider(
                value = sliderLevel.toFloat(),
                onValueChange = { sliderLevel = it.roundToInt().coerceIn(1, 5) },
                onValueChangeFinished = { actions.onSetMissLevel(sliderLevel, relId) },
                valueRange = 1f..5f,
                steps = 3,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Text(
                stringResource(missTexts[(sliderLevel - 1).coerceIn(0, 4)]),
                color = Color(0xFFFFB2B9),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = { actions.onSendKiss(relId); kissBurst++ },
                modifier = Modifier.padding(top = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4D8D)),
            ) {
                Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.fun_kiss), color = Color.White, modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                pluralStringResource(R.plurals.fun_kisses, kisses, kisses),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                stringResource(notes[noteIndex % notes.size]),
                color = Color.White.copy(alpha = 0.85f),
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .clickable { noteIndex = (noteIndex + 1) % notes.size },
            )
            Text(
                stringResource(R.string.fun_note_hint),
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (kissBurst > 0) ConfettiBurst(kissBurst)
    }
    if (showDatePicker) {
        FunDatePickerDialog(
            current = lastMet ?: LocalDate.now().minusDays(2),
            title = R.string.fun_miss_set_date,
            onDismiss = { showDatePicker = false },
            onSave = { actions.onSetLastMet(it, relId); showDatePicker = false },
        )
    }
}

@Composable
private fun TimerBox(value: String, @StringRes label: Int) {
    Column(
        Modifier
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(
            stringResource(label).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
        )
    }
}

/** Datumsauswahl wie in den Einstellungen (der DatePicker rechnet in UTC-Millisekunden) */
@Composable
private fun FunDatePickerDialog(
    current: LocalDate,
    @StringRes title: Int,
    onDismiss: () -> Unit,
    onSave: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onSave(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state = state, title = { Text(stringResource(title), Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}

/** Frage mit Ja und einem Nein-Knopf, der erst fleht und ab dem dritten Nein ausweicht */
@Composable
private fun AskStep(
    @StringRes question: Int,
    pleading: List<Int>,
    onYes: () -> Unit,
) {
    var noCount by remember { mutableStateOf(0) }
    var dodgeX by remember { mutableStateOf(0f) }
    var dodgeY by remember { mutableStateOf(0f) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(question),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (noCount > 0) {
            Text(
                stringResource(pleading[(noCount - 1).coerceAtMost(pleading.size - 1)]),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onYes) { Text(stringResource(R.string.fun_wizard_yes)) }
            Button(
                onClick = {
                    noCount++
                    if (noCount >= 3) {
                        dodgeX = (-100..100).random().toFloat()
                        dodgeY = (-28..28).random().toFloat()
                    }
                },
                modifier = Modifier.offset(x = dodgeX.dp, y = dodgeY.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.fun_wizard_no)) }
        }
    }
}

@Composable
private fun WizardDialog(@StringRes title: Int, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(24.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel)) }
                }
                content()
            }
        }
    }
}

@Composable
private fun WizardNav(canNext: Boolean, onBack: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.fun_wizard_back)) }
        Button(onClick = onNext, enabled = canNext) { Text(stringResource(R.string.fun_wizard_next)) }
    }
}

private val pleadingLines = listOf(
    R.string.fun_no_1, R.string.fun_no_2, R.string.fun_no_3, R.string.fun_no_4, R.string.fun_no_5,
)

private val foodOptions = listOf(
    "kacchi" to R.string.fun_food_kacchi,
    "khichuri" to R.string.fun_food_khichuri,
    "grilled" to R.string.fun_food_grilled,
    "fuchka" to R.string.fun_food_fuchka,
    "borhani" to R.string.fun_food_borhani,
    "falooda" to R.string.fun_food_falooda,
)

private val timeSlots = listOf(
    LocalTime.of(12, 0), LocalTime.of(15, 0), LocalTime.of(18, 30),
    LocalTime.of(19, 30), LocalTime.of(20, 30), LocalTime.of(21, 0),
)

/** Date planen: fragen → Datum und Uhrzeit → Essen → Zusammenfassung und teilen */
@Composable
private fun DateWizard(relId: String, onDismiss: () -> Unit) {
    var stage by remember { mutableStateOf(0) }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var time by remember { mutableStateOf<LocalTime?>(null) }
    var foods by remember { mutableStateOf(setOf<String>()) }
    var showPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    WizardDialog(R.string.fun_plan_date, onDismiss) {
        when (stage) {
            0 -> AskStep(R.string.fun_wizard_date_q, pleadingLines) { stage = 1 }
            1 -> {
                Text(stringResource(R.string.fun_wizard_when), style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Text(date?.let { formatDate(it) } ?: stringResource(R.string.fun_wizard_date))
                }
                Text(
                    stringResource(R.string.fun_wizard_time),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    timeSlots.forEach { slot ->
                        FilterChip(
                            selected = time == slot,
                            onClick = { time = slot },
                            label = { Text(formatTime(slot)) },
                        )
                    }
                }
                WizardNav(canNext = date != null && time != null, onBack = onDismiss, onNext = { stage = 2 })
            }
            2 -> {
                Text(stringResource(R.string.fun_wizard_food), style = MaterialTheme.typography.titleMedium)
                FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    foodOptions.forEach { (id, label) ->
                        FilterChip(
                            selected = id in foods,
                            onClick = { foods = if (id in foods) foods - id else foods + id },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
                WizardNav(canNext = foods.isNotEmpty(), onBack = { stage = 1 }, onNext = { stage = 3 })
            }
            else -> {
                val menu = foods.map { id -> context.getString(foodOptions.first { it.first == id }.second) }.joinToString(", ")
                val shareMessage = context.getString(
                    R.string.fun_date_share,
                    date?.let { formatDate(it) }.orEmpty(),
                    time?.let { formatTime(it) }.orEmpty(),
                    menu,
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.fun_wizard_summary), style = MaterialTheme.typography.titleMedium)
                        Text(shareMessage, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.fun_wizard_done)) }
                    Button(onClick = { shareText(context, shareMessage) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Send, null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.fun_wizard_share), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
    if (showPicker) {
        FunDatePickerDialog(
            current = date ?: LocalDate.now(),
            title = R.string.fun_wizard_date,
            onDismiss = { showPicker = false },
            onSave = { date = it; showPicker = false },
        )
    }
}

private val destinationOptions = listOf(
    "maldives" to R.string.fun_dest_maldives,
    "switzerland" to R.string.fun_dest_switzerland,
    "bali" to R.string.fun_dest_bali,
    "paris" to R.string.fun_dest_paris,
)

/** Reise planen: fragen → Ziel → Datum → Zusammenfassung und teilen */
@Composable
private fun TripWizard(relId: String, onDismiss: () -> Unit) {
    var stage by remember { mutableStateOf(0) }
    var destination by remember { mutableStateOf<String?>(null) }
    var localSpot by remember { mutableStateOf("") }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    WizardDialog(R.string.fun_plan_trip, onDismiss) {
        when (stage) {
            0 -> AskStep(R.string.fun_wizard_trip_q, pleadingLines) { stage = 1 }
            1 -> {
                Text(stringResource(R.string.fun_wizard_where), style = MaterialTheme.typography.titleMedium)
                FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    destinationOptions.forEach { (id, label) ->
                        FilterChip(
                            selected = destination == id,
                            onClick = { destination = id },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Text(
                        stringResource(R.string.fun_wizard_budget_note),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                OutlinedTextField(
                    value = localSpot,
                    onValueChange = { localSpot = it },
                    label = { Text(stringResource(R.string.fun_wizard_local)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    singleLine = true,
                )
                WizardNav(
                    canNext = destination != null || localSpot.isNotBlank(),
                    onBack = onDismiss,
                    onNext = { stage = 2 },
                )
            }
            2 -> {
                Text(stringResource(R.string.fun_wizard_when), style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Text(date?.let { formatDate(it) } ?: stringResource(R.string.fun_wizard_date))
                }
                WizardNav(canNext = date != null, onBack = { stage = 1 }, onNext = { stage = 3 })
            }
            else -> {
                val where = destination?.let { id -> context.getString(destinationOptions.first { it.first == id }.second) }
                    ?: localSpot.trim()
                val shareMessage = context.getString(
                    R.string.fun_trip_share,
                    where,
                    date?.let { formatDate(it) }.orEmpty(),
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.fun_wizard_summary), style = MaterialTheme.typography.titleMedium)
                        Text(shareMessage, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.fun_wizard_done)) }
                    Button(onClick = { shareText(context, shareMessage) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Send, null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.fun_wizard_share), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
    if (showPicker) {
        FunDatePickerDialog(
            current = date ?: LocalDate.now(),
            title = R.string.fun_wizard_date,
            onDismiss = { showPicker = false },
            onSave = { date = it; showPicker = false },
        )
    }
}

/** Überraschung schreiben: Anlass wählen, Nachricht schreiben, an das andere Handy schicken */
@Composable
private fun SurpriseComposer(relId: String, isPaired: Boolean, actions: FunActions, onDismiss: () -> Unit) {
    var occasion by remember { mutableStateOf(SurpriseOccasions.JUST_BECAUSE) }
    var message by remember { mutableStateOf("") }
    WizardDialog(R.string.fun_surprise_send, onDismiss) {
        Text(stringResource(R.string.fun_surprise_occasion), style = MaterialTheme.typography.titleMedium)
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SurpriseOccasions.ALL.forEach { occ ->
                FilterChip(
                    selected = occasion == occ,
                    onClick = { occasion = occ },
                    label = { Text(occasionTitle(occ)) },
                )
            }
        }
        OutlinedTextField(
            value = message,
            onValueChange = { if (it.length <= 500) message = it },
            label = { Text(stringResource(R.string.fun_surprise_message)) },
            placeholder = { Text(stringResource(R.string.fun_surprise_message_hint)) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            minLines = 3,
        )
        if (!isPaired) {
            Text(
                stringResource(R.string.fun_surprise_not_paired),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Button(
            onClick = {
                actions.onSendSurprise(
                    Surprise(relationshipId = relId, occasion = occasion, message = message.trim(), from = null),
                    relId,
                )
                onDismiss()
            },
            enabled = message.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) { Text(stringResource(R.string.fun_surprise_send_button)) }
    }
}

/** Empfangene Überraschung öffnen: erst spielen, dann die Nachricht — mit Konfetti */
@Composable
private fun SurpriseReveal(surprise: Surprise, settings: LoveSettings, onKeep: () -> Unit, onDismiss: () -> Unit) {
    var opened by remember { mutableStateOf(false) }
    var burst by remember { mutableStateOf(0) }
    val fromName = surprise.from?.let { settings.person(it)?.name }
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
            Box(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("🎁", style = MaterialTheme.typography.displayMedium)
                    Text(
                        stringResource(R.string.fun_reveal_title),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (fromName != null) {
                        Text(
                            stringResource(R.string.fun_reveal_from, fromName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (!opened) {
                        AskStep(R.string.fun_reveal_q, pleadingLines) { opened = true; burst++ }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(occasionTitle(surprise.occasion), style = MaterialTheme.typography.titleMedium)
                                Text(surprise.message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                        Button(onClick = onKeep, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Text(stringResource(R.string.fun_reveal_keep))
                        }
                    }
                }
                if (burst > 0) {
                    Box(Modifier.matchParentSize()) { ConfettiBurst(burst) }
                }
            }
        }
    }
}
