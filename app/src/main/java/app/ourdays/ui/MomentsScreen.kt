package app.ourdays.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.ourdays.data.MomentFilter
import app.ourdays.data.MomentCalendar
import androidx.compose.foundation.border
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.platform.LocalConfiguration
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ourdays.app.R
import app.ourdays.data.LoveSettings
import app.ourdays.data.Moment
import app.ourdays.data.MomentMath
import app.ourdays.data.Texts
import app.ourdays.data.formatNumber
import coil3.compose.AsyncImage
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Vorschläge im leeren Zustand: positiv und für jede Beziehung passend — Paar, Eltern, Geschwister, Freunde */
val MomentSuggestions = listOf(
    R.string.suggest_met,
    R.string.suggest_married,
    R.string.suggest_first_trip,
    R.string.suggest_first_eid,
    R.string.suggest_umrah,
    R.string.suggest_birthday,
)

/** Eintrag der Zeitleiste: ein gespeicherter Moment oder euer Starttag */
private sealed interface TimelineEntry {
    val date: LocalDate

    /** Namen der Beziehung, wenn es mehrere gibt, sonst null */
    val who: String?

    data class Saved(val moment: Moment, override val who: String? = null) : TimelineEntry {
        override val date get() = moment.date
    }

    data class Start(override val date: LocalDate, override val who: String? = null) : TimelineEntry
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MomentsScreen(
    settings: LoveSettings,
    today: LocalDate,
    onAdd: (String?) -> Unit,
    onOpen: (Moment) -> Unit,
    onNewestFirst: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    // Mit mehreren Beziehungen steht an jedem Eintrag, zu wem er gehört
    val multi = settings.relationships.size > 1
    fun who(id: String?) = settings.relationships.firstOrNull { it.id == id }?.takeIf { multi }?.let(settings::namesOf)
    val moments = settings.visibleMoments
    // Suche und Filter erst, wenn sich genug Momente angesammelt haben
    val searchable = moments.size >= MomentFilter.MIN_MOMENTS
    var query by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf<Int?>(null) }
    var relationship by rememberSaveable { mutableStateOf<String?>(null) }
    val filterRelationships = multi && settings.showAll
    // Ansicht: 0 Zeitleiste, 1 Kalender, 2 Fotokalender, 3 Karten
    var view by rememberSaveable { mutableStateOf(0) }
    var monthIndex by rememberSaveable { mutableStateOf(today.year * 12 + today.monthValue - 1) }
    val filtering = searchable && (query.isNotBlank() || year != null || (filterRelationships && relationship != null))
    val shown = if (filtering) {
        moments.filter { MomentFilter.matches(it, query, year, relationship.takeIf { filterRelationships }) }
    } else {
        moments
    }
    val starts = when {
        filtering -> emptyList()
        settings.showAll -> settings.relationships
        else -> listOf(settings.relationship)
    }
    val entries = (
        shown.map { m ->
            // Nach einem Abgleich steht dabei, wer den Moment angelegt hat
            val by = m.addedBy?.let(settings::person)?.name?.let { context.getString(R.string.moment_added_by, it) }
            TimelineEntry.Saved(m, listOfNotNull(who(m.relationshipId), by).joinToString(" · ").ifBlank { null })
        } +
            starts.map { TimelineEntry.Start(it.startDate, who(it.id)) }
        )
        .sortedBy { it.date }
    // Die Zeitleiste lässt sich umdrehen, der Kalender bleibt immer gleich
    val timeline = if (settings.newestFirst) entries.asReversed() else entries

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp),
    ) {
        Text(
            stringResource(R.string.moments_title),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(top = 28.dp),
        )
        Text(
            if (moments.isEmpty()) {
                ""
            } else {
                pluralStringResource(R.plurals.moments_count, moments.size, formatNumber(moments.size.toLong()))
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )

        if (moments.isEmpty()) {
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
                        stringResource(R.string.moments_empty_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        stringResource(R.string.moments_empty_sub),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        MomentSuggestions.forEach { id ->
                            val label = stringResource(id)
                            SuggestionChip(onClick = { onAdd(label) }, label = { Text(label) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        FilledTonalButton(
            onClick = { onAdd(null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
        ) {
            Icon(Icons.Rounded.Add, null)
            Text(stringResource(R.string.moment_add), Modifier.padding(start = 8.dp))
        }

        if (moments.isNotEmpty()) {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                listOf(
                    0 to (R.string.moments_view_list to Icons.AutoMirrored.Rounded.ViewList),
                    1 to (R.string.moments_view_calendar to Icons.Rounded.CalendarMonth),
                    2 to (R.string.moments_view_photos to Icons.Rounded.PhotoLibrary),
                    3 to (R.string.moments_view_cards to Icons.Rounded.GridView),
                ).forEachIndexed { i, (value, labelIcon) ->
                    val (label, icon) = labelIcon
                    SegmentedButton(
                        selected = view == value,
                        onClick = { view = value },
                        shape = SegmentedButtonDefaults.itemShape(i, 4),
                        icon = {
                            SegmentedButtonDefaults.Icon(view == value) {
                                Icon(
                                    icon,
                                    null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                )
                            }
                        },
                    ) { Text(stringResource(label)) }
                }
            }
        }

        if (searchable) {
            OutlinedTextField(
                query,
                { query = it },
                placeholder = { Text(stringResource(R.string.moments_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.moments_search_clear))
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (filterRelationships) {
                    settings.relationships.forEach { r ->
                        FilterChip(
                            selected = relationship == r.id,
                            onClick = { relationship = if (relationship == r.id) null else r.id },
                            label = { Text(settings.namesOf(r)) },
                        )
                    }
                }
                MomentFilter.years(moments).forEach { y ->
                    FilterChip(
                        selected = year == y,
                        onClick = { year = if (year == y) null else y },
                        label = { Text(y.toString()) },
                    )
                }
            }
            if (filtering && shown.isEmpty()) {
                Text(
                    stringResource(R.string.moments_no_results),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                )
            }
        }

        when {
            view == 1 && moments.isNotEmpty() -> {
                val month = YearMonth.of(monthIndex / 12, monthIndex % 12 + 1)
                CalendarView(
                    month = month,
                    today = today,
                    entries = entries,
                    anniversaries = MomentCalendar.anniversaries(shown, month),
                    onMonth = { monthIndex += it },
                    onToday = { monthIndex = today.year * 12 + today.monthValue - 1 },
                    onOpen = onOpen,
                )
            }
            view == 2 && moments.isNotEmpty() -> PhotoCalendarView(entries, today, onOpen)
            view == 3 && moments.isNotEmpty() -> {
                SortToggle(timeline.size, settings.newestFirst, onNewestFirst)
                CardsView(timeline, today, onOpen)
            }
            else -> {
                SortToggle(timeline.size, settings.newestFirst, onNewestFirst)
                timeline.forEachIndexed { index, entry ->
                    TimelineItem(
                        entry = entry,
                        today = today,
                        isFirst = index == 0,
                        isLast = index == timeline.lastIndex,
                        onClick = (entry as? TimelineEntry.Saved)?.let { { onOpen(it.moment) } },
                        relative = relativeText(context, entry.date, today),
                    )
                }
            }
        }

        // Platz für die schwebende Leiste
        Spacer(Modifier.height(96.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/** Monatskalender: Tage mit Momenten sind gefüllt, Jahrestage haben einen Punkt, darunter die Einträge des Monats */
@Composable
private fun CalendarView(
    month: YearMonth,
    today: LocalDate,
    entries: List<TimelineEntry>,
    anniversaries: List<MomentCalendar.Anniversary>,
    onMonth: (Int) -> Unit,
    onToday: () -> Unit,
    onOpen: (Moment) -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val inMonth = entries.filter { YearMonth.from(it.date) == month }
    val byDay = inMonth.groupBy { it.date }
    val anniversaryDays = anniversaries.map { it.date }.toSet()
    val colors = MaterialTheme.colorScheme

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMonth(-1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.calendar_previous))
                }
                val title = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
                    .replaceFirstChar { it.titlecase(locale) }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable(onClick = onToday)
                        .padding(vertical = 8.dp),
                )
                IconButton(onClick = { onMonth(1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.calendar_next))
                }
            }
            val firstDay = WeekFields.of(locale).firstDayOfWeek
            Row(Modifier.padding(top = 4.dp)) {
                (0L until 7L).forEach { i ->
                    Text(
                        firstDay.plus(i).getDisplayName(java.time.format.TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            MomentCalendar.cells(month, firstDay).chunked(7).forEach { week ->
                Row(Modifier.padding(top = 4.dp)) {
                    week.forEach { day ->
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (day != null) {
                                val saved = byDay[day].orEmpty()
                                val filled = saved.isNotEmpty()
                                val first = saved.firstNotNullOfOrNull { (it as? TimelineEntry.Saved)?.moment }
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                saved.any { it is TimelineEntry.Start } -> colors.primary
                                                filled -> colors.primaryContainer
                                                else -> androidx.compose.ui.graphics.Color.Transparent
                                            },
                                        )
                                        .then(
                                            if (day == today) Modifier.border(2.dp, colors.primary, CircleShape) else Modifier,
                                        )
                                        .then(if (first != null) Modifier.clickable { onOpen(first) } else Modifier),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        day.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (filled || day == today) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            saved.any { it is TimelineEntry.Start } -> colors.onPrimary
                                            filled -> colors.onPrimaryContainer
                                            else -> colors.onSurface
                                        },
                                    )
                                    if (day in anniversaryDays) {
                                        Box(
                                            Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = 4.dp)
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(colors.primary),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (anniversaries.isNotEmpty()) {
        Text(
            stringResource(R.string.calendar_anniversaries).uppercase(),
            style = LabelCaps,
            color = colors.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        anniversaries.forEach { a ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surfaceContainerLow)
                    .clickable { onOpen(a.moment) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(Icons.Rounded.NotificationsActive, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        stringResource(
                            R.string.moment_anniversary_title,
                            a.moment.title,
                            context.resources.getQuantityString(R.plurals.years, a.years, formatNumber(a.years.toLong())),
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(Texts.dateWithWeekday(a.date), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (inMonth.isEmpty() && anniversaries.isEmpty()) {
        Text(
            stringResource(R.string.calendar_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        )
    }
    inMonth.forEachIndexed { index, entry ->
        TimelineItem(
            entry = entry,
            today = today,
            isFirst = index == 0,
            isLast = index == inMonth.lastIndex,
            onClick = (entry as? TimelineEntry.Saved)?.let { { onOpen(it.moment) } },
            relative = relativeText(context, entry.date, today),
        )
    }
}

/** Schalter „neueste zuerst“ — steht über der Zeitleiste und über den Karten */
@Composable
private fun ColumnScope.SortToggle(count: Int, newestFirst: Boolean, onNewestFirst: (Boolean) -> Unit) {
    if (count <= 1) return
    TextButton(
        onClick = { onNewestFirst(!newestFirst) },
        modifier = Modifier
            .align(Alignment.End)
            .padding(bottom = 8.dp),
    ) {
        Icon(Icons.Rounded.SwapVert, null, modifier = Modifier.size(18.dp))
        Text(
            stringResource(if (newestFirst) R.string.moments_newest_first else R.string.moments_oldest_first),
            Modifier.padding(start = 8.dp),
        )
    }
}

/** Fotokalender wie in Google Fotos: Monate mit einem Raster, in dem jeder Tag das Foto des Moments zeigt */
@Composable
private fun PhotoCalendarView(entries: List<TimelineEntry>, today: LocalDate, onOpen: (Moment) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val colors = MaterialTheme.colorScheme
    val byDay = entries.filterIsInstance<TimelineEntry.Saved>().groupBy { it.date }
    val months = byDay.keys.map { YearMonth.from(it) }.distinct().sortedDescending()
    months.forEach { month ->
        Text(
            month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
                .replaceFirstChar { it.titlecase(locale) },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )
        val firstDay = WeekFields.of(locale).firstDayOfWeek
        val offset = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
        val cells: List<LocalDate?> = List(offset) { null } +
            (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.padding(top = 4.dp)) {
                week.forEach { day ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day != null) {
                            val saved = byDay[day].orEmpty()
                            val withPhoto = saved.firstOrNull { it.moment.photoPaths.isNotEmpty() }
                                ?: saved.firstOrNull()
                            val photo = withPhoto?.moment?.photoPaths?.firstOrNull()
                            if (photo != null && withPhoto != null) {
                                AsyncImage(
                                    model = File(photo),
                                    contentDescription = withPhoto.moment.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .then(
                                            if (day == today) Modifier.border(2.dp, colors.primary, RoundedCornerShape(8.dp)) else Modifier,
                                        )
                                        .clickable { onOpen(withPhoto.moment) },
                                )
                            } else {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (saved.isNotEmpty()) colors.primaryContainer else Color.Transparent)
                                        .then(
                                            if (day == today) Modifier.border(2.dp, colors.primary, RoundedCornerShape(8.dp)) else Modifier,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        day.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (saved.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                                        color = if (saved.isNotEmpty()) colors.onPrimaryContainer else colors.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Große Karten: Foto, Titel und „Tag N“ (Tage seit dem Moment) */
@Composable
private fun CardsView(entries: List<TimelineEntry>, today: LocalDate, onOpen: (Moment) -> Unit) {
    val context = LocalContext.current
    entries.forEach { entry ->
        val moment = (entry as? TimelineEntry.Saved)?.moment
        val photos = moment?.photoPaths.orEmpty()
        val days = ChronoUnit.DAYS.between(entry.date, today).coerceAtLeast(0)
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(28.dp))
                .then(if (moment != null) Modifier.clickable { onOpen(moment) } else Modifier),
        ) {
            Column {
                if (photos.isNotEmpty()) {
                    AsyncImage(
                        model = File(photos.first()),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f),
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f)
                            .background(Presets[0].brush),
                        contentAlignment = Alignment.Center,
                    ) {
                        BeatingHeart(size = 40.dp)
                    }
                }
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text(
                        "${Texts.longDate(entry.date)} · ${relativeText(context, entry.date, today)}".uppercase(),
                        style = LabelCaps,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        moment?.title ?: stringResource(R.string.moment_together_start),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    entry.who?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        stringResource(R.string.moments_day_n, formatNumber(days)),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (!moment?.note.isNullOrBlank()) {
                        Text(
                            moment!!.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun relativeText(context: android.content.Context, date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        days == 0L -> context.getString(R.string.today_word)
        days > 0 -> context.resources.getQuantityString(R.plurals.days_ago, days.toInt(), formatNumber(days))
        else -> context.resources.getQuantityString(R.plurals.in_days, (-days).toInt(), formatNumber(-days))
    }
}

@Composable
private fun TimelineItem(
    entry: TimelineEntry,
    today: LocalDate,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: (() -> Unit)?,
    relative: String,
) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        // Linie mit Punkt links
        Box(
            Modifier
                .width(28.dp)
                .fillMaxHeight(),
        ) {
            // Linie verbindet die Punkte: beim ersten Eintrag ab dem Punkt, beim letzten bis zum Punkt
            if (!(isFirst && isLast)) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (isFirst) 22.dp else 0.dp)
                        .width(2.dp)
                        .then(if (isLast) Modifier.height(22.dp) else Modifier.fillMaxHeight())
                        .background(line),
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        if (entry is TimelineEntry.Start) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    ),
            )
        }
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (entry is TimelineEntry.Start) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            ),
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, bottom = 14.dp)
                .clip(RoundedCornerShape(24.dp))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        ) {
            val moment = (entry as? TimelineEntry.Saved)?.moment
            val photos = moment?.photoPaths.orEmpty()
            if (photos.isNotEmpty()) {
                Box {
                    AsyncImage(
                        model = File(photos.first()),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f),
                    )
                    if (photos.size > 1) {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                "+${photos.size - 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(
                    "${Texts.longDate(entry.date)} · $relative".uppercase(),
                    style = LabelCaps,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    moment?.title ?: stringResource(R.string.moment_together_start),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                entry.who?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (!moment?.note.isNullOrBlank()) {
                    Text(
                        moment!!.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (moment != null && moment.yearlyReminder && !moment.date.isAfter(today)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(
                            Icons.Rounded.NotificationsActive,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            stringResource(
                                R.string.moment_next_anniversary,
                                Texts.dateWithWeekday(MomentMath.nextAnniversary(moment, today)),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
