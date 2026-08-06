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
import kotlin.random.Random

class TetrisGameView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {

    val soundManager = SoundManager(context)
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val handler = Handler(Looper.getMainLooper())
    
    private val COLS = 10; private val ROWS = 20
    private val grid = Array(ROWS) { IntArray(COLS) { 0 } }
    
    private var score = 0; private var speedMs = 600L
    private var isGameOver = false; private var isWaitingForAd = false; private var adCountdown = 5

    data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Int)
    private val particles = mutableListOf<Particle>()

    private val neonBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAA0B132B.toInt(); style = Paint.Style.FILL }
    private val boardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF42E5FF.toInt(); style = Paint.Style.STROKE; strokeWidth = 8f; setShadowLayer(15f, 0f, 0f, 0xFF42E5FF.toInt()) }
    
    private val blockBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glassOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val emptyPaint = Paint().apply { color = 0x2AFFFFFF; style = Paint.Style.STROKE; strokeWidth = 2f }

    private val text3DPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var cellSize = 0f; private var boardSizeW = 0f; private var boardSizeH = 0f
    private var boardX = 0f; private var boardY = 0f

    private val btnLeft = RectF(); private val btnRotate = RectF()
    private val btnDown = RectF(); private val btnRight = RectF()
    private val centerBtnRect = RectF()

    val SHAPES = listOf(
        arrayOf(intArrayOf(1, 1, 1, 1)), arrayOf(intArrayOf(1, 1), intArrayOf(1, 1)), arrayOf(intArrayOf(0, 1, 0), intArrayOf(1, 1, 1)),
        arrayOf(intArrayOf(1, 0, 0), intArrayOf(1, 1, 1)), arrayOf(intArrayOf(0, 0, 1), intArrayOf(1, 1, 1)),
        arrayOf(intArrayOf(0, 1, 1), intArrayOf(1, 1, 0)), arrayOf(intArrayOf(1, 1, 0), intArrayOf(0, 1, 1))
    )

    class Tetromino(var matrix: Array<IntArray>, val colorId: Int) { var x = 3; var y = 0 }
    private var currentPiece: Tetromino? = null; private var nextPiece: Tetromino? = null

    private val gameLoop = object : Runnable {
        override fun run() { if (!isGameOver && !isWaitingForAd) { moveDown(); invalidate(); handler.postDelayed(this, speedMs) } }
    }
    private val renderLoop = object : Runnable { override fun run() { invalidate(); handler.postDelayed(this, 16L) } }
    
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isWaitingForAd && adCountdown > 0) {
                soundManager.playCountdownTick() // ONLY TICK TICK
                adCountdown--
                if (adCountdown == 0) { 
                    isWaitingForAd = false; isGameOver = true
                    soundManager.playGameOver() // Play game over when timer hits 0
                } else {
                    handler.postDelayed(this, 1000L)
                }
                invalidate()
            }
        }
    }

    init { nextPiece = generatePiece(); spawnPiece(); handler.postDelayed(gameLoop, speedMs); handler.post(renderLoop) }

    private fun vibratePhone(duration: Long = 50L) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") vibrator.vibrate(duration)
        } catch (e: Exception) { }
    }

    private fun generatePiece(): Tetromino {
        val matrix = SHAPES[Random.nextInt(SHAPES.size)]
        return Tetromino(Array(matrix.size) { r -> IntArray(matrix[r].size) { c -> matrix[r][c] } }, Random.nextInt(1, 6))
    }

    private fun spawnPiece() {
        currentPiece = nextPiece; nextPiece = generatePiece()
        if (!isValidPosition(currentPiece!!.matrix, currentPiece!!.x, currentPiece!!.y)) {
            // NO GAME OVER SOUND HERE, ONLY TIMER STARTS
            isWaitingForAd = true; adCountdown = 5; handler.post(timerRunnable); invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padding = w * 0.15f; boardSizeW = w - padding * 2; cellSize = boardSizeW / COLS
        boardSizeH = cellSize * ROWS; boardX = padding; boardY = 200f
        val controlCenterY = boardY + boardSizeH + 180f; val controlCenterX = w / 2f
        val btnSize = 150f; val gap = 25f
        btnRotate.set(controlCenterX - btnSize/2, controlCenterY - btnSize - gap, controlCenterX + btnSize/2, controlCenterY - gap)
        btnLeft.set(controlCenterX - btnSize - btnSize/2 - gap, controlCenterY, controlCenterX - btnSize/2 - gap, controlCenterY + btnSize)
        btnDown.set(controlCenterX - btnSize/2, controlCenterY, controlCenterX + btnSize/2, controlCenterY + btnSize)
        btnRight.set(controlCenterX + btnSize/2 + gap, controlCenterY, controlCenterX + btnSize + btnSize/2 + gap, controlCenterY + btnSize)
        centerBtnRect.set(w/2f - 300f, height/2f + 50f, w/2f + 300f, height/2f + 190f)
    }

    private fun drawGeminiBackground(canvas: Canvas) {
        canvas.drawColor(0xFF0F172A.toInt()) 
        val time = System.currentTimeMillis()
        neonBgPaint.shader = RadialGradient(width / 2f + Math.sin(time / 2000.0).toFloat() * 250f, height / 3f + Math.cos(time / 1500.0).toFloat() * 250f, 800f, intArrayOf(0x66E94560, 0x00E94560), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), neonBgPaint)
        neonBgPaint.shader = RadialGradient(width / 2f + Math.cos(time / 1800.0).toFloat() * 300f, height / 1.5f + Math.sin(time / 2200.0).toFloat() * 300f, 900f, intArrayOf(0x660F80FF, 0x000F80FF), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), neonBgPaint)
    }

    private fun draw3DText(canvas: Canvas, text: String, x: Float, y: Float, mainColor: Int, depthColor: Int, size: Float, align: Paint.Align = Paint.Align.CENTER) {
        text3DPaint.textSize = size; text3DPaint.color = depthColor; text3DPaint.textAlign = align
        for (i in 1..5) canvas.drawText(text, x, y + i * 2, text3DPaint)
        text3DPaint.color = mainColor; canvas.drawText(text, x, y, text3DPaint)
    }

    private fun draw3DButton(canvas: Canvas, rect: RectF, text: String, topColor: Int, bottomColor: Int) {
        btnPaint.color = bottomColor
        canvas.drawRoundRect(RectF(rect.left, rect.top + 15f, rect.right, rect.bottom + 15f), 30f, 30f, btnPaint)
        btnPaint.color = topColor
        canvas.drawRoundRect(rect, 30f, 30f, btnPaint)
        text3DPaint.textSize = 45f; text3DPaint.color = Color.WHITE; text3DPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, rect.centerX(), rect.centerY() + 15f, text3DPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawGeminiBackground(canvas)

        draw3DText(canvas, "SCORE", boardX + 60f, 100f, 0xFFFFD700.toInt(), 0xFF8B6508.toInt(), 50f, Paint.Align.LEFT)
        draw3DText(canvas, "$score", boardX + 60f, 160f, Color.WHITE, Color.DKGRAY, 65f, Paint.Align.LEFT)
        val nextTitleX = boardX + boardSizeW - 100f
        draw3DText(canvas, "NEXT", nextTitleX, 100f, 0xFF42E5FF.toInt(), 0xFF0055FF.toInt(), 50f)
        
        nextPiece?.let { piece ->
            val previewSize = cellSize * 0.7f
            for (r in 0 until piece.matrix.size) for (c in 0 until piece.matrix[0].size) 
                if (piece.matrix[r][c] != 0) drawGlassy3DBlock(canvas, nextTitleX - (piece.matrix[0].size*previewSize)/2f + c * previewSize, 120f + r * previewSize, previewSize, piece.colorId)
        }

        val rect = RectF(boardX, boardY, boardX + boardSizeW, boardY + boardSizeH)
        canvas.drawRoundRect(rect, 20f, 20f, boardPaint)
        canvas.drawRoundRect(rect, 20f, 20f, boardBorderPaint)

        for (r in 0 until ROWS) for (c in 0 until COLS) {
            val cx = boardX + c * cellSize; val cy = boardY + r * cellSize
            canvas.drawRoundRect(RectF(cx + 2, cy + 2, cx + cellSize - 2, cy + cellSize - 2), 8f, 8f, emptyPaint)
            if (grid[r][c] != 0) drawGlassy3DBlock(canvas, cx, cy, cellSize, grid[r][c])
        }

        currentPiece?.let { piece ->
            for (r in 0 until piece.matrix.size) for (c in 0 until piece.matrix[0].size) 
                if (piece.matrix[r][c] != 0) drawGlassy3DBlock(canvas, boardX + (piece.x + c) * cellSize, boardY + (piece.y + r) * cellSize, cellSize, piece.colorId)
        }

        draw3DControlButton(canvas, btnLeft, "◀", 0xFF9D4EDD.toInt(), 0xFF4A00E0.toInt())
        draw3DControlButton(canvas, btnRotate, "↻", 0xFF00B4D8.toInt(), 0xFF0077B6.toInt())
        draw3DControlButton(canvas, btnDown, "▼", 0xFFE63946.toInt(), 0xFF9B2226.toInt())
        draw3DControlButton(canvas, btnRight, "▶", 0xFF2DC653.toInt(), 0xFF1B4332.toInt())

        if (particles.isNotEmpty()) {
            val iterator = particles.iterator(); val pPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            while (iterator.hasNext()) {
                val p = iterator.next(); pPaint.color = p.color; pPaint.alpha = (p.life * 255).toInt().coerceIn(0, 255)
                canvas.drawCircle(p.x, p.y, cellSize * 0.15f * p.life, pPaint)
                p.x += p.vx; p.y += p.vy; p.vy += 1.5f; p.life -= 0.03f
                if (p.life <= 0) iterator.remove()
            }
        }

        if (isWaitingForAd) {
            canvas.drawColor(0xDD000000.toInt())
            draw3DText(canvas, "NO MOVES", width / 2f, height / 2f - 120f, 0xFFFF5E62.toInt(), 0xFF8B0000.toInt(), 90f)
            draw3DText(canvas, "$adCountdown", width / 2f, height / 2f, Color.WHITE, Color.DKGRAY, 150f)
            draw3DButton(canvas, centerBtnRect, "▶ WATCH AD (1 CHANCE)", 0xFF42E5FF.toInt(), 0xFF0055FF.toInt())
        } else if (isGameOver) {
            canvas.drawColor(0xDD000000.toInt())
            draw3DText(canvas, "GAME OVER", width / 2f, height / 2f - 40f, 0xFFFF5E62.toInt(), 0xFF8B0000.toInt(), 110f)
            draw3DButton(canvas, centerBtnRect, "RESTART", 0xFFFF5E62.toInt(), 0xFF8B0000.toInt())
        }
    }

    private fun draw3DControlButton(canvas: Canvas, rect: RectF, text: String, topColor: Int, bottomColor: Int) {
        btnPaint.color = bottomColor
        canvas.drawRoundRect(RectF(rect.left, rect.top + 15f, rect.right, rect.bottom + 15f), 40f, 40f, btnPaint)
        btnPaint.color = topColor
        canvas.drawRoundRect(rect, 40f, 40f, btnPaint)
        text3DPaint.textSize = rect.height() * 0.45f; text3DPaint.color = Color.WHITE
        canvas.drawText(text, rect.centerX(), rect.centerY() + (text3DPaint.textSize / 3f), text3DPaint)
    }

    private fun drawGlassy3DBlock(canvas: Canvas, x: Float, y: Float, size: Float, colorId: Int) {
        val rect = RectF(x + 2, y + 2, x + size - 2, y + size - 2)
        val baseColor = getBaseColor(colorId)
        val grad = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, intArrayOf(adjustColorLightness(baseColor, 1.4f), baseColor, adjustColorLightness(baseColor, 0.6f)), null, Shader.TileMode.CLAMP)
        blockBasePaint.shader = grad; canvas.drawRoundRect(rect, 10f, 10f, blockBasePaint); blockBasePaint.shader = null 
        val overlayRect = RectF(rect.left + 2, rect.top + 2, rect.right - 2, rect.top + size * 0.4f)
        val shineGrad = LinearGradient(overlayRect.left, overlayRect.top, overlayRect.left, overlayRect.bottom, 0x88FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        glassOverlayPaint.shader = shineGrad; canvas.drawRoundRect(overlayRect, 8f, 8f, glassOverlayPaint)
    }

    private fun adjustColorLightness(color: Int, factor: Float): Int {
        val hsv = FloatArray(3); Color.colorToHSV(color, hsv); hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f); return Color.HSVToColor(hsv)
    }
    private fun getBaseColor(id: Int): Int = when (id) { 1 -> 0xFFE63946.toInt(); 2 -> 0xFF00B4D8.toInt(); 3 -> 0xFF2DC653.toInt(); 4 -> 0xFFFFB703.toInt(); 5 -> 0xFF9D4EDD.toInt(); else -> 0xFFFFFFFF.toInt() }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        val tx = event.x; val ty = event.y

        if (isWaitingForAd && centerBtnRect.contains(tx, ty)) {
            soundManager.playBtnClick(); handler.removeCallbacks(timerRunnable)
            (context as android.app.Activity).let { activity ->
                AdManager.showRewardAd(activity) { rewarded ->
                    if (rewarded) {
                        isWaitingForAd = false
                        for (r in ROWS - 4 until ROWS) for (c in 0 until COLS) grid[r][c] = 0
                        spawnPiece(); handler.postDelayed(gameLoop, speedMs); invalidate()
                    } else { 
                        isWaitingForAd = false; isGameOver = true; soundManager.playGameOver(); invalidate() 
                    }
                }
            }
            return true
        }

        if (isGameOver) {
            if (centerBtnRect.contains(tx, ty)) {
                soundManager.playBtnClick()
                for (r in 0 until ROWS) for (c in 0 until COLS) grid[r][c] = 0
                score = 0; speedMs = 600L; isGameOver = false
                spawnPiece(); handler.postDelayed(gameLoop, speedMs); return true
            }
            return true
        }

        when {
            btnLeft.contains(tx, ty) -> { soundManager.playBtnClick(); moveLeft() }
            btnRight.contains(tx, ty) -> { soundManager.playBtnClick(); moveRight() }
            btnRotate.contains(tx, ty) -> rotatePiece()
            btnDown.contains(tx, ty) -> { soundManager.playPick(); moveDown() }
        }
        return true
    }

    private fun moveLeft() { currentPiece?.let { if (isValidPosition(it.matrix, it.x - 1, it.y)) it.x-- } }
    private fun moveRight() { currentPiece?.let { if (isValidPosition(it.matrix, it.x + 1, it.y)) it.x++ } }
    private fun rotatePiece() {
        currentPiece?.let {
            val r = it.matrix.size; val c = it.matrix[0].size; val newM = Array(c) { IntArray(r) }
            for (i in 0 until r) for (j in 0 until c) newM[j][r - 1 - i] = it.matrix[i][j]
            if (isValidPosition(newM, it.x, it.y)) { it.matrix = newM; soundManager.playPick() }
        }
    }

    private fun moveDown() { currentPiece?.let { if (isValidPosition(it.matrix, it.x, it.y + 1)) it.y++ else lockPiece() } }

    private fun lockPiece() {
        currentPiece?.let { piece ->
            soundManager.playDrop()
            for (r in 0 until piece.matrix.size) for (c in 0 until piece.matrix[0].size) 
                if (piece.matrix[r][c] != 0 && piece.y + r in 0 until ROWS && piece.x + c in 0 until COLS) grid[piece.y + r][piece.x + c] = piece.colorId
            checkLines(); spawnPiece()
        }
    }

    private fun checkLines() {
        var linesCleared = 0; var r = ROWS - 1
        val clearedRows = mutableListOf<Int>()
        while (r >= 0) {
            var isFull = true
            for (c in 0 until COLS) if (grid[r][c] == 0) { isFull = false; break }
            if (isFull) {
                linesCleared++; clearedRows.add(r)
                for (shiftR in r downTo 1) for (c in 0 until COLS) grid[shiftR][c] = grid[shiftR - 1][c]
                for (c in 0 until COLS) grid[0][c] = 0
            } else r--
        }
        if (linesCleared > 0) {
            soundManager.playClear(); vibratePhone(150L)
            handler.postDelayed({ soundManager.playComboVoice(linesCleared) }, 600)
            score += (linesCleared * 100) * linesCleared; speedMs = maxOf(150L, speedMs - 20L)
            for (cr in clearedRows) {
                val blastY = boardY + cr * cellSize + cellSize/2f
                for (c in 0 until COLS) {
                    val blastX = boardX + c * cellSize + cellSize/2f
                    for(i in 0..6) particles.add(Particle(blastX, blastY, Random.nextFloat()*16-8f, Random.nextFloat()*20-15f, 1f, getBaseColor(Random.nextInt(1,6))))
                }
            }
        }
    }

    private fun isValidPosition(matrix: Array<IntArray>, x: Int, y: Int): Boolean {
        for (r in 0 until matrix.size) for (c in 0 until matrix[0].size) if (matrix[r][c] != 0) {
            val gX = x + c; val gY = y + r
            if (gX < 0 || gX >= COLS || gY >= ROWS || (gY >= 0 && grid[gY][gX] != 0)) return false
        }
        return true
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); handler.removeCallbacks(gameLoop); handler.removeCallbacks(renderLoop); handler.removeCallbacks(timerRunnable); soundManager.release() }
}
