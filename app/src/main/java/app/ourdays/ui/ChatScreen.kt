package app.ourdays.ui

import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ourdays.BuildConfig
import app.ourdays.R
import app.ourdays.chat.ChatMessage
import app.ourdays.chat.ChatStore
import app.ourdays.chat.ChatViewModel
import app.ourdays.data.LoveSettings
import app.ourdays.data.Pairing
import coil3.compose.AsyncImage
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * Our Chat: messages between the two paired phones, through one shared Telegram
 * channel. Each phone uses its own bot (both bots are channel admins) — see
 * TelegramChat for why one shared bot can never deliver messages. Everything on
 * the channel is encrypted with the pairing key, so only the two phones can read
 * it — Telegram itself only sees ciphertext.
 */
@Composable
fun ChatScreen(
    settings: LoveSettings,
    pairing: Pairing?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: ChatViewModel = viewModel(),
) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val undecryptable by vm.undecryptable.collectAsStateWithLifecycle()
    val isFdroid = BuildConfig.FLAVOR == "fdroid"
    var showSetup by remember { mutableStateOf(false) }

    // While the chat is open, poll about once a minute on top of the background worker
    LaunchedEffect(config.enabled, pairing) {
        if (!config.enabled || pairing == null) return@LaunchedEffect
        while (true) {
            delay(60_000)
            vm.refresh()
        }
    }

    // imePadding moves the input above the keyboard (edge-to-edge makes
    // adjustResize alone insufficient); navigationBarsPadding keeps it off the
    // system bar when the keyboard is closed.
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(
                stringResource(R.string.chat_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (config.isConfigured && config.enabled) {
                IconButton(onClick = { showSetup = true }) {
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.chat_settings))
                }
                IconButton(onClick = { vm.refresh() }, enabled = !refreshing && !isFdroid) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.chat_refresh))
                }
            }
        }
        if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (isFdroid) {
            Text(
                stringResource(R.string.chat_fdroid_note),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (undecryptable > 0) {
            Text(
                stringResource(R.string.chat_decrypt_failed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        when {
            pairing == null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.chat_not_paired_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.chat_not_paired_text))
                Spacer(Modifier.height(16.dp))
                Button(onClick = onOpenSettings) { Text(stringResource(R.string.chat_open_settings)) }
            }
            !config.isConfigured || !config.enabled || showSetup -> SetupForm(
                vm = vm,
                initialToken = config.botToken,
                initialChatId = config.chatId,
                showDisable = config.enabled,
                onDone = { showSetup = false },
            )
            else -> ChatList(
                modifier = Modifier.weight(1f),
                vm = vm,
                messages = messages,
                myId = pairing.me,
                nameFor = { id ->
                    settings.people.firstOrNull { it.id == id }?.name?.takeIf { it.isNotBlank() }
                },
                sendEnabled = !isFdroid,
            )
        }
    }
}

@Composable
private fun SetupForm(
    vm: ChatViewModel,
    initialToken: String,
    initialChatId: String,
    showDisable: Boolean,
    onDone: () -> Unit,
) {
    var token by remember(initialToken, initialChatId) { mutableStateOf(initialToken) }
    var chatId by remember(initialToken, initialChatId) { mutableStateOf(initialChatId) }
    var testing by remember { mutableStateOf(false) }
    var testOk by remember { mutableStateOf<Boolean?>(null) }
    val cloudChannel = vm.cloudChannelId
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.chat_setup_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.chat_setup_intro))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it; testOk = null },
            label = { Text(stringResource(R.string.chat_bot_token)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = chatId,
            onValueChange = { chatId = it },
            label = { Text(stringResource(R.string.chat_channel_id)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (cloudChannel != null) {
            OutlinedButton(onClick = { chatId = cloudChannel }) {
                Text(stringResource(R.string.chat_reuse_cloud))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.saveConfig(token, chatId); onDone() },
                enabled = token.isNotBlank() && chatId.isNotBlank(),
            ) { Text(stringResource(R.string.chat_save)) }
            OutlinedButton(
                onClick = {
                    testing = true
                    testOk = null
                    vm.testConnection(token) { ok ->
                        testing = false
                        testOk = ok
                    }
                },
                enabled = token.isNotBlank() && !testing,
            ) { Text(stringResource(R.string.chat_test)) }
            if (testing) CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterVertically))
        }
        testOk?.let {
            Text(
                stringResource(if (it) R.string.chat_test_ok else R.string.chat_test_failed),
                color = if (it) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        if (showDisable) {
            OutlinedButton(onClick = { vm.disable(); onDone() }) { Text(stringResource(R.string.chat_disable)) }
        }
    }
}

@Composable
private fun ChatList(
    modifier: Modifier = Modifier,
    vm: ChatViewModel,
    messages: List<ChatMessage>,
    myId: String?,
    nameFor: (String?) -> String?,
    sendEnabled: Boolean,
) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            vm.sendMedia(uri, draft)
            draft = ""
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    Column(modifier) {
        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.chat_empty))
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    val mine = msg.from != null && msg.from == myId
                    val label = if (mine) {
                        stringResource(R.string.chat_you)
                    } else {
                        nameFor(msg.from) ?: stringResource(R.string.chat_partner)
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                        Column(
                            modifier = Modifier.widthIn(max = 300.dp),
                            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                            if (msg.isMedia && msg.mediaLocal != null) {
                                val file = ChatStore.mediaFile(context, msg.mediaLocal)
                                if (file.exists()) {
                                    if (msg.kind == ChatMessage.KIND_VIDEO) {
                                        VideoBubble(file)
                                    } else {
                                        AsyncImage(
                                            model = file,
                                            contentDescription = stringResource(R.string.chat_shared_photo),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                                                .clip(RoundedCornerShape(12.dp)),
                                        )
                                    }
                                    if (msg.text.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(msg.text)
                                    }
                                } else if (msg.text.isNotBlank()) {
                                    Text(msg.text)
                                }
                            } else {
                                Surface(
                                    color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Text(msg.text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                                }
                            }
                            Text(formatTime(msg.at), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    pickMedia.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                    )
                },
                enabled = sendEnabled,
            ) {
                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = stringResource(R.string.chat_attach))
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text(stringResource(R.string.chat_hint)) },
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { vm.send(draft); draft = "" },
                enabled = sendEnabled && draft.isNotBlank(),
            ) {
                Icon(Icons.Rounded.Send, contentDescription = stringResource(R.string.chat_send))
            }
        }
    }
}

@Composable
private fun VideoBubble(file: File) {
    var playing by remember { mutableStateOf(false) }
    if (playing) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setVideoPath(file.absolutePath)
                    setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                    setOnPreparedListener { it.start() }
                }
            },
            modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Box(
            Modifier.fillMaxWidth().height(240.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { playing = true },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(48.dp))
                Text(stringResource(R.string.chat_tap_to_play), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun formatTime(at: Long): String {
    val dt = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    return if (dt.toLocalDate() == LocalDate.now()) {
        dt.format(DateTimeFormatter.ofPattern("HH:mm"))
    } else {
        dt.format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))
    }
}
