package app.ourdays.ui

import androidx.annotation.PluralsRes
import ourdays.app.R
import app.ourdays.data.Texts
import androidx.compose.ui.res.stringResource
import app.ourdays.share.ShareCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ourdays.data.LiveSpan
import app.ourdays.data.LoveSettings
import app.ourdays.data.formatNumber
import app.ourdays.data.liveSpan
import kotlinx.coroutines.delay
import java.time.LocalDateTime

private data class LiveRow(val key: String, val value: Long, @PluralsRes val unit: Int, val total: Long)

private fun LiveSpan.rows(): List<LiveRow> = buildList {
    if (years > 0) add(LiveRow("y", years.toLong(), R.plurals.years, years.toLong()))
    add(LiveRow("mo", months.toLong(), R.plurals.months, totalMonths))
    add(LiveRow("d", days.toLong(), R.plurals.days, totalDays))
    add(LiveRow("h", hours.toLong(), R.plurals.hours, totalHours))
    add(LiveRow("mi", minutes.toLong(), R.plurals.minutes, totalMinutes))
    add(LiveRow("s", seconds.toLong(), R.plurals.seconds, totalSeconds))
}

/** Sekundengenauer Live-Zähler als Karte direkt unter der Übersicht */
@Composable
fun LiveSection(settings: LoveSettings, active: Boolean, now: LocalDateTime? = null) {
    var current by remember { mutableStateOf(now ?: LocalDateTime.now()) }
    LaunchedEffect(active, now) {
        while (active && now == null) {
            current = LocalDateTime.now()
            delay(1000 - System.currentTimeMillis() % 1000)
        }
    }
    val start = settings.startDateTime
    val future = start.isAfter(current)
    val span = liveSpan(start, current)
    val context = LocalContext.current
    val shareLive = {
        val text = context.getString(
            R.string.share_text,
            settings.names,
            span.rows().filter { it.value > 0 }.joinToString(", ") { Texts.count(context, it.unit, it.value) },
        )
        ShareCard.share(context, settings, current.toLocalDate(), text)
    }
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(if (future) R.string.live_until else R.string.live_together).uppercase(),
                style = LabelCaps,
                color = MaterialTheme.colorScheme.primary,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                span.rows().filter { it.value > 0 }.forEach { row ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            formatNumber(row.value),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            context.resources.getQuantityString(row.unit, row.value.toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = shareLive,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Icon(Icons.Rounded.IosShare, null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.share), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Spaß-Bereich: noch leer — hier kommen später Premium-Funktionen für die beiden hin */
@Composable
fun FunScreen(settings: LoveSettings) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.Celebration,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Text(
            stringResource(R.string.fun_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            stringResource(R.string.fun_premium_soon),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(72.dp))
    }
}
