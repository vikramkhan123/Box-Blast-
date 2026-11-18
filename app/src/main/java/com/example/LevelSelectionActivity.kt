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

class LevelSelectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
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

@Composable
fun LevelSelectionScreen(onBackClick: () -> Unit, onLevelClick: (Int) -> Unit) {
    // Current unlocked level progress (e.g., player is at level 8)
    val unlockedLevelMax = 8
    val completedLevelsWithStars = mapOf(1 to 3, 2 to 3, 3 to 2, 4 to 3, 5 to 1, 6 to 3, 7 to 2) // Example completed level stars

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFDF6F0)) // Clean light cream background matching user theme
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2E8F0))
                ) {
                    Text(text = "← Back", color = Color(0xFF1E293B), fontWeight = FontWeight.Bold)
                }

                Text(
                    text = "ADVENTURE MAP",
                    color = Color(0xFF1E293B),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )

                Spacer(modifier = Modifier.width(60.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                    val earnedStars = completedLevelsWithStars[levelNum] ?: 0

                    LevelCard(
                        levelNumber = levelNum,
                        isUnlocked = isUnlocked,
                        earnedStars = earnedStars,
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
fun LevelCard(levelNumber: Int, isUnlocked: Boolean, earnedStars: Int, onClick: () -> Unit) {
    val bgBrush = when {
        isUnlocked && earnedStars > 0 -> Brush.verticalGradient(listOf(Color(0xFFFBBF24), Color(0xFFD97706))) // Completed (Glossy Gold)
        isUnlocked -> Brush.verticalGradient(listOf(Color(0xFF38BDF8), Color(0xFF0284C7))) // Unlocked current (Glossy Blue)
        else -> Brush.verticalGradient(listOf(Color(0xFF94A3B8), Color(0xFF64748B))) // Locked dark gray
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
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
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                if (earnedStars > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "⭐".repeat(earnedStars),
                        fontSize = 12.sp
                    )
                }
            } else {
                // Chain Lock / Locked representation
                Text(
                    text = "🔒",
                    fontSize = 26.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "LOCKED",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
