package app.ourdays.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ourdays.app.R

@Composable
fun IntroScreen(onContinue: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
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
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + scaleIn(
                    initialScale = 0.6f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_heart),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(140.dp)
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(700, delayMillis = 350)) +
                    slideInVertically(
                        initialOffsetY = { 60 },
                        animationSpec = tween(700, delayMillis = 350)
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
                visible = visible,
                enter = fadeIn(animationSpec = tween(700, delayMillis = 650)) +
                    slideInVertically(
                        initialOffsetY = { 60 },
                        animationSpec = tween(700, delayMillis = 650)
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
                visible = visible,
                enter = fadeIn(animationSpec = tween(700, delayMillis = 1000))
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
            visible = visible,
            enter = fadeIn(animationSpec = tween(700, delayMillis = 1200)),
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
