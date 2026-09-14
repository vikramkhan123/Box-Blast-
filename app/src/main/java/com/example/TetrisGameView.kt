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

    // 10 Light Aesthetic Color Schemes (Outer BG + Inner Board)
    data class ColorTheme(val bg: Int, val boardBg: Int, val boardBorder: Int, val textPrimary: Int, val textSecondary: Int)
    private val COLOR_THEMES = listOf(
        ColorTheme(0xFFE8EEF5.toInt(), 0xFFFAFCFF.toInt(), 0xFFC9D8E6.toInt(), 0xFF1E293B.toInt(), 0xFF64748B.toInt()),
        ColorTheme(0xFFF7ECE1.toInt(), 0xFFFFFDF9.toInt(), 0xFFE6D2C0.toInt(), 0xFF4A3525.toInt(), 0xFF8C715A.toInt()),
        ColorTheme(0xFFE9F5ED.toInt(), 0xFFFBFFFC.toInt(), 0xFFCDE4D4.toInt(), 0xFF1B4332.toInt(), 0xFF52796F.toInt()),
        ColorTheme(0xFFF5EBF7.toInt(), 0xFFFEFAFF.toInt(), 0xFFE2CCE6.toInt(), 0xFF3C1642.toInt(), 0xFF7B5080.toInt()),
        ColorTheme(0xFFFDF0ED.toInt(), 0xFFFFF9F8.toInt(), 0xFFF2D1CA.toInt(), 0xFF4A1E17.toInt(), 0xFF8C5B53.toInt()),
        ColorTheme(0xFFE6F3F7.toInt(), 0xFFF7FDFF.toInt(), 0xFFC3DFE8.toInt(), 0xFF0F3443.toInt(), 0xFF4A7282.toInt()),
        ColorTheme(0xFFF9F7E8.toInt(), 0xFFFFFFFA.toInt(), 0xFFEAE5BE.toInt(), 0xFF3D3A1B.toInt(), 0xFF7D774D.toInt()),
        ColorTheme(0xFFECEEF8.toInt(), 0xFFFBFCFF.toInt(), 0xFFCCD1EB.toInt(), 0xFF1D2447.toInt(), 0xFF565F87.toInt()),
        ColorTheme(0xFFF3F1EC.toInt(), 0xFFFAF9F6.toInt(), 0xFFD8D3C5.toInt(), 0xFF363228.toInt(), 0xFF6E685B.toInt()),
        ColorTheme(0xFFE5F5F3.toInt(), 0xFFF5FFFE.toInt(), 0xFFBFE5E0.toInt(), 0xFF0D3B36.toInt(), 0xFF467570.toInt())
    )
    private var activePaletteIndex = 0

    // User Image Bitmaps Cache
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val tetrisBitmaps = mutableMapOf<Int, Bitmap?>()

    enum class BlastType { LIGHTNING, MELT, POP, BROKEN, BURN, COKE }

    data class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float, 
        var life: Float, val color: Int, val type: BlastType, 
        var size: Float, var rotation: Float = 0f
    )
    private val particles = mutableListOf<Particle>()
    data class Confetti(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Int, var size: Float, var rot: Float, var rotSpeed: Float)
    private val confettis = mutableListOf<Confetti>()
    data class GlowLine(val isRow: Boolean, val index: Int, var alpha: Float = 1f)
    private val glowLines = mutableListOf<GlowLine>()
    data class FloatingWord(val text: String, var y: Float, var alpha: Float = 1f, var scale: Float = 0.5f)
    private val floatingWords = mutableListOf<FloatingWord>()

    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val boardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 8f }
    private val text3DPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.CYAN; style = Paint.Style.FILL; setShadowLayer(30f, 0f, 0f, Color.WHITE) }

    private var cellSize = 0f; private var boardSizeW = 0f; private var boardSizeH = 0f
    private var boardX = 0f; private var boardY = 0f

    private val btnLeft = RectF(); private val btnRotate = RectF()
    private val btnDown = RectF(); private val btnRight = RectF()
    private val restartBtnRect = RectF(); private val menuBtnRect = RectF()

    val SHAPES = listOf(
        arrayOf(intArrayOf(1, 1, 1, 1)), 
        arrayOf(intArrayOf(1), intArrayOf(1), intArrayOf(1), intArrayOf(1)), 
        arrayOf(intArrayOf(1, 1), intArrayOf(1, 1)), 
        arrayOf(intArrayOf(0, 1, 0), intArrayOf(1, 1, 1)),
        arrayOf(intArrayOf(1, 0, 0), intArrayOf(1, 1, 1)), 
        arrayOf(intArrayOf(0, 0, 1), intArrayOf(1, 1, 1)),
        arrayOf(intArrayOf(0, 1, 1), intArrayOf(1, 1, 0)), 
        arrayOf(intArrayOf(1, 1, 0), intArrayOf(0, 1, 1))
    )

    class Tetromino(var matrix: Array<IntArray>, val imageId: Int) { var x = 3; var y = 0 }
    private var currentPiece: Tetromino? = null; private var nextPiece: Tetromino? = null

    private val gameLoop = object : Runnable { 
        override fun run() { 
            if (!isGameOver && !isWaitingForAd && !showResumePopup) { 
                moveDown(); invalidate(); handler.postDelayed(this, speedMs) 
            } 
        } 
    }
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
        loadTetrisBitmaps()
        activePaletteIndex = Random.nextInt(COLOR_THEMES.size)
        if (prefs.getBoolean("TetrisSaved", false)) showResumePopup = true else restartGame()
        handler.post(renderLoop) 
    }

    private fun loadTetrisBitmaps() {
        val res = context.resources
        val mapping = mapOf(
            1 to "block_chocolate", // Donut
            2 to "block_biscuit",   // Cookie
            3 to "block_brick",     // Heart Brick
            4 to "block_coke",      // Soda Can
            5 to "block_wood",      // Wooden Board
            6 to "block_kerosene",  // Bomb
            7 to "block_lemon",     // Lemon
            8 to "block_candy",     // Candy
            9 to "block_orange",    // Orange
            10 to "block_mirror"    // Gold Mirror
        )
        for ((id, name) in mapping) {
            val resId = res.getIdentifier(name, "drawable", context.packageName)
            tetrisBitmaps[id] = if (resId != 0) BitmapFactory.decodeResource(res, resId) else null
        }
    }

    private fun vibratePhone(duration: Long = 70L) { 
        try { 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)) 
            else @Suppress("DEPRECATION") vibrator.vibrate(duration) 
        } catch (e: Exception) { } 
    }

    private fun restartGame() {
        for (r in 0 until ROWS) for (c in 0 until COLS) grid[r][c] = 0
        score = 0; speedMs = 600L; isGameOver = false; isWaitingForAd = false; adCountdown = 10; isNewHighScore = false; hsRewardGiven = false
        activePaletteIndex = Random.nextInt(COLOR_THEMES.size)
        particles.clear(); glowLines.clear(); floatingWords.clear(); confettis.clear()
        prefs.edit().putBoolean("TetrisSaved", false).putBoolean("TetrisHSReward", false).apply()
        nextPiece = generatePiece(); spawnPiece(); handler.removeCallbacks(gameLoop); handler.postDelayed(gameLoop, speedMs)
    }

    private fun loadGame() {
        try {
            score = prefs.getInt("TetrisScore", 0); hsRewardGiven = prefs.getBoolean("TetrisHSReward", false)
            activePaletteIndex = prefs.getInt("TetrisPaletteIndex", 0)
            val gridStr = prefs.getString("TetrisGrid", "")
            if (gridStr?.isNotEmpty() == true) { 
                val rows = gridStr.split(";")
                for (r in 0 until Math.min(ROWS, rows.size)) { 
                    val cols = rows[r].split(",")
                    for (c in 0 until Math.min(COLS, cols.size)) {
                        grid[r][c] = cols[c].toIntOrNull() ?: 0
                    } 
                } 
            }
        } catch (e: Exception) { 
            e.printStackTrace(); restartGame(); return 
        }
        nextPiece = generatePiece(); spawnPiece(); handler.removeCallbacks(gameLoop); handler.postDelayed(gameLoop, speedMs)
    }

    private fun saveGame() {
        if (isGameOver || showResumePopup || isWaitingForAd) return
        prefs.edit()
            .putBoolean("TetrisSaved", true)
            .putInt("TetrisScore", score)
            .putBoolean("TetrisHSReward", hsRewardGiven)
            .putInt("TetrisPaletteIndex", activePaletteIndex)
            .putString("TetrisGrid", grid.joinToString(";") { it.joinToString(",") })
            .apply()
    }

    private fun generatePiece(): Tetromino { 
        val matrix = SHAPES.random()
        val randomImageId = Random.nextInt(1, 11)
        return Tetromino(Array(matrix.size) { r -> IntArray(matrix[r].size) { c -> matrix[r][c] } }, randomImageId) 
    }

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

    private fun drawGlossy3DText(canvas: Canvas, text: String, x: Float, y: Float, mainColor: Int, depthColor: Int, size: Float, align: Paint.Align = Paint.Align.CENTER) {
        text3DPaint.textSize = size; text3DPaint.textAlign = align; text3DPaint.clearShadowLayer()
        text3DPaint.style = Paint.Style.STROKE; text3DPaint.strokeWidth = size * 0.14f; text3DPaint.strokeJoin = Paint.Join.ROUND
        text3DPaint.color = depthColor; canvas.drawText(text, x, y + size * 0.08f, text3DPaint)
        text3DPaint.style = Paint.Style.FILL; text3DPaint.color = mainColor; canvas.drawText(text, x, y, text3DPaint)
    }

    private fun drawCoinIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFB8860B.toInt(); canvas.drawCircle(cx, cy + radius * 0.15f, radius, p)
        p.color = 0xFFFFD700.toInt(); canvas.drawCircle(cx, cy, radius, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = radius * 0.2f; p.color = 0xFFDAA520.toInt(); canvas.drawCircle(cx, cy, radius * 0.6f, p)
        p.style = Paint.Style.FILL; p.color = 0xAAFFFFFF.toInt(); canvas.drawOval(RectF(cx - radius*0.5f, cy - radius*0.8f, cx + radius*0.5f, cy - radius*0.1f), p)
    }

    private fun draw3DButton(canvas: Canvas, rect: RectF, text: String, topColor: Int, bottomColor: Int, size: Float = 45f) {
        btnPaint.color = bottomColor; canvas.drawRoundRect(RectF(rect.left, rect.top + 12f, rect.right, rect.bottom + 12f), 30f, 30f, btnPaint)
        btnPaint.color = topColor; canvas.drawRoundRect(rect, 30f, 30f, btnPaint)
        drawGlossy3DText(canvas, text, rect.centerX(), rect.centerY() + size/3f, Color.WHITE, Color.DKGRAY, size)
    }

    private fun draw3DControlButton(canvas: Canvas, rect: RectF, text: String, topColor: Int, bottomColor: Int) {
        btnPaint.color = bottomColor; canvas.drawRoundRect(RectF(rect.left, rect.top + 12f, rect.right, rect.bottom + 12f), 40f, 40f, btnPaint)
        btnPaint.color = topColor; canvas.drawRoundRect(rect, 40f, 40f, btnPaint)
        drawGlossy3DText(canvas, text, rect.centerX(), rect.centerY() + rect.height() * 0.15f, Color.WHITE, Color.DKGRAY, rect.height() * 0.45f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val palette = COLOR_THEMES[activePaletteIndex]
        
        // 1. Light Outer Screen Background
        canvas.drawColor(palette.bg)

        val topY = 90f
        drawGlossy3DText(canvas, "SCORE", boardX + 20f, topY, palette.textPrimary, palette.boardBorder, 30f, Paint.Align.LEFT)
        drawGlossy3DText(canvas, "$score", boardX + 20f, topY + 40f, palette.textPrimary, palette.textSecondary, 40f, Paint.Align.LEFT)
        
        drawGlossy3DText(canvas, "BEST", width/2f, topY, palette.textPrimary, palette.boardBorder, 30f, Paint.Align.CENTER)
        drawGlossy3DText(canvas, "$highScore", width/2f, topY + 40f, palette.textPrimary, palette.textSecondary, 40f, Paint.Align.CENTER)
        
        val nextTitleX = boardX + boardSizeW - 20f
        drawGlossy3DText(canvas, "NEXT", nextTitleX, topY, palette.textPrimary, palette.boardBorder, 30f, Paint.Align.RIGHT)
        
        // Next Preview Block Rendering (Using Bitmaps)
        nextPiece?.let { piece ->
            val previewSize = cellSize * 0.5f
            for (r in 0 until piece.matrix.size) for (c in 0 until piece.matrix[0].size) 
                if (piece.matrix[r][c] != 0) {
                    val px = nextTitleX - (piece.matrix[0].size * previewSize) + c * previewSize
                    val py = topY + 15f + r * previewSize
                    drawItemBitmap(canvas, px, py, previewSize, piece.imageId)
                }
        }

        val currentCoins = prefs.getInt("BoxBlastCoins", 0)
        drawCoinIcon(canvas, width/2f - 30f, 50f, 15f)
        drawGlossy3DText(canvas, "$currentCoins", width/2f + 5f, 60f, 0xFFB8860B.toInt(), 0xFF654321.toInt(), 30f, Paint.Align.LEFT)

        // 2. Light Inner Game Board (Even lighter than background)
        val rect = RectF(boardX, boardY, boardX + boardSizeW, boardY + boardSizeH)
        boardPaint.color = palette.boardBg
        boardBorderPaint.color = palette.boardBorder
        canvas.drawRoundRect(rect, 20f, 20f, boardPaint)
        canvas.drawRoundRect(rect, 20f, 20f, boardBorderPaint)

        val iteratorGlow = glowLines.iterator()
        while(iteratorGlow.hasNext()) {
            val glow = iteratorGlow.next(); glowPaint.alpha = (glow.alpha * 180).toInt()
            if (glow.isRow) canvas.drawRoundRect(RectF(boardX, boardY + glow.index * cellSize, boardX + boardSizeW, boardY + (glow.index+1)*cellSize), 12f, 12f, glowPaint)
            glow.alpha -= 0.05f; if (glow.alpha <= 0) iteratorGlow.remove()
        }

        val emptyPaint = Paint().apply { color = 0x14000000; style = Paint.Style.STROKE; strokeWidth = 2f }
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            val cx = boardX + c * cellSize; val cy = boardY + r * cellSize
            canvas.drawRoundRect(RectF(cx + 2, cy + 2, cx + cellSize - 2, cy + cellSize - 2), 8f, 8f, emptyPaint)
            if (grid[r][c] != 0) drawItemBitmap(canvas, cx + 2, cy + 2, cellSize - 4, grid[r][c])
        }

        // Active Falling Piece Rendering
        currentPiece?.let { piece ->
            for (r in 0 until piece.matrix.size) for (c in 0 until piece.matrix[0].size) 
                if (piece.matrix[r][c] != 0) {
                    val cx = boardX + (piece.x + c) * cellSize
                    val cy = boardY + (piece.y + r) * cellSize
                    drawItemBitmap(canvas, cx + 2, cy + 2, cellSize - 4, piece.imageId)
                }
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
            drawGlossy3DText(canvas, fw.text, width/2f, fw.y, 0xFF0077B6.toInt(), 0xFF023E8A.toInt(), 90f)
            canvas.restore(); if (fw.alpha <= 0) iteratorWords.remove()
        }

        if (particles.isNotEmpty()) {
            val iterator = particles.iterator(); val pPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            while (iterator.hasNext()) {
                val p = iterator.next()
                pPaint.color = p.color
                pPaint.alpha = (p.life * 255).toInt().coerceIn(0, 255)

                when (p.type) {
                    BlastType.LIGHTNING -> {
                        pPaint.style = Paint.Style.STROKE; pPaint.strokeWidth = 4f * p.life
                        canvas.drawLine(p.x, p.y, p.x + p.vx * 2f, p.y + p.vy * 2f, pPaint)
                    }
                    BlastType.MELT -> {
                        pPaint.style = Paint.Style.FILL
                        canvas.drawOval(RectF(p.x - p.size, p.y - p.size * 1.5f, p.x + p.size, p.y + p.size * 1.5f), pPaint)
                        p.vy += 0.7f
                    }
                    BlastType.BROKEN -> {
                        pPaint.style = Paint.Style.FILL
                        canvas.save(); canvas.translate(p.x, p.y); canvas.rotate(p.rotation)
                        canvas.drawRect(-p.size, -p.size, p.size, p.size, pPaint); canvas.restore()
                        p.rotation += 12f
                    }
                    BlastType.BURN -> {
                        pPaint.style = Paint.Style.FILL
                        canvas.drawCircle(p.x, p.y, p.size * p.life, pPaint)
                        p.vy -= 0.6f
                    }
                    BlastType.COKE -> {
                        pPaint.style = Paint.Style.STROKE; pPaint.strokeWidth = 3f
                        canvas.drawCircle(p.x, p.y, p.size * (1f - p.life + 0.3f), pPaint)
                        p.vy -= 1.4f
                    }
                    BlastType.POP -> {
                        pPaint.style = Paint.Style.FILL
                        canvas.drawCircle(p.x, p.y, p.size * p.life, pPaint)
                    }
                }
                p.x += p.vx; p.y += p.vy; p.life -= 0.035f
                if (p.life <= 0) iterator.remove()
            }
        }

        if (showResumePopup) {
            canvas.drawColor(0xDD000000.toInt())
            drawGlossy3DText(canvas, "GAME SAVED", width / 2f, boardY + boardSizeH / 2f - 160f, Color.WHITE, Color.DKGRAY, 80f)
            draw3DButton(canvas, resumeBtnRect, "RESUME", 0xFF2ECC71.toInt(), 0xFF1E8449.toInt())
            draw3DButton(canvas, newGameBtnRect, "NEW GAME", 0xFFE74C3C.toInt(), 0xFF922B21.toInt())
        } else if (isWaitingForAd) {
            canvas.drawColor(0xDD000000.toInt())
            drawGlossy3DText(canvas, "NO MOVES", width / 2f, boardY + boardSizeH / 2f - 80f, 0xFFE74C3C.toInt(), 0xFF922B21.toInt(), 90f)
            drawGlossy3DText(canvas, "$adCountdown", width / 2f, boardY + boardSizeH / 2f + 60f, Color.WHITE, Color.DKGRAY, 150f)
            draw3DButton(canvas, menuBtnRect, "▶ WATCH AD (1 CHANCE)", 0xFF3498DB.toInt(), 0xFF1B4F72.toInt(), 40f)
        } else if (isGameOver) {
            canvas.drawColor(0xEE000000.toInt())
            if (isNewHighScore) {
                text3DPaint.textSize = 150f; text3DPaint.clearShadowLayer()
                canvas.drawText("👑", width/2f, boardY - 50f, text3DPaint)
                drawGlossy3DText(canvas, "NEW BEST!", width / 2f, boardY + 60f, 0xFFFFD700.toInt(), 0xFF8B6508.toInt(), 100f)
            } else { 
                drawGlossy3DText(canvas, "GAME OVER", width / 2f, boardY + boardSizeH / 2f - 120f, 0xFFE74C3C.toInt(), 0xFF922B21.toInt(), 110f) 
            }
            draw3DButton(canvas, restartBtnRect, "RESTART", 0xFFE74C3C.toInt(), 0xFF922B21.toInt())
            draw3DButton(canvas, menuBtnRect, "MAIN MENU", 0xFFF39C12.toInt(), 0xFFB9770E.toInt())
            if (isNewHighScore) { 
                val pPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                for(c in confettis) { 
                    pPaint.color = c.color; canvas.save(); canvas.translate(c.x, c.y); canvas.rotate(c.rot)
                    canvas.drawRect(-c.size, -c.size, c.size, c.size, pPaint); canvas.restore()
                    c.x += c.vx; c.y += c.vy; c.vy += 0.5f; c.rot += c.rotSpeed 
                } 
            }
        }
    }

    // Direct Transparent PNG Rendering for Tetris Blocks
    private fun drawItemBitmap(canvas: Canvas, x: Float, y: Float, size: Float, type: Int) {
        val rect = RectF(x, y, x + size, y + size)
        val bmp = tetrisBitmaps[type] ?: tetrisBitmaps[1]
        if (bmp != null) {
            canvas.drawBitmap(bmp, null, rect, bitmapPaint)
        } else {
            val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A90E2.toInt(); style = Paint.Style.FILL }
            canvas.drawRoundRect(rect, 8f, 8f, fallbackPaint)
        }
    }

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
            val offsetX = (c - r) / 2
            val offsetY = (r - c) / 2
            if (isValidPosition(newM, it.x + offsetX, it.y + offsetY)) { 
                it.matrix = newM; it.x += offsetX; it.y += offsetY; soundManager.playPick() 
            } else if (isValidPosition(newM, it.x, it.y)) { 
                it.matrix = newM; soundManager.playPick() 
            }
        }
    }

    private fun moveDown() { currentPiece?.let { if (isValidPosition(it.matrix, it.x, it.y + 1)) it.y++ else lockPiece() } }

    private fun lockPiece() {
        currentPiece?.let { piece ->
            soundManager.playDrop()
            for (r in 0 until piece.matrix.size) for (c in 0 until piece.matrix[0].size) 
                if (piece.matrix[r][c] != 0 && piece.y + r in 0 until ROWS && piece.x + c in 0 until COLS) grid[piece.y + r][piece.x + c] = piece.imageId
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
            val randomBlast = BlastType.values().random()
            soundManager.playBlastSound(randomBlast.name)
            vibratePhone(120L) // Line clear vibration
            
            handler.postDelayed({ 
                val word = soundManager.playComboVoice(linesCleared)
                if (word.isNotEmpty()) floatingWords.add(FloatingWord(word, boardY + boardSizeH/2f)) 
            }, 800)
            
            score += (linesCleared * 100) * linesCleared; speedMs = maxOf(150L, speedMs - 20L)
            
            for (cr in clearedRows) {
                glowLines.add(GlowLine(true, cr))
                val blastY = boardY + cr * cellSize + cellSize/2f
                for (c in 0 until COLS) {
                    val blastX = boardX + c * cellSize + cellSize/2f
                    spawnBlastParticles(blastX, blastY, randomBlast)
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

    private fun spawnBlastParticles(cx: Float, cy: Float, blastType: BlastType) {
        val color = when (blastType) {
            BlastType.BURN -> 0xFFFF4500.toInt()
            BlastType.BROKEN -> 0xFF8B4513.toInt()
            BlastType.MELT -> 0xFF4A2C11.toInt()
            BlastType.COKE -> 0xFFE71D36.toInt()
            BlastType.LIGHTNING -> 0xFF00E5FF.toInt()
            BlastType.POP -> 0xFFFFD700.toInt()
        }
        val count = if (blastType == BlastType.COKE || blastType == BlastType.LIGHTNING) 14 else 8
        for (i in 0 until count) {
            val vx = Random.nextFloat() * 16f - 8f
            val vy = Random.nextFloat() * 18f - 10f
            particles.add(Particle(cx, cy, vx, vy, 1f, color, blastType, cellSize * 0.2f, Random.nextFloat() * 360f))
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

    override fun onDetachedFromWindow() { 
        super.onDetachedFromWindow(); saveGame()
        handler.removeCallbacks(gameLoop); handler.removeCallbacks(renderLoop); handler.removeCallbacks(timerRunnable); soundManager.release() 
    }
}
