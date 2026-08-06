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

class AdventureGameView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    val soundManager = SoundManager(context)
    private val prefs = context.getSharedPreferences("BoxBlastPrefs", Context.MODE_PRIVATE)
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    
    private val grid = Array(8) { IntArray(8) { 0 } }
    
    private var currentLevel = prefs.getInt("AdventureLevel", 1)
    private var targetGems = 10 + (currentLevel * 2) 
    private var gemsCollected = 0
    private var isGameOver = false
    private var isLevelComplete = false

    data class FlyingGem(var startX: Float, var startY: Float, var progress: Float = 0f)
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
    
    private val targetTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 65f; typeface = Typeface.DEFAULT_BOLD; setShadowLayer(10f, 0f, 0f, Color.BLACK) }
    private val levelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD700.toInt(); textSize = 45f; typeface = Typeface.DEFAULT_BOLD }
    private val overlayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF38EF7D.toInt(); textSize = 90f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; setShadowLayer(15f, 0f, 10f, Color.BLACK) }
    
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2CD04E.toInt(); style = Paint.Style.FILL }
    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        color = Color.WHITE; textSize = 50f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        setShadowLayer(10f, 0f, 0f, Color.BLACK)
    }

    private var cellSize = 0f
    private var boardSize = 0f
    private var boardX = 0f
    private var boardY = 0f
    private var trayY = 0f
    private var trayCellSize = 0f
    private var targetUiX = 0f
    private var targetUiY = 120f

    private val nextLevelBtnRect = RectF()
    private val handler = Handler(Looper.getMainLooper())

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
    private var draggingShapeIndex = -1
    private var draggingShape: Shape? = null
    private var dragTouchOffsetX = 0f
    private var dragTouchOffsetY = 0f
    private var hoverRow = -1; private var hoverCol = -1; private var canFitHover = false

    private val renderLoop = object : Runnable {
        override fun run() {
            invalidate()
            handler.postDelayed(this, 16L) 
        }
    }

    init {
        initLevel()
        handler.post(renderLoop)
    }

    private fun vibratePhone(duration: Long = 50L) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } catch (e: Exception) { }
    }

    private fun initLevel() {
        targetGems = 10 + (currentLevel * 2)
        gemsCollected = 0
        isGameOver = false
        isLevelComplete = false
        flyingGems.clear()
        particles.clear()

        for (r in 0 until 8) { for (c in 0 until 8) grid[r][c] = 0 }
        var spawned = 0
        val initialGems = minOf(targetGems, 12) 
        while(spawned < initialGems) {
            val r = Random.nextInt(8); val c = Random.nextInt(8)
            if (grid[r][c] == 0) { grid[r][c] = 10; spawned++ }
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
                    if (validCoords.isNotEmpty()) { val (gr, gc) = validCoords.random(); copy[gr][gc] = 10 }
                }
                trayShapes[i] = Shape(copy)
            }
        }
        if (width > 0 && height > 0) updateTrayPositions()
        checkGameOver()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padding = 30f 
        boardSize = w - padding * 2
        cellSize = boardSize / 8
        boardX = padding; boardY = padding + 220f
        trayY = boardY + boardSize + 100f
        trayCellSize = cellSize * 0.65f
        targetUiX = w / 2f
        val bw = 400f; val bh = 120f
        nextLevelBtnRect.set(w/2f - bw/2f, boardY + boardSize/2f + 100f, w/2f + bw/2f, boardY + boardSize/2f + 100f + bh)
        updateTrayPositions()
    }

    // MISSING FUNCTION ADDED HERE
    private fun updateTrayPositions() {
        if (width == 0) return
        val sectionWidth = width / 3f
        for (i in 0 until 3) {
            val shape = trayShapes[i]
            if (shape != null && !shape.placed) {
                val shapeWidth = shape.cols * trayCellSize
                val shapeHeight = shape.rows * trayCellSize
                shape.cx = (i * sectionWidth) + (sectionWidth - shapeWidth) / 2f
                shape.cy = trayY + (sectionWidth - shapeHeight) / 2f
            }
        }
    }

    private fun drawGeminiBackground(canvas: Canvas) {
        canvas.drawColor(0xFF0F172A.toInt()) 
        val time = System.currentTimeMillis()
        
        val cx1 = width / 2f + Math.sin(time / 3000.0).toFloat() * 300f
        val cy1 = height / 3f + Math.cos(time / 2000.0).toFloat() * 300f
        neonBgPaint.shader = RadialGradient(cx1, cy1, 600f, intArrayOf(0x55E94560, 0x00E94560), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), neonBgPaint)

        val cx2 = width / 2f + Math.cos(time / 2500.0).toFloat() * 400f
        val cy2 = height / 1.5f + Math.sin(time / 3500.0).toFloat() * 400f
        neonBgPaint.shader = RadialGradient(cx2, cy2, 700f, intArrayOf(0x550F3460, 0x000F3460), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), neonBgPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawGeminiBackground(canvas)

        canvas.drawText("LEVEL $currentLevel", 40f, 80f, levelTextPaint)
        drawStarGem(canvas, targetUiX - 60f, targetUiY - 40f, 50f)
        canvas.drawText("$gemsCollected / $targetGems", targetUiX + 10f, targetUiY + 10f, targetTextPaint)

        val rect = RectF(boardX, boardY, boardX + boardSize, boardY + boardSize)
        canvas.drawRoundRect(rect, 24f, 24f, boardPaint)
        canvas.drawRoundRect(rect, 24f, 24f, boardBorderPaint)

        val emptyPaint = Paint().apply { color = 0x2AFFFFFF; style = Paint.Style.STROKE; strokeWidth = 2f }

        for (r in 0 until 8) {
            for (c in 0 until 8) {
                val cx = boardX + c * cellSize
                val cy = boardY + r * cellSize
                val cellId = grid[r][c]
                val cellRect = RectF(cx + 4, cy + 4, cx + cellSize - 4, cy + cellSize - 4)
                canvas.drawRoundRect(cellRect, 12f, 12f, emptyPaint)
                if (cellId != 0) drawGlassy3DBlock(canvas, cx, cy, cellSize, cellId)
            }
        }

        draggingShape?.let { shape ->
            if (canFitHover && hoverRow in 0..7 && hoverCol in 0..7) {
                drawNeonShadow(canvas, shape, boardX + hoverCol * cellSize, boardY + hoverRow * cellSize, cellSize)
            }
        }

        for (i in 0 until 3) {
            if (i == draggingShapeIndex) continue
            val shape = trayShapes[i]
            if (shape != null && !shape.placed) drawShape(canvas, shape, shape.cx, shape.cy, trayCellSize)
        }

        draggingShape?.let { drawShape(canvas, it, it.cx, it.cy, cellSize) }

        if (particles.isNotEmpty()) {
            val iterator = particles.iterator()
            val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            while (iterator.hasNext()) {
                val p = iterator.next()
                particlePaint.color = p.color
                particlePaint.alpha = (p.life * 255).toInt().coerceIn(0, 255)
                canvas.drawCircle(p.x, p.y, cellSize * 0.15f * p.life, particlePaint)
                
                p.x += p.vx
                p.y += p.vy
                p.vy += 1.5f 
                p.life -= 0.03f
                if (p.life <= 0) iterator.remove()
            }
        }

        if (flyingGems.isNotEmpty()) {
            val iterator = flyingGems.iterator()
            while (iterator.hasNext()) {
                val gem = iterator.next()
                gem.progress += 0.04f 
                if (gem.progress >= 1f) {
                    gemsCollected++
                    if (gemsCollected >= targetGems && !isLevelComplete) {
                        isLevelComplete = true; soundManager.playClear() 
                    }
                    soundManager.playPick() 
                    iterator.remove()
                } else {
                    val currentX = gem.startX + (targetUiX - 60f - gem.startX) * gem.progress
                    val currentY = gem.startY + (targetUiY - 40f - gem.startY) * gem.progress
                    drawStarGem(canvas, currentX, currentY, cellSize * 0.7f * (1f - gem.progress * 0.3f))
                }
            }
        }

        if (isLevelComplete && flyingGems.isEmpty()) {
            canvas.drawColor(0xDD000000.toInt())
            canvas.drawText("WELL DONE!", width / 2f, boardY + boardSize / 2f, overlayTextPaint)
            canvas.drawRoundRect(nextLevelBtnRect, 30f, 30f, btnPaint)
            canvas.drawText("NEXT LEVEL", nextLevelBtnRect.centerX(), nextLevelBtnRect.centerY() + 15f, btnTextPaint)
        } else if (isGameOver) {
            overlayTextPaint.color = 0xFFFF5E62.toInt()
            canvas.drawColor(0xDD000000.toInt())
            canvas.drawText("NO MOVES!", width / 2f, boardY + boardSize / 2f, overlayTextPaint)
            btnPaint.color = 0xFFFF5E62.toInt()
            canvas.drawRoundRect(nextLevelBtnRect, 30f, 30f, btnPaint)
            canvas.drawText("RESTART", nextLevelBtnRect.centerX(), nextLevelBtnRect.centerY() + 15f, btnTextPaint)
            btnPaint.color = 0xFF2CD04E.toInt() 
        }
    }

    private fun drawShape(canvas: Canvas, shape: Shape, x: Float, y: Float, size: Float) {
        for (r in 0 until shape.rows) {
            for (c in 0 until shape.cols) {
                if (shape.matrix[r][c] != 0) {
                    drawGlassy3DBlock(canvas, x + c * size, y + r * size, size, shape.matrix[r][c])
                }
            }
        }
    }

    private fun drawNeonShadow(canvas: Canvas, shape: Shape, x: Float, y: Float, size: Float) {
        var firstColorId = 0
        for (row in shape.matrix) {
            for (cell in row) {
                if (cell != 0) {
                    firstColorId = cell
                    break
                }
            }
            if (firstColorId != 0) break
        }
        
        var neonColor = getBaseColor(if (firstColorId != 0) firstColorId else 1)
        if (firstColorId == 10) neonColor = Color.YELLOW 
        
        neonShadowPaint.color = neonColor
        neonShadowPaint.setShadowLayer(25f, 0f, 0f, neonColor)
        
        for (r in 0 until shape.rows) {
            for (c in 0 until shape.cols) {
                if (shape.matrix[r][c] != 0) {
                    val rect = RectF(x + c * size + 4, y + r * size + 4, x + c * size + size - 4, y + r * size + size - 4)
                    canvas.drawRoundRect(rect, 12f, 12f, neonShadowPaint)
                }
            }
        }
    }

    private fun drawGlassy3DBlock(canvas: Canvas, x: Float, y: Float, size: Float, colorId: Int) {
        val rect = RectF(x + 2, y + 2, x + size - 2, y + size - 2)

        if (colorId == 10) {
            blockBasePaint.color = 0xFF0D152B.toInt()
            canvas.drawRoundRect(rect, 16f, 16f, blockBasePaint)
            
            val innerRect = RectF(rect.left + 8f, rect.top + 8f, rect.right - 8f, rect.bottom - 8f)
            blockBasePaint.color = 0xFF050A1A.toInt() 
            canvas.drawRoundRect(innerRect, 8f, 8f, blockBasePaint)

            drawStarGem(canvas, x + size * 0.15f, y + size * 0.15f, size * 0.7f)
            return
        }

        val baseColor = getBaseColor(colorId)
        val grad = LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(adjustColorLightness(baseColor, 1.4f), baseColor, adjustColorLightness(baseColor, 0.6f)),
            null, Shader.TileMode.CLAMP)
        
        blockBasePaint.shader = grad
        canvas.drawRoundRect(rect, 16f, 16f, blockBasePaint)
        blockBasePaint.shader = null 

        val overlayRect = RectF(rect.left + 2, rect.top + 2, rect.right - 2, rect.top + size * 0.4f)
        val shineGrad = LinearGradient(overlayRect.left, overlayRect.top, overlayRect.left, overlayRect.bottom,
            0x88FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        glassOverlayPaint.shader = shineGrad
        canvas.drawRoundRect(overlayRect, 14f, 14f, glassOverlayPaint)
    }

    private fun adjustColorLightness(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    }

    private fun drawStarGem(canvas: Canvas, x: Float, y: Float, size: Float) {
        val cx = x + size / 2f
        val cy = y + size / 2f
        val outerRadius = size / 2f
        val innerRadius = outerRadius / 2.2f

        val path = Path()
        for (i in 0 until 10) {
            val angle = i * (Math.PI / 5) - (Math.PI / 2)
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            val px = cx + cos(angle).toFloat() * radius
            val py = cy + sin(angle).toFloat() * radius
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()

        val gemGrad = RadialGradient(cx, cy, outerRadius, intArrayOf(0xFFFFFFA0.toInt(), 0xFFFFD700.toInt(), 0xFFE65C00.toInt()), null, Shader.TileMode.CLAMP)
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gemGrad; style = Paint.Style.FILL }
        
        canvas.drawPath(path, starPaint)
        
        starPaint.shader = null
        starPaint.color = 0xAAFFFFFF.toInt()
        canvas.drawCircle(cx - size*0.15f, cy - size*0.15f, size*0.1f, starPaint)
    }

    private fun getBaseColor(id: Int): Int {
        return when (id) {
            1 -> 0xFFE63946.toInt() 
            2 -> 0xFF00B4D8.toInt() 
            3 -> 0xFF2DC653.toInt() 
            4 -> 0xFFFFB703.toInt() 
            5 -> 0xFF9D4EDD.toInt() 
            else -> 0xFFFFFFFF.toInt()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tx = event.x
        val ty = event.y

        if (event.action == MotionEvent.ACTION_DOWN) {
            if ((isLevelComplete || isGameOver) && nextLevelBtnRect.contains(tx, ty)) {
                soundManager.playBtnClick()
                if (isLevelComplete) {
                    currentLevel++
                    prefs.edit().putInt("AdventureLevel", currentLevel).apply()
                }
                initLevel()
                return true
            }
        }
        if (isGameOver || isLevelComplete) return true

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                for (i in 0 until 3) {
                    val shape = trayShapes[i]
                    if (shape != null && !shape.placed) {
                        val hitRect = RectF(shape.cx - 30f, shape.cy - 30f, shape.cx + (shape.cols * trayCellSize) + 30f, shape.cy + (shape.rows * trayCellSize) + 30f)
                        if (hitRect.contains(tx, ty)) {
                            soundManager.playPick()
                            draggingShapeIndex = i
                            draggingShape = shape
                            shape.cx = tx - (shape.cols * cellSize) / 2f
                            shape.cy = ty - (shape.rows * cellSize) - 180f
                            dragTouchOffsetX = tx - shape.cx; dragTouchOffsetY = ty - shape.cy
                            return true
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                draggingShape?.let { shape ->
                    shape.cx = tx - dragTouchOffsetX; shape.cy = ty - dragTouchOffsetY
                    val centerCol = (shape.cx + (shape.cols * cellSize)/2f - boardX) / cellSize
                    val centerRow = (shape.cy + (shape.rows * cellSize)/2f - boardY) / cellSize
                    hoverCol = (centerCol - shape.cols/2f).roundToInt()
                    hoverRow = (centerRow - shape.rows/2f).roundToInt()
                    canFitHover = canPlaceShape(shape, hoverRow, hoverCol)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingShape?.let { shape ->
                    if (canFitHover && hoverRow in 0..7 && hoverCol in 0..7) {
                        placeShape(shape, hoverRow, hoverCol)
                        shape.placed = true
                        soundManager.playDrop()
                        if (trayShapes.all { it == null || it.placed }) fillTray() else checkGameOver()
                    } else { updateTrayPositions() }
                    draggingShape = null; draggingShapeIndex = -1; hoverRow = -1; hoverCol = -1; canFitHover = false
                    return true
                }
            }
        }
        return true
    }

    private fun canPlaceShape(shape: Shape, rOffset: Int, cOffset: Int): Boolean {
        for (r in 0 until shape.rows) {
            for (c in 0 until shape.cols) {
                if (shape.matrix[r][c] != 0) {
                    val targetR = rOffset + r; val targetC = cOffset + c
                    if (targetR !in 0..7 || targetC !in 0..7 || grid[targetR][targetC] != 0) return false
                }
            }
        }
        return true
    }

    private fun placeShape(shape: Shape, rOffset: Int, cOffset: Int) {
        for (r in 0 until shape.rows) {
            for (c in 0 until shape.cols) {
                if (shape.matrix[r][c] != 0) grid[rOffset + r][cOffset + c] = shape.matrix[r][c]
            }
        }
        checkLines()
    }

    private fun checkLines() {
        val rowsToClear = mutableListOf<Int>()
        val colsToClear = mutableListOf<Int>()

        for (r in 0 until 8) { if ((0 until 8).all { c -> grid[r][c] != 0 }) rowsToClear.add(r) }
        for (c in 0 until 8) { if ((0 until 8).all { r -> grid[r][c] != 0 }) colsToClear.add(c) }

        val totalLines = rowsToClear.size + colsToClear.size

        if (totalLines > 0) {
            soundManager.playClear()
            vibratePhone(100L) 
            
            handler.postDelayed({ soundManager.playComboVoice(totalLines) }, 600)
            
            for (r in rowsToClear) {
                for (c in 0 until 8) {
                    val colorId = grid[r][c]
                    val bX = boardX + c * cellSize + cellSize/2f
                    val bY = boardY + r * cellSize + cellSize/2f
                    
                    for(i in 0..5) {
                        particles.add(Particle(bX, bY, Random.nextFloat()*16-8f, Random.nextFloat()*16-12f, 1f, getBaseColor(colorId)))
                    }
                    if (colorId == 10) flyingGems.add(FlyingGem(bX - cellSize/2f, bY - cellSize/2f))
                    grid[r][c] = 0
                }
            }
            for (c in colsToClear) {
                for (r in 0 until 8) {
                    val colorId = grid[r][c]
                    if (colorId != 0) { 
                        val bX = boardX + c * cellSize + cellSize/2f
                        val bY = boardY + r * cellSize + cellSize/2f
                        for(i in 0..5) {
                            particles.add(Particle(bX, bY, Random.nextFloat()*16-8f, Random.nextFloat()*16-12f, 1f, getBaseColor(colorId)))
                        }
                        if (colorId == 10) flyingGems.add(FlyingGem(bX - cellSize/2f, bY - cellSize/2f))
                        grid[r][c] = 0
                    }
                }
            }
        }
    }

    private fun checkGameOver() {
        if(isLevelComplete) return
        var canMakeMove = false
        for (shape in trayShapes) {
            if (shape != null && !shape.placed) {
                for (r in 0 until 8) {
                    for (c in 0 until 8) { if (canPlaceShape(shape, r, c)) { canMakeMove = true; break } }
                    if (canMakeMove) break
                }
            }
            if (canMakeMove) break
        }
        if (!canMakeMove) { isGameOver = true; soundManager.playGameOver() }
    }

    override fun onDetachedFromWindow() { 
        super.onDetachedFromWindow()
        handler.removeCallbacks(renderLoop)
        soundManager.release() 
    }
}
