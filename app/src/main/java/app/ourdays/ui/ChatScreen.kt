package app.ourdays.ui

import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ourdays.app.BuildConfig
import ourdays.app.R
import app.ourdays.chat.ChatMessage
import app.ourdays.chat.ChatStore
import app.ourdays.chat.ChatTransport
import app.ourdays.chat.ChatViewModel
import app.ourdays.data.LoveSettings
import app.ourdays.data.Pairing
import app.ourdays.data.Person
import coil3.compose.AsyncImage
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * Our Chat: messages between the two paired phones. Two ways to connect, and
 * the couple picks one — Telegram (own bot per phone + shared channel, text
 * and media) or Firebase (shared room joined with a 6-digit code, text
 * only). Everything is encrypted with the pairing key, so only the two
 * phones can read it — neither Telegram nor Google ever sees plain text.
 */
@Composable
fun ChatScreen(
    settings: LoveSettings,
    pairing: Pairing?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onSetMe: (String) -> Unit = {},
    vm: ChatViewModel = viewModel(),
) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val undecryptable by vm.undecryptable.collectAsStateWithLifecycle()
    val isFdroid = BuildConfig.FLAVOR == "fdroid"
    var showSetup by remember { mutableStateOf(false) }
    // Which transport's setup form is showing; null means the choice screen.
    // A fresh setup starts at the choice; reconfiguring starts at the current transport.
    var setupTransport by remember(config.isConfigured, showSetup) {
        mutableStateOf(if (config.isConfigured) config.transport else null)
    }

    // While the chat is open, check for new messages every few seconds so they
    // appear on their own — nobody should have to tap refresh. (The poll is
    // cheap: Telegram uses an offset, Firebase only asks for newer messages.)
    LaunchedEffect(config.enabled, pairing) {
        if (!config.enabled || pairing == null) return@LaunchedEffect
        while (true) {
            vm.refresh()
            delay(5_000)
        }
    }

    // imePadding moves the input above the keyboard (edge-to-edge makes
    // adjustResize alone insufficient); navigationBarsPadding keeps it off the
    // system bar when the keyboard is closed. statusBarsPadding keeps the
    // title row out from under the notification panel.
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
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
            !config.isConfigured || !config.enabled || showSetup -> when (setupTransport) {
                null -> TransportChoice(
                    vm = vm,
                    onPick = { setupTransport = it },
                )
                ChatTransport.TELEGRAM -> SetupForm(
                    vm = vm,
                    initialToken = config.botToken,
                    initialChatId = config.chatId,
                    showDisable = config.enabled,
                    onDone = { showSetup = false },
                    onChangeMethod = { setupTransport = null },
                )
                ChatTransport.FIREBASE -> FirebaseSetup(
                    vm = vm,
                    showDisable = config.enabled,
                    onDone = { showSetup = false },
                    onChangeMethod = { setupTransport = null },
                )
            }
            else -> ChatList(
                modifier = Modifier.weight(1f),
                vm = vm,
                messages = messages,
                myId = pairing.me,
                people = settings.people,
                nameFor = { id ->
                    settings.people.firstOrNull { it.id == id }?.name?.takeIf { it.isNotBlank() }
                },
                onSetMe = onSetMe,
                sendEnabled = !isFdroid,
                mediaEnabled = config.transport == ChatTransport.TELEGRAM,
            )
        }
    }
}

@Composable
private fun TransportChoice(
    vm: ChatViewModel,
    onPick: (ChatTransport) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.chat_choose_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.chat_choose_text))
        TransportCard(
            title = stringResource(R.string.chat_telegram_title),
            limits = stringResource(R.string.chat_telegram_limits),
            onClick = { onPick(ChatTransport.TELEGRAM) },
        )
        if (vm.firebaseAvailable) {
            TransportCard(
                title = stringResource(R.string.chat_firebase_title),
                limits = stringResource(R.string.chat_firebase_limits),
                onClick = { onPick(ChatTransport.FIREBASE) },
            )
        }
    }
}

@Composable
private fun TransportCard(title: String, limits: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(limits, style = MaterialTheme.typography.bodyMedium)
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
    onChangeMethod: () -> Unit,
) {
    var token by remember(initialToken, initialChatId) { mutableStateOf(initialToken) }
    var chatId by remember(initialToken, initialChatId) { mutableStateOf(initialChatId) }
    var testing by remember { mutableStateOf(false) }
    var testOk by remember { mutableStateOf<Boolean?>(null) }
    var finding by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<Boolean?>(null) }
    val cloudChannel = vm.cloudChannelId
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.chat_setup_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.chat_setup_intro))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { uriHandler.openUri("https://t.me/BotFather") }) {
                Text(stringResource(R.string.chat_open_botfather))
            }
            OutlinedButton(
                onClick = {
                    finding = true
                    found = null
                    vm.findChannelId(token) { id ->
                        finding = false
                        if (id != null) {
                            chatId = id
                            found = true
                        } else {
                            found = false
                        }
                    }
                },
                enabled = token.isNotBlank() && !finding,
            ) { Text(stringResource(R.string.chat_find_channel)) }
            if (finding) CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterVertically))
        }
        found?.let {
            Text(
                stringResource(if (it) R.string.chat_channel_found else R.string.chat_channel_not_found),
                color = if (it) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
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
        OutlinedButton(onClick = onChangeMethod) { Text(stringResource(R.string.chat_change_method)) }
    }
}

