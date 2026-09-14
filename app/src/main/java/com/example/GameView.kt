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
    private var isGameOver = false
    private var isWaitingForAd = false
    private var adCountdown = 10
    private var isNewHighScore = false
    private var hsRewardGiven = false

    private var showResumePopup = false
    private val resumeBtnRect = RectF()
    private val newGameBtnRect = RectF()
    private val shuffleBtnRect = RectF()

    // 10 Light Aesthetic Color Schemes (Outer BG + Inner Board)
    data class ColorTheme(val bg: Int, val boardBg: Int, val boardBorder: Int, val textPrimary: Int, val textSecondary: Int)
    private val COLOR_THEMES = listOf(
        ColorTheme(0xFFE8EEF5.toInt(), 0xFFFAFCFF.toInt(), 0xFFC9D8E6.toInt(), 0xFF1E293B.toInt(), 0xFF64748B.toInt()), // Ice Blue
        ColorTheme(0xFFF7ECE1.toInt(), 0xFFFFFDF9.toInt(), 0xFFE6D2C0.toInt(), 0xFF4A3525.toInt(), 0xFF8C715A.toInt()), // Warm Sand
        ColorTheme(0xFFE9F5ED.toInt(), 0xFFFBFFFC.toInt(), 0xFFCDE4D4.toInt(), 0xFF1B4332.toInt(), 0xFF52796F.toInt()), // Mint Sage
        ColorTheme(0xFFF5EBF7.toInt(), 0xFFFEFAFF.toInt(), 0xFFE2CCE6.toInt(), 0xFF3C1642.toInt(), 0xFF7B5080.toInt()), // Soft Lavender
        ColorTheme(0xFFFDF0ED.toInt(), 0xFFFFF9F8.toInt(), 0xFFF2D1CA.toInt(), 0xFF4A1E17.toInt(), 0xFF8C5B53.toInt()), // Peach Blush
        ColorTheme(0xFFE6F3F7.toInt(), 0xFFF7FDFF.toInt(), 0xFFC3DFE8.toInt(), 0xFF0F3443.toInt(), 0xFF4A7282.toInt()), // Pastel Cyan
        ColorTheme(0xFFF9F7E8.toInt(), 0xFFFFFFFA.toInt(), 0xFFEAE5BE.toInt(), 0xFF3D3A1B.toInt(), 0xFF7D774D.toInt()), // Pale Vanilla
        ColorTheme(0xFFECEEF8.toInt(), 0xFFFBFCFF.toInt(), 0xFFCCD1EB.toInt(), 0xFF1D2447.toInt(), 0xFF565F87.toInt()), // Periwinkle Mist
        ColorTheme(0xFFF3F1EC.toInt(), 0xFFFAF9F6.toInt(), 0xFFD8D3C5.toInt(), 0xFF363228.toInt(), 0xFF6E685B.toInt()), // Nordic Clay
        ColorTheme(0xFFE5F5F3.toInt(), 0xFFF5FFFE.toInt(), 0xFFBFE5E0.toInt(), 0xFF0D3B36.toInt(), 0xFF467570.toInt())  // Sea Foam
    )
    private var activePaletteIndex = 0

    // User Images mapped with sounds and blast types
    enum class ThemeType(val blastName: String, val resName: String, val displayName: String) {
        DONUT("MELT", "block_chocolate", "CHOCOLATE"),
        COOKIE("POP", "block_biscuit", "BISCUIT"),
        BRICK("BROKEN", "block_brick", "BRICK"),
        WOOD("BROKEN", "block_wood", "WOOD"),
        BOMB("BURN", "block_kerosene", "KEROSENE"),
        SODA("COKE", "block_coke", "COKE"),
        LEMON("POP", "block_lemon", "LEMON"),
        CANDY("POP", "block_candy", "CANDY"),
        ORANGE("POP", "block_orange", "ORANGE"),
        MIRROR("BROKEN", "block_mirror", "MIRROR")
    }
    private var currentThemeIndex = 0
    private var totalBlastsCount = 0

    // Bitmap Cache
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val themeBitmaps = mutableMapOf<ThemeType, Bitmap?>()

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

    private var cellSize = 0f
    private var boardSize = 0f
    private var boardX = 0f
    private var boardY = 0f
    private var trayY = 0f
    private var trayCellSize = 0f
    private val restartBtnRect = RectF()
    private val menuBtnRect = RectF()

    // Upgraded Shapes Matrix: Triangle ratio reduced; 6, 5, and 9-block matrices added
    val SHAPES = listOf(
        arrayOf(intArrayOf(1)),
        arrayOf(intArrayOf(1, 1)),
        arrayOf(intArrayOf(1), intArrayOf(1)),
        arrayOf(intArrayOf(1, 1, 1)),
        arrayOf(intArrayOf(1), intArrayOf(1), intArrayOf(1)),
        arrayOf(intArrayOf(1, 1, 1, 1)),
        arrayOf(intArrayOf(1), intArrayOf(1), intArrayOf(1), intArrayOf(1)),
        arrayOf(intArrayOf(1, 1, 1, 1, 1)),
        arrayOf(intArrayOf(1, 1), intArrayOf(1, 1)),
        // 6-block rectangles (3x2 and 2x3)
        arrayOf(intArrayOf(1, 1, 1), intArrayOf(1, 1, 1)),
        arrayOf(intArrayOf(1, 1), intArrayOf(1, 1), intArrayOf(1, 1)),
        // 9-block full 3x3 matrix
        arrayOf(intArrayOf(1, 1, 1), intArrayOf(1, 1, 1), intArrayOf(1, 1, 1)),
        // 5-block (Cross, T, and Steps)
        arrayOf(intArrayOf(0, 1, 0), intArrayOf(1, 1, 1), intArrayOf(0, 1, 0)),
        arrayOf(intArrayOf(1, 1, 1), intArrayOf(0, 1, 0), intArrayOf(0, 1, 0)),
        arrayOf(intArrayOf(1, 1, 1), intArrayOf(1, 0, 0), intArrayOf(1, 0, 0)),
        // Rare Corner shapes (Single count)
        arrayOf(intArrayOf(1, 0), intArrayOf(1, 1)),
        arrayOf(intArrayOf(0, 1), intArrayOf(1, 1)),
        arrayOf(intArrayOf(1, 1), intArrayOf(1, 0)),
        arrayOf(intArrayOf(1, 1), intArrayOf(0, 1))
    )

    class Shape(val matrix: Array<IntArray>) { val rows = matrix.size; val cols = matrix[0].size; var cx = 0f; var cy = 0f; var placed = false }
    private val trayShapes = arrayOfNulls<Shape>(3)
    private var draggingShapeIndex = -1
    private var draggingShape: Shape? = null
    private var dragTouchOffsetX = 0f
    private var dragTouchOffsetY = 0f
    private var hoverRow = -1
    private var hoverCol = -1
    private var canFitHover = false

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
        loadThemeBitmaps()
        activePaletteIndex = Random.nextInt(COLOR_THEMES.size)
        if (prefs.getBoolean("ClassicSaved", false)) showResumePopup = true else restartGame()
        handler.post(renderLoop) 
    }

    private fun loadThemeBitmaps() {
        val res = context.resources
        for (theme in ThemeType.values()) {
            val id = res.getIdentifier(theme.resName, "drawable", context.packageName)
            themeBitmaps[theme] = if (id != 0) BitmapFactory.decodeResource(res, id) else null
        }
    }

    private fun useCoinsOrFreeShuffle(): Boolean { 
        var freeShuffles = prefs.getInt("FreeShuffles", 0)
        if (freeShuffles > 0) { freeShuffles--; prefs.edit().putInt("FreeShuffles", freeShuffles).apply(); return true }
        var coins = prefs.getInt("BoxBlastCoins", 0)
        if (coins >= 50) { coins -= 50; prefs.edit().putInt("BoxBlastCoins", coins).apply(); return true }
        return false 
    }

    private fun vibratePhone(duration: Long = 70L) { 
        try { 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)) 
            else @Suppress("DEPRECATION") vibrator.vibrate(duration) 
        } catch (e: Exception) { } 
    }

    private fun restartGame() {
        for (r in 0 until 8) for (c in 0 until 8) grid[r][c] = 0
        score = 0; isGameOver = false; isWaitingForAd = false; adCountdown = 10; isNewHighScore = false; hsRewardGiven = false
        totalBlastsCount = 0; currentThemeIndex = 0
        activePaletteIndex = Random.nextInt(COLOR_THEMES.size)
        particles.clear(); glowLines.clear(); floatingWords.clear(); confettis.clear()
        prefs.edit().putBoolean("ClassicSaved", false).putBoolean("ClassicHSReward", false).apply()
        for (i in 0 until 3) trayShapes[i] = null; fillTray()
    }

    private fun loadGame() {
        try {
            score = prefs.getInt("ClassicScore", 0)
            hsRewardGiven = prefs.getBoolean("ClassicHSReward", false)
            currentThemeIndex = prefs.getInt("ClassicThemeIndex", 0)
            totalBlastsCount = prefs.getInt("ClassicTotalBlasts", 0)
            activePaletteIndex = prefs.getInt("ClassicPaletteIndex", 0)
            val gridStr = prefs.getString("ClassicGrid", "")
            if (!gridStr.isNullOrEmpty()) {
                val rows = gridStr.split(";")
                for (r in 0 until minOf(8, rows.size)) {
                    val cols = rows[r].split(",")
                    for (c in 0 until minOf(8, cols.size)) {
                        grid[r][c] = cols[c].toIntOrNull() ?: 0
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            restartGame(); return
        }
        for (i in 0 until 3) trayShapes[i] = null; fillTray()
    }

    private fun saveGame() {
        if (isGameOver || showResumePopup || isWaitingForAd) return
        prefs.edit()
            .putBoolean("ClassicSaved", true)
            .putInt("ClassicScore", score)
            .putBoolean("ClassicHSReward", hsRewardGiven)
            .putInt("ClassicThemeIndex", currentThemeIndex)
            .putInt("ClassicTotalBlasts", totalBlastsCount)
            .putInt("ClassicPaletteIndex", activePaletteIndex)
            .putString("ClassicGrid", grid.joinToString(";") { it.joinToString(",") })
            .apply()
    }

    private fun fillTray() {
        for (i in 0 until 3) {
            if (trayShapes[i] == null || trayShapes[i]!!.placed) {
                var safeShape: Shape? = null
                for (attempt in 0..20) { 
                    val rawM = SHAPES.random()
                    val testShape = Shape(Array(rawM.size) { r -> IntArray(rawM[r].size) { c -> if (rawM[r][c] == 1) 1 else 0 } })
                    if (canFitAnywhere(testShape)) { safeShape = testShape; break } 
                }
                if (safeShape == null) safeShape = Shape(arrayOf(intArrayOf(1)))
                trayShapes[i] = safeShape
            }
        }
        if (width > 0 && height > 0) updateTrayPositions(); checkGameOverCondition()
    }

    private fun canFitAnywhere(shape: Shape): Boolean { 
        for (r in 0 until 8) for (c in 0 until 8) if (canPlaceShape(shape, r, c)) return true
        return false 
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        boardSize = w - 60f; cellSize = boardSize / 8; boardX = 30f; boardY = 250f
        trayY = boardY + boardSize + 90f; trayCellSize = cellSize * 0.65f
        val bw = 600f; val bh = 130f; val cx = w / 2f
        restartBtnRect.set(cx - bw/2f, boardY + boardSize/2f - 40f, cx + bw/2f, boardY + boardSize/2f - 40f + bh)
        menuBtnRect.set(cx - bw/2f, restartBtnRect.bottom + 30f, cx + bw/2f, restartBtnRect.bottom + 30f + bh)
        resumeBtnRect.set(cx - bw/2f, boardY + boardSize/2f - 60f, cx + bw/2f, boardY + boardSize/2f - 60f + bh)
        newGameBtnRect.set(cx - bw/2f, resumeBtnRect.bottom + 40f, cx + bw/2f, resumeBtnRect.bottom + 40f + bh)
        shuffleBtnRect.set(cx - 150f, trayY + trayCellSize * 4.5f, cx + 150f, trayY + trayCellSize * 4.5f + 110f)
        updateTrayPositions()
    }

    private fun updateTrayPositions() {
        if (width == 0) return
        val sectionWidth = width / 3f
        for (i in 0 until 3) trayShapes[i]?.let { 
            if (!it.placed) { 
                it.cx = (i * sectionWidth) + (sectionWidth - (it.cols * trayCellSize)) / 2f
                it.cy = trayY + (sectionWidth - (it.rows * trayCellSize)) / 2f 
            } 
        }
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val palette = COLOR_THEMES[activePaletteIndex]
        
        // 1. Light Outer Background
        canvas.drawColor(palette.bg) 
        
        drawGlossy3DText(canvas, "SCORE", width / 4f, 100f, palette.textPrimary, palette.boardBorder, 45f)
        drawGlossy3DText(canvas, "$score", width / 4f, 160f, palette.textPrimary, palette.textSecondary, 65f)
        drawGlossy3DText(canvas, "BEST", (width / 4f) * 3f, 100f, palette.textPrimary, palette.boardBorder, 45f)
        drawGlossy3DText(canvas, "$highScore", (width / 4f) * 3f, 160f, palette.textPrimary, palette.textSecondary, 65f)
        
        val currentCoins = prefs.getInt("BoxBlastCoins", 0)
        drawCoinIcon(canvas, width / 2f - 40f, 115f, 20f)
        drawGlossy3DText(canvas, "$currentCoins", width / 2f + 10f, 130f, 0xFFB8860B.toInt(), 0xFF654321.toInt(), 45f, Paint.Align.LEFT)

        // 2. Light Inner Game Board (Even lighter than background)
        val rect = RectF(boardX, boardY, boardX + boardSize, boardY + boardSize)
        boardPaint.color = palette.boardBg
        boardBorderPaint.color = palette.boardBorder
        canvas.drawRoundRect(rect, 24f, 24f, boardPaint)
        canvas.drawRoundRect(rect, 24f, 24f, boardBorderPaint)

        val iteratorGlow = glowLines.iterator()
        while(iteratorGlow.hasNext()) {
            val glow = iteratorGlow.next(); glowPaint.alpha = (glow.alpha * 180).toInt()
            if (glow.isRow) canvas.drawRoundRect(RectF(boardX, boardY + glow.index * cellSize, boardX + boardSize, boardY + (glow.index+1)*cellSize), 12f, 12f, glowPaint)
            else canvas.drawRoundRect(RectF(boardX + glow.index * cellSize, boardY, boardX + (glow.index+1)*cellSize, boardY + boardSize), 12f, 12f, glowPaint)
            glow.alpha -= 0.05f; if (glow.alpha <= 0) iteratorGlow.remove()
        }

        val emptyPaint = Paint().apply { color = 0x14000000; style = Paint.Style.STROKE; strokeWidth = 2f }
        for (r in 0 until 8) for (c in 0 until 8) {
            val cx = boardX + c * cellSize; val cy = boardY + r * cellSize
            canvas.drawRoundRect(RectF(cx + 4, cy + 4, cx + cellSize - 4, cy + cellSize - 4), 12f, 12f, emptyPaint)
            if (grid[r][c] != 0) drawBlock(canvas, cx, cy, cellSize)
        }

        // Full Surface Hover Highlight
        draggingShape?.let { if (canFitHover) drawFullNeonHoverShadow(canvas, it, boardX + hoverCol * cellSize, boardY + hoverRow * cellSize, cellSize) }
        for (i in 0 until 3) if (i != draggingShapeIndex) trayShapes[i]?.let { if (!it.placed) drawShape(canvas, it, it.cx, it.cy, trayCellSize) }
        draggingShape?.let { drawShape(canvas, it, it.cx, it.cy, cellSize) }
        
        if(!isGameOver && !showResumePopup && !isWaitingForAd) {
            val freeShuffles = prefs.getInt("FreeShuffles", 0)
            val shuffleText = if (freeShuffles > 0) "🔀 FREE" else "🔀 50"
            draw3DButton(canvas, shuffleBtnRect, shuffleText, 0xFF8E44AD.toInt(), 0xFF5B2C6F.toInt(), 40f)
        }

        val iteratorWords = floatingWords.iterator()
        while(iteratorWords.hasNext()) {
            val fw = iteratorWords.next()
            if (fw.scale < 1f) fw.scale += 0.05f; fw.y -= 3f; fw.alpha -= 0.02f
            canvas.save(); canvas.scale(fw.scale, fw.scale, width/2f, fw.y)
            drawGlossy3DText(canvas, fw.text, width/2f, fw.y, 0xFF0077B6.toInt(), 0xFF023E8A.toInt(), 90f)
            canvas.restore(); if (fw.alpha <= 0) iteratorWords.remove()
        }

        // Particle Animations Rendering
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
            drawGlossy3DText(canvas, "GAME SAVED", width / 2f, boardY + boardSize / 2f - 160f, Color.WHITE, Color.DKGRAY, 80f)
            draw3DButton(canvas, resumeBtnRect, "RESUME", 0xFF2ECC71.toInt(), 0xFF1E8449.toInt())
            draw3DButton(canvas, newGameBtnRect, "NEW GAME", 0xFFE74C3C.toInt(), 0xFF922B21.toInt())
        } else if (isWaitingForAd) {
            canvas.drawColor(0xDD000000.toInt())
            drawGlossy3DText(canvas, "OUT OF MOVES", width / 2f, boardY + boardSize / 2f - 80f, 0xFFE74C3C.toInt(), 0xFF922B21.toInt(), 90f)
            drawGlossy3DText(canvas, "$adCountdown", width / 2f, boardY + boardSize / 2f + 60f, Color.WHITE, Color.DKGRAY, 150f)
            draw3DButton(canvas, menuBtnRect, "▶ WATCH AD (1 CHANCE)", 0xFF3498DB.toInt(), 0xFF1B4F72.toInt(), 40f)
        } else if (isGameOver) {
            canvas.drawColor(0xEE000000.toInt())
            if (isNewHighScore) {
                text3DPaint.textSize = 150f; text3DPaint.clearShadowLayer()
                canvas.drawText("👑", width/2f, boardY - 50f, text3DPaint)
                drawGlossy3DText(canvas, "NEW BEST!", width / 2f, boardY + 60f, 0xFFFFD700.toInt(), 0xFF8B6508.toInt(), 100f)
            } else { 
                drawGlossy3DText(canvas, "GAME OVER", width / 2f, boardY + boardSize / 2f - 120f, 0xFFE74C3C.toInt(), 0xFF922B21.toInt(), 110f) 
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

    private fun drawShape(canvas: Canvas, shape: Shape, x: Float, y: Float, size: Float) {
        for (r in 0 until shape.rows) for (c in 0 until shape.cols) 
            if (shape.matrix[r][c] != 0) drawBlock(canvas, x + c * size, y + r * size, size)
    }

    // Direct User Image Bitmap Render
    private fun drawBlock(canvas: Canvas, x: Float, y: Float, size: Float) {
        val rect = RectF(x + 2, y + 2, x + size - 2, y + size - 2)
        val activeTheme = ThemeType.values()[currentThemeIndex]
        val bmp = themeBitmaps[activeTheme]

        if (bmp != null) {
            canvas.drawBitmap(bmp, null, rect, bitmapPaint)
        } else {
            // Fallback soft color if image not found
            val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A90E2.toInt(); style = Paint.Style.FILL }
            canvas.drawRoundRect(rect, 12f, 12f, fallbackPaint)
        }
    }

    // Full Surface Hover Neon Highlight (Whole cell fill + neon stroke)
    private fun drawFullNeonHoverShadow(canvas: Canvas, shape: Shape, x: Float, y: Float, size: Float) {
        val neonColor = 0xFF00B4D8.toInt()
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, Color.red(neonColor), Color.green(neonColor), Color.blue(neonColor))
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = neonColor
            style = Paint.Style.STROKE
            strokeWidth = 6f
            setShadowLayer(25f, 0f, 0f, neonColor)
        }
        for (r in 0 until shape.rows) for (c in 0 until shape.cols) if (shape.matrix[r][c] != 0) {
            val rectBox = RectF(x + c * size + 4, y + r * size + 4, x + c * size + size - 4, y + r * size + size - 4)
            canvas.drawRoundRect(rectBox, 14f, 14f, fillPaint)
            canvas.drawRoundRect(rectBox, 14f, 14f, strokePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tx = event.x; val ty = event.y
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (showResumePopup) {
                if (resumeBtnRect.contains(tx, ty)) { soundManager.playBtnClick(); loadGame(); showResumePopup = false; return true }
                if (newGameBtnRect.contains(tx, ty)) { soundManager.playBtnClick(); prefs.edit().putBoolean("ClassicSaved", false).apply(); showResumePopup = false; restartGame(); return true }
                return true
            }
            if (!isGameOver && !showResumePopup && !isWaitingForAd && shuffleBtnRect.contains(tx, ty)) {
                if (useCoinsOrFreeShuffle()) { soundManager.playBtnClick(); for(i in 0 until 3) trayShapes[i] = null; fillTray() } 
                return true
            }
            if (isWaitingForAd && menuBtnRect.contains(tx, ty)) {
                soundManager.playBtnClick(); soundManager.stopCountdownTick(); handler.removeCallbacks(timerRunnable)
                (context as Activity).let { activity ->
                    AdManager.showRewardAd(activity) { rewarded ->
                        if (rewarded) {
                            isWaitingForAd = false; for(r in 5..7) for(c in 0 until 8) grid[r][c] = 0 
                            trayShapes[0] = Shape(arrayOf(intArrayOf(1))); trayShapes[1] = null; trayShapes[2] = null; updateTrayPositions(); invalidate()
                        } else { isWaitingForAd = false; isGameOver = true; soundManager.playGameOver(); checkAndTriggerConfetti(); invalidate() }
                    }
                }
                return true
            }
            if (isGameOver) {
                if (restartBtnRect.contains(tx, ty)) { soundManager.playBtnClick(); restartGame(); return true }
                if (menuBtnRect.contains(tx, ty)) { prefs.edit().putBoolean("ClassicSaved", false).apply(); (context as Activity).finish(); return true }
            }
        }
        if (isGameOver || isWaitingForAd || showResumePopup) return true

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                for (i in 0 until 3) trayShapes[i]?.let { 
                    if (!it.placed && RectF(it.cx - 30f, it.cy - 30f, it.cx + (it.cols * trayCellSize) + 30f, it.cy + (it.rows * trayCellSize) + 30f).contains(tx, ty)) {
                        soundManager.playPick(); draggingShapeIndex = i; draggingShape = it
                        it.cx = tx - (it.cols * cellSize) / 2f; it.cy = ty - (it.rows * cellSize) - 180f
                        dragTouchOffsetX = tx - it.cx; dragTouchOffsetY = ty - it.cy; return true
                    }
                }
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
        for (r in 0 until shape.rows) for (c in 0 until shape.cols) 
            if (shape.matrix[r][c] != 0 && (rOff + r !in 0..7 || cOff + c !in 0..7 || grid[rOff + r][cOff + c] != 0)) return false
        return true 
    }

    private fun placeShape(shape: Shape, rOff: Int, cOff: Int) { 
        for (r in 0 until shape.rows) for (c in 0 until shape.cols) 
            if (shape.matrix[r][c] != 0) { grid[rOff + r][cOff + c] = 1 }
        score += 10; checkLines() 
    }

    private fun checkLines() {
        val rows = mutableListOf<Int>(); val cols = mutableListOf<Int>()
        for (r in 0 until 8) if ((0 until 8).all { c -> grid[r][c] != 0 }) rows.add(r)
        for (c in 0 until 8) if ((0 until 8).all { r -> grid[r][c] != 0 }) cols.add(c)
        val total = rows.size + cols.size

        if (total > 0) {
            totalBlastsCount += total
            // Switch theme after every 10 blasts
            val newThemeIndex = (totalBlastsCount / 10) % ThemeType.values().size
            if (newThemeIndex != currentThemeIndex) {
                currentThemeIndex = newThemeIndex
                floatingWords.add(FloatingWord(ThemeType.values()[currentThemeIndex].displayName + " MODE!", boardY + boardSize/2f - 90f))
            }

            val currentTheme = ThemeType.values()[currentThemeIndex]
            soundManager.playBlastSound(currentTheme.blastName)
            vibratePhone(100L) // Line clear vibration

            handler.postDelayed({ 
                val word = soundManager.playComboVoice(total)
                if (word.isNotEmpty()) floatingWords.add(FloatingWord(word, boardY + boardSize/2f)) 
            }, 800)

            for (r in rows) { 
                glowLines.add(GlowLine(true, r))
                for (c in 0 until 8) { 
                    spawnBlastParticles(boardX + c * cellSize + cellSize/2f, boardY + r * cellSize + cellSize/2f, BlastType.valueOf(currentTheme.blastName))
                    grid[r][c] = 0 
                }
                score += 100 
            }
            for (c in cols) { 
                glowLines.add(GlowLine(false, c))
                for (r in 0 until 8) { 
                    if (grid[r][c] != 0) {
                        spawnBlastParticles(boardX + c * cellSize + cellSize/2f, boardY + r * cellSize + cellSize/2f, BlastType.valueOf(currentTheme.blastName))
                        grid[r][c] = 0 
                    }
                }
                score += 100 
            }
            
            if (score > highScore) { 
                highScore = score; prefs.edit().putInt("ClassicHighScore", highScore).apply() 
                if (!hsRewardGiven) { 
                    hsRewardGiven = true
                    prefs.edit().putInt("FreeShuffles", prefs.getInt("FreeShuffles", 0) + 1).apply()
                    floatingWords.add(FloatingWord("FREE SHUFFLE!", boardY + boardSize/2f + 80f)) 
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

    private fun checkGameOverCondition() {
        var canMakeMove = false
        for (shape in trayShapes) if (shape != null && !shape.placed) { 
            for (r in 0 until 8) for (c in 0 until 8) if (canPlaceShape(shape, r, c)) { canMakeMove = true; break }
            if (canMakeMove) break 
        }
        if (!canMakeMove) { isWaitingForAd = true; adCountdown = 10; handler.post(timerRunnable); invalidate() }
    }

    private fun checkAndTriggerConfetti() {
        val lastHS = prefs.getInt("LastClassicHS", 0)
        if (score > lastHS && score == highScore && score > 0) {
            isNewHighScore = true; prefs.edit().putInt("LastClassicHS", score).apply()
            val colors = listOf(Color.RED, Color.GREEN, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.WHITE)
            for (i in 0..150) confettis.add(Confetti(width/2f, boardY + 50f, Random.nextFloat()*30f-15f, Random.nextFloat() * -30f - 10f, colors.random(), Random.nextFloat()*15f+10f, Random.nextFloat()*360f, Random.nextFloat()*20f-10f))
        }
    }

    override fun onDetachedFromWindow() { 
        super.onDetachedFromWindow(); saveGame()
        handler.removeCallbacks(renderLoop); handler.removeCallbacks(timerRunnable); soundManager.release() 
    }
}
