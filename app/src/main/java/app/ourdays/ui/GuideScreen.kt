package app.ourdays.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ourdays.app.R

/** Anleitung zum Koppeln und Abgleichen — fünf Schritte mit echten Bildschirmfotos,
 *  keine gezeichneten Handy-Attrappen. Die Bilder liegen als Base64-Text in
 *  assets/guide (sie kommen aus den ScreenshotTests: guide_qr, guide_sync_unpaired,
 *  guide_sync_paired, guide_send_scope, home). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.guide_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            GuideStep(
                number = 1,
                title = stringResource(R.string.guide_step1_title),
                text = stringResource(R.string.guide_step1_text),
                imageName = "step1_qr",
            )
            GuideStep(
                number = 2,
                title = stringResource(R.string.guide_step2_title),
                text = stringResource(R.string.guide_step2_text),
                imageName = "step2_scan",
            )
            GuideStep(
                number = 3,
                title = stringResource(R.string.guide_step3_title),
                text = stringResource(R.string.guide_step3_text),
                imageName = "step3_paired",
            )
            GuideStep(
                number = 4,
                title = stringResource(R.string.guide_step4_title),
                text = stringResource(R.string.guide_step4_text),
                imageName = "step4_share",
            )
            GuideStep(
                number = 5,
                title = stringResource(R.string.guide_step5_title),
                text = stringResource(R.string.guide_step5_text),
                imageName = "step5_open",
            )
            Text(
                stringResource(R.string.guide_footer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 32.dp),
            )
        }
    }
}

@Composable
private fun GuideStep(number: Int, title: String, text: String, imageName: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val bitmap = guideBitmap(imageName)
            if (bitmap != null) {
                Image(
                    bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                )
            }
        }
    }
}

/** Bild aus assets/guide/<name>.b64 lesen und als Bitmap liefern (null, wenn es fehlt). */
@Composable
private fun guideBitmap(name: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(name) {
        try {
            val b64 = context.assets.open("guide/$name.b64").bufferedReader().readText()
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}
