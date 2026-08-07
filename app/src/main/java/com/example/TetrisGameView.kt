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
import kotlin.random.Random

class TetrisGameView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {

    val soundManager = SoundManager(context)
    private val prefs = context.getSharedPreferences("BoxBlastPrefs", Context.MODE_PRIVATE)
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val handler = Handler(Looper.getMainLooper())
    
    private val COLS = 10; private val ROWS = 20
    private val grid = Array(ROWS) { IntArray(COLS) { 0 } }
    
    private var score = 0; private var speedMs = 600L
    private var highScore = prefs.getInt("TetrisHighScore", 0) 
    
    private var isGameOver = false; private var isWaitingForAd = false; private var adCountdown = 10 
    private var isNewHighScore = false; private var hsRewardGiven = false

    private var showResumePopup = false
    private val resumeBtnRect = RectF(); private val newGameBtnRect = RectF()

    data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Int)
    private val particles = mutableListOf<Particle>()
    data class Confetti(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Int, var size: Float, var rot: Float, var rotSpeed: Float)
    private val confettis = mutableListOf<Confetti>()
    data class GlowLine(val isRow: Boolean, val index: Int, var alpha: Float = 1f)
    private val glowLines = mutableListOf<GlowLine>()
    data class FloatingWord(val text: String, var y: Float, var alpha: Float = 1f, var scale: Float = 0.5f)
    private val floatingWords = mutableListOf<FloatingWord>()

    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAA0B132B.toInt(); style = Paint.Style.FILL }
    private val boardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF42E5FF.toInt(); style = Paint.Style.STROKE; strokeWidth = 8f }
    private val blockBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glassOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val emptyPaint = Paint().apply { color = 0x2AFFFFFF; style = Paint.Style.STROKE; strokeWidth = 2f }
    private val text3DPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.CYAN; style = Paint.Style.FILL; setShadowLayer(30f, 0f, 0f, Color.WHITE) }

    private var cellSize = 0f; private var boardSizeW = 0f; private var boardSizeH = 0f
    private var boardX = 0f; private var boardY = 0f

    private val btnLeft = RectF(); private val btnRotate = RectF()
    private val btnDown = RectF(); private val btnRight = RectF()
    private val restartBtnRect = RectF(); private val menuBtnRect = RectF()

    val SHAPES = listOf(
        arrayOf(intArrayOf(1, 1, 1, 1)), arrayOf(intArrayOf(1, 1), intArrayOf(1, 1)), arrayOf(intArrayOf(0, 1, 0), intArrayOf(1, 1, 1)),
        arrayOf(intArrayOf(1, 0, 0), intArrayOf(1, 1, 1)), arrayOf(intArrayOf(0, 0, 1), intArrayOf(1, 1, 1)),
        arrayOf(intArrayOf(0, 1, 1), intArrayOf(1, 1, 0)), arrayOf(intArrayOf(1, 1, 0), intArrayOf(0, 1, 1))
    )

    class Tetromino(var matrix: Array<IntArray>, val colorId: Int) { var x = 3; var y = 0 }
    private var currentPiece: Tetromino? = null; private var nextPiece: Tetromino? = null

    private val gameLoop = object : Runnable { override fun run() { if (!isGameOver && !isWaitingForAd && !showResumePopup) { moveDown(); invalidate(); handler.postDelayed(this, speedMs) } } }
    private val renderLoop = object : Runnable { override fun run() { invalidate(); handler.postDelayed(this, 16L) } }
    
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isWaitingForAd && adCountdown > 0) {
                soundManager.playCountdownTick(); adCountdown--
                if (adCountdown == 0) { isWaitingForAd = false; isGameOver = true; soundManager.stopCountdownTick(); soundManager.playGameOver(); checkAndTriggerConfetti() } 
                else handler.postDelayed(this, 1000L)
                invalidate()
            }
        }
    }

    init { 
        if (prefs.getBoolean("TetrisSaved", false)) showResumePopup = true else restartGame()
        handler.post(renderLoop) 
    }

    private fun vibratePhone(duration: Long = 50L) { try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") vibrator.vibrate(duration) } catch (e: Exception) { } }

    private fun restartGame() {
        for (r in 0 until ROWS) for (c in 0 until COLS) grid[r][c] = 0
        score = 0; speedMs = 600L; isGameOver = false; isWaitingForAd = false; adCountdown = 10; isNewHighScore = false; hsRewardGiven = false
        particles.clear(); glowLines.clear(); floatingWords.clear(); confettis.clear()
        prefs.edit().putBoolean("TetrisSaved", false).putBoolean("TetrisHSReward", false).apply()
        nextPiece = generatePiece(); spawnPiece(); handler.removeCallbacks(gameLoop); handler.postDelayed(gameLoop, speedMs)
    }

    private fun loadGame() {
        try {
            score = prefs.getInt("TetrisScore", 0); hsRewardGiven = prefs.getBoolean("TetrisHSReward", false)
            val gridStr = prefs.getString("TetrisGrid", "")
            if (!gridStr.isNullOrEmpty()) { val rows = gridStr.split(";"); for (r in 0 until ROWS) { val cols = rows[r].split(","); if(cols.size >= COLS){ for (c in 0 until COLS) grid[r][c] = cols[c].toInt() } } }
        } catch (e: Exception) { restartGame(); return }
        nextPiece = generatePiece(); spawnPiece(); handler.removeCallbacks(gameLoop); handler.postDelayed(gameLoop, speedMs)
    }

    private fun saveGame() {
        if (isGameOver || showResumePopup || isWaitingForAd) return
        prefs.edit().putBoolean("TetrisSaved", true).putInt("TetrisScore", score).putBoolean("TetrisHSReward", hsRewardGiven).putString("TetrisGrid", grid.joinToString(";") { it.joinToString(",") }).apply()
    }

    private fun generatePiece(): Tetromino { val matrix = SHAPES[Random.nextInt(SHAPES.size)]; return Tetromino(Array(matrix.size) { r -> IntArray(matrix[r].size) { c -> matrix[r][c] } }, Random.nextInt(1, 6)) }

    private fun spawnPiece() {
        currentPiece = nextPiece; nextPiece = generatePiece()
        if (!isValidPosition(currentPiece!!.matrix, currentPiece!!.x, currentPiece!!.y)) {
            isWaitingForAd = true; adCountdown = 10; handler.post(timerRunnable); invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padding = w * 0.15f; boardSizeW = w - padding * 2; cellSize = boardSizeW / COLS
        boardSizeH = cellSize * ROWS; boardX = padding; boardY = 220f
        val controlCenterY = boardY + boardSizeH + 180f; val cx = w / 2f
        val btnSize = 150f; val gap = 25f
        btnRotate.set(cx - btnSize/2, controlCenterY - btnSize - gap, cx + btnSize/2, controlCenterY - gap)
        btnLeft.set(cx - btnSize - btnSize/2 - gap, controlCenterY, cx - btnSize/2 - gap, controlCenterY + btnSize)
        btnDown.set(cx - btnSize/2, controlCenterY, cx + btnSize/2, controlCenterY + btnSize)
        btnRight.set(cx + btnSize/2 + gap, controlCenterY, cx + btnSize + btnSize/2 + gap, controlCenterY + btnSize)
        
        val bw = 600f; val bh = 130f
        restartBtnRect.set(cx - bw/2f, boardY + boardSizeH/2f - 40f, cx + bw/2f, boardY + boardSizeH/2f - 40f + bh)
        menuBtnRect.set(cx - bw/2f, restartBtnRect.bottom + 30f, cx + bw/2f, restartBtnRect.bottom + 30f + bh)
        resumeBtnRect.set(cx - bw/2f, boardY + boardSizeH/2f - 60f, cx + bw/2f, boardY + boardSizeH/2f - 60f + bh)
        newGameBtnRect.set(cx - bw/2f, resumeBtnRect.bottom + 40f, cx + bw/2f, resumeBtnRect.bottom + 40f + bh)
    }

    // CLAYMATION TEXT
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

    // MISSING FUNCTION FIXED HERE!
    private fun draw3DControlButton(canvas: Canvas, rect: RectF, text: String, topColor: Int, bottomColor: Int) {
        btnPaint.color = bottomColor
        canvas.drawRoundRect(RectF(rect.left, rect.top + 15f, rect.right, rect.bottom + 15f), 40f, 40f, btnPaint)
        btnPaint.color = topColor
        canvas.drawRoundRect(rect, 40f, 40f, btnPaint)
        drawGlossy3DText(canvas, text, rect.centerX(), rect.centerY() + rect.height() * 0.15f, Color.WHITE, Color.DKGRAY, rect.height() * 0.45f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.TRANSPARENT)

        val topY = 90f
        drawGlossy3DText(canvas, "SCORE", boardX + 20f, topY, 0xFFFFD700.toInt(), 0xFF8B6508.toInt(), 30f, Paint.Align.LEFT)
        drawGlossy3DText(canvas, "$score", boardX + 20f, topY + 40f, Color.WHITE, Color.DKGRAY, 40f, Paint.Align.LEFT)
        
        drawGlossy3DText(canvas, "BEST", width/2f, topY, 0xFF42E5FF.toInt(), 0xFF0055FF.toInt(), 30f, Paint.Align.CENTER)
        drawGlossy3DText(canvas, "$highScore", width/2f, topY + 40f, Color.WHITE, Color.DKGRAY, 40f, Paint.Align.CENTER)
        
        val nextTitleX = boardX + boardSizeW - 20f
        drawGlossy3DText(canvas, "NEXT", nextTitleX, topY, 0xFF42E5FF.toInt(), 0xFF0055FF.toInt(), 30f, Paint.Align.RIGHT)
        nextPiece?.let { piece ->
            val previewSize = cellSize * 0.45f
            for (r in 0 until piece.matrix.size) for (c in 0 until piece.matrix[0].size) 
                if (piece.matrix[r][c] != 0) {
                    val px = nextTitleX - (piece.matrix[0].size * previewSize) + c * previewSize
                    val py = topY + 15f + r * previewSize
                    drawGlassy3DBlock(canvas, px, py, previewSize, piece.colorId)
                }
        }

        val currentCoins = prefs.getInt("BoxBlastCoins", 0)
        drawCoinIcon(canvas, width/2f - 30f, 50f, 15f)
        drawGlossy3DText(canvas, "$currentCoins", width/2f + 5f, 60f, Color.YELLOW, Color.DKGRAY, 30f, Paint.Align.LEFT)

        val rect = RectF(boardX, boardY, boardX + boardSizeW, boardY + boardSizeH)
        canvas.drawRoundRect(rect, 20f, 20f, boardPaint)
        canvas.drawRoundRect(rect, 20f, 20f, boardBorderPaint)

        val iteratorGlow = glowLines.iterator()
        while(iteratorGlow.hasNext()) {
            val glow = iteratorGlow.next(); glowPaint.alpha = (glow.alpha * 200).toInt()
            if (glow.isRow) canvas.drawRoundRect(RectF(boardX, boardY + glow.index * cellSize, boardX + boardSizeW, boardY + (glow.index+1)*cellSize), 12f, 12f, glowPaint)
            glow.alpha -= 0.05f; if (glow.alpha <= 0) iteratorGlow.remove()
        }

        val emptyPaint = Paint().apply { color = 0x2AFFFFFF; style = Paint.Style.STROKE; strokeWidth = 2f }
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

        if (showResumePopup) {
            canvas.drawColor(0xEE000000.toInt())
            drawGlossy3DText(canvas, "GAME SAVED", width / 2f, boardY + boardSizeH / 2f - 160f, 0xFF42E5FF.toInt(), 0xFF0055FF.toInt(), 80f)
            draw3DButton(canvas, resumeBtnRect, "RESUME", 0xFF2CD04E.toInt(), 0xFF147A29.toInt())
            draw3DButton(canvas, newGameBtnRect, "NEW GAME", 0xFFFF5E62.toInt(), 0xFF8B0000.toInt())
        } else if (isWaitingForAd) {
            canvas.drawColor(0xDD000000.toInt())
            drawGlossy3DText(canvas, "NO MOVES", width / 2f, boardY + boardSizeH / 2f - 80f, 0xFFFF5E62.toInt(), 0xFF8B0000.toInt(), 90f)
            drawGlossy3DText(canvas, "$adCountdown", width / 2f, boardY + boardSizeH / 2f + 60f, Color.WHITE, Color.DKGRAY, 150f)
            draw3DButton(canvas, menuBtnRect, "▶ WATCH AD (1 CHANCE)", 0xFF42E5FF.toInt(), 0xFF0055FF.toInt(), 40f)
        } else if (isGameOver) {
            canvas.drawColor(0xEE000000.toInt())
            if (isNewHighScore) {
                text3DPaint.textSize = 150f; canvas.drawText("👑", width/2f, boardY - 50f, text3DPaint)
                drawGlossy3DText(canvas, "NEW BEST!", width / 2f, boardY + 60f, 0xFFFFD700.toInt(), 0xFF8B6508.toInt(), 100f)
            } else { drawGlossy3DText(canvas, "GAME OVER", width / 2f, boardY + boardSizeH / 2f - 120f, 0xFFFF5E62.toInt(), 0xFF8B0000.toInt(), 110f) }
            draw3DButton(canvas, restartBtnRect, "RESTART", 0xFFFF5E62.toInt(), 0xFF8B0000.toInt())
            draw3DButton(canvas, menuBtnRect, "MAIN MENU", 0xFFFFA500.toInt(), 0xFFB87333.toInt())
            if (isNewHighScore) { val pPaint = Paint(Paint.ANTI_ALIAS_FLAG); for(c in confettis) { pPaint.color = c.color; canvas.save(); canvas.translate(c.x, c.y); canvas.rotate(c.rot); canvas.drawRect(-c.size, -c.size, c.size, c.size, pPaint); canvas.restore(); c.x += c.vx; c.y += c.vy; c.vy += 0.5f; c.rot += c.rotSpeed } }
        }
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

    private fun adjustColorLightness(color: Int, factor: Float): Int { val hsv = FloatArray(3); Color.colorToHSV(color, hsv); hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f); return Color.HSVToColor(hsv) }
    private fun getBaseColor(id: Int): Int = when (id) { 1 -> 0xFFE63946.toInt(); 2 -> 0xFF00B4D8.toInt(); 3 -> 0xFF2DC653.toInt(); 4 -> 0xFFFFB703.toInt(); 5 -> 0xFF9D4EDD.toInt(); else -> 0xFFFFFFFF.toInt() }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        val tx = event.x; val ty = event.y

        if (showResumePopup) {
            if (resumeBtnRect.contains(tx, ty)) { soundManager.playBtnClick(); loadGame(); showResumePopup = false; return true }
            if (newGameBtnRect.contains(tx, ty)) { soundManager.playBtnClick(); prefs.edit().putBoolean("TetrisSaved", false).apply(); showResumePopup = false; restartGame(); return true }
            return true
        }

        if (isWaitingForAd && menuBtnRect.contains(tx, ty)) {
            soundManager.playBtnClick(); soundManager.stopCountdownTick(); handler.removeCallbacks(timerRunnable)
            (context as Activity).let { activity ->
                AdManager.showRewardAd(activity) { rewarded ->
                    if (rewarded) {
                        isWaitingForAd = false; for (r in ROWS - 4 until ROWS) for (c in 0 until COLS) grid[r][c] = 0
                        spawnPiece(); handler.postDelayed(gameLoop, speedMs); invalidate()
                    } else { isWaitingForAd = false; isGameOver = true; soundManager.playGameOver(); checkAndTriggerConfetti(); invalidate() }
                }
            }
            return true
        }

        if (isGameOver) {
            if (restartBtnRect.contains(tx, ty)) { soundManager.playBtnClick(); restartGame(); return true }
            if (menuBtnRect.contains(tx, ty)) { 
                soundManager.playBtnClick()
                prefs.edit().putBoolean("TetrisSaved", false).apply()
                (context as Activity).finish(); return true 
            }
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
            soundManager.playClear(); vibratePhone(150L); 
            handler.postDelayed({ val word = soundManager.playComboVoice(linesCleared); floatingWords.add(FloatingWord(word, boardY + boardSizeH/2f)) }, 900)
            
            score += (linesCleared * 100) * linesCleared; speedMs = maxOf(150L, speedMs - 20L)
            
            for (cr in clearedRows) {
                glowLines.add(GlowLine(true, cr))
                val blastY = boardY + cr * cellSize + cellSize/2f
                for (c in 0 until COLS) {
                    val blastX = boardX + c * cellSize + cellSize/2f
                    for(i in 0..6) particles.add(Particle(blastX, blastY, Random.nextFloat()*16-8f, Random.nextFloat()*20-15f, 1f, getBaseColor(Random.nextInt(1,6))))
                }
            }
            if (score > highScore) { 
                highScore = score; prefs.edit().putInt("TetrisHighScore", highScore).apply() 
                if (!hsRewardGiven) {
                    hsRewardGiven = true
                    prefs.edit().putInt("FreeShuffles", prefs.getInt("FreeShuffles", 0) + 1).apply()
                    floatingWords.add(FloatingWord("FREE SHUFFLE!", boardY + boardSizeH/2f + 80f)) 
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

    private fun checkAndTriggerConfetti() {
        val lastHS = prefs.getInt("LastTetrisHS", 0)
        if (score > lastHS && score == highScore && score > 0) {
            isNewHighScore = true; prefs.edit().putInt("LastTetrisHS", score).apply()
            val colors = listOf(Color.RED, Color.GREEN, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.WHITE)
            for (i in 0..150) confettis.add(Confetti(width/2f, boardY + 50f, Random.nextFloat()*30f-15f, Random.nextFloat() * -30f - 10f, colors.random(), Random.nextFloat()*15f+10f, Random.nextFloat()*360f, Random.nextFloat()*20f-10f))
        }
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); saveGame(); handler.removeCallbacks(gameLoop); handler.removeCallbacks(renderLoop); handler.removeCallbacks(timerRunnable); soundManager.release() }
}
