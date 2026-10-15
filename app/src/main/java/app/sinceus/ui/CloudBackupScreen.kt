package app.sinceus.ui

import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.sinceus.R
import app.sinceus.cloud.CloudBackupScheduler
import app.sinceus.cloud.CloudBackupStore
import app.sinceus.cloud.CloudConfig
import app.sinceus.cloud.CloudProvider
import app.sinceus.cloud.DriveBackends
import app.sinceus.cloud.TelegramBackup
import app.sinceus.cloud.restoreCloudBackup
import app.sinceus.cloud.runCloudBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private sealed interface RemoteItem {
    val label: String
    val date: Long
    data class Telegram(val stamp: String, override val date: Long) : RemoteItem {
        override val label: String = "Backup $stamp"
    }
    data class Drive(val id: String, val name: String, override val date: Long) : RemoteItem {
        override val label: String = name
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(CloudBackupStore.load(context)) }
    var confirmPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var remote by remember { mutableStateOf<List<RemoteItem>?>(null) }
    var restoreTarget by remember { mutableStateOf<RemoteItem?>(null) }
    var restorePassword by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }
    val driveBackend = remember { DriveBackends.create(context) }

    fun persist(next: CloudConfig) {
        config = next
        CloudBackupStore.save(context, next)
        CloudBackupScheduler.schedule(context, next)
    }

    fun runBusy(key: String, block: suspend () -> String?) {
        scope.launch {
            busy = key
            notice = null
            try {
                val msg = withContext(Dispatchers.IO) { block() }
                if (msg != null) notice = msg
            } catch (e: Exception) {
                notice = e.message?.take(300) ?: e.toString().take(300)
            } finally {
                busy = null
            }
        }
    }

    // Ferne Sicherungen laden, sobald Anbieter und Zugangsdaten stehen
    LaunchedEffect(config.provider, config.botToken, config.chatId, config.driveAccount) {
        remote = null
        if (config.provider == CloudProvider.TELEGRAM && config.botToken.isNotBlank()) {
            remote = runCatching {
                withContext(Dispatchers.IO) {
                    TelegramBackup.listBackups(config.botToken).map {
                        RemoteItem.Telegram(it.stamp, it.date)
                    }
                }
            }.getOrNull()
        } else if (config.provider == CloudProvider.DRIVE && config.driveAccount.isNotBlank() && driveBackend != null) {
            remote = runCatching {
                withContext(Dispatchers.IO) {
                    driveBackend.listBackups(config.driveAccount).map {
                        RemoteItem.Drive(it.id, it.name, it.modified)
                    }
                }
            }.getOrNull()
        }
    }

    val accountPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.let { name ->
                persist(config.copy(driveAccount = name))
            }
        }
    }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.cloud_intro), style = MaterialTheme.typography.bodyMedium)

        // Anbieter wählen
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = config.provider == CloudProvider.NONE,
                onClick = { persist(config.copy(provider = CloudProvider.NONE)) },
                label = { Text(stringResource(R.string.cloud_off)) },
            )
            FilterChip(
                selected = config.provider == CloudProvider.TELEGRAM,
                onClick = { persist(config.copy(provider = CloudProvider.TELEGRAM)) },
                label = { Text(stringResource(R.string.cloud_telegram)) },
            )
            if (driveBackend != null) {
                FilterChip(
                    selected = config.provider == CloudProvider.DRIVE,
                    onClick = { persist(config.copy(provider = CloudProvider.DRIVE)) },
                    label = { Text(stringResource(R.string.cloud_drive)) },
                )
            }
        }

        when (config.provider) {
            CloudProvider.TELEGRAM -> {
                OutlinedTextField(
                    value = config.botToken,
                    onValueChange = { persist(config.copy(botToken = it.trim())) },
                    label = { Text(stringResource(R.string.cloud_bot_token)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = config.chatId,
                    onValueChange = { persist(config.copy(chatId = it.trim())) },
                    label = { Text(stringResource(R.string.cloud_chat_id)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showHelp = true },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.cloud_howto)) }
                    OutlinedButton(
                        onClick = {
                            runBusy("test") {
                                TelegramBackup.sendTestMessage(config.botToken, config.chatId)
                                context.getString(R.string.cloud_test_ok)
                            }
                        },
                        enabled = busy == null && config.botToken.isNotBlank() && config.chatId.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (busy == "test") CircularProgressIndicator(Modifier.padding(4.dp))
                        else Text(stringResource(R.string.cloud_test))
                    }
                }
            }
            CloudProvider.DRIVE -> {
                if (driveBackend == null) {
                    Text(
                        stringResource(R.string.cloud_drive_unavailable),
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (config.driveAccount.isBlank()) {
                    Button(onClick = {
                        accountPicker.launch(
                            AccountManager.newChooseAccountIntent(
                                null, null, arrayOf("com.google"), false, null, null, null, null,
                            ),
                        )
                    }) { Text(stringResource(R.string.cloud_pick_account)) }
                } else {
                    ListItem(
                        headlineContent = { Text(config.driveAccount) },
                        leadingContent = { Icon(Icons.Rounded.Cloud, null) },
                        trailingContent = {
                            TextButton(onClick = { persist(config.copy(driveAccount = "")) }) {
                                Text(stringResource(R.string.cloud_sign_out))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            CloudProvider.NONE -> Unit
        }

        if (config.provider != CloudProvider.NONE) {
            OutlinedTextField(
                value = config.password,
                onValueChange = { persist(config.copy(password = it)) },
                label = { Text(stringResource(R.string.cloud_password)) },
                supportingText = { Text(stringResource(R.string.cloud_password_hint)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (config.password.isNotBlank()) {
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(stringResource(R.string.cloud_password_confirm)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = confirmPassword.isNotEmpty() && confirmPassword != config.password,
                )
            }

            // Häufigkeit
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.cloud_frequency), modifier = Modifier.weight(1f))
                FilterChip(
                    selected = config.frequencyDays == 1,
                    onClick = { persist(config.copy(frequencyDays = 1)) },
                    label = { Text(stringResource(R.string.cloud_daily)) },
                )
                FilterChip(
                    selected = config.frequencyDays == 7,
                    onClick = { persist(config.copy(frequencyDays = 7)) },
                    label = { Text(stringResource(R.string.cloud_weekly)) },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.cloud_wifi_only)) },
                supportingContent = { Text(stringResource(R.string.cloud_wifi_only_summary)) },
                trailingContent = {
                    Switch(
                        checked = config.wifiOnly,
                        onCheckedChange = { persist(config.copy(wifiOnly = it)) },
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { persist(config.copy(wifiOnly = !config.wifiOnly)) },
            )

            // Status
            val lastText = if (config.lastBackup > 0) {
                DateTimeFormatter.ofPattern("d.M.yyyy HH:mm")
                    .format(Instant.ofEpochMilli(config.lastBackup).atZone(ZoneId.systemDefault()))
            } else stringResource(R.string.cloud_never)
            Text(
                stringResource(R.string.cloud_last_backup, lastText),
                style = MaterialTheme.typography.bodySmall,
            )
            config.lastError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            notice?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    runBusy("now") {
                        withContext(Dispatchers.IO) {
                            runCloudBackup(context, CloudBackupStore.load(context))
                        }
                        config = CloudBackupStore.load(context)
                        context.getString(R.string.cloud_backup_ok)
                    }
                },
                enabled = busy == null && config.password.isNotBlank() &&
                    (confirmPassword.isEmpty() || confirmPassword == config.password),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy == "now") CircularProgressIndicator(Modifier.padding(4.dp))
                else {
                    Icon(Icons.Rounded.CloudUpload, null, Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.cloud_backup_now))
                }
            }

            // Wiederherstellen
            Text(stringResource(R.string.cloud_restore), style = MaterialTheme.typography.titleSmall)
            when (val r = remote) {
                null -> Text(stringResource(R.string.cloud_restore_loading), style = MaterialTheme.typography.bodySmall)
                else -> if (r.isEmpty()) {
                    Text(stringResource(R.string.cloud_restore_empty), style = MaterialTheme.typography.bodySmall)
                } else {
                    r.take(10).forEach { item ->
                        val date = DateTimeFormatter.ofPattern("d.M.yyyy HH:mm")
                            .format(Instant.ofEpochMilli(item.date).atZone(ZoneId.systemDefault()))
                        ListItem(
                            headlineContent = { Text(item.label) },
                            supportingContent = { Text(date) },
                            leadingContent = { Icon(Icons.Rounded.Restore, null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable(enabled = busy == null) {
                                restoreTarget = item
                                restorePassword = ""
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("OK") } },
            title = { Text(stringResource(R.string.cloud_howto)) },
            text = { Text(stringResource(R.string.cloud_howto_telegram)) },
        )
    }

    restoreTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        val t = target
                        restoreTarget = null
                        runBusy("restore") {
                            withContext(Dispatchers.IO) {
                                restoreCloudBackup(
                                    context,
                                    CloudBackupStore.load(context),
                                    when (t) {
                                        is RemoteItem.Telegram -> t.stamp
                                        is RemoteItem.Drive -> t.id
                                    },
                                    restorePassword,
                                )
                            }
                            context.getString(R.string.cloud_restore_ok)
                        }
                    },
                    enabled = restorePassword.isNotBlank() && busy == null,
                ) { Text(stringResource(R.string.cloud_restore_confirm)) }
            },
            dismissButton = { TextButton(onClick = { restoreTarget = null }) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.cloud_restore_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.cloud_restore_warning))
                    OutlinedTextField(
                        value = restorePassword,
                        onValueChange = { restorePassword = it },
                        label = { Text(stringResource(R.string.cloud_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                    )
                    if (busy == "restore") CircularProgressIndicator()
                }
            },
        )
    }
}

/** Kurze Zusammenfassung für die Einstellungs-Übersicht. */
@Composable
fun cloudBackupSummary(): String {
    val context = LocalContext.current
    val config = remember { CloudBackupStore.load(context) }
    val driveOk = remember { DriveBackends.create(context) != null }
    return when (config.provider) {
        CloudProvider.NONE -> stringResource(R.string.cloud_off)
        CloudProvider.TELEGRAM -> stringResource(R.string.cloud_telegram)
        CloudProvider.DRIVE -> if (driveOk) stringResource(R.string.cloud_drive)
        else stringResource(R.string.cloud_drive_unavailable)
    }
}
