package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainMenuScreen()
        }
    }
}

@Composable
fun MainMenuScreen() {
    val context = LocalContext.current
    val soundManager = remember { SoundManager(context) }
    
    // BGM ko app minimize/maximize hone par control karne ke liye
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                soundManager.playBGM()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                soundManager.pauseBGM()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            soundManager.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.bg_main), 
            contentDescription = "Background",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 60.dp), 
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            
            PremiumButton(
                title = "TETRIS FALL",
                icon = "🧩",
                topColor = Color(0xFFFFDF00),
                bottomColor = Color(0xFFE67300),
                borderColor = Color(0xFFFFFF88)
            ) {
                soundManager.playBtnClick()
                context.startActivity(Intent(context, TetrisGameActivity::class.java))
            }

            Spacer(modifier = Modifier.height(20.dp))

            PremiumButton(
                title = "ADVENTURE",
                icon = "🗺️",
                topColor = Color(0xFF42E5FF),
                bottomColor = Color(0xFF0055FF),
                borderColor = Color(0xFF8BFFFF)
            ) {
                soundManager.playBtnClick()
                context.startActivity(Intent(context, AdventureGameActivity::class.java))
            }

            Spacer(modifier = Modifier.height(20.dp))

            PremiumButton(
                title = "CLASSIC",
                icon = "👑",
                topColor = Color(0xFFB452FF),
                bottomColor = Color(0xFF5E17EB),
                borderColor = Color(0xFFE48DFF)
            ) {
                soundManager.playBtnClick()
                context.startActivity(Intent(context, ClassicGameActivity::class.java))
            }
        }
    }
}

@Composable
fun PremiumButton(
    title: String,
    icon: String,
    topColor: Color,
    bottomColor: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.92f else 1f, label = "bounce")

    Box(
        modifier = Modifier
            .fillMaxWidth(0.75f) 
            .height(72.dp) 
            .scale(scale)
            .shadow(16.dp, RoundedCornerShape(36.dp), spotColor = bottomColor) 
            .clip(RoundedCornerShape(36.dp))
            .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
            .border(3.dp, borderColor, RoundedCornerShape(36.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x66FFFFFF), Color(0x00FFFFFF), Color(0x33000000))
                    ),
                    RoundedCornerShape(33.dp)
                )
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = icon, fontSize = 28.sp, modifier = Modifier.padding(end = 12.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(
                    shadow = Shadow(color = Color(0xAA000000), blurRadius = 8f, offset = Offset(2f, 4f))
                )
            )
        }
    }
}
