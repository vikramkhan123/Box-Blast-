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

    // 1 to 9 Mapping: 7 Colors + Wood + Brick
    private val blockBitmaps = mutableMapOf<Int, Bitmap?>()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    data class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float, 
        var life: Float, val color: Int, var size: Float, var rotation: Float = 0f
    )
    private val particles = mutableListOf<Particle>()
    data class GlowLine(val isRow: Boolean, val index: Int, var alpha: Float = 1f)
    private val glowLines = mutableListOf<GlowLine>()
    data class FloatingWord(val text: String, var y: Float, var alpha: Float = 1f, var scale: Float = 0.5f)
    private val floatingWords = mutableListOf<FloatingWord>()

    // Paints for Exact Sancha Board Frame
    private val boardFrameRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFBF8F5.toInt(); style = Paint.Style.FILL }
    private val boardBorderStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE0D3C4.toInt(); style = Paint.Style.STROKE; strokeWidth = 5f }
    private val boardInnerSurfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF2E7DC.toInt(); style = Paint.Style.FILL }
    private val cellEmptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE5D8CC.toInt(); style = Paint.Style.FILL }

    private val text3DPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }

    private var cellSize = 0f
    private var boardInnerSize = 0f
    private var boardX = 0f
    private var boardY = 0f
    private var trayY = 0f
    private var trayCellSize = 0f
    private val restartBtnRect = RectF()
    private val menuBtnRect = RectF()

    // 3-triangle/corner, 5-plus, and 5-T excluded.
    // 4-Square and 6-Rectangle boosted for long gameplay.
    val SHAPES = listOf(
        // 1-Block Dot
        arrayOf(intArrayOf(1)),
        
        // 2-Block Lines
        arrayOf(intArrayOf(1, 1)),
        arrayOf(intArrayOf(1), intArrayOf(1)),
        
        // 3-Block Straight Lines
        arrayOf(intArrayOf(1, 1, 1)),
        arrayOf(intArrayOf(1), intArrayOf(1), intArrayOf(1)),
        
        // 4-Block Lines
        arrayOf(intArrayOf(1, 1, 1, 1)),
        arrayOf(intArrayOf(1), intArrayOf(1), intArrayOf(1), intArrayOf(1)),
        
        // 4-Square (2x2) - Weighted
        arrayOf(intArrayOf(1, 1), intArrayOf(1, 1)),
        arrayOf(intArrayOf(1, 1), intArrayOf(1, 1)),
        arrayOf(intArrayOf(1, 1), intArrayOf(1, 1)),
        
        // 6-Rectangle (3x2 and 2x3) - Weighted
        arrayOf(intArrayOf(1, 1, 1), intArrayOf(1, 1, 1)),
        arrayOf(intArrayOf(1, 1, 1), intArrayOf(1, 1, 1)),
        arrayOf(intArrayOf(1, 1), intArrayOf(1, 1), intArrayOf(1, 1)),
        arrayOf(intArrayOf(1, 1), intArrayOf(1, 1), intArrayOf(1, 1))
    )

    class Shape(val matrix: Array<IntArray>, val typeId: Int) { 
        val rows = matrix.size
        val cols = matrix[0].size
        var cx = 0f
        var cy = 0f
        var placed = false 
    }

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
                if (adCountdown == 0) { isWaitingForAd = false; isGameOver = true; soundManager.stopCountdownTick(); soundManager.playGameOver() } 
                else handler.postDelayed(this, 1000L)
                invalidate()
            }
        }
    }

    init { 
        loadBitmaps()
        if (prefs.getBoolean("ClassicSaved", false)) showResumePopup = true else restartGame()
        handler.post(renderLoop) 
    }

    private fun loadBitmaps() {
        val res = context.resources
        val map = mapOf(
            1 to "tile_peach",
            2 to "tile_green",
            3 to "tile_red",
            4 to "tile_orange",
            5 to "tile_gray",
            6 to "tile_blue",
            7 to "tile_purple",
            8 to "block_wood",
            9 to "block_brick"
        )
        for ((k, name) in map) {
            val id = res.getIdentifier(name, "drawable", context.packageName)
            blockBitmaps[k] = if (id != 0) BitmapFactory.decodeResource(res, id) else null
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
        particles.clear(); glowLines.clear(); floatingWords.clear()
        prefs.edit().putBoolean("ClassicSaved", false).apply()
        for (i in 0 until 3) trayShapes[i] = null; fillTray()
    }

    private fun loadGame() {
        try {
            score = prefs.getInt("ClassicScore", 0)
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
            restartGame(); return
        }
        for (i in 0 until 3) trayShapes[i] = null; fillTray()
    }

    private fun saveGame() {
        if (isGameOver || showResumePopup || isWaitingForAd) return
        prefs.edit()
            .putBoolean("ClassicSaved", true)
            .putInt("ClassicScore", score)
            .putString("ClassicGrid", grid.joinToString(";") { it.joinToString(",") })
            .apply()
    }

    private fun fillTray() {
        for (i in 0 until 3) {
            if (trayShapes[i] == null || trayShapes[i]!!.placed) {
                var safeShape: Shape? = null
                
                // 35% Chance direct to 4-Square or 6-Rectangle
                if (Random.nextFloat() < 0.35f) {
                    val bigShapes = listOf(
                        arrayOf(intArrayOf(1, 1), intArrayOf(1, 1)),
                        arrayOf(intArrayOf(1, 1, 1), intArrayOf(1, 1, 1)),
                        arrayOf(intArrayOf(1, 1), intArrayOf(1, 1), intArrayOf(1, 1))
                    )
                    val rawM = bigShapes.random()
                    val testShape = Shape(rawM, Random.nextInt(1, 10))
                    if (canFitAnywhere(testShape)) {
                        safeShape = testShape
                    }
                }

                // 25% Chance for dot/line pieces to prevent stuck situations
                if (safeShape == null && Random.nextFloat() < 0.25f) {
                    val easyPieces = listOf(
                        arrayOf(intArrayOf(1)),
                        arrayOf(intArrayOf(1, 1)),
                        arrayOf(intArrayOf(1), intArrayOf(1)),
                        arrayOf(intArrayOf(1, 1, 1)),
                        arrayOf(intArrayOf(1), intArrayOf(1), intArrayOf(1))
                    )
                    val rawM = easyPieces.random()
                    safeShape = Shape(rawM, Random.nextInt(1, 10))
                }

                if (safeShape == null) {
                    for (attempt in 0..25) { 
                        val rawM = SHAPES.random()
                        val testShape = Shape(rawM, Random.nextInt(1, 10))
                        if (canFitAnywhere(testShape)) { 
                            safeShape = testShape
                            break 
                        } 
                    }
                }
                
                if (safeShape == null) {
                    safeShape = Shape(arrayOf(intArrayOf(1)), Random.nextInt(1, 10))
                }
                
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
        val outerPadding = 36f
        boardInnerSize = w - outerPadding * 2 - 24f
        cellSize = boardInnerSize / 8
        boardX = outerPadding + 12f
        boardY = 250f
        trayY = boardY + boardInnerSize + 90f
        trayCellSize = cellSize * 0.65f
        
        val bw = 600f; val bh = 130f; val cx = w / 2f
        restartBtnRect.set(cx - bw/2f, boardY + boardInnerSize/2f - 40f, cx + bw/2f, boardY + boardInnerSize/2f - 40f + bh)
        menuBtnRect.set(cx - bw/2f, restartBtnRect.bottom + 30f, cx + bw/2f, restartBtnRect.bottom + 30f + bh)
        resumeBtnRect.set(cx - bw/2f, boardY + boardInnerSize/2f - 60f, cx + bw/2f, boardY + boardInnerSize/2f - 60f + bh)
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
    }

    private fun draw3DButton(canvas: Canvas, rect: RectF, text: String, topColor: Int, bottomColor: Int, size: Float = 45f) {
        btnPaint.color = bottomColor; canvas.drawRoundRect(RectF(rect.left, rect.top + 12f, rect.right, rect.bottom + 12f), 30f, 30f, btnPaint)
        btnPaint.color = topColor; canvas.drawRoundRect(rect, 30f, 30f, btnPaint)
        drawGlossy3DText(canvas, text, rect.centerX(), rect.centerY() + size/3f, Color.WHITE, Color.DKGRAY, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Clean Warm Cream Background
        canvas.drawColor(0xFFFBF4EB.toInt())
        
        drawGlossy3DText(canvas, "SCORE", width / 4f, 100f, 0xFF4A3525.toInt(), 0xFFE0D3C4.toInt(), 45f)
        drawGlossy3DText(canvas, "$score", width / 4f, 160f, 0xFF4A3525.toInt(), 0xFF8C715A.toInt(), 65f)
        drawGlossy3DText(canvas, "BEST", (width / 4f) * 3f, 100f, 0xFF4A3525.toInt(), 0xFFE0D3C4.toInt(), 45f)
        drawGlossy3DText(canvas, "$highScore", (width / 4f) * 3f, 160f, 0xFF4A3525.toInt(), 0xFF8C715A.toInt(), 65f)
        
        val currentCoins = prefs.getInt("BoxBlastCoins", 0)
        drawCoinIcon(canvas, width / 2f - 40f, 115f, 20f)
        drawGlossy3DText(canvas, "$currentCoins", width / 2f + 10f, 130f, 0xFFB8860B.toInt(), 0xFF654321.toInt(), 45f, Paint.Align.LEFT)

        // Exact Sancha Board Frame
        val outerFrameRect = RectF(boardX - 16f, boardY - 16f, boardX + boardInnerSize + 16f, boardY + boardInnerSize + 16f)
        canvas.drawRoundRect(outerFrameRect, 38f, 38f, boardFrameRimPaint)
        canvas.drawRoundRect(outerFrameRect, 38f, 38f, boardBorderStrokePaint)

        val innerBoardRect = RectF(boardX - 6f, boardY - 6f, boardX + boardInnerSize + 6f, boardY + boardInnerSize + 6f)
        canvas.drawRoundRect(innerBoardRect, 28f, 28f, boardInnerSurfacePaint)

        // Cavity Grids
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                val cx = boardX + c * cellSize
                val cy = boardY + r * cellSize
                val cellRect = RectF(cx + 3.5f, cy + 3.5f, cx + cellSize - 3.5f, cy + cellSize - 3.5f)
                canvas.drawRoundRect(cellRect, 10f, 10f, cellEmptyPaint)
                
                if (grid[r][c] != 0) {
                    drawTile(canvas, cx + 2f, cy + 2f, cellSize - 4f, grid[r][c])
                }
            }
        }

        // Line Clear Glow
        val iteratorGlow = glowLines.iterator()
        while(iteratorGlow.hasNext()) {
            val glow = iteratorGlow.next(); glowPaint.alpha = (glow.alpha * 200).toInt()
            if (glow.isRow) canvas.drawRoundRect(RectF(boardX, boardY + glow.index * cellSize, boardX + boardInnerSize, boardY + (glow.index+1)*cellSize), 12f, 12f, glowPaint)
            else canvas.drawRoundRect(RectF(boardX + glow.index * cellSize, boardY, boardX + (glow.index+1)*cellSize, boardY + boardInnerSize), 12f, 12f, glowPaint)
            glow.alpha -= 0.05f; if (glow.alpha <= 0) iteratorGlow.remove()
        }

        // Hover Neon Grid Highlight
        draggingShape?.let { 
            if (canFitHover) {
                drawNeonHover(canvas, it, boardX + hoverCol * cellSize, boardY + hoverRow * cellSize, cellSize) 
            }
        }

        // Tray Pieces
        for (i in 0 until 3) {
            if (i != draggingShapeIndex) {
                trayShapes[i]?.let { if (!it.placed) drawShape(canvas, it, it.cx, it.cy, trayCellSize) }
            }
        }
        draggingShape?.let { drawShape(canvas, it, it.cx, it.cy, cellSize) }
        
        if(!isGameOver && !showResumePopup && !isWaitingForAd) {
            val freeShuffles = prefs.getInt("FreeShuffles", 0)
            val shuffleText = if (freeShuffles > 0) "🔀 FREE" else "🔀 50"
            draw3DButton(canvas, shuffleBtnRect, shuffleText, 0xFF8E44AD.toInt(), 0xFF5B2C6F.toInt(), 40f)
        }

        // Floating Combos & Blast Particles
        val iteratorWords = floatingWords.iterator()
        while(iteratorWords.hasNext()) {
            val fw = iteratorWords.next()
            if (fw.scale < 1f) fw.scale += 0.05f; fw.y -= 3f; fw.alpha -= 0.02f
            canvas.save(); canvas.scale(fw.scale, fw.scale, width/2f, fw.y)
            drawGlossy3DText(canvas, fw.text, width/2f, fw.y, 0xFFD97706.toInt(), 0xFF78350F.toInt(), 90f)
            canvas.restore(); if (fw.alpha <= 0) iteratorWords.remove()
        }

        if (particles.isNotEmpty()) {
            val iterator = particles.iterator(); val pPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            while (iterator.hasNext()) {
                val p = iterator.next()
                pPaint.color = p.color; pPaint.alpha = (p.life * 255).toInt().coerceIn(0, 255)
                canvas.drawCircle(p.x, p.y, p.size * p.life, pPaint)
                p.x += p.vx; p.y += p.vy; p.life -= 0.04f
                if (p.life <= 0) iterator.remove()
            }
        }

        if (showResumePopup) {
            canvas.drawColor(0xDD000000.toInt())
            drawGlossy3DText(canvas, "GAME SAVED", width / 2f, boardY + boardInnerSize / 2f - 160f, Color.WHITE, Color.DKGRAY, 80f)
            draw3DButton(canvas, resumeBtnRect, "RESUME", 0xFF2ECC71.toInt(), 0xFF1E8449.toInt())
            draw3DButton(canvas, newGameBtnRect, "NEW GAME", 0xFFE74C3C.toInt(), 0xFF922B21.toInt())
        } else if (isWaitingForAd) {
            canvas.drawColor(0xDD000000.toInt())
            drawGlossy3DText(canvas, "OUT OF MOVES", width / 2f, boardY + boardInnerSize / 2f - 80f, 0xFFE74C3C.toInt(), 0xFF922B21.toInt(), 90f)
            drawGlossy3DText(canvas, "$adCountdown", width / 2f, boardY + boardInnerSize / 2f + 60f, Color.WHITE, Color.DKGRAY, 150f)
            draw3DButton(canvas, menuBtnRect, "▶ WATCH AD", 0xFF3498DB.toInt(), 0xFF1B4F72.toInt(), 40f)
        } else if (isGameOver) {
            canvas.drawColor(0xEE000000.toInt())
            drawGlossy3DText(canvas, "GAME OVER", width / 2f, boardY + boardInnerSize / 2f - 120f, 0xFFE74C3C.toInt(), 0xFF922B21.toInt(), 110f)
            draw3DButton(canvas, restartBtnRect, "RESTART", 0xFFE74C3C.toInt(), 0xFF922B21.toInt())
            draw3DButton(canvas, menuBtnRect, "MAIN MENU", 0xFFF39C12.toInt(), 0xFFB9770E.toInt())
        }
    }

    private fun drawShape(canvas: Canvas, shape: Shape, x: Float, y: Float, size: Float) {
        for (r in 0 until shape.rows) {
            for (c in 0 until shape.cols) {
                if (shape.matrix[r][c] != 0) {
                    drawTile(canvas, x + c * size, y + r * size, size, shape.typeId)
                }
            }
        }
    }

    private fun drawTile(canvas: Canvas, x: Float, y: Float, size: Float, typeId: Int) {
        val rect = RectF(x, y, x + size, y + size)
        val bmp = blockBitmaps[typeId]
        if (bmp != null) {
            canvas.drawBitmap(bmp, null, rect, bitmapPaint)
        } else {
            val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFA07A.toInt() }
            canvas.drawRoundRect(rect, 10f, 10f, fallbackPaint)
        }
    }

    private fun drawNeonHover(canvas: Canvas, shape: Shape, x: Float, y: Float, size: Float) {
        val hoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x88FFFFFF.toInt()
            style = Paint.Style.FILL
        }
        for (r in 0 until shape.rows) for (c in 0 until shape.cols) if (shape.matrix[r][c] != 0) {
            val cellRect = RectF(x + c * size + 2, y + r * size + 2, x + c * size + size - 2, y + r * size + size - 2)
            canvas.drawRoundRect(cellRect, 10f, 10f, hoverPaint)
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
                            trayShapes[0] = Shape(arrayOf(intArrayOf(1)), 1); trayShapes[1] = null; trayShapes[2] = null; updateTrayPositions(); invalidate()
                        } else { isWaitingForAd = false; isGameOver = true; soundManager.playGameOver(); invalidate() }
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
                        draggingShapeIndex = i; draggingShape = it
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
            if (shape.matrix[r][c] != 0) { grid[rOff + r][cOff + c] = shape.typeId }
        score += 10; checkLines() 
    }

    private fun checkLines() {
        val rows = mutableListOf<Int>(); val cols = mutableListOf<Int>()
        for (r in 0 until 8) if ((0 until 8).all { c -> grid[r][c] != 0 }) rows.add(r)
        for (c in 0 until 8) if ((0 until 8).all { r -> grid[r][c] != 0 }) cols.add(c)
        val total = rows.size + cols.size

        if (total > 0) {
            soundManager.playClear()
            vibratePhone(90L)

            handler.postDelayed({ 
                val word = soundManager.playComboVoice(total)
                if (word.isNotEmpty()) floatingWords.add(FloatingWord(word, boardY + boardInnerSize/2f)) 
            }, 750)

            for (r in rows) { 
                glowLines.add(GlowLine(true, r))
                for (c in 0 until 8) { 
                    spawnBlastParticles(boardX + c * cellSize + cellSize/2f, boardY + r * cellSize + cellSize/2f)
                    grid[r][c] = 0 
                }
                score += 100 
            }
            for (c in cols) { 
                glowLines.add(GlowLine(false, c))
                for (r in 0 until 8) { 
                    if (grid[r][c] != 0) {
                        spawnBlastParticles(boardX + c * cellSize + cellSize/2f, boardY + r * cellSize + cellSize/2f)
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
                    floatingWords.add(FloatingWord("FREE SHUFFLE!", boardY + boardInnerSize/2f + 80f)) 
                }
            }
        }
    }

    private fun spawnBlastParticles(cx: Float, cy: Float) {
        val colors = listOf(0xFFFFA07A.toInt(), 0xFF98FB98.toInt(), 0xFFFF6B6B.toInt(), 0xFF87CEFA.toInt(), 0xFFDDA0DD.toInt())
        for (i in 0 until 10) {
            val vx = Random.nextFloat() * 14f - 7f
            val vy = Random.nextFloat() * 16f - 8f
            particles.add(Particle(cx, cy, vx, vy, 1f, colors.random(), cellSize * 0.18f))
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

    override fun onDetachedFromWindow() { 
        super.onDetachedFromWindow(); saveGame()
        handler.removeCallbacks(renderLoop); handler.removeCallbacks(timerRunnable); soundManager.release() 
    }
}
