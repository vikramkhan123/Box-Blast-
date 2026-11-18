package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BoxBlastTheme {
                MainScreen(
                    onClassicClick = {
                        startActivity(Intent(this, ClassicGameActivity::class.java))
                    },
                    onAdventureClick = {
                        startActivity(Intent(this, LevelSelectionActivity::class.java))
                    }
                )
            }
        }
    }
}

@Composable
fun MainScreen(onClassicClick: () -> Unit, onAdventureClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepMidnightBg, DarkIndigoCard)
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar (Coins & Settings)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Coin Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = GlassWhite,
                    modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🪙", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "2,200", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // Gem Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = GlassWhite,
                    modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "💎", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "0", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Center Title Logo Area
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "BOX",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black,
                    color = NeonGold,
                    letterSpacing = 4.sp
                )
                Text(
                    text = "BLAST",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    color = NeonCyan,
                    letterSpacing = 6.sp
                )
            }

            // Menu Buttons (Glassmorphic Styled)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MenuButton(
                    text = "CLASSIC MODE",
                    gradient = Brush.horizontalGradient(listOf(NeonCyan, Color(0xFF3B82F6))),
                    onClick = onClassicClick
                )

                MenuButton(
                    text = "ADVENTURE MODE",
                    gradient = Brush.horizontalGradient(listOf(NeonGold, Color(0xFFD97706))),
                    onClick = onAdventureClick
                )

                MenuButton(
                    text = "ZEN BLITZ MODE",
                    gradient = Brush.horizontalGradient(listOf(NeonPurple, NeonPink)),
                    onClick = { /* Future Mode or Classic fallback */ }
                )
            }

            // Bottom Footer
            Text(
                text = "High-End Experience v2.0",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun MenuButton(text: String, gradient: Brush, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(gradient)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
    }
}
