package com.example

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class AdventureGameView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {

    val soundManager = SoundManager(context)
    private val prefs = context.getSharedPreferences("BoxBlastPrefs", Context.MODE_PRIVATE)
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val handler = Handler(Looper.getMainLooper())
    
    private val grid = Array(8) { IntArray(8) { 0 } }
    private var currentLevel = prefs.getInt("CurrentPlayingLevel", 1)
    private var maxLevel = prefs.getInt("MaxAdventureLevel", prefs.getInt("AdventureLevel", 1))
    
    private val targetGems = mutableMapOf<Int, Int>()
    private val gemsCollected = mutableMapOf<Int, Int>()
    
    private var isLevelComplete = false; private var isWaitingForAd = false
    private var adCountdown = 10; private var isGameOver = false; private var earnedCoins = 0
    private var showResumePopup = false
    private val resumeBtnRect = RectF(); private val newGameBtnRect = RectF()
    private val shuffleBtnRect = RectF()

    data class FlyingGem(var startX: Float, var startY: Float, var type: Int, var progress: Float = 0f)
    private val flyingGems = mutableListOf<FlyingGem>()
    data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Int)
    private val particles = mutableListOf<Particle>()
    data class GlowLine(val isRow: Boolean, val index: Int, var alpha: Float = 1f)
    private val glowLines = mutableListOf<GlowLine>()
    data class FloatingWord(val text: String, var y: Float, var alpha: Float = 1f, var scale: Float = 0.5f)
    private val floatingWords = mutableListOf<FloatingWord>()

    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAA0B132B.toInt(); style = Paint.Style.FILL }
    private val boardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF42E5FF.toInt(); style = Paint.Style.STROKE; strokeWidth = 8f }
    private val blockBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glassOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text3DPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val neonShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.CYAN; style = Paint.Style.FILL; setShadowLayer(30f, 0f, 0f, Color.WHITE) }

    private var cellSize = 0f; private var boardSize = 0f; private var boardX = 0f; private var boardY = 0f
    private var trayY = 0f; private var trayCellSize = 0f
    private val restartBtnRect = RectF(); private val menuBtnRect = RectF()

    val SHAPES = listOf(
        arrayOf(intArrayOf(1)), arrayOf(intArrayOf(1, 1)), arrayOf(intArrayOf(1), intArrayOf(1)),
        arrayOf(intArrayOf(1, 1), intArrayOf(1, 1)), arrayOf(intArrayOf(1, 1, 1)),
        arrayOf(intArrayOf(1), intArrayOf(1), intArrayOf(1)), arrayOf(intArrayOf(1, 1, 1, 1)),
        arrayOf(intArrayOf(1, 0), intArrayOf(1, 1)), arrayOf(intArrayOf(0, 1), intArrayOf(1, 1)), 
        arrayOf(intArrayOf(1, 1), intArrayOf(1, 0)), arrayOf(intArrayOf(1, 1), intArrayOf(0, 1))
    )

    class Shape(val matrix: Array<IntArray>) { val rows = matrix.size; val cols = matrix[0].size; var cx = 0f; var cy = 0f; var placed = false }

    private val trayShapes = arrayOfNulls<Shape>(3)
    private var draggingShapeIndex = -1; private var draggingShape: Shape? = null
    private var dragTouchOffsetX = 0f; private var dragTouchOffsetY = 0f
    private var hoverRow = -1; private var hoverCol = -1; private var canFitHover = false

    private val renderLoop = object : Runnable { override fun run() { invalidate(); handler.postDelayed(this, 16L) } }
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isWaitingForAd && adCountdown > 0) {
                soundManager.playCountdownTick(); adCountdown--
                if (adCountdown == 0) { isWaitingForAd = false; isGameOver = true; soundManager.stopCountdownTick(); soundManager.playGameOver() } 
                else handler.postDelayed(this, 1000L)
                invalidate()
            }
        }
    }

    init { 
        if (prefs.getBoolean("AdvSaved", false)) { showResumePopup = true; getAvailableGemTypes().forEach { targetGems[it] = 10; gemsCollected[it] = 0 } } else { initLevel() }
        handler.post(renderLoop) 
    }

    private fun addCoins(amount: Int) { val coins = prefs.getInt("BoxBlastCoins", 0) + amount; prefs.edit().putInt("BoxBlastCoins", coins).apply() }
    
    private fun useCoinsOrFreeShuffle(): Boolean { 
        var freeShuffles = prefs.getInt("FreeShuffles", 0)
        if (freeShuffles > 0) { freeShuffles--; prefs.edit().putInt("FreeShuffles", freeShuffles).apply(); return true }
        var coins = prefs.getInt("BoxBlastCoins", 0)
        if (coins >= 50) { coins -= 50; prefs.edit().putInt("BoxBlastCoins", coins).apply(); return true }
        return false 
    }

    private fun vibratePhone(duration: Long = 50L) { try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") vibrator.vibrate(duration) } catch (e: Exception) { } }

    private fun getAvailableGemTypes(): List<Int> {
        val available = mutableListOf(11) 
        if (currentLevel >= 2) available.add(12) 
        if (currentLevel >= 5) available.add(13) 
        if (currentLevel >= 15) available.add(14) 
        if (currentLevel >= 25) available.add(15) 
        if (currentLevel >= 40) available.add(16) 
        val numTypesToUse = minOf(4, 1 + (currentLevel / 5)) 
        return available.shuffled().take(maxOf(1, numTypesToUse))
    }

    private fun initLevel() {
        val totalTarget = 10 + (currentLevel * 2)
        val gemTypes = getAvailableGemTypes(); val targetPerGem = totalTarget / gemTypes.size
        targetGems.clear(); gemsCollected.clear(); gemTypes.forEach { type -> targetGems[type] = targetPerGem; gemsCollected[type] = 0 }
        isGameOver = false; isLevelComplete = false; isWaitingForAd = false; adCountdown = 10; earnedCoins = 0
        flyingGems.clear(); particles.clear(); glowLines.clear(); floatingWords.clear()
        for (r in 0 until 8) for (c in 0 until 8) grid[r][c] = 0
        prefs.edit().putBoolean("AdvSaved", false).apply()
        
        var spawned = 0; val initialGems = minOf(totalTarget, 16) 
        while(spawned < initialGems) { val r = Random.nextInt(8); val c = Random.nextInt(8); if (grid[r][c] == 0) { grid[r][c] = gemTypes.random(); spawned++ } }
        for (i in 0 until 3) trayShapes[i] = null; fillTray()
    }

    private fun loadGame() {
        try {
            val gridStr = prefs.getString("AdvGrid", "")
            if (gridStr?.isNotEmpty() == true) { 
                val rows = gridStr.split(";")
                for (r in 0 until Math.min(8, rows.size)) { 
                    val cols = rows[r].split(",")
                    for (c in 0 until Math.min(8, cols.size)) {
                        grid[r][c] = cols[c].toInt() 
                    } 
                } 
            }
            targetGems.clear(); gemsCollected.clear()
            prefs.getString("AdvGemsColl", "")?.split(";")?.forEach { if(it.contains(":")){ val parts = it.split(":"); gemsCollected[parts[0].toInt()] = parts[1].toInt() } }
            prefs.getString("AdvTargetGems", "")?.split(";")?.forEach { if(it.contains(":")){ val parts = it.split(":"); targetGems[parts[0].toInt()] = parts[1].toInt() } }
        } catch (e: Exception) { 
            e.printStackTrace()
            initLevel(); return 
        }
        for (i in 0 until 3) trayShapes[i] = null; fillTray()
    }

    private fun saveGame() {
        if (isGameOver || showResumePopup || isWaitingForAd || isLevelComplete) return
        val gemsCollStr = gemsCollected.entries.joinToString(";") { "${it.key}:${it.value}" }
        val targetGemsStr = targetGems.entries.joinToString(";") { "${it.key}:${it.value}" }
        prefs.edit().putBoolean("AdvSaved", true).putString("AdvGrid", grid.joinToString(";") { it.joinToString(",") }).putString("AdvGemsColl", gemsCollStr).putString("AdvTargetGems", targetGemsStr).apply()
    }

    private fun fillTray() {
        for (i in 0 until 3) {
            if (trayShapes[i] == null || trayShapes[i]!!.placed) {
                var safeShape: Shape? = null
                for (attempt in 0..20) { 
                    // BUG FIXED HERE!
                    val rawM = SHAPES.random() 
                    val testShape = Shape(Array(rawM.size) { r -> IntArray(rawM[r].size) { c -> if (rawM[r][c] == 1) Random.nextInt(1, 6) else 0 } })
                    if (canFitAnywhere(testShape)) { safeShape = testShape; break } 
                }
                if (safeShape == null) safeShape = Shape(arrayOf(intArrayOf(Random.nextInt(1, 6))))
                
                if (!isLevelComplete && Random.nextFloat() < 0.4f) {
                    val neededGems = targetGems.filter { (t, target) -> (gemsCollected[t] ?: 0) < target }.keys.toList()
                    if (neededGems.isNotEmpty()) { val validCoords = mutableListOf<Pair<Int, Int>>(); for (r in safeShape.matrix.indices) for (c in safeShape.matrix[0].indices) if (safeShape.matrix[r][c] != 0) validCoords.add(Pair(r, c)); if (validCoords.isNotEmpty()) { val (gr, gc) = validCoords.random(); safeShape.matrix[gr][gc] = neededGems.random() } }
                }
                trayShapes[i] = safeShape
            }
        }
        if (width > 0 && height > 0) updateTrayPositions(); checkGameOverCondition()
    }

    private fun canFitAnywhere(shape: Shape): Boolean { for (r in 0 until 8) for (c in 0 until 8) if (canPlaceShape(shape, r, c)) return true; return false }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        boardSize = w - 60f; cellSize = boardSize / 8; boardX = 30f; boardY = 280f
        trayY = boardY + boardSize + 100f; trayCellSize = cellSize * 0.65f
        val bw = 600f; val bh = 130f; val cx = w/2f
        restartBtnRect.set(cx - bw/2f, boardY + boardSize/2f + 40f, cx + bw/2f, boardY + boardSize/2f + 40f + bh)
        menuBtnRect.set(cx - bw/2f, restartBtnRect.bottom + 30f, cx + bw/2f, restartBtnRect.bottom + 30f + bh)
        resumeBtnRect.set(cx - bw/2f, boardY + boardSize/2f - 60f, cx + bw/2f, boardY + boardSize/2f - 60f + bh)
        newGameBtnRect.set(cx - bw/2f, resumeBtnRect.bottom + 40f, cx + bw/2f, resumeBtnRect.bottom + 40f + bh)
        shuffleBtnRect.set(cx - 150f, trayY + trayCellSize * 4.5f, cx + 150f, trayY + trayCellSize * 4.5f + 110f)
        updateTrayPositions()
    }

    private fun updateTrayPositions() {
        if (width == 0) return
        val sectionWidth = width / 3f
        for (i in 0 until 3) trayShapes[i]?.let { if (!it.placed) { it.cx = (i * sectionWidth) + (sectionWidth - (it.cols * trayCellSize)) / 2f; it.cy = trayY + (sectionWidth - (it.rows * trayCellSize)) / 2f } }
    }

    private fun drawGlossy3DText(canvas: Canvas, text: String, x: Float, y: Float, mainColor: Int, depthColor: Int, size: Float, align: Paint.Align = Paint.Align.CENTER) {
        text3DPaint.textSize = size; text3DPaint.textAlign = align; text3DPaint.clearShadowLayer()
        text3DPaint.style = Paint.Style.STROKE; text3DPaint.strokeWidth = size * 0.15f; text3DPaint.strokeJoin = Paint.Join.ROUND
        text3DPaint.color = depthColor; canvas.drawText(text, x, y + size * 0.08f, text3DPaint)
        text3DPaint.style = Paint.Style.FILL; text3DPaint.color = mainColor; canvas.drawText(text, x, y, text3DPaint)
        text3DPaint.color = Color.argb(90, 255, 255, 255); canvas.drawText(text, x, y - size * 0.03f, text3DPaint)
    }

    private fun drawCoinIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFB8860B.toInt(); canvas.drawCircle(cx, cy + radius * 0.15f, radius, p)
        p.color = 0xFFFFD700.toInt(); canvas.drawCircle(cx, cy, radius, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = radius * 0.2f; p.color = 0xFFDAA520.toInt(); canvas.drawCircle(cx, cy, radius * 0.6f, p)
        p.style = Paint.Style.FILL; p.color = 0xAAFFFFFF.toInt(); canvas.drawOval(RectF(cx - radius*0.5f, cy - radius*0.8f, cx + radius*0.5f, cy - radius*0.1f), p)
    }

    private fun draw3DButton(canvas: Canvas, rect: RectF, text: String, topColor: Int, bottomColor: Int, size: Float = 45f) {
        btnPaint.color = bottomColor; canvas.drawRoundRect(RectF(rect.left, rect.top + 15f, rect.right, rect.bottom + 15f), 30f, 30f, btnPaint)
        btnPaint.color = topColor; canvas.drawRoundRect(rect, 30f, 30f, btnPaint)
        drawGlossy3DText(canvas, text, rect.centerX(), rect.centerY() + size/3f, Color.WHITE, Color.DKGRAY, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.TRANSPARENT)

        drawGlossy3DText(canvas, "LEVEL $currentLevel", width/2f, 90f, 0xFFFFD700.toInt(), 0xFF8B6508.toInt(), 65f)
        
        val currentCoins = prefs.getInt("BoxBlastCoins", 0)
        drawCoinIcon(canvas, width - 150f, 70f, 25f)
        drawGlossy3DText(canvas, "$currentCoins", width - 110f, 85f, Color.YELLOW, 0xFF8B6508.toInt(), 45f, Paint.Align.LEFT)

        val typesList = targetGems.keys.toList()
        val spacing = width / (typesList.size + 1).toFloat()
        typesList.forEachIndexed { index, type ->
            val cx = spacing * (index + 1)
            drawGemShape(canvas, cx - 25f, 130f, 50f, type)
            val collected = minOf(gemsCollected[type] ?: 0, targetGems[type] ?: 0)
            drawGlossy3DText(canvas, "$collected/${targetGems[type]}", cx, 220f, Color.WHITE, Color.DKGRAY, 40f)
        }

        val rect = RectF(boardX, boardY, boardX + boardSize, boardY + boardSize)
        canvas.drawRoundRect(rect, 24f, 24f, boardPaint)
        canvas.drawRoundRect(rect, 24f, 24f, boardBorderPaint)

        val iteratorGlow = glowLines.iterator()
        while(iteratorGlow.hasNext()) {
            val glow = iteratorGlow.next(); glowPaint.alpha = (glow.alpha * 200).toInt()
            if (glow.isRow) canvas.drawRoundRect(RectF(boardX, boardY + glow.index * cellSize, boardX + boardSize, boardY + (glow.index+1)*cellSize), 12f, 12f, glowPaint)
            else canvas.drawRoundRect(RectF(boardX + glow.index * cellSize, boardY, boardX + (glow.index+1)*cellSize, boardY + boardSize), 12f, 12f, glowPaint)
            glow.alpha -= 0.05f; if (glow.alpha <= 0) iteratorGlow.remove()
        }

        val emptyPaint = Paint().apply { color = 0x2AFFFFFF; style = Paint.Style.STROKE; strokeWidth = 2f }
        for (r in 0 until 8) for (c in 0 until 8) {
            val cx = boardX + c * cellSize; val cy = boardY + r * cellSize
            canvas.drawRoundRect(RectF(cx + 4, cy + 4, cx + cellSize - 4, cy + cellSize - 4), 12f, 12f, emptyPaint)
            if (grid[r][c] != 0) drawGlassy3DBlock(canvas, cx, cy, cellSize, grid[r][c])
        }

        draggingShape?.let { if (canFitHover) drawNeonShadow(canvas, it, boardX + hoverCol * cellSize, boardY + hoverRow * cellSize, cellSize) } 
        for (i in 0 until 3) if (i != draggingShapeIndex) trayShapes[i]?.let { if (!it.placed) drawShape(canvas, it, it.cx, it.cy, trayCellSize) }
        draggingShape?.let { drawShape(canvas, it, it.cx, it.cy, cellSize) }

        if(!isGameOver && !showResumePopup && !isWaitingForAd && !isLevelComplete) {
            val freeShuffles = prefs.getInt("FreeShuffles", 0)
            val shuffleText = if (freeShuffles > 0) "🔀 FREE" else "🔀 50"
            draw3DButton(canvas, shuffleBtnRect, shuffleText, 0xFF9D4EDD.toInt(), 0xFF4A00E0.toInt(), 40f)
        }

        val iteratorWords = floatingWords.iterator()
        while(iteratorWords.hasNext()) {
            val fw = iteratorWords.next()
            if (fw.scale < 1f) fw.scale += 0.05f; fw.y -= 3f; fw.alpha -= 0.02f
            canvas.save(); canvas.scale(fw.scale, fw.scale, width/2f, fw.y)
            drawGlossy3DText(canvas, fw.text, width/2f, fw.y, 0xFF42E5FF.toInt(), 0xFF0055FF.toInt(), 100f)
            canvas.restore(); if (fw.alpha <= 0) iteratorWords.remove()
        }

        if (particles.isNotEmpty()) {
            val iterator = particles.iterator(); val pPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            while (iterator.hasNext()) {
                val p = iterator.next(); pPaint.color = p.color; pPaint.alpha = (p.life * 255).toInt().coerceIn(0, 255)
                canvas.drawCircle(p.x, p.y, cellSize * 0.15f * p.life, pPaint)
                p.x += p.vx; p.y += p.vy; p.vy += 1.5f; p.life -= 0.03f
                if (p.life <= 0) iterator.remove()
            }
        }

        if (flyingGems.isNotEmpty()) {
            val iterator = flyingGems.iterator()
            while (iterator.hasNext()) {
                val gem = iterator.next(); gem.progress += 0.04f 
                if (gem.progress >= 1f) {
                    gemsCollected[gem.type] = (gemsCollected[gem.type] ?: 0) + 1
                    var allCompleted = true
                    targetGems.forEach { (t, target) -> if ((gemsCollected[t] ?: 0) < target) allCompleted = false }
                    if (allCompleted && !isLevelComplete) { isLevelComplete = true; earnedCoins = Random.nextInt(10, 21); addCoins(earnedCoins); soundManager.playVictory() }
                    soundManager.playPick(); iterator.remove()
                } else {
                    val tIndex = typesList.indexOf(gem.type)
                    val tX = spacing * (tIndex + 1) - 25f
                    val currentX = gem.startX + (tX - gem.startX) * gem.progress
                    val currentY = gem.startY + (130f - gem.startY) * gem.progress
                    drawGemShape(canvas, currentX, currentY, cellSize * 0.8f * (1f - gem.progress * 0.3f), gem.type)
                }
            }
        }

        if (showResumePopup) {
            canvas.drawColor(0xEE000000.toInt())
            drawGlossy3DText(canvas, "GAME SAVED", width / 2f, boardY + boardSize / 2f - 160f, 0xFF42E5FF.toInt(), 0xFF0055FF.toInt(), 80f)
            draw3DButton(canvas, resumeBtnRect, "RESUME", 0xFF2CD04E.toInt(), 0xFF147A29.toInt())
            draw3DButton(canvas, newGameBtnRect, "NEW GAME", 0xFFFF5E62.toInt(), 0xFF8B0000.toInt())
        } else if (isLevelComplete && flyingGems.isEmpty()) {
            canvas.drawColor(0xDD000000.toInt())
            drawGlossy3DText(canvas, "VICTORY!", width / 2f, boardY + boardSize / 2f - 100f, 0xFF38EF7D.toInt(), 0xFF0B6623.toInt(), 110f)
            drawCoinIcon(canvas, width/2f - 100f, boardY + boardSize / 2f - 40f, 35f)
            drawGlossy3DText(canvas, "+$earnedCoins", width / 2f + 20f, boardY + boardSize / 2f, Color.YELLOW, Color.DKGRAY, 80f, Paint.Align.LEFT)
            draw3DButton(canvas, restartBtnRect, "NEXT LEVEL", 0xFF2CD04E.toInt(), 0xFF147A29.toInt())
            draw3DButton(canvas, menuBtnRect, "MAIN MENU", 0xFFFFA500.toInt(), 0xFFB87333.toInt())
        } else if (isWaitingForAd) {
            canvas.drawColor(0xDD000000.toInt())
            drawGlossy3DText(canvas, "OUT OF MOVES", width / 2f, boardY + boardSize / 2f - 80f, 0xFFFF5E62.toInt(), 0xFF8B0000.toInt(), 90f)
            drawGlossy3DText(canvas, "$adCountdown", width / 2f, boardY + boardSize / 2f + 60f, Color.WHITE, Color.DKGRAY, 150f)
            draw3DButton(canvas, menuBtnRect, "▶ WATCH AD (1 CHANCE)", 0xFF42E5FF.toInt(), 0xFF0055FF.toInt(), 40f)
        } else if (isGameOver) {
            canvas.drawColor(0xDD000000.toInt())
            drawGlossy3DText(canvas, "GAME OVER", width / 2f, boardY + boardSize / 2f - 120f, 0xFFFF5E62.toInt(), 0xFF8B0000.toInt(), 110f)
            draw3DButton(canvas, restartBtnRect, "RESTART", 0xFFFF5E62.toInt(), 0xFF8B0000.toInt())
            draw3DButton(canvas, menuBtnRect, "MAIN MENU", 0xFFFFA500.toInt(), 0xFFB87333.toInt())
        }
    }

    private fun drawShape(canvas: Canvas, shape: Shape, x: Float, y: Float, size: Float) {
        for (r in 0 until shape.rows) for (c in 0 until shape.cols) if (shape.matrix[r][c] != 0) drawGlassy3DBlock(canvas, x + c * size, y + r * size, size, shape.matrix[r][c])
    }

    private fun drawNeonShadow(canvas: Canvas, shape: Shape, x: Float, y: Float, size: Float) {
        var firstColorId = 0
        for (row in shape.matrix) { for (cell in row) { if (cell != 0) { firstColorId = cell; break } }; if (firstColorId != 0) break }
        var neonColor = getBaseColor(if (firstColorId >= 10) firstColorId - 9 else firstColorId)
        if (firstColorId >= 10) neonColor = Color.YELLOW 
        neonShadowPaint.color = neonColor; neonShadowPaint.setShadowLayer(25f, 0f, 0f, neonColor)
        for (r in 0 until shape.rows) for (c in 0 until shape.cols) if (shape.matrix[r][c] != 0) canvas.drawRoundRect(RectF(x + c * size + 4, y + r * size + 4, x + c * size + size - 4, y + r * size + size - 4), 12f, 12f, neonShadowPaint)
    }

    private fun drawGlassy3DBlock(canvas: Canvas, x: Float, y: Float, size: Float, colorId: Int) {
        val rect = RectF(x + 2, y + 2, x + size - 2, y + size - 2)
        val baseColor = getBaseColor(if (colorId >= 10) (colorId - 9) else colorId)
        val grad = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, intArrayOf(adjustColorLightness(baseColor, 1.4f), baseColor, adjustColorLightness(baseColor, 0.6f)), null, Shader.TileMode.CLAMP)
        blockBasePaint.shader = grad; canvas.drawRoundRect(rect, 16f, 16f, blockBasePaint); blockBasePaint.shader = null 
        val overlayRect = RectF(rect.left + 2, rect.top + 2, rect.right - 2, rect.top + size * 0.4f)
        val shineGrad = LinearGradient(overlayRect.left, overlayRect.top, overlayRect.left, overlayRect.bottom, 0x88FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        glassOverlayPaint.shader = shineGrad; canvas.drawRoundRect(overlayRect, 14f, 14f, glassOverlayPaint)
        if (colorId >= 10) drawGemShape(canvas, x + size * 0.15f, y + size * 0.15f, size * 0.7f, colorId)
    }

    private fun adjustColorLightness(color: Int, factor: Float): Int { val hsv = FloatArray(3); Color.colorToHSV(color, hsv); hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f); return Color.HSVToColor(hsv) }

    private fun drawGemShape(canvas: Canvas, x: Float, y: Float, size: Float, type: Int) {
        val cx = x + size / 2f; val cy = y + size / 2f; val path = Path()
        var colors = intArrayOf(0xFFFFFFA0.toInt(), 0xFFFFD700.toInt(), 0xFFE65C00.toInt())
        when(type) {
            11 -> { colors = intArrayOf(0xFFD4F1F9.toInt(), 0xFF00E5FF.toInt(), 0xFF0055FF.toInt()); path.moveTo(cx, y); path.lineTo(x + size, cy); path.lineTo(cx, y + size); path.lineTo(x, cy); path.close() }
            12 -> { colors = intArrayOf(0xFFF9D4F1.toInt(), 0xFFFF00FF.toInt(), 0xFF8B008B.toInt()); for (i in 0 until 6) { val angle = i * (Math.PI / 3); val px = cx + cos(angle).toFloat() * (size/2f); val py = cy + sin(angle).toFloat() * (size/2f); if (i == 0) path.moveTo(px, py) else path.lineTo(px, py) }; path.close() }
            13 -> { colors = intArrayOf(0xFFFFB6C1.toInt(), 0xFFFF0040.toInt(), 0xFF8B0000.toInt()); path.moveTo(cx, y + size/4); path.cubicTo(x, y - size/4, x - size/2, cy, cx, y + size); path.moveTo(cx, y + size/4); path.cubicTo(x + size, y - size/4, x + size + size/2, cy, cx, y + size) }
            14 -> { colors = intArrayOf(0xFFD4F9D4.toInt(), 0xFF00FF00.toInt(), 0xFF008000.toInt()); path.moveTo(cx, y + size * 0.1f); path.lineTo(x + size * 0.9f, y + size * 0.9f); path.lineTo(x + size * 0.1f, y + size * 0.9f); path.close() }
            15 -> { colors = intArrayOf(0xFFFFE4B5.toInt(), 0xFFFFA500.toInt(), 0xFFFF4500.toInt()); for (i in 0 until 5) { val angle = i * (Math.PI * 2 / 5) - (Math.PI / 2); val px = cx + cos(angle).toFloat() * (size / 2f); val py = cy + sin(angle).toFloat() * (size / 2f); if (i == 0) path.moveTo(px, py) else path.lineTo(px, py) }; path.close() }
            16 -> { colors = intArrayOf(0xFFFFFFFF.toInt(), 0xFFC0C0C0.toInt(), 0xFF808080.toInt()); path.addCircle(cx, cy, size / 2.2f, Path.Direction.CW) }
        }
        val depthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000.toInt(); style = Paint.Style.FILL }
        canvas.save(); canvas.translate(0f, size * 0.1f); canvas.drawPath(path, depthPaint); canvas.restore() 
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(cx, cy, size/2f, colors, null, Shader.TileMode.CLAMP); style = Paint.Style.FILL }
        canvas.drawPath(path, starPaint)
        val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAAFFFFFF.toInt(); style = Paint.Style.FILL }
        canvas.drawOval(cx - size*0.2f, cy - size*0.4f, cx + size*0.2f, cy - size*0.1f, glossPaint)
    }

    private fun getBaseColor(id: Int): Int = when (id) { 1 -> 0xFFE63946.toInt(); 2 -> 0xFF00B4D8.toInt(); 3 -> 0xFF2DC653.toInt(); 4 -> 0xFFFFB703.toInt(); 5 -> 0xFF9D4EDD.toInt(); else -> 0xFFFFFFFF.toInt() }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tx = event.x; val ty = event.y
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (showResumePopup) {
                if (resumeBtnRect.contains(tx, ty)) { soundManager.playBtnClick(); loadGame(); showResumePopup = false; return true }
                if (newGameBtnRect.contains(tx, ty)) { soundManager.playBtnClick(); prefs.edit().putBoolean("AdvSaved", false).apply(); showResumePopup = false; initLevel(); return true }
                return true
            }
            if (!isGameOver && !showResumePopup && !isWaitingForAd && !isLevelComplete && shuffleBtnRect.contains(tx, ty)) {
                if (useCoinsOrFreeShuffle()) { soundManager.playBtnClick(); for(i in 0 until 3) trayShapes[i] = null; fillTray() } 
                return true
            }
            if (isLevelComplete) {
                if (restartBtnRect.contains(tx, ty)) { soundManager.playBtnClick(); currentLevel++; prefs.edit().putInt("CurrentPlayingLevel", currentLevel).apply(); if (currentLevel > maxLevel) { maxLevel = currentLevel; prefs.edit().putInt("MaxAdventureLevel", maxLevel).apply() }; initLevel(); return true }
                if (menuBtnRect.contains(tx, ty)) { 
                    soundManager.playBtnClick(); currentLevel++
                    prefs.edit().putInt("CurrentPlayingLevel", currentLevel).apply()
                    if (currentLevel > maxLevel) { maxLevel = currentLevel; prefs.edit().putInt("MaxAdventureLevel", maxLevel).apply() }
                    prefs.edit().putBoolean("AdvSaved", false).apply()
                    (context as Activity).finish()
                    return true 
                }
            }
            if (isWaitingForAd && menuBtnRect.contains(tx, ty)) { 
                soundManager.playBtnClick(); soundManager.stopCountdownTick(); handler.removeCallbacks(timerRunnable)
                (context as Activity).let { activity ->
                    AdManager.showRewardAd(activity) { rewarded ->
                        if (rewarded) {
                            isWaitingForAd = false; for(r in 5..7) for(c in 0 until 8) grid[r][c] = 0
                            trayShapes[0] = Shape(arrayOf(intArrayOf(1))); trayShapes[1] = null; trayShapes[2] = null
                            updateTrayPositions(); invalidate()
                        } else { isWaitingForAd = false; isGameOver = true; soundManager.playGameOver(); invalidate() }
                    }
                }
                return true
            }
            if (isGameOver) {
                if (restartBtnRect.contains(tx, ty)) { soundManager.playBtnClick(); initLevel(); return true }
                if (menuBtnRect.contains(tx, ty)) { prefs.edit().putBoolean("AdvSaved", false).apply(); (context as Activity).finish(); return true }
            }
        }
        if (isGameOver || isLevelComplete || isWaitingForAd || showResumePopup) return true

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                for (i in 0 until 3) trayShapes[i]?.let { if (!it.placed && RectF(it.cx - 30f, it.cy - 30f, it.cx + (it.cols * trayCellSize) + 30f, it.cy + (it.rows * trayCellSize) + 30f).contains(tx, ty)) {
                    soundManager.playPick(); draggingShapeIndex = i; draggingShape = it
                    it.cx = tx - (it.cols * cellSize) / 2f; it.cy = ty - (it.rows * cellSize) - 180f
                    dragTouchOffsetX = tx - it.cx; dragTouchOffsetY = ty - it.cy; return true
                }}
            }
            MotionEvent.ACTION_MOVE -> {
                draggingShape?.let { 
                    it.cx = tx - dragTouchOffsetX; it.cy = ty - dragTouchOffsetY
                    hoverCol = ((it.cx + (it.cols * cellSize)/2f - boardX) / cellSize - it.cols/2f).roundToInt()
                    hoverRow = ((it.cy + (it.rows * cellSize)/2f - boardY) / cellSize - it.rows/2f).roundToInt()
                    canFitHover = canPlaceShape(it, hoverRow, hoverCol); return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingShape?.let { 
                    if (canFitHover && hoverRow in 0..7 && hoverCol in 0..7) {
                        placeShape(it, hoverRow, hoverCol); it.placed = true; soundManager.playDrop()
                        if (trayShapes.all { s -> s == null || s.placed }) fillTray() else checkGameOverCondition()
                    } else updateTrayPositions()
                    draggingShape = null; draggingShapeIndex = -1; hoverRow = -1; hoverCol = -1; canFitHover = false; return true
                }
            }
        }
        return true
    }

    private fun canPlaceShape(shape: Shape, rOff: Int, cOff: Int): Boolean { for (r in 0 until shape.rows) for (c in 0 until shape.cols) if (shape.matrix[r][c] != 0 && (rOff + r !in 0..7 || cOff + c !in 0..7 || grid[rOff + r][cOff + c] != 0)) return false; return true }

    private fun placeShape(shape: Shape, rOff: Int, cOff: Int) { for (r in 0 until shape.rows) for (c in 0 until shape.cols) if (shape.matrix[r][c] != 0) grid[rOff + r][cOff + c] = shape.matrix[r][c]; checkLines() }

    private fun checkLines() {
        val rows = mutableListOf<Int>(); val cols = mutableListOf<Int>()
        for (r in 0 until 8) if ((0 until 8).all { c -> grid[r][c] != 0 }) rows.add(r)
        for (c in 0 until 8) if ((0 until 8).all { r -> grid[r][c] != 0 }) cols.add(c)
        val total = rows.size + cols.size

        if (total > 0) {
            soundManager.playClear(); vibratePhone(100L) 
            handler.postDelayed({ val word = soundManager.playComboVoice(total); floatingWords.add(FloatingWord(word, boardY + boardSize/2f)) }, 900)

            for (r in rows) { glowLines.add(GlowLine(true, r)); for (c in 0 until 8) { val id = grid[r][c]; if (id>=10) flyingGems.add(FlyingGem(boardX+c*cellSize, boardY+r*cellSize, id)); grid[r][c] = 0 } }
            for (c in cols) { glowLines.add(GlowLine(false, c)); for (r in 0 until 8) { val id = grid[r][c]; if (id>=10) flyingGems.add(FlyingGem(boardX+c*cellSize, boardY+r*cellSize, id)); grid[r][c] = 0 } }
        }
    }

    private fun checkGameOverCondition() {
        if (isLevelComplete) return
        var canMakeMove = false
        for (shape in trayShapes) if (shape != null && !shape.placed) { for (r in 0 until 8) for (c in 0 until 8) if (canPlaceShape(shape, r, c)) { canMakeMove = true; break }; if (canMakeMove) break }
        if (!canMakeMove) { isWaitingForAd = true; adCountdown = 10; handler.post(timerRunnable); invalidate() }
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); saveGame(); handler.removeCallbacks(renderLoop); handler.removeCallbacks(timerRunnable); soundManager.release() }
}
