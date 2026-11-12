package app.ourdays.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun IntroScreen(onContinue: () -> Unit) {
    var started by remember { mutableStateOf(false) }
    var met by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        started = true
        delay(750)
        met = true
    }
    // Die beiden Herzen laufen von links und rechts aufeinander zu und bilden das Logo
    val whiteX by animateDpAsState(
        if (started) (-16).dp else (-360).dp,
        tween(750, easing = FastOutSlowInEasing),
        label = "whiteHeart",
    )
    val redX by animateDpAsState(
        if (started) 16.dp else 360.dp,
        tween(750, easing = FastOutSlowInEasing),
        label = "redHeart",
    )
    // Kleiner Aufprall, wenn die Herzen sich treffen
    val pop by animateFloatAsState(
        if (met) 1f else 0.9f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "logoPop",
    )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(pop),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .offset(x = whiteX)
                        .size(84.dp),
                )
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFFF5A78),
                    modifier = Modifier
                        .offset(x = redX)
                        .size(84.dp),
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            AnimatedVisibility(
                visible = met,
                enter = fadeIn(animationSpec = tween(700, delayMillis = 100)) +
                    slideInVertically(
                        initialOffsetY = { 60 },
                        animationSpec = tween(700, delayMillis = 100)
                    )
            ) {
                Text(
                    text = "Our Days",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            AnimatedVisibility(
                visible = met,
                enter = fadeIn(animationSpec = tween(700, delayMillis = 350)) +
                    slideInVertically(
                        initialOffsetY = { 60 },
                        animationSpec = tween(700, delayMillis = 350)
                    )
            ) {
                Text(
                    text = "Count every day that matters.",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(56.dp))
            AnimatedVisibility(
                visible = met,
                enter = fadeIn(animationSpec = tween(700, delayMillis = 650))
            ) {
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF5A78),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Continue to App",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = met,
            enter = fadeIn(animationSpec = tween(700, delayMillis = 850)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Text(
                text = "Your story stays yours.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 48.dp)
            )
        }
    }
}
