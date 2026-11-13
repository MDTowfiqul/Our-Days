package app.ourdays.ui

import android.content.Context
import android.util.Base64
import android.view.View
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

// Das Intro ist jetzt ein echtes Video (Full HD, 60 fps, 3,2 s), kein
// gezeichnetes Compose-Intro mehr. Das Video liegt als Base64-Text in den
// Assets (push kann keine Binärdateien), wird beim ersten Start in den Cache
// entpackt und dann mit dem eingebauten VideoView abgespielt — keine neue
// Bibliothek nötig.
@Composable
fun IntroScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    var videoFile by remember { mutableStateOf<File?>(null) }
    val done = remember { AtomicBoolean(false) }
    val finish: () -> Unit = {
        if (done.compareAndSet(false, true)) {
            onContinue()
        }
    }
    LaunchedEffect(Unit) {
        videoFile = withContext(Dispatchers.IO) { introVideoFile(context) }
    }
    // Sicherheitsnetz: Das Intro darf nie länger als 5 Sekunden dauern,
    // auch wenn das Video nicht lädt oder nicht abspielt.
    LaunchedEffect(Unit) {
        delay(5000)
        finish()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF9A2438),
                        Color(0xFF7A1A2C),
                        Color(0xFF3D0D16)
                    )
                )
            )
    ) {
        val file = videoFile
        if (file != null) {
            AndroidView(
                factory = { ctx ->
                    try {
                        VideoView(ctx).apply {
                            setVideoPath(file.absolutePath)
                            setOnPreparedListener { it.start() }
                            setOnCompletionListener { finish() }
                            setOnErrorListener { _, _, _ ->
                                finish()
                                true
                            }
                        }
                    } catch (_: Exception) {
                        View(ctx)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// Entpackt das Intro-Video aus den Assets in den Cache. Schreibt die Datei
// nur neu, wenn sie fehlt oder eine andere Größe hat.
private fun introVideoFile(context: Context): File? {
    return try {
        val out = File(context.cacheDir, "intro.mp4")
        val encoded = context.assets.open("intro/intro.mp4.b64").readBytes()
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        if (!out.exists() || out.length() != bytes.size.toLong()) {
            out.writeBytes(bytes)
        }
        out
    } catch (_: Exception) {
        null
    }
}
