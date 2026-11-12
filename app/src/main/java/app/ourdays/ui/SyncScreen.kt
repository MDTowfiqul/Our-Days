package app.ourdays.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ourdays.app.R
import app.ourdays.data.LoveSettings
import app.ourdays.data.Pairing
import app.ourdays.data.Texts
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.time.Instant
import java.time.ZoneId

/** Aktionen der Seite „Abgleich“ — jede für eine bestimmte Beziehung */
class SyncActions(
    /** Neue Kopplung für eine Beziehung anlegen (mit dem Menschen, der dieses Handy nutzt) */
    val onCreate: (String?, String) -> Unit = { _, _ -> },
    /** QR-Code des anderen Handys scannen */
    val onScan: (String?, String) -> Unit = { _, _ -> },
    val onMe: (String?) -> Unit = {},
    val onUnpair: (String) -> Unit = {},
    val onSend: (String, Boolean) -> Unit = { _, _ -> },
    val onOpen: () -> Unit = {},
    /** Kopplung als Datei teilen (für Handys, die keinen QR-Code scannen können) */
    val onSharePairing: (String) -> Unit = {},
)

/** Abgleich mit dem Handy des anderen Menschen: pro Beziehung koppeln per QR-Code, dann Dateien austauschen */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SyncPage(
    settings: LoveSettings,
    pairings: Map<String, Pairing>,
    actions: SyncActions,
    /** Beim Einrichten auf einem neuen Handy: nur koppeln und Dateien öffnen, nichts senden */
    partnerMode: Boolean = false,
) {
    var me by remember(pairings) { mutableStateOf(pairings.values.firstNotNullOfOrNull { it.me }) }
    var showQrFor by remember { mutableStateOf<String?>(null) }
    var confirmUnpairFor by remember { mutableStateOf<String?>(null) }
    var sendScopeFor by remember { mutableStateOf<String?>(null) }
    var sendIncludeMoments by remember { mutableStateOf(true) }
    // Welche Verbindung soll geteilt werden — bei einer einzigen gibt es nichts zu wählen
    var selectedRel by remember(settings.relationships) {
        mutableStateOf(
            settings.relationships.firstOrNull { it.id == settings.selected }?.id
                ?: settings.relationships.firstOrNull()?.id,
        )
    }

    Text(
        stringResource(if (pairings.isEmpty()) R.string.sync_intro else R.string.sync_intro_paired),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )

    // Wer nutzt dieses Handy — auf einem frischen Handy erst nach dem ersten Abgleich sichtbar,
    // wenn die Namen vom anderen Handy da sind
    if (!partnerMode || settings.people.any { it.name.isNotBlank() }) {
        SyncCard {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.sync_who), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    settings.people.forEach { p ->
                        FilterChip(
                            selected = me == p.id,
                            onClick = {
                                me = p.id
                                if (pairings.isNotEmpty()) actions.onMe(p.id)
                            },
                            label = { Text(p.name) },
                        )
                    }
                }
            }
        }
    }

    // Welche Verbindung soll geteilt werden — bei einer einzigen gibt es nichts zu wählen
    if (settings.relationships.size > 1) {
        SyncCard {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.sync_share_which), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    settings.relationships.forEach { rel ->
                        FilterChip(
                            selected = selectedRel == rel.id,
                            onClick = { selectedRel = rel.id },
                            label = { Text(settings.titleOf(rel)) },
                        )
                    }
                }
            }
        }
    }

    // Die Karte der gewählten Verbindung: sie hat ihre eigene Kopplung und ihre eigene Abgleich-Datei
    val r = settings.relationships.firstOrNull { it.id == selectedRel } ?: settings.relationships.firstOrNull()
    if (r != null) {
        val pairing = pairings[r.id]
        SyncCard {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
                Text(
                    settings.titleOf(r),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (pairing == null) {
                        stringResource(R.string.sync_not_paired_yet)
                    } else if (pairing.lastSync > 0) {
                        stringResource(R.string.sync_last, Texts.mediumDate(day(pairing.lastSync)))
                    } else {
                        stringResource(R.string.sync_paired_since, Texts.mediumDate(day(pairing.since)))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (pairing == null) {
                SyncRow(Icons.Rounded.QrCode2, stringResource(R.string.sync_show_qr), stringResource(R.string.sync_show_qr_summary)) {
                    actions.onCreate(me, r.id)
                    showQrFor = r.id
                }
                Line()
                SyncRow(Icons.Rounded.QrCodeScanner, stringResource(R.string.sync_scan), stringResource(R.string.sync_scan_summary)) {
                    actions.onScan(me, r.id)
                }
                Line()
                SyncRow(Icons.Rounded.Share, stringResource(R.string.pairing_share_file), stringResource(R.string.pairing_share_file_summary)) {
                    actions.onSharePairing(r.id)
                }
            } else {
                if (!partnerMode) {
                    SyncRow(Icons.Rounded.Send, stringResource(R.string.sync_send), stringResource(R.string.sync_send_summary)) {
                        sendIncludeMoments = true
                        sendScopeFor = r.id
                    }
                    Line()
                }
                SyncRow(Icons.Rounded.FileOpen, stringResource(R.string.sync_open), stringResource(R.string.sync_open_summary), onClick = actions.onOpen)
                Line()
                SyncRow(Icons.Rounded.QrCode2, stringResource(R.string.sync_show_qr_again), stringResource(R.string.sync_show_qr_again_summary)) {
                    showQrFor = r.id
                }
                Line()
                SyncRow(Icons.Rounded.Share, stringResource(R.string.pairing_share_file), stringResource(R.string.pairing_share_file_summary)) {
                    actions.onSharePairing(r.id)
                }
                Line()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.sync_unpair), color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Rounded.LinkOff, null, tint = MaterialTheme.colorScheme.error) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { confirmUnpairFor = r.id },
                )
            }
        }
    }

    // Kurze Anleitung, wie Abgleich und Abgleich-Datei funktionieren
    SyncCard {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.sync_how_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.sync_how_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    val qrPairing = showQrFor?.let { pairings[it] }
    if (qrPairing != null && showQrFor != null) {
        AlertDialog(
            onDismissRequest = { showQrFor = null },
            title = { Text(stringResource(R.string.sync_show_qr)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.sync_qr_text))
                    QrCode(
                        Pairing.qrText(qrPairing, showQrFor!!),
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp)),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showQrFor = null }) { Text(stringResource(R.string.done)) } },
        )
    }
    // Was soll die Abgleich-Datei enthalten: nur Namen und Datum — oder auch alle Momente?
    if (sendScopeFor != null) {
        AlertDialog(
            onDismissRequest = { sendScopeFor = null },
            title = { Text(stringResource(R.string.sync_send_scope_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(true to R.string.sync_send_all, false to R.string.sync_send_names_only).forEach { (all, title) ->
                        val sub = if (all) R.string.sync_send_all_sub else R.string.sync_send_names_only_sub
                        ListItem(
                            headlineContent = { Text(stringResource(title)) },
                            supportingContent = { Text(stringResource(sub)) },
                            leadingContent = {
                                androidx.compose.material3.RadioButton(
                                    selected = sendIncludeMoments == all,
                                    onClick = { sendIncludeMoments = all },
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { sendIncludeMoments = all },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions.onSend(sendScopeFor!!, sendIncludeMoments)
                        sendScopeFor = null
                    },
                ) { Text(stringResource(R.string.sync_send)) }
            },
            dismissButton = { TextButton(onClick = { sendScopeFor = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmUnpairFor != null) {
        AlertDialog(
            onDismissRequest = { confirmUnpairFor = null },
            icon = { Icon(Icons.Rounded.LinkOff, null) },
            title = { Text(stringResource(R.string.sync_unpair_question)) },
            text = { Text(stringResource(R.string.sync_unpair_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions.onUnpair(confirmUnpairFor!!)
                        confirmUnpairFor = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.sync_unpair)) }
            },
            dismissButton = { TextButton(onClick = { confirmUnpairFor = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Auf einem neuen Handy: mit dem Partner koppeln und seine Abgleich-Datei öffnen, statt die Einrichtung auszufüllen */
@Composable
internal fun PartnerSyncPage(
    settings: LoveSettings,
    pairings: Map<String, Pairing>,
    actions: SyncActions,
    onContinue: () -> Unit,
) {
    val synced = pairings.values.any { it.lastSync > 0 }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(R.string.partner_sync_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp),
        )
        Text(
            stringResource(R.string.partner_sync_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
        SyncPage(settings, pairings, actions, partnerMode = true)
        if (synced) {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Text(stringResource(R.string.partner_sync_continue))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
private fun SyncCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) { Column { content() } }
}

@Composable
private fun Line() = HorizontalDivider(
    Modifier.padding(horizontal = 16.dp),
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
)

@Composable
private fun SyncRow(icon: ImageVector, title: String, summary: String, onClick: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

/** QR-Code als schwarze Quadrate auf Weiß */
@Composable
internal fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2))
    }
    Canvas(
        modifier
            .aspectRatio(1f)
            .background(Color.White),
    ) {
        val cell = size.width / matrix.width
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                if (matrix[x, y]) {
                    // Etwas überlappen, damit keine hellen Linien zwischen den Feldern entstehen
                    drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}
