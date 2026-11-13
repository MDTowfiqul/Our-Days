package app.ourdays.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ourdays.app.R
import java.util.concurrent.atomic.AtomicBoolean

// Einfacher Willkommens-Bildschirm wie früher: App-Symbol, "Welcome" und ein
// kurzer Satz — kein Video, keine Animation. Nach 2 Sekunden geht es von
// selbst weiter.
@Composable
fun IntroScreen(onContinue: () -> Unit) {
    val done = remember { AtomicBoolean(false) }
    val finish: () -> Unit = {
        if (done.compareAndSet(false, true)) {
            onContinue()
        }
    }
    LaunchedEffect(Unit) {
        delay(2000)
        finish()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(48.dp))
                    .background(Presets[0].brush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_heart),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(96.dp),
                )
            }
            Text(
                stringResource(R.string.welcome),
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(top = 40.dp),
            )
            Text(
                stringResource(R.string.welcome_sub),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
