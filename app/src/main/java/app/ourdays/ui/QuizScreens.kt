package app.ourdays.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ourdays.R
import app.ourdays.data.KnowMeQuestion
import app.ourdays.data.WrQuestion

/**
 * "How well do you know me" form: one question after another, four options each.
 * Used both for answering about yourself and for guessing your partner's answers.
 * [onDone] fires only when every question has an answer.
 */
@Composable
fun KnowMeForm(
    questions: List<KnowMeQuestion>,
    initialAnswers: Map<String, Int>,
    bengali: Boolean,
    onDone: (Map<String, Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var answers by remember(questions) { mutableStateOf(initialAnswers) }
    Column(modifier.verticalScroll(rememberScrollState())) {
        questions.forEach { q ->
            Text(q.text(bengali), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                q.options(bengali).forEachIndexed { index, option ->
                    FilterChip(
                        selected = answers[q.id] == index,
                        onClick = { answers = answers + (q.id to index) },
                        label = { Text(option) },
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onDone(answers) },
            enabled = questions.all { answers.containsKey(it.id) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.quiz_save_answers))
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Would-you-rather form: two options per question, side by side.
 */
@Composable
fun WrForm(
    questions: List<WrQuestion>,
    initialAnswers: Map<String, Int>,
    bengali: Boolean,
    onDone: (Map<String, Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var answers by remember(questions) { mutableStateOf(initialAnswers) }
    Column(modifier.verticalScroll(rememberScrollState())) {
        questions.forEach { q ->
            Text(q.text(bengali), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = answers[q.id] == 0,
                    onClick = { answers = answers + (q.id to 0) },
                    label = { Text(q.a(bengali)) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = answers[q.id] == 1,
                    onClick = { answers = answers + (q.id to 1) },
                    label = { Text(q.b(bengali)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onDone(answers) },
            enabled = questions.all { answers.containsKey(it.id) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.quiz_save_answers))
        }
        Spacer(Modifier.height(24.dp))
    }
}
