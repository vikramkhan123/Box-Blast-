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
import kotlin.math.roundToInt
import kotlin.random.Random

class GameView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {

    val soundManager = SoundManager(context)
    private val prefs = context.getSharedPreferences("BoxBlastPrefs", Context.MODE_PRIVATE)
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val handler = Handler(Looper.getMainLooper())
    private val grid = Array(8) { IntArray(8) { 0 } }
    
    private var score = 0
    private var highScore = prefs.getInt("ClassicHighScore", 0)
    
    private var isGameOver = false; private var isWaitingForAd = false; private var adCountdown = 10 

    data class GlowLine(val isRow: Boolean, val index: Int, var alpha: Float = 1f)
    private val glowLines = mutableListOf<GlowLine>()

    data class FloatingWord(val text: String, var y: Float, var alpha: Float = 1f, var scale: Float = 0.5f)
    private val floatingWords = mutableListOf<FloatingWord>()

    private val bgPaint = Paint().apply { style = Paint.Style.FILL }
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAA0B132B.toInt(); style = Paint.Style.FILL }
    private val boardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF42E5FF.toInt(); style = Paint.Style.STROKE; strokeWidth = 8f; setShadowLayer(15f, 0f, 0f, 0xFF42E5FF.toInt()) }
    private val blockBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glassOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val neonShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f }
    private val text3DPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 90f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.CYAN; style = Paint.Style.FILL; setShadowLayer(30f, 0f, 0f, Color.WHITE) }

    private var cellSize = 0f; private var boardSize = 0f; private var boardX = 0f; private var boardY = 0f
    private var trayY = 0f; private var trayCellSize = 0f
    
    private val restartBtnRect = RectF()
    private val menuBtnRect = RectF()

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

    init { restartGame(); handler.post(renderLoop) }

    private fun vibratePhone(duration: Long = 50L) { try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") vibrator.vibrate(duration) } catch (e: Exception) { } }

    private fun restartGame() {
        for (r in 0 until 8) for (c in 0 until 8) grid[r][c] = 0
        score = 0; isGameOver = false; isWaitingForAd = false; adCountdown = 10
        glowLines.clear(); floatingWords.clear()
        for (i in 0 until 3) trayShapes[i] = null
        fillTray()
    }

    private fun fillTray() {
        for (i in 0 until 3) {
            if (trayShapes[i] == null || trayShapes[i]!!.placed) {
                var safeShape: Shape? = null
                for (attempt in 0..20) {
                    val rawMatrix = SHAPES[Random.nextInt(SHAPES.size)]
                    val testShape = Shape(Array(rawMatrix.size) { r -> IntArray(rawMatrix[r].size) { c -> if (rawMatrix[r][c] == 1) Random.nextInt(1, 6) else 0 } })
                    if (canFitAnywhere(testShape)) { safeShape = testShape; break }
                }
                if (safeShape == null) safeShape = Shape(arrayOf(intArrayOf(Random.nextInt(1, 6))))
                trayShapes[i] = safeShape
            }
        }
        if (width > 0 && height > 0) updateTrayPositions()
        checkGameOverCondition()
    }

    private fun canFitAnywhere(shape: Shape): Boolean {
        for (r in 0 until 8) for (c in 0 until 8) if (canPlaceShape(shape, r, c)) return true
        return false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        boardSize = w - 60f; cellSize = boardSize / 8; boardX = 30f; boardY = 250f
        trayY = boardY + boardSize + 100f; trayCellSize = cellSize * 0.65f
        val bw = 600f; val bh = 130f
        val cx = w/2f
        restartBtnRect.set(cx - bw/2f, boardY + boardSize/2f - 40f, cx + bw/2f, boardY + boardSize/2f - 40f + bh)
        menuBtnRect.set(cx - bw/2f, restartBtnRect.bottom + 30f, cx + bw/2f, restartBtnRect.bottom + 30f + bh)
        updateTrayPositions()
    }

    private fun updateTrayPositions() {
        if (width == 0) return
        val sectionWidth = width / 3f
        for (i in 0 until 3) trayShapes[i]?.let { if (!it.placed) { it.cx = (i * sectionWidth) + (sectionWidth - (it.cols * trayCellSize)) / 2f; it.cy = trayY + (sectionWidth - (it.rows * trayCellSize)) / 2f } }
    }

    private fun draw3DText(canvas: Canvas, text: String, x: Float, y: Float, mainColor: Int, depthColor: Int, size: Float) {
        text3DPaint.textSize = size; text3DPaint.color = depthColor
        for (i in 1..6) canvas.drawText(text, x, y + i * 2, text3DPaint)
        text3DPaint.color = mainColor; canvas.drawText(text, x, y, text3DPaint)
    }

    private fun draw3DButton(canvas: Canvas, rect: RectF, text: String, topColor: Int, bottomColor: Int) {
        btnPaint.color = bottomColor
        canvas.drawRoundRect(RectF(rect.left, rect.top + 15f, rect.right, rect.bottom + 15f), 30f, 30f, btnPaint)
        btnPaint.color = topColor
        canvas.drawRoundRect(rect, 30f, 30f, btnPaint)
        text3DPaint.textSize = 45f; text3DPaint.color = Color.WHITE
        canvas.drawText(text, rect.centerX(), rect.centerY() + 15f, text3DPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF0F172A.toInt()) 
        
        draw3DText(canvas, "SCORE", width / 4f, 100f, 0xFFFFD700.toInt(), 0xFF8B6508.toInt(), 60f)
        draw3DText(canvas, "$score", width / 4f, 160f, Color.WHITE, Color.DKGRAY, 80f)
        draw3DText(canvas, "BEST", (width / 4f) * 3f, 100f, 0xFF42E5FF.toInt(), 0xFF0055FF.toInt(), 60f)
        draw3DText(canvas, "$highScore", (width / 4f) * 3f, 160f, Color.WHITE, Color.DKGRAY, 80f)

        val rect = RectF(boardX, boardY, boardX + boardSize, boardY + boardSize)
        canvas.drawRoundRect(rect, 24f, 24f, boardPaint)
        canvas.drawRoundRect(rect, 24f, 24f, boardBorderPaint)

        // Draw Glow Lines
        val iteratorGlow = glowLines.iterator()
        while(iteratorGlow.hasNext()) {
            val glow = iteratorGlow.next()
            glowPaint.alpha = (glow.alpha * 200).toInt()
            if (glow.isRow) canvas.drawRoundRect(RectF(boardX, boardY + glow.index * cellSize, boardX + boardSize, boardY + (glow.index+1)*cellSize), 12f, 12f, glowPaint)
            else canvas.drawRoundRect(RectF(boardX + glow.index * cellSize, boardY, boardX + (glow.index+1)*cellSize, boardY + boardSize), 12f, 12f, glowPaint)
            glow.alpha -= 0.05f
            if (glow.alpha <= 0) iteratorGlow.remove()
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

        // Draw Floating Words
        val iteratorWords = floatingWords.iterator()
        while(iteratorWords.hasNext()) {
            val fw = iteratorWords.next()
            if (fw.scale < 1f) fw.scale += 0.05f
            fw.y -= 3f; fw.alpha -= 0.02f
            canvas.save(); canvas.scale(fw.scale, fw.scale, width/2f, fw.y)
            draw3DText(canvas, fw.text, width/2f, fw.y, 0xFF42E5FF.toInt(), 0xFF0055FF.toInt(), 100f)
            canvas.restore()
            if (fw.alpha <= 0) iteratorWords.remove()
        }

        if (isWaitingForAd) {
            canvas.drawColor(0xDD000000.toInt())
            draw3DText(canvas, "OUT OF MOVES", width / 2f, boardY + boardSize / 2f - 80f, 0xFFFF5E62.toInt(), 0xFF8B0000.toInt(), 90f)
            draw3DText(canvas, "$adCountdown", width / 2f, boardY + boardSize / 2f + 40f, Color.WHITE, Color.DKGRAY, 150f)
            draw3DButton(canvas, menuBtnRect, "▶ WATCH AD (1 CHANCE)", 0xFF42E5FF.toInt(), 0xFF0055FF.toInt())
        } else if (isGameOver) {
            canvas.drawColor(0xDD000000.toInt())
            draw3DText(canvas, "GAME OVER", width / 2f, boardY + boardSize / 2f - 120f, 0xFFFF5E62.toInt(), 0xFF8B0000.toInt(), 110f)
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
        val neonColor = getBaseColor(if (firstColorId != 0) firstColorId else 1)
        neonShadowPaint.color = neonColor; neonShadowPaint.setShadowLayer(25f, 0f, 0f, neonColor)
        for (r in 0 until shape.rows) for (c in 0 until shape.cols) if (shape.matrix[r][c] != 0) canvas.drawRoundRect(RectF(x + c * size + 4, y + r * size + 4, x + c * size + size - 4, y + r * size + size - 4), 12f, 12f, neonShadowPaint)
    }

    private fun drawGlassy3DBlock(canvas: Canvas, x: Float, y: Float, size: Float, colorId: Int) {
        val rect = RectF(x + 2, y + 2, x + size - 2, y + size - 2)
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

    private fun getBaseColor(id: Int): Int = when (id) { 1 -> 0xFFE63946.toInt(); 2 -> 0xFF00B4D8.toInt(); 3 -> 0xFF2DC653.toInt(); 4 -> 0xFFFFB703.toInt(); 5 -> 0xFF9D4EDD.toInt(); 6 -> 0xFF00FF00.toInt(); 7 -> 0xFFFFA500.toInt(); else -> 0xFFFFFFFF.toInt() }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tx = event.x; val ty = event.y

        if (event.action == MotionEvent.ACTION_DOWN) {
            if (isWaitingForAd && menuBtnRect.contains(tx, ty)) {
                soundManager.playBtnClick(); soundManager.stopCountdownTick(); handler.removeCallbacks(timerRunnable)
                (context as Activity).let { activity ->
                    AdManager.showRewardAd(activity) { rewarded ->
                        if (rewarded) {
                            isWaitingForAd = false
                            for(r in 5..7) for(c in 0 until 8) grid[r][c] = 0 // Clear 3 lines
                            trayShapes[0] = Shape(arrayOf(intArrayOf(1))); trayShapes[1] = null; trayShapes[2] = null
                            updateTrayPositions(); invalidate()
                        } else { isWaitingForAd = false; isGameOver = true; soundManager.playGameOver(); invalidate() }
                    }
                }
                return true
            }
            if (isGameOver) {
                if (restartBtnRect.contains(tx, ty)) { soundManager.playBtnClick(); restartGame(); return true }
                if (menuBtnRect.contains(tx, ty)) { (context as Activity).finish(); return true }
            }
        }
        if (isGameOver || isWaitingForAd) return true

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
        var blocks = 0
        for (r in 0 until shape.rows) for (c in 0 until shape.cols) if (shape.matrix[r][c] != 0) { grid[rOff + r][cOff + c] = shape.matrix[r][c]; blocks++ }
        score += blocks * 10; checkLines()
    }

    private fun checkLines() {
        val rows = mutableListOf<Int>(); val cols = mutableListOf<Int>()
        for (r in 0 until 8) if ((0 until 8).all { c -> grid[r][c] != 0 }) rows.add(r)
        for (c in 0 until 8) if ((0 until 8).all { r -> grid[r][c] != 0 }) cols.add(c)
        val total = rows.size + cols.size

        if (total > 0) {
            soundManager.playClear(); vibratePhone(100L)
            handler.postDelayed({ soundManager.playComboVoice(total) }, 600)
            
            val msg = when(total) { 1 -> "GOOD!"; 2 -> "SUPER!"; 3 -> "EXCELLENT!"; else -> "MAGNIFICENT!" }
            floatingWords.add(FloatingWord(msg, boardY + boardSize/2f))

            for (r in rows) { glowLines.add(GlowLine(true, r)); for (c in 0 until 8) { grid[r][c] = 0 }; score += 100 }
            for (c in cols) { glowLines.add(GlowLine(false, c)); for (r in 0 until 8) { grid[r][c] = 0 }; score += 100 }
            
            if (score > highScore) { highScore = score; prefs.edit().putInt("ClassicHighScore", highScore).apply() }
        }
    }

    private fun checkGameOverCondition() {
        var canMakeMove = false
        for (shape in trayShapes) if (shape != null && !shape.placed) {
            for (r in 0 until 8) for (c in 0 until 8) if (canPlaceShape(shape, r, c)) { canMakeMove = true; break }
            if (canMakeMove) break
        }
        if (!canMakeMove) { isWaitingForAd = true; adCountdown = 10; handler.post(timerRunnable); invalidate() }
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); handler.removeCallbacks(renderLoop); handler.removeCallbacks(timerRunnable); soundManager.release() }
}