@Composable
private fun FirebaseSetup(
    vm: ChatViewModel,
    showDisable: Boolean,
    onDone: () -> Unit,
    onChangeMethod: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var createdCode by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    var joinFailed by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.chat_firebase_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.chat_firebase_limits))
        if (createdCode == null) {
            Button(
                onClick = {
                    creating = true
                    vm.createFirebaseRoom { created ->
                        creating = false
                        createdCode = created
                    }
                },
                enabled = !creating,
            ) { Text(stringResource(R.string.chat_firebase_create)) }
            if (creating) CircularProgressIndicator(Modifier.size(24.dp))
        } else {
            Text(stringResource(R.string.chat_firebase_code_label), style = MaterialTheme.typography.labelMedium)
            Text(createdCode!!, style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.chat_firebase_code_share))
            Button(onClick = onDone) { Text(stringResource(R.string.done)) }
        }
        Text(stringResource(R.string.chat_firebase_join_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter { ch -> ch.isDigit() }.take(6); joinFailed = false },
            label = { Text(stringResource(R.string.chat_firebase_code_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    joining = true
                    vm.joinFirebaseRoom(code) { ok ->
                        joining = false
                        if (ok) onDone() else joinFailed = true
                    }
                },
                enabled = code.length == 6 && !joining,
            ) { Text(stringResource(R.string.chat_firebase_join)) }
            if (joining) CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterVertically))
        }
        if (joinFailed) {
            Text(
                stringResource(R.string.chat_firebase_invalid),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (showDisable) {
            OutlinedButton(onClick = { vm.disable(); onDone() }) { Text(stringResource(R.string.chat_disable)) }
        }
        OutlinedButton(onClick = onChangeMethod) { Text(stringResource(R.string.chat_change_method)) }
    }
}

@Composable
private fun ChatList(
    modifier: Modifier = Modifier,
    vm: ChatViewModel,
    messages: List<ChatMessage>,
    myId: String?,
    people: List<Person>,
    nameFor: (String?) -> String?,
    onSetMe: (String) -> Unit,
    sendEnabled: Boolean,
    mediaEnabled: Boolean,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var draft by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<ChatMessage?>(null) }
    var editing by remember { mutableStateOf<ChatMessage?>(null) }
    var deleteFor by remember { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()
    // My name and the partner's name, from the people list: a message whose
    // sender id is not in the list is still the partner's (two-person chat),
    // so it gets the other person's name instead of the bare "Partner".
    val myName = people.firstOrNull { it.id == myId }?.name?.takeIf { it.isNotBlank() }
    val partnerName = if (myId == null) null else
        people.firstOrNull { it.id != myId }?.name?.takeIf { it.isNotBlank() }
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
        // Without "who uses this phone" the app cannot tell my messages from
        // my partner's, so every message would show as "Partner" on the left.
        // Ask once, right here in the chat, before the first message is sent.
        if (myId == null && people.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.chat_who_uses), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (person in people) {
                        OutlinedButton(onClick = { onSetMe(person.id) }) { Text(person.name) }
                    }
                }
            }
        }
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
                        myName ?: stringResource(R.string.chat_you)
                    } else {
                        nameFor(msg.from) ?: partnerName ?: stringResource(R.string.chat_partner)
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                        Column(
                            modifier = Modifier.widthIn(max = 300.dp)
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = { menuFor = msg },
                                ),
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
                                    shape = RoundedCornerShape(
                                        topStart = 16.dp, topEnd = 16.dp,
                                        bottomEnd = if (mine) 4.dp else 16.dp,
                                        bottomStart = if (mine) 16.dp else 4.dp,
                                    ),
                                ) {
                                    Text(msg.text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                                }
                            }
                            Text(
                                formatTime(msg.at) + if (msg.edited) " • " + stringResource(R.string.chat_edited) else "",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        if (menuFor == msg) {
                            DropdownMenu(
                                expanded = true,
                                onDismissRequest = { menuFor = null },
                            ) {
                                if (msg.text.isNotBlank()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.chat_copy)) },
                                        onClick = {
                                            clipboard.setText(AnnotatedString(msg.text))
                                            menuFor = null
                                        },
                                    )
                                }
                                if (mine && msg.kind == ChatMessage.KIND_TEXT) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.chat_edit)) },
                                        onClick = {
                                            draft = msg.text
                                            editing = msg
                                            menuFor = null
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_delete)) },
                                    onClick = {
                                        deleteFor = msg
                                        menuFor = null
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (!mediaEnabled) {
            Text(
                stringResource(R.string.chat_firebase_text_only),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (editing != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.chat_editing),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { editing = null; draft = "" }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (mediaEnabled) {
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
                onClick = {
                    val target = editing
                    if (target != null) {
                        vm.editMessage(target.id, draft)
                        editing = null
                    } else {
                        vm.send(draft)
                    }
                    draft = ""
                },
                enabled = sendEnabled && draft.isNotBlank(),
            ) {
                Icon(Icons.Rounded.Send, contentDescription = stringResource(R.string.chat_send))
            }
        }
    }
    if (deleteFor != null) {
        val target = deleteFor!!
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text(stringResource(R.string.chat_delete_title)) },
            text = null,
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        vm.deleteMessage(target.id, false)
                        deleteFor = null
                    }) { Text(stringResource(R.string.chat_delete_for_me)) }
                    if (myId != null && target.from == myId) {
                        TextButton(onClick = {
                            vm.deleteMessage(target.id, true)
                            deleteFor = null
                        }) { Text(stringResource(R.string.chat_delete_for_everyone)) }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteFor = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
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
