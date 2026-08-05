package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    
    val bgBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF162456), Color(0xFF0A0D24))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "BOX BLAST",
            style = TextStyle(
                fontSize = 54.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFFFdf38),
                shadow = Shadow(
                    color = Color(0x99000000),
                    blurRadius = 15f
                ),
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.padding(bottom = 60.dp)
        )

        // Classic Mode
        GameModeButton(
            title = "CLASSIC MODE",
            subtitle = "Score High & Relax",
            topColor = Color(0xFF2CD04E),
            bottomColor = Color(0xFF199131)
        ) {
            context.startActivity(Intent(context, ClassicGameActivity::class.java))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Adventure Mode
        GameModeButton(
            title = "ADVENTURE",
            subtitle = "Collect Gems & Candies",
            topColor = Color(0xFFB92B27),
            bottomColor = Color(0xFF6C1613)
        ) {
            context.startActivity(Intent(context, AdventureGameActivity::class.java))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tetris Mode (Agle step me active karenge)
        GameModeButton(
            title = "TETRIS FALL",
            subtitle = "Rotate & Drop",
            topColor = Color(0xFF00C6FF),
            bottomColor = Color(0xFF0072FF)
        ) {
            // TODO: Start Tetris Activity
        }
    }
}

@Composable
fun GameModeButton(
    title: String,
    subtitle: String,
    topColor: Color,
    bottomColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.92f else 1f, label = "bounce")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(if (isPressed) 2.dp else 10.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
            .border(3.dp, Color(0x66FFFFFF), RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = Shadow(color = Color(0x66000000), blurRadius = 5f))
            )
            Text(
                text = subtitle,
                color = Color(0xCCFFFFFF),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
