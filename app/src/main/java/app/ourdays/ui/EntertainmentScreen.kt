package app.ourdays.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ourdays.R
import app.ourdays.data.AiLevel
import app.ourdays.data.DailyChallenge
import app.ourdays.data.DotsMove
import app.ourdays.data.GameKind
import app.ourdays.data.GameMode
import app.ourdays.data.GameMerge
import app.ourdays.data.GameState
import app.ourdays.data.GomokuMove
import app.ourdays.data.KNOW_ME_QUESTIONS
import app.ourdays.data.Pairing
import app.ourdays.data.QuizSyncData
import app.ourdays.data.WR_QUESTIONS
import app.ourdays.data.WrQuestion
import app.ourdays.data.knowMeScore
import app.ourdays.data.quizIsBengali
import app.ourdays.data.wrAgreement
import java.time.LocalDate

private sealed interface QuizView {
    data object KnowMeAnswer : QuizView
    data object KnowMeGuess : QuizView
    data class Wr(val key: String, val questions: List<WrQuestion>, val title: String) : QuizView
}

/**
 * Our Entertainment: games with your partner (async, over the sync file), games
 * against the AI, and couples quizzes. Deliberately no real-time games and no
 * premium gate: see the post-launch plan.
 */
@Composable
fun EntertainmentScreen(
    games: List<GameState>,
    quiz: QuizSyncData,
    pairing: Pairing?,
    peopleNames: Map<String, String>,
    today: LocalDate,
    onBack: () -> Unit,
    onStartPartner: (GameKind) -> Unit,
    onStartAi: (GameKind, AiLevel) -> Unit,
    onDeleteGame: (String) -> Unit,
    onDotsMove: (String, DotsMove) -> Unit,
    onGomokuMove: (String, GomokuMove) -> Unit,
    onSendGame: () -> Unit,
    onSaveKnowMeSheet: (Map<String, Int>) -> Unit,
    onSaveKnowMeAttempt: (String, Map<String, Int>) -> Unit,
    onSaveWr: (String, Map<String, Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bengali = quizIsBengali(context)
    val myId = pairing?.me
    var openGameId by remember { mutableStateOf<String?>(null) }
    var quizView by remember { mutableStateOf<QuizView?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = openGameId != null || quizView != null) {
        if (openGameId != null) openGameId = null else quizView = null
    }
    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text(stringResource(R.string.game_delete_title)) },
            text = { Text(stringResource(R.string.game_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteGame(deleteId!!)
                    if (openGameId == deleteId) openGameId = null
                    deleteId = null
                }) { Text(stringResource(R.string.game_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    val aiName = stringResource(R.string.game_ai)
    val playerOneName = stringResource(R.string.game_player_one)
    val playerTwoName = stringResource(R.string.game_player_two)
    fun nameOf(id: String): String = when (id) {
        "ai" -> aiName
        "player1" -> playerOneName
        "player2" -> playerTwoName
        else -> peopleNames[id] ?: id
    }

    val openGame = games.firstOrNull { it.id == openGameId }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = {
                if (openGameId != null) openGameId = null
                else if (quizView != null) quizView = null
                else onBack()
            }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
            }
            Text(
                when {
                    openGame != null -> stringResource(if (openGame.kind == GameKind.DOTS) R.string.game_dots_boxes else R.string.game_gomoku)
                    quizView is QuizView.KnowMeAnswer || quizView is QuizView.KnowMeGuess -> stringResource(R.string.quiz_know_me)
                    quizView is QuizView.Wr -> (quizView as QuizView.Wr).title
                    else -> stringResource(R.string.entertainment)
                },
                style = MaterialTheme.typography.titleLarge,
            )
        }

        when {
            openGame != null -> openGame.let { game ->
                GameView(
                    game = game,
                    myId = myId,
                    nameOf = ::nameOf,
                    onDotsMove = { onDotsMove(game.id, it) },
                    onGomokuMove = { onGomokuMove(game.id, it) },
                    onSendGame = onSendGame,
                    onDelete = { deleteId = game.id },
                )
            }

            quizView is QuizView.KnowMeAnswer -> {
                val mySheet = quiz.sheets.firstOrNull { it.owner == myId }
                KnowMeForm(
                    questions = KNOW_ME_QUESTIONS,
                    initialAnswers = mySheet?.answers.orEmpty(),
                    bengali = bengali,
                    onDone = { answers ->
                        onSaveKnowMeSheet(answers)
                        Toast.makeText(context, R.string.quiz_saved, Toast.LENGTH_SHORT).show()
                        quizView = null
                    },
                )
            }

            quizView is QuizView.KnowMeGuess -> {
                val sheet = quiz.sheets.firstOrNull { it.owner != myId } ?: quiz.sheets.firstOrNull()
                if (sheet != null) {
                    val attempt = quiz.attempts.firstOrNull { it.sheetOwner == sheet.owner && it.guesser == myId }
                    if (attempt != null) {
                        Text(
                            stringResource(R.string.quiz_your_score, knowMeScore(sheet, attempt), KNOW_ME_QUESTIONS.size),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                    val sheetOwner = sheet.owner
                    KnowMeForm(
                        questions = KNOW_ME_QUESTIONS,
                        initialAnswers = attempt?.answers.orEmpty(),
                        bengali = bengali,
                        onDone = { answers ->
                            onSaveKnowMeAttempt(sheetOwner, answers)
                            Toast.makeText(context, R.string.quiz_saved, Toast.LENGTH_SHORT).show()
                            quizView = null
                        },
                    )
                } else {
                    Text(stringResource(R.string.quiz_no_sheet_yet), modifier = Modifier.padding(top = 16.dp))
                }
            }

            quizView is QuizView.Wr -> {
                val view = quizView as QuizView.Wr
                val mineRec = quiz.wr.firstOrNull { it.date == view.key && it.player == myId }
                val partnerRec = quiz.wr.firstOrNull { it.date == view.key && it.player != myId }
                if (mineRec != null && partnerRec != null) {
                    Text(
                        stringResource(R.string.quiz_agreement, wrAgreement(mineRec, partnerRec), view.questions.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                val viewKey = view.key
                WrForm(
                    questions = view.questions,
                    initialAnswers = mineRec?.answers.orEmpty(),
                    bengali = bengali,
                    onDone = { answers ->
                        onSaveWr(viewKey, answers)
                        Toast.makeText(context, R.string.quiz_saved, Toast.LENGTH_SHORT).show()
                        quizView = null
                    },
                )
            }

            else -> EntertainmentList(
                games = games,
                quiz = quiz,
                pairing = pairing,
                myId = myId,
                today = today,
                nameOf = ::nameOf,
                onOpenGame = { openGameId = it },
                onStartPartner = onStartPartner,
                onStartAi = onStartAi,
                onKnowMeAnswer = { quizView = QuizView.KnowMeAnswer },
                onKnowMeGuess = { quizView = QuizView.KnowMeGuess },
                onWrDaily = {
                    quizView = QuizView.Wr(
                        key = today.toString(),
                        questions = DailyChallenge.questionsFor(today),
                        title = context.getString(R.string.quiz_daily_challenge),
                    )
                },
                onWrAll = {
                    quizView = QuizView.Wr(
                        key = "casual",
                        questions = WR_QUESTIONS,
                        title = context.getString(R.string.quiz_all_questions),
                    )
                },
            )
        }
    }
}

@Composable
private fun GameView(
    game: GameState,
    myId: String?,
    nameOf: (String) -> String,
    onDotsMove: (DotsMove) -> Unit,
    onGomokuMove: (GomokuMove) -> Unit,
    onSendGame: () -> Unit,
    onDelete: () -> Unit,
) {
    val myTurn = myId != null && GameMerge.turnIsMine(game, myId)
    val banner = when {
        game.finished && game.winner == 0 -> stringResource(R.string.game_draw)
        game.finished && game.mode == GameMode.PARTNER -> {
            val winnerId = if (game.winner == 1) game.player1 else game.player2
            if (winnerId == myId) stringResource(R.string.game_you_win) else stringResource(R.string.game_partner_wins)
        }
        game.finished -> if (game.winner == 1) stringResource(R.string.game_you_win) else stringResource(R.string.game_ai_wins)
        game.mode == GameMode.PARTNER && !myTurn -> stringResource(R.string.game_partner_turn)
        else -> stringResource(R.string.game_your_turn)
    }
    Text(banner, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
    when (game.kind) {
        GameKind.DOTS -> {
            val state = game.dotsState()
            if (state != null) {
                Text(
                    "${nameOf(game.player1)} · ${state.score(1)} — ${state.score(2)} · ${nameOf(game.player2)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                DotsBoxesBoard(state = state, interactive = !game.finished && (game.mode != GameMode.PARTNER || myTurn), onMove = onDotsMove)
            }
        }
        GameKind.GOMOKU -> {
            val state = game.gomokuState()
            if (state != null) {
                Text(
                    "${nameOf(game.player1)} — ${nameOf(game.player2)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                GomokuBoard(state = state, interactive = !game.finished && (game.mode != GameMode.PARTNER || myTurn), onMove = onGomokuMove)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    if (game.mode == GameMode.PARTNER && !game.finished) {
        Button(onClick = onSendGame, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.game_send_to_partner))
        }
        Spacer(Modifier.height(8.dp))
    }
    OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.game_delete))
    }
}

@Composable
private fun EntertainmentList(
    games: List<GameState>,
    quiz: QuizSyncData,
    pairing: Pairing?,
    myId: String?,
    today: LocalDate,
    nameOf: (String) -> String,
    onOpenGame: (String) -> Unit,
    onStartPartner: (GameKind) -> Unit,
    onStartAi: (GameKind, AiLevel) -> Unit,
    onKnowMeAnswer: () -> Unit,
    onKnowMeGuess: () -> Unit,
    onWrDaily: () -> Unit,
    onWrAll: () -> Unit,
) {
    val sorted = games.sortedByDescending { it.updatedAt }
    val yourTurn = sorted.filter { it.mode == GameMode.PARTNER && !it.finished && myId != null && GameMerge.turnIsMine(it, myId) }
    val partnerSheet = quiz.sheets.firstOrNull { it.owner != myId } ?: quiz.sheets.firstOrNull()
    val myAttempt = partnerSheet?.let { sheet ->
        quiz.attempts.firstOrNull { it.sheetOwner == sheet.owner && it.guesser == myId }
    }
    val dailyMine = quiz.wr.firstOrNull { it.date == today.toString() && it.player == myId }
    val dailyPartner = quiz.wr.firstOrNull { it.date == today.toString() && it.player != myId }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.entertainment_subtitle), style = MaterialTheme.typography.bodyMedium)
        }
        if (yourTurn.isNotEmpty()) {
            item { Text(stringResource(R.string.entertainment_your_turn), style = MaterialTheme.typography.titleMedium) }
            items(yourTurn) { game ->
                GameRow(game = game, nameOf = nameOf, status = stringResource(R.string.game_your_turn), onOpen = { onOpenGame(game.id) })
            }
        }
        item { Text(stringResource(R.string.entertainment_my_games), style = MaterialTheme.typography.titleMedium) }
        if (sorted.isEmpty()) {
            item { Text(stringResource(R.string.entertainment_no_games)) }
        } else {
            items(sorted) { game ->
                val status = when {
                    game.finished -> stringResource(R.string.game_finished)
                    game.mode == GameMode.PARTNER && myId != null && GameMerge.turnIsMine(game, myId) -> stringResource(R.string.game_your_turn)
                    game.mode == GameMode.PARTNER -> stringResource(R.string.game_partner_turn)
                    else -> stringResource(R.string.game_your_turn)
                }
                GameRow(game = game, nameOf = nameOf, status = status, onOpen = { onOpenGame(game.id) })
            }
        }
        item { Text(stringResource(R.string.entertainment_start_game), style = MaterialTheme.typography.titleMedium) }
        if (pairing == null) {
            item { Text(stringResource(R.string.entertainment_pairing_needed)) }
        }
        item {
            StartGameCard(
                title = stringResource(R.string.game_dots_boxes),
                desc = stringResource(R.string.game_dots_boxes_desc),
                pairing = pairing,
                onStartPartner = { onStartPartner(GameKind.DOTS) },
                onStartAi = { level -> onStartAi(GameKind.DOTS, level) },
            )
        }
        item {
            StartGameCard(
                title = stringResource(R.string.game_gomoku),
                desc = stringResource(R.string.game_gomoku_desc),
                pairing = pairing,
                onStartPartner = { onStartPartner(GameKind.GOMOKU) },
                onStartAi = { level -> onStartAi(GameKind.GOMOKU, level) },
            )
        }
        item { Text(stringResource(R.string.entertainment_quizzes), style = MaterialTheme.typography.titleMedium) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.quiz_know_me), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.quiz_know_me_desc))
                    if (myAttempt != null && partnerSheet != null) {
                        Text(stringResource(R.string.quiz_your_score, knowMeScore(partnerSheet, myAttempt), KNOW_ME_QUESTIONS.size))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onKnowMeAnswer) { Text(stringResource(R.string.quiz_answer_about_me)) }
                        OutlinedButton(onClick = onKnowMeGuess, enabled = partnerSheet != null) { Text(stringResource(R.string.quiz_guess_partner)) }
                    }
                    if (partnerSheet == null) Text(stringResource(R.string.quiz_no_sheet_yet))
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.quiz_would_you_rather), style = MaterialTheme.typography.titleSmall)
                    if (dailyMine != null && dailyPartner != null) {
                        Text(stringResource(R.string.quiz_agreement, wrAgreement(dailyMine, dailyPartner), DailyChallenge.COUNT))
                    } else if (dailyMine != null) {
                        Text(stringResource(R.string.quiz_waiting_partner))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onWrDaily) { Text(stringResource(R.string.quiz_daily_challenge)) }
                        OutlinedButton(onClick = onWrAll) { Text(stringResource(R.string.quiz_all_questions)) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun GameRow(game: GameState, nameOf: (String) -> String, status: String, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(12.dp)) {
            Text(
                stringResource(if (game.kind == GameKind.DOTS) R.string.game_dots_boxes else R.string.game_gomoku),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                when (game.mode) {
                    GameMode.PARTNER -> "${nameOf(game.player1)} — ${nameOf(game.player2)} · $status"
                    GameMode.AI_EASY -> "${stringResource(R.string.game_ai_easy)} · $status"
                    GameMode.AI_HARD -> "${stringResource(R.string.game_ai_hard)} · $status"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StartGameCard(
    title: String,
    desc: String,
    pairing: Pairing?,
    onStartPartner: () -> Unit,
    onStartAi: (AiLevel) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(desc)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onStartPartner, enabled = pairing != null) {
                    Text(stringResource(R.string.entertainment_vs_partner))
                }
                OutlinedButton(onClick = { onStartAi(AiLevel.EASY) }) {
                    Text(stringResource(R.string.entertainment_vs_ai_easy))
                }
                OutlinedButton(onClick = { onStartAi(AiLevel.HARD) }) {
                    Text(stringResource(R.string.entertainment_vs_ai_hard))
                }
            }
        }
    }
}
