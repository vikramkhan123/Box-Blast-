package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        soundManager = SoundManager(this)
        
        setContent {
            val prefs = getSharedPreferences("BoxBlastPrefs", Context.MODE_PRIVATE)
            val maxLevel = prefs.getInt("MaxAdventureLevel", prefs.getInt("AdventureLevel", 1))

            Box(modifier = Modifier.fillMaxSize()) {
                // Background calling fixed here
                LevelNeonBackground() 
                
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
                        Text(text = "ADVENTURE MAP", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    
                    val scrollState = rememberLazyListState()
                    LazyColumn(
                        state = scrollState, modifier = Modifier.fillMaxSize(),
                        reverseLayout = true, contentPadding = PaddingValues(vertical = 40.dp)
                    ) {
                        items(50) { index ->
                            val level = index + 1
                            val isUnlocked = level <= maxLevel
                            val isCurrent = level == maxLevel
                            val xOffset = (sin(index * 0.7) * 120).dp

                            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                if (level < 50) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val nextOffset = (sin((index + 1) * 0.7) * 120).dp.toPx()
                                        val currentOffset = xOffset.toPx()
                                        drawLine(color = Color(0x66FFFFFF), start = Offset(size.width / 2 + currentOffset, size.height / 2), end = Offset(size.width / 2 + nextOffset, -size.height / 2), strokeWidth = 15f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f))
                                    }
                                }
                                Box(
                                    modifier = Modifier.offset(x = xOffset).size(if (isCurrent) 85.dp else 65.dp).clip(CircleShape)
                                        .background(if (isCurrent) Brush.radialGradient(listOf(Color(0xFFFFD700), Color(0xFFE65C00))) else if (isUnlocked) Brush.radialGradient(listOf(Color(0xFF42E5FF), Color(0xFF0055FF))) else Brush.radialGradient(listOf(Color(0xFF555555), Color(0xFF222222))))
                                        .border(4.dp, if (isUnlocked) Color.White else Color.DarkGray, CircleShape)
                                        .clickable(enabled = isUnlocked) {
                                            soundManager.playBtnClick()
                                            prefs.edit().putInt("CurrentPlayingLevel", level).apply()
                                            startActivity(Intent(this@LevelSelectionActivity, AdventureGameActivity::class.java))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isUnlocked) Text(text = "$level", color = Color.White, fontSize = if (isCurrent) 32.sp else 24.sp, fontWeight = FontWeight.Bold)
                                    else Text(text = "🔒", fontSize = 24.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    override fun onResume() { super.onResume(); soundManager.playBGM() }
    override fun onPause() { super.onPause(); soundManager.pauseBGM() }
    override fun onDestroy() { super.onDestroy(); soundManager.release() }
}

@Composable
fun LevelNeonBackground() {
    val infiniteTransition = rememberInfiniteTransition()
    val time by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 1000f, animationSpec = infiniteRepeatable(tween(15000, easing = LinearEasing)))
    Canvas(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        val cx1 = size.width / 2f + kotlin.math.sin(time / 150f) * 350f
        val cy1 = size.height / 3f + kotlin.math.cos(time / 120f) * 350f
        drawRect(Brush.radialGradient(listOf(Color(0x77E94560), Color(0x00E94560)), Offset(cx1, cy1), 900f))
        
        val cx2 = size.width / 2f + kotlin.math.cos(time / 140f) * 400f
        val cy2 = size.height / 1.5f + kotlin.math.sin(time / 160f) * 400f
        drawRect(Brush.radialGradient(listOf(Color(0x770F80FF), Color(0x000F80FF)), Offset(cx2, cy2), 1000f))
    }
}
