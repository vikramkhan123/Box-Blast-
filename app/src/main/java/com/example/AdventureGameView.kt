package com.example

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
    private var targetGems = 10 + (currentLevel * 2) 
    private var gemsCollected = 0
    private var isLevelComplete = false
    
    private var isWaitingForAd = false
    private var adCountdown = 5
    private var isGameOver = false

    data class FlyingGem(var startX: Float, var startY: Float, var type: Int, var progress: Float = 0f)
    private val flyingGems = mutableListOf<FlyingGem>()
    
    data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Int)
    private val particles = mutableListOf<Particle>()

    private val bgPaint = Paint().apply { style = Paint.Style.FILL }
    private val neonBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAA0B132B.toInt(); style = Paint.Style.FILL }
    private val boardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF42E5FF.toInt(); style = Paint.Style.STROKE; strokeWidth = 8f; setShadowLayer(15f, 0f, 0f, 0xFF42E5FF.toInt()) }
    
    private val blockBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glassOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val neonShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f }
    
    private val text3DPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 90f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    
    private var cellSize = 0f; private var boardSize = 0f; private var boardX = 0f; private var boardY = 0f
    private var trayY = 0f; private var trayCellSize = 0f
    private var targetUiX = 0f; private var targetUiY = 120f
    private val centerBtnRect = RectF()

    val SHAPES = listOf(
        arrayOf(intArrayOf(1)), arrayOf(intArrayOf(1, 1)), arrayOf(intArrayOf(1), intArrayOf(1)),
        arrayOf(intArrayOf(1, 1), intArrayOf(1, 1)), arrayOf(intArrayOf(1, 1, 1)),
        arrayOf(intArrayOf(1), intArrayOf(1), intArrayOf(1)), arrayOf(intArrayOf(1, 1, 1, 1)),
        arrayOf(intArrayOf(1, 0), intArrayOf(1, 1)), arrayOf(intArrayOf(0, 1), intArrayOf(1, 1)), 
        arrayOf(intArrayOf(1, 1), intArrayOf(1, 0)), arrayOf(intArrayOf(1, 1), intArrayOf(0, 1))
    )

    class Shape(val matrix: Array<IntArray>) {
        val rows = matrix.size; val cols = matrix[0].size
        var cx = 0f; var cy = 0f; var placed = false
    }

    private val trayShapes = arrayOfNulls<Shape>(3)
    private var draggingShapeIndex = -1; private var draggingShape: Shape? = null
    private var dragTouchOffsetX = 0f; private var dragTouchOffsetY = 0f
    private var hoverRow = -1; private var hoverCol = -1; private var canFitHover = false

    private val renderLoop = object : Runnable { override fun run() { invalidate(); handler.postDelayed(this, 16L) } }

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isWaitingForAd && adCountdown > 0) {
                adCountdown--
                soundManager.playCountdownTick()
                if (adCountdown == 0) { isWaitingForAd = false; isGameOver = true; soundManager.playGameOver() } else handler.postDelayed(this, 1000L)
            }
        }
    }

    init { initLevel(); handler.post(renderLoop) }

    private fun vibratePhone(duration: Long = 50L) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") vibrator.vibrate(duration)
        } catch (e: Exception) { }
    }

    private fun getAvailableGemTypes(): List<Int> {
        val types = mutableListOf(10) // Star
        if (currentLevel >= 3) types.add(11) // Diamond
        if (currentLevel >= 6) types.add(12) // Hexagon
        if (currentLevel >= 10) types.add(13) // Heart
        return types
    }

    private fun initLevel() {
        targetGems = 10 + (currentLevel * 2)
        gemsCollected = 0; isGameOver = false; isLevelComplete = false; isWaitingForAd = false; adCountdown = 5
        flyingGems.clear(); particles.clear()
        for (r in 0 until 8) for (c in 0 until 8) grid[r][c] = 0
        var spawned = 0
        val initialGems = minOf(targetGems, 12); val gemTypes = getAvailableGemTypes()
        while(spawned < initialGems) {
            val r = Random.nextInt(8); val c = Random.nextInt(8)
            if (grid[r][c] == 0) { grid[r][c] = gemTypes.random(); spawned++ }
        }
        for (i in 0 until 3) trayShapes[i] = null
        fillTray()
    }

    private fun fillTray() {
        for (i in 0 until 3) {
            if (trayShapes[i] == null || trayShapes[i]!!.placed) {
                val rawMatrix = SHAPES[Random.nextInt(SHAPES.size)]
                val colorId = Random.nextInt(1, 6)
                val copy = Array(rawMatrix.size) { r -> IntArray(rawMatrix[r].size) { c -> if (rawMatrix[r][c] == 1) colorId else 0 } }
                if (gemsCollected < targetGems && Random.nextFloat() < 0.35f) {
                    val validCoords = mutableListOf<Pair<Int, Int>>()
                    for (r in copy.indices) for (c in copy[0].indices) if (copy[r][c] != 0) validCoords.add(Pair(r, c))
                    if (validCoords.isNotEmpty()) { val (gr, gc) = validCoords.random(); copy[gr][gc] = getAvailableGemTypes().random() }
                }
                trayShapes[i] = Shape(copy)
            }
        }
        if (width > 0 && height > 0) updateTrayPositions()
        checkGameOverCondition()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padding = 30f 
        boardSize = w - padding * 2; cellSize = boardSize / 8; boardX = padding; boardY = padding + 250f
        trayY = boardY + boardSize + 100f; trayCellSize = cellSize * 0.65f; targetUiX = w / 2f
        val bw = 600f; val bh = 140f
        centerBtnRect.set(w/2f - bw/2f, boardY + boardSize/2f + 80f, w/2f + bw/2f, boardY + boardSize/2f + 80f + bh)
        updateTrayPositions()
    }

    private fun updateTrayPositions() {
        if (width == 0) return
        val sectionWidth = width / 3f
        for (i in 0 until 3) trayShapes[i]?.let { if (!it.placed) { it.cx = (i * sectionWidth) + (sectionWidth - (it.cols * trayCellSize)) / 2f; it.cy = trayY + (sectionWidth - (it.rows * trayCellSize)) / 2f } }
    }

    private fun drawGeminiBackground(canvas: Canvas) {
        canvas.drawColor(0xFF0F172A.toInt()) 
        val time = System.currentTimeMillis()
        neonBgPaint.shader = RadialGradient(width / 2f + Math.sin(time / 2000.0).toFloat() * 250f, height / 3f + Math.cos(time / 1500.0).toFloat() * 250f, 800f, intArrayOf(0x66E94560, 0x00E94560), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), neonBgPaint)
        neonBgPaint.shader = RadialGradient(width / 2f + Math.cos(time / 1800.0).toFloat() * 300f, height / 1.5f + Math.sin(time / 2200.0).toFloat() * 300f, 900f, intArrayOf(0x660F80FF, 0x000F80FF), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), neonBgPaint)
    }

    private fun draw3DText(canvas: Canvas, text: String, x: Float, y: Float, mainColor: Int, depthColor: Int, size: Float) {
        text3DPaint.textSize = size; text3DPaint.color = depthColor
        for (i in 1..6) canvas.drawText(text, x, y + i * 2, text3DPaint)
        text3DPaint.color = mainColor; canvas.drawText(text, x, y, text3DPaint)
    }

    private fun draw3DButton(canvas: Canvas, rect: RectF, text: String, topColor: Int, bottomColor: Int) {
        btnPaint.color = bottomColor
        val shadowRect = RectF(rect.left, rect.top + 15f, rect.right, rect.bottom + 15f)
        canvas.drawRoundRect(shadowRect, 30f, 30f, btnPaint)
        btnPaint.color = topColor
        canvas.drawRoundRect(rect, 30f, 30f, btnPaint)
        text3DPaint.textSize = 45f; text3DPaint.color = Color.WHITE
        canvas.drawText(text, rect.centerX(), rect.centerY() + 15f, text3DPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawGeminiBackground(canvas)

        draw3DText(canvas, "LEVEL $currentLevel", width/2f, 100f, 0xFFFFD700.toInt(), 0xFF8B6508.toInt(), 70f)
        drawGemShape(canvas, targetUiX - 80f, targetUiY - 40f, 60f, 10)
        draw3DText(canvas, "$gemsCollected / $targetGems", targetUiX + 30f, targetUiY + 15f, Color.WHITE, Color.DKGRAY, 65f)

        val rect = RectF(boardX, boardY, boardX + boardSize, boardY + boardSize)
        canvas.drawRoundRect(rect, 24f, 24f, boardPaint)
        canvas.drawRoundRect(rect, 24f, 24f, boardBorderPaint)

        val emptyPaint = Paint().apply { color = 0x2AFFFFFF; style = Paint.Style.STROKE; strokeWidth = 2f }
        for (r in 0 until 8) for (c in 0 until 8) {
            val cx = boardX + c * cellSize; val cy = boardY + r * cellSize
            canvas.drawRoundRect(RectF(cx + 4, cy + 4, cx + cellSize - 4, cy + cellSize - 4), 12f, 12f, emptyPaint)
            if (grid[r][c] != 0) drawGlassy3DBlock(canvas, cx, cy, cellSize, grid[r][c])
        }

        draggingShape?.let { if (canFitHover) drawNeonShadow(canvas, it, boardX + hoverCol * cellSize, boardY + hoverRow * cellSize, cellSize) }
        for (i in 0 until 3) if (i != draggingShapeIndex) trayShapes[i]?.let { if (!it.placed) drawShape(canvas, it, it.cx, it.cy, trayCellSize) }
        draggingShape?.let { drawShape(canvas, it, it.cx, it.cy, cellSize) }

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
                    gemsCollected++
                    if (gemsCollected >= targetGems && !isLevelComplete) { isLevelComplete = true; soundManager.playVictory() }
                    soundManager.playPick(); iterator.remove()
                } else {
                    val currentX = gem.startX + (targetUiX - 80f - gem.startX) * gem.progress
                    val currentY = gem.startY + (targetUiY - 40f - gem.startY) * gem.progress
                    drawGemShape(canvas, currentX, currentY, cellSize * 0.8f * (1f - gem.progress * 0.3f), gem.type)
                }
            }
        }

        if (isLevelComplete && flyingGems.isEmpty()) {
            canvas.drawColor(0xDD000000.toInt())
            draw3DText(canvas, "VICTORY!", width / 2f, boardY + boardSize / 2f - 40f, 0xFF38EF7D.toInt(), 0xFF0B6623.toInt(), 110f)
            draw3DButton(canvas, centerBtnRect, "NEXT LEVEL", 0xFF2CD04E.toInt(), 0xFF147A29.toInt())
        } else if (isWaitingForAd) {
            canvas.drawColor(0xDD000000.toInt())
            draw3DText(canvas, "OUT OF MOVES", width / 2f, boardY + boardSize / 2f - 120f, 0xFFFF5E62.toInt(), 0xFF8B0000.toInt(), 90f)
            draw3DText(canvas, "$adCountdown", width / 2f, boardY + boardSize / 2f, Color.WHITE, Color.DKGRAY, 150f)
            draw3DButton(canvas, centerBtnRect, "▶ WATCH AD (1 CHANCE)", 0xFF42E5FF.toInt(), 0xFF0055FF.toInt())
        } else if (isGameOver) {
            canvas.drawColor(0xDD000000.toInt())
            draw3DText(canvas, "GAME OVER", width / 2f, boardY + boardSize / 2f - 40f, 0xFFFF5E62.toInt(), 0xFF8B0000.toInt(), 110f)
            draw3DButton(canvas, centerBtnRect, "RESTART", 0xFFFF5E62.toInt(), 0xFF8B0000.toInt())
        }
    }

    private fun drawShape(canvas: Canvas, shape: Shape, x: Float, y: Float, size: Float) {
        for (r in 0 until shape.rows) for (c in 0 until shape.cols) if (shape.matrix[r][c] != 0) drawGlassy3DBlock(canvas, x + c * size, y + r * size, size, shape.matrix[r][c])
    }

    private fun drawNeonShadow(canvas: Canvas, shape: Shape, x: Float, y: Float, size: Float) {
        var firstColorId = 0
        for (row in shape.matrix) { for (cell in row) { if (cell != 0) { firstColorId = cell; break } }; if (firstColorId != 0) break }
        var neonColor = getBaseColor(if (firstColorId != 0) firstColorId else 1)
        if (firstColorId >= 10) neonColor = Color.YELLOW 
        neonShadowPaint.color = neonColor; neonShadowPaint.setShadowLayer(25f, 0f, 0f, neonColor)
        for (r in 0 until shape.rows) for (c in 0 until shape.cols) if (shape.matrix[r][c] != 0) canvas.drawRoundRect(RectF(x + c * size + 4, y + r * size + 4, x + c * size + size - 4, y + r * size + size - 4), 12f, 12f, neonShadowPaint)
    }

    private fun drawGlassy3DBlock(canvas: Canvas, x: Float, y: Float, size: Float, colorId: Int) {
        val rect = RectF(x + 2, y + 2, x + size - 2, y + size - 2)
        if (colorId >= 10) {
            blockBasePaint.color = 0xFF0D152B.toInt(); canvas.drawRoundRect(rect, 16f, 16f, blockBasePaint)
            val innerRect = RectF(rect.left + 8f, rect.top + 8f, rect.right - 8f, rect.bottom - 8f)
            blockBasePaint.color = 0xFF050A1A.toInt(); canvas.drawRoundRect(innerRect, 8f, 8f, blockBasePaint)
            drawGemShape(canvas, x + size * 0.15f, y + size * 0.15f, size * 0.7f, colorId); return
        }
        val baseColor = getBaseColor(colorId)
        val grad = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, intArrayOf(adjustColorLightness(baseColor, 1.4f), baseColor, adjustColorLightness(baseColor, 0.6f)), null, Shader.TileMode.CLAMP)
        blockBasePaint.shader = grad; canvas.drawRoundRect(rect, 16f, 16f, blockBasePaint); blockBasePaint.shader = null 
        val overlayRect = RectF(rect.left + 2, rect.top + 2, rect.right - 2, rect.top + size * 0.4f)
        val shineGrad = LinearGradient(overlayRect.left, overlayRect.top, overlayRect.left, overlayRect.bottom, 0x88FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        glassOverlayPaint.shader = shineGrad; canvas.drawRoundRect(overlayRect, 14f, 14f, glassOverlayPaint)
    }

    private fun adjustColorLightness(color: Int, factor: Float): Int {
        val hsv = FloatArray(3); Color.colorToHSV(color, hsv); hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f); return Color.HSVToColor(hsv)
    }

    private fun drawGemShape(canvas: Canvas, x: Float, y: Float, size: Float, type: Int) {
        val cx = x + size / 2f; val cy = y + size / 2f; val path = Path()
        var colors = intArrayOf(0xFFFFFFA0.toInt(), 0xFFFFD700.toInt(), 0xFFE65C00.toInt())
        when(type) {
            10 -> {
                val outR = size / 2f; val inR = outR / 2.2f
                for (i in 0 until 10) {
                    val angle = i * (Math.PI / 5) - (Math.PI / 2); val r = if (i % 2 == 0) outR else inR
                    if (i == 0) path.moveTo(cx + cos(angle).toFloat() * r, cy + sin(angle).toFloat() * r) else path.lineTo(cx + cos(angle).toFloat() * r, cy + sin(angle).toFloat() * r)
                }
                path.close()
            }
            11 -> {
                colors = intArrayOf(0xFFD4F1F9.toInt(), 0xFF00E5FF.toInt(), 0xFF0055FF.toInt())
                path.moveTo(cx, y); path.lineTo(x + size, cy); path.lineTo(cx, y + size); path.lineTo(x, cy); path.close()
            }
            12 -> {
                colors = intArrayOf(0xFFF9D4F1.toInt(), 0xFFFF00FF.toInt(), 0xFF8B008B.toInt())
                for (i in 0 until 6) {
                    val angle = i * (Math.PI / 3); val px = cx + cos(angle).toFloat() * (size/2f); val py = cy + sin(angle).toFloat() * (size/2f)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }
            13 -> {
                colors = intArrayOf(0xFFFFB6C1.toInt(), 0xFFFF0040.toInt(), 0xFF8B0000.toInt())
                path.moveTo(cx, y + size/4); path.cubicTo(x, y - size/4, x - size/2, cy, cx, y + size)
                path.moveTo(cx, y + size/4); path.cubicTo(x + size, y - size/4, x + size + size/2, cy, cx, y + size)
            }
        }
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(cx, cy, size/2f, colors, null, Shader.TileMode.CLAMP); style = Paint.Style.FILL }
        canvas.drawPath(path, starPaint); starPaint.shader = null; starPaint.color = 0xAAFFFFFF.toInt()
        canvas.drawCircle(cx - size*0.15f, cy - size*0.15f, size*0.1f, starPaint)
    }

    private fun getBaseColor(id: Int): Int = when (id) { 1 -> 0xFFE63946.toInt(); 2 -> 0xFF00B4D8.toInt(); 3 -> 0xFF2DC653.toInt(); 4 -> 0xFFFFB703.toInt(); 5 -> 0xFF9D4EDD.toInt(); else -> 0xFFFFFFFF.toInt() }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tx = event.x; val ty = event.y
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (isLevelComplete && centerBtnRect.contains(tx, ty)) {
                soundManager.playBtnClick(); currentLevel++; prefs.edit().putInt("CurrentPlayingLevel", currentLevel).apply()
                if (currentLevel > maxLevel) { maxLevel = currentLevel; prefs.edit().putInt("MaxAdventureLevel", maxLevel).apply() }
                initLevel(); return true
            }
            if (isWaitingForAd && centerBtnRect.contains(tx, ty)) {
                soundManager.playBtnClick(); handler.removeCallbacks(timerRunnable)
                (context as android.app.Activity).let { activity ->
                    AdManager.showRewardAd(activity) { rewarded ->
                        if (rewarded) {
                            isWaitingForAd = false; trayShapes[0] = Shape(arrayOf(intArrayOf(1))); trayShapes[1] = null; trayShapes[2] = null
                            updateTrayPositions(); invalidate()
                        } else { isWaitingForAd = false; isGameOver = true; invalidate() }
                    }
                }
                return true
            }
            if (isGameOver && centerBtnRect.contains(tx, ty)) { soundManager.playBtnClick(); initLevel(); return true }
        }
        if (isGameOver || isLevelComplete || isWaitingForAd) return true

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

    private fun canPlaceShape(shape: Shape, rOff: Int, cOff: Int): Boolean {
        for (r in 0 until shape.rows) for (c in 0 until shape.cols) if (shape.matrix[r][c] != 0 && (rOff + r !in 0..7 || cOff + c !in 0..7 || grid[rOff + r][cOff + c] != 0)) return false
        return true
    }

    private fun placeShape(shape: Shape, rOff: Int, cOff: Int) {
        for (r in 0 until shape.rows) for (c in 0 until shape.cols) if (shape.matrix[r][c] != 0) grid[rOff + r][cOff + c] = shape.matrix[r][c]
        checkLines()
    }

    private fun checkLines() {
        val rows = mutableListOf<Int>(); val cols = mutableListOf<Int>()
        for (r in 0 until 8) if ((0 until 8).all { c -> grid[r][c] != 0 }) rows.add(r)
        for (c in 0 until 8) if ((0 until 8).all { r -> grid[r][c] != 0 }) cols.add(c)
        val total = rows.size + cols.size

        if (total > 0) {
            soundManager.playClear(); vibratePhone(100L) 
            handler.postDelayed({ soundManager.playComboVoice(total) }, 600)
            for (r in rows) for (c in 0 until 8) {
                val colorId = grid[r][c]; val bX = boardX + c * cellSize + cellSize/2f; val bY = boardY + r * cellSize + cellSize/2f
                for(i in 0..5) particles.add(Particle(bX, bY, Random.nextFloat()*16-8f, Random.nextFloat()*16-12f, 1f, getBaseColor(colorId)))
                if (colorId >= 10) flyingGems.add(FlyingGem(bX - cellSize/2f, bY - cellSize/2f, colorId))
                grid[r][c] = 0
            }
            for (c in cols) for (r in 0 until 8) {
                val colorId = grid[r][c]
                if (colorId != 0) { 
                    val bX = boardX + c * cellSize + cellSize/2f; val bY = boardY + r * cellSize + cellSize/2f
                    for(i in 0..5) particles.add(Particle(bX, bY, Random.nextFloat()*16-8f, Random.nextFloat()*16-12f, 1f, getBaseColor(colorId)))
                    if (colorId >= 10) flyingGems.add(FlyingGem(bX - cellSize/2f, bY - cellSize/2f, colorId))
                    grid[r][c] = 0
                }
            }
        }
    }

    private fun checkGameOverCondition() {
        if (isLevelComplete) return
        var canMakeMove = false
        for (shape in trayShapes) if (shape != null && !shape.placed) {
            for (r in 0 until 8) for (c in 0 until 8) if (canPlaceShape(shape, r, c)) { canMakeMove = true; break }
            if (canMakeMove) break
        }
        if (!canMakeMove) { 
            soundManager.playGameOver()
            handler.postDelayed({
                isWaitingForAd = true; adCountdown = 5; handler.post(timerRunnable); invalidate()
            }, 1500) 
        }
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); handler.removeCallbacks(renderLoop); handler.removeCallbacks(timerRunnable); soundManager.release() }
}
