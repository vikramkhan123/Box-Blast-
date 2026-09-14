package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

class LevelSelectionActivity : ComponentActivity() {
    private lateinit var soundManager: SoundManager
    private var maxLevelState by mutableIntStateOf(1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        soundManager = SoundManager(this)
        val prefs = getSharedPreferences("BoxBlastPrefs", Context.MODE_PRIVATE)
        maxLevelState = prefs.getInt("MaxAdventureLevel", prefs.getInt("AdventureLevel", 1))

        setContent {
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0xFFE8EEF5), Color(0xFFD6E2EE), Color(0xFFCAD8E6))
                )
            )) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 42.dp, bottom = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ADVENTURE MAP",
                            color = Color(0xFF2C3E50),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }

                    val scrollState = rememberLazyListState()

                    // Auto-scroll seedha unlocked level par le aayega
                    LaunchedEffect(maxLevelState) {
                        val targetIndex = (maxLevelState - 1).coerceIn(0, 499)
                        scrollState.scrollToItem(targetIndex)
                    }

                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true,
                        contentPadding = PaddingValues(vertical = 40.dp)
                    ) {
                        items(500) { index ->
                            val level = index + 1
                            val isUnlocked = level <= maxLevelState
                            val isCurrent = level == maxLevelState
                            val xOffset = (sin(index * 0.75) * 110).dp

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (level < 500) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val nextOffset = (sin((index + 1) * 0.75) * 110).dp.toPx()
                                        val curOffset = xOffset.toPx()
                                        drawLine(
                                            color = Color(0x334A6572),
                                            start = Offset(size.width / 2 + curOffset, size.height / 2),
                                            end = Offset(size.width / 2 + nextOffset, -size.height / 2),
                                            strokeWidth = 10f,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 20f), 0f)
                                        )
                                    }
                                }

                                val btnSize = if (isCurrent) 84.dp else 68.dp
                                Box(
                                    modifier = Modifier
                                        .offset(x = xOffset)
                                        .size(btnSize)
                                        .shadow(
                                            elevation = if (isUnlocked) 12.dp else 3.dp,
                                            shape = CircleShape,
                                            ambientColor = if (isCurrent) Color(0xFFFFB300) else Color(0x33000000),
                                            spotColor = if (isCurrent) Color(0xFFFF8F00) else Color(0x44000000)
                                        )
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isCurrent -> Brush.linearGradient(
                                                    listOf(Color(0xFFFFE082), Color(0xFFFFB300), Color(0xFFFF8F00))
                                                )
                                                isUnlocked -> Brush.linearGradient(
                                                    listOf(Color(0xFFF7FAFD), Color(0xFFE2EAF2), Color(0xFFCBD8E6))
                                                )
                                                else -> Brush.linearGradient(
                                                    listOf(Color(0xFFB0BEC5), Color(0xFF90A4AE))
                                                )
                                            }
                                        )
                                        .border(
                                            width = if (isCurrent) 4.dp else 2.5.dp,
                                            brush = if (isCurrent) Brush.verticalGradient(
                                                listOf(Color.White, Color(0xFFFFD54F))
                                            ) else Brush.verticalGradient(
                                                listOf(Color(0xFFFFFFFF), Color(0xFFB0BEC5))
                                            ),
                                            shape = CircleShape
                                        )
                                        .clickable(enabled = isUnlocked) {
                                            soundManager.playBtnClick()
                                            prefs.edit().putInt("CurrentPlayingLevel", level).apply()
                                            startActivity(Intent(this@LevelSelectionActivity, AdventureGameActivity::class.java))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isUnlocked) {
                                        Text(
                                            text = "$level",
                                            color = if (isCurrent) Color(0xFF4E342E) else Color(0xFF263238),
                                            fontSize = if (isCurrent) 26.sp else 20.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    } else {
                                        Text(text = "🔒", fontSize = 18.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        maxLevelState = getSharedPreferences("BoxBlastPrefs", Context.MODE_PRIVATE).getInt("MaxAdventureLevel", 1)
        soundManager.playBGM()
    }
    override fun onPause() { super.onPause(); soundManager.pauseBGM() }
    override fun onDestroy() { super.onDestroy(); soundManager.release() }
}
