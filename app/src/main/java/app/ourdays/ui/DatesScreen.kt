package app.ourdays.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ourdays.BuildConfig
import app.ourdays.R
import app.ourdays.data.DatePlan
import app.ourdays.data.DatePlanMath
import app.ourdays.data.PremiumUnlockCode
import app.ourdays.data.Texts
import app.ourdays.data.formatNumber
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/** "Our Dates" tab: countdown to the next date, upcoming and past dates. */
@Composable
fun DatesScreen(
    settings: app.ourdays.data.LoveSettings,
    today: LocalDate,
    onAdd: () -> Unit,
    onOpen: (DatePlan) -> Unit,
    /** Opens settings on the premium page (from the locked card) */
    onOpenPremium: () -> Unit = {},
) {
    val dates = settings.visibleDates
    val upcoming = DatePlanMath.upcoming(dates, today, Int.MAX_VALUE)
    val past = DatePlanMath.past(dates, today)
    val next = upcoming.firstOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp),
    ) {
        Text(
            stringResource(R.string.dates_title),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(top = 28.dp),
        )
        Text(
            if (dates.isEmpty()) {
                ""
            } else {
                pluralStringResource(R.plurals.dates_count, dates.size, formatNumber(dates.size.toLong()))
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )

        if (!settings.isPremium) {
            PremiumLockedCard(onOpenPremium)
        } else {
            next?.let { NextDateCard(it, today) }

            if (dates.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Presets[0].brush),
                            contentAlignment = Alignment.Center,
                        ) {
                            BeatingHeart(size = 34.dp)
                        }
                        Text(
                            stringResource(R.string.dates_empty_title),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        Text(
                            stringResource(R.string.dates_empty_sub),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            FilledTonalButton(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
            ) {
                Icon(Icons.Rounded.Add, null)
                Text(stringResource(R.string.dates_add), Modifier.padding(start = 8.dp))
            }

            if (upcoming.isNotEmpty()) {
                Text(
                    stringResource(R.string.dates_upcoming).uppercase(),
                    style = LabelCaps,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                upcoming.forEach { DateRow(it, today, onOpen) }
                Spacer(Modifier.height(8.dp))
            }

            if (past.isNotEmpty()) {
                Text(
                    stringResource(R.string.dates_past).uppercase(),
                    style = LabelCaps,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                past.forEach { DateRow(it, today, onOpen) }
            }
        }

        // Platz für die schwebende Leiste
        Spacer(Modifier.height(96.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun NextDateCard(plan: DatePlan, today: LocalDate) {
    val context = LocalContext.current
    val days = DatePlanMath.daysUntil(plan, today)
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.date_next).uppercase(),
                style = LabelCaps,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                plan.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                when (days) {
                    0L -> stringResource(R.string.today_word)
                    1L -> stringResource(R.string.tomorrow)
                    2L -> stringResource(R.string.day_after_tomorrow)
                    else -> pluralStringResource(R.plurals.in_days, days.toInt(), formatNumber(days))
                },
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                listOfNotNull(
                    Texts.dateWithWeekday(plan.date),
                    plan.time?.let { Texts.time(context, it) },
                    plan.place.ifBlank { null },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DateRow(plan: DatePlan, today: LocalDate, onOpen: (DatePlan) -> Unit) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable { onOpen(plan) },
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                listOfNotNull(
                    Texts.dateWithWeekday(plan.date),
                    plan.time?.let { Texts.time(context, it) },
                    plan.place.ifBlank { null },
                ).joinToString(" · ").uppercase(),
                style = LabelCaps,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                plan.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dateRelativeText(context, plan.date, today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (plan.remindBefore > 0) {
                    Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(16.dp),
                    )
                }
            }
            if (plan.note.isNotBlank()) {
                Text(
                    plan.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

private fun dateRelativeText(context: android.content.Context, date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        days == 0L -> context.getString(R.string.today_word)
        days > 0 -> context.resources.getQuantityString(R.plurals.days_ago, days.toInt(), formatNumber(days))
        else -> context.resources.getQuantityString(R.plurals.in_days, (-days).toInt(), formatNumber(-days))
    }
}

/**
 * Locked state of Our Dates, shown on the Dates tab when premium is not
 * unlocked. Buying happens in [PremiumPage] (settings); this card only
 * explains and links there via [onOpenPremium].
 */
@Composable
fun PremiumLockedCard(onOpenPremium: () -> Unit = {}) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Presets[0].brush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }
            Text(
                stringResource(R.string.premium_locked_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                stringResource(R.string.premium_locked_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
            FilledTonalButton(onClick = onOpenPremium, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.premium_buy))
            }
        }
    }
}

/**
 * Premium page (settings): status, buying (Play only), unlock code entry,
 * and showing the own code for the partner. Shared by the Dates tab card
 * through [PremiumLockedCard] only for the buy button; the full page lives
 * here so settings and dates never implement premium twice.
 */
@Composable
fun PremiumPage(
    settings: app.ourdays.data.LoveSettings,
    onBuyPremium: () -> Unit,
    onUnlockWithCode: (String) -> Unit,
    onRestorePurchase: () -> Unit,
) {
    val isPlay = BuildConfig.FLAVOR == "play"
    var code by rememberSaveable { mutableStateOf("") }
    var showCode by rememberSaveable { mutableStateOf(false) }
    val premium = settings.premium

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    stringResource(if (premium != null) R.string.premium_active else R.string.premium_free),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (premium != null) {
                    Text(
                        stringResource(
                            when (premium.source) {
                                "code" -> R.string.premium_source_code
                                "sync" -> R.string.premium_source_sync
                                else -> R.string.premium_source_play
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showCode = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.premium_show_code)) }
                    if (isPlay) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onRestorePurchase,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.premium_restore)) }
                    }
                } else {
                    Text(
                        stringResource(R.string.premium_buy_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    if (isPlay) {
                        Button(onClick = onBuyPremium, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.premium_buy))
                        }
                    } else {
                        Text(
                            stringResource(R.string.premium_not_available),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.premium_code_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text(stringResource(R.string.premium_code_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = { onUnlockWithCode(code) },
                        enabled = code.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.premium_code_enter)) }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.premium_sync_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showCode && premium != null) {
        AlertDialog(
            onDismissRequest = { showCode = false },
            icon = { Icon(Icons.Rounded.Star, null) },
            title = { Text(stringResource(R.string.premium_code_title)) },
            text = {
                Column {
                    Text(
                        PremiumUnlockCode.displayCode(premium.orderId),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.premium_code_share_text),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showCode = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

/**
 * New date ([existing] = null) or edit. Mirrors MomentEditorScreen, but
 * without photos: title, date, optional time, place, notes, reminder and
 * relationship.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DateEditorScreen(
    existing: DatePlan?,
    defaultDate: LocalDate,
    onClose: () -> Unit,
    onSave: (DatePlan) -> Unit,
    onDelete: (DatePlan) -> Unit,
    /** Relationships as ID and title; only asked when there are several */
    relationships: List<Pair<String, String>> = emptyList(),
    /** Preselection for new dates, null = all */
    defaultRelationship: String? = null,
) {
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var epochDay by rememberSaveable { mutableStateOf((existing?.date ?: defaultDate).toEpochDay()) }
    // Seconds of day, -1 = no time
    var timeSecond by rememberSaveable { mutableStateOf(existing?.time?.toSecondOfDay() ?: -1) }
    var place by rememberSaveable { mutableStateOf(existing?.place.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var remind by rememberSaveable { mutableStateOf(existing?.remindBefore ?: 0) }
    var relationship by rememberSaveable {
        mutableStateOf(if (existing != null) existing.relationshipId else defaultRelationship)
    }
    var dateDialog by rememberSaveable { mutableStateOf(false) }
    var timeDialog by rememberSaveable { mutableStateOf(false) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (existing == null) R.string.date_new else R.string.date_edit)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { deleteDialog = true }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.moment_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.date_field_title)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.date_field_date)) },
                supportingContent = {
                    Text(Texts.longDate(LocalDate.ofEpochDay(epochDay)), color = MaterialTheme.colorScheme.primary)
                },
                leadingContent = { Icon(Icons.Rounded.CalendarMonth, null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { dateDialog = true },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.date_field_time)) },
                supportingContent = {
                    val context = LocalContext.current
                    Text(
                        if (timeSecond < 0) {
                            stringResource(R.string.date_field_time_none)
                        } else {
                            Texts.time(context, LocalTime.ofSecondOfDay(timeSecond.toLong()))
                        },
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                leadingContent = { Icon(Icons.Rounded.Schedule, null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { timeDialog = true },
            )
            OutlinedTextField(
                value = place,
                onValueChange = { place = it },
                label = { Text(stringResource(R.string.date_field_place)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.date_field_note)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Text(
                    stringResource(R.string.date_field_remind),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        0 to R.string.date_remind_none,
                        1 to R.string.date_remind_day_before,
                        7 to R.string.date_remind_week_before,
                    ).forEach { (days, label) ->
                        FilterChip(
                            selected = remind == days,
                            onClick = { remind = days },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }

            if (relationships.size > 1) {
                Column {
                    Text(
                        stringResource(R.string.moment_for),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = relationship == null,
                            onClick = { relationship = null },
                            label = { Text(stringResource(R.string.poly_all)) },
                        )
                        relationships.forEach { (id, name) ->
                            FilterChip(
                                selected = relationship == id,
                                onClick = { relationship = id },
                                label = { Text(name) },
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    onSave(
                        DatePlan(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            title = title.trim(),
                            date = LocalDate.ofEpochDay(epochDay),
                            time = timeSecond.takeIf { it >= 0 }?.let { LocalTime.ofSecondOfDay(it.toLong()) },
                            note = note.trim(),
                            place = place.trim(),
                            remindBefore = remind,
                            relationshipId = relationship?.takeIf { id -> relationships.any { it.first == id } },
                            updatedAt = existing?.updatedAt ?: 0,
                            addedBy = existing?.addedBy,
                        ),
                    )
                },
                enabled = title.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) { Text(stringResource(R.string.save)) }
        }
    }

    if (dateDialog) {
        DateDialog(
            LocalDate.ofEpochDay(epochDay),
            onDismiss = { dateDialog = false },
            title = R.string.date_field_date,
        ) {
            epochDay = it.toEpochDay()
            dateDialog = false
        }
    }
    if (timeDialog) {
        TimeDialog(
            title = stringResource(R.string.date_field_time),
            time = timeSecond.takeIf { it >= 0 }?.let { LocalTime.ofSecondOfDay(it.toLong()) },
            onDismiss = { timeDialog = false },
            onClear = { timeSecond = -1; timeDialog = false },
            onSave = { timeSecond = it.toSecondOfDay(); timeDialog = false },
        )
    }
    if (deleteDialog && existing != null) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, null) },
            title = { Text(stringResource(R.string.date_delete_question)) },
            text = { Text(stringResource(R.string.date_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteDialog = false
                        onDelete(existing)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
