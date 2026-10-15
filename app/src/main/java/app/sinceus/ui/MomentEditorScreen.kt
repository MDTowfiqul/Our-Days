package app.sinceus.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.sinceus.R
import app.sinceus.data.Moment
import app.sinceus.data.Texts
import coil3.compose.AsyncImage
import java.io.File
import java.time.LocalDate
import java.util.UUID
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip

/**
 * Neuen Moment anlegen ([existing] = null, optional mit [suggestedTitle]) oder bestehenden bearbeiten.
 * [onSave] bekommt den Moment, neu gewählte Fotos (Uris) und die Pfade entfernter Fotos.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MomentEditorScreen(
    existing: Moment?,
    suggestedTitle: String?,
    defaultDate: LocalDate,
    onClose: () -> Unit,
    onSave: (Moment, List<Uri>, List<String>) -> Unit,
    onDelete: (Moment) -> Unit,
    /** Beziehungen zur Auswahl als ID und Titel; nur bei mehreren wird gefragt, zu wem der Moment gehört */
    relationships: List<Pair<String, String>> = emptyList(),
    /** Vorauswahl für neue Momente, null = für alle */
    defaultRelationship: String? = null,
    /** Gespeicherten Moment als Bild teilen */
    onShare: (Moment) -> Unit = {},
) {
    var title by rememberSaveable { mutableStateOf(existing?.title ?: suggestedTitle.orEmpty()) }
    var epochDay by rememberSaveable { mutableStateOf((existing?.date ?: defaultDate).toEpochDay()) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var remind by rememberSaveable { mutableStateOf(existing?.yearlyReminder ?: true) }
    var relationship by rememberSaveable {
        mutableStateOf(if (existing != null) existing.relationshipId else defaultRelationship)
    }
    /** Bereits gespeicherte Fotos (Pfade), die bleiben sollen */
    var keptPhotos by rememberSaveable { mutableStateOf<List<String>>(existing?.photoPaths.orEmpty()) }
    /** Neu gewählte Fotos (Uri-Strings), die beim Speichern in den App-Speicher kopiert werden */
    var newPhotos by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var dateDialog by rememberSaveable { mutableStateOf(false) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }

    // Mehrfachauswahl, wo das Gerät sie kann; sonst einzelne Auswahl als Rückfall
    val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) newPhotos = (newPhotos + uri.toString()).distinct()
    }
    val multiPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(25)) { uris ->
        if (uris.isNotEmpty()) newPhotos = (newPhotos + uris.map { it.toString() }).distinct()
    }
    fun launchPhotoPicker() {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        try {
            multiPicker.launch(request)
        } catch (e: Exception) {
            singlePicker.launch(request)
        }
    }

    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (existing == null) R.string.moment_new else R.string.moment_edit)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { onShare(existing) }) {
                            Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.moment_share))
                        }
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
                label = { Text(stringResource(R.string.moment_field_title)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.moment_field_date)) },
                supportingContent = {
                    Text(Texts.longDate(LocalDate.ofEpochDay(epochDay)), color = MaterialTheme.colorScheme.primary)
                },
                leadingContent = { Icon(Icons.Rounded.CalendarMonth, null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { dateDialog = true },
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.moment_field_note)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            // Fotos im Diarium-Stil: waagerecht scrollbare Vorschaubilder,
            // jedes mit eigenem Entfernen-Knopf, dazu eine Kachel zum Hinzufügen
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.moment_photos),
                    style = MaterialTheme.typography.titleSmall,
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(keptPhotos + newPhotos, key = { it }) { item ->
                        val isNew = item in newPhotos
                        Box(
                            Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow),
                        ) {
                            AsyncImage(
                                model = if (isNew) Uri.parse(item) else File(item),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            IconButton(
                                onClick = {
                                    if (isNew) newPhotos = newPhotos - item else keptPhotos = keptPhotos - item
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f)),
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.moment_remove_photo),
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                    item(key = "add") {
                        Box(
                            Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                                .clickable { launchPhotoPicker() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.AddPhotoAlternate,
                                contentDescription = stringResource(R.string.moment_add_photos),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp),
                            )
                        }
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

            ListItem(
                headlineContent = { Text(stringResource(R.string.moment_remind)) },
                supportingContent = { Text(stringResource(R.string.moment_remind_summary)) },
                trailingContent = { Switch(checked = remind, onCheckedChange = { remind = it }) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { remind = !remind },
            )

            Button(
                onClick = {
                    val moment = Moment(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        title = title.trim(),
                        date = LocalDate.ofEpochDay(epochDay),
                        note = note.trim(),
                        photoPaths = existing?.photoPaths.orEmpty(),
                        yearlyReminder = remind,
                        relationshipId = relationship?.takeIf { id -> relationships.any { it.first == id } },
                    )
                    val removedPhotos = existing?.photoPaths.orEmpty() - keptPhotos.toSet()
                    onSave(moment, newPhotos.map(Uri::parse), removedPhotos)
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
            title = R.string.moment_field_date,
        ) {
            epochDay = it.toEpochDay()
            dateDialog = false
        }
    }
    if (deleteDialog && existing != null) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, null) },
            title = { Text(stringResource(R.string.moment_delete_question)) },
            text = { Text(stringResource(R.string.moment_delete_text)) },
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
