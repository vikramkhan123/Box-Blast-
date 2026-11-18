package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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

class LevelSelectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BoxBlastTheme {
                LevelSelectionScreen(
                    onBackClick = { finish() },
                    onLevelClick = { level ->
                        val intent = Intent(this, AdventureGameActivity::class.java)
                        intent.putExtra("EXTRA_LEVEL", level)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun LevelSelectionScreen(onBackClick: () -> Unit, onLevelClick: (Int) -> Unit) {
    // Let's assume player has unlocked up to Level 8
    val unlockedLevelMax = 8

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepMidnightBg, DarkIndigoCard)
                )
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBackClick,
                    colors = ButtonDefaults.buttonColors(containerColor = GlassWhite)
                ) {
                    Text(text = "← Back", color = Color.White)
                }

                Text(
                    text = "ADVENTURE MAP",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(60.dp)) // balance layout
            }

            // Stars Progress Header
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = GlassWhite
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "⭐", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Stars: 45 / 300",
                        color = NeonGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Level Grid (3 Columns)
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(30) { index ->
                    val levelNum = index + 1
                    val isUnlocked = levelNum <= unlockedLevelMax
                    val isCurrent = levelNum == unlockedLevelMax

                    LevelCard(
                        levelNumber = levelNum,
                        isUnlocked = isUnlocked,
                        isCurrent = isCurrent,
                        onClick = {
                            if (isUnlocked) {
                                onLevelClick(levelNum)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LevelCard(levelNumber: Int, isUnlocked: Boolean, isCurrent: Boolean, onClick: () -> Unit) {
    val bgBrush = when {
        isCurrent -> Brush.verticalGradient(listOf(NeonGold, Color(0xFFD97706))) // Active Golden Glossy
        isUnlocked -> Brush.verticalGradient(listOf(NeonCyan, Color(0xFF2563EB))) // Completed Blue Glossy
        else -> Brush.verticalGradient(listOf(Color(0xFF334155), Color(0xFF1E293B))) // Locked dark style
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(bgBrush)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isUnlocked) {
                Text(
                    text = "$levelNumber",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⭐⭐⭐",
                    fontSize = 12.sp
                )
            } else {
                // Locked level representation (Chain Lock style)
                Text(
                    text = "🔒",
                    fontSize = 28.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "LOCKED",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
