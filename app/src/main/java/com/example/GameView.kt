package com.example

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt
import kotlin.random.Random

class GameView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val soundManager = SoundManager(context)
    
    // Grid ab raw color ki jagah Color ID (1 to 5) store karega gradient ke liye
    private val grid = Array(8) { IntArray(8) { 0 } }
    private var score = 0
    private var isGameOver = false

    // Background & Board Paints
    private val bgPaint = Paint().apply { style = Paint.Style.FILL }
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF121E47.toInt() // Deep premium navy
        style = Paint.Style.FILL
    }
    private val boardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4EA8FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }
    
    // Block Paints
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val blockShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88000000.toInt()
        style = Paint.Style.FILL
    }
    private val blockGlossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x77FFFFFF // White Gloss
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    // Magnet Glow Paint
    private val glowStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD700.toInt() // Vibrant Gold
        style = Paint.Style.STROKE
        strokeWidth = 8f
        setShadowLayer(20f, 0f, 0f, 0xFFFFD700.toInt())
    }
    private val glowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x44FFD700 // Light Gold Transparent
        style = Paint.Style.FILL
    }

    // UI Paints
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 55f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1D3273.toInt()
        style = Paint.Style.FILL
    }
    private val gameOverTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF5E62.toInt()
        textSize = 100f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        setShadowLayer(15f, 0f, 10f, Color.BLACK)
    }

    private var cellSize = 0f
    private var boardSize = 0f
    private var boardX = 0f
    private var boardY = 0f
    private var trayY = 0f
    private var trayCellSize = 0f

    val SHAPES = listOf(
        arrayOf(intArrayOf(1)),
        arrayOf(intArrayOf(1, 1)),
        arrayOf(intArrayOf(1), intArrayOf(1)),
        arrayOf(intArrayOf(1, 1), intArrayOf(1, 1)),
        arrayOf(intArrayOf(1, 1, 1)),
        arrayOf(intArrayOf(1), intArrayOf(1), intArrayOf(1)),
        arrayOf(intArrayOf(1, 1, 1, 1)),
        arrayOf(intArrayOf(1), intArrayOf(1), intArrayOf(1), intArrayOf(1)),
        arrayOf(intArrayOf(1, 0), intArrayOf(1, 1)),
        arrayOf(intArrayOf(0, 1), intArrayOf(1, 1)),
        arrayOf(intArrayOf(1, 1), intArrayOf(1, 0)),
        arrayOf(intArrayOf(1, 1), intArrayOf(0, 1))
    )

    // Color ID instead of raw color
    class Shape(val matrix: Array<IntArray>, val colorId: Int) {
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

    init {
        fillTray()
    }

    private fun fillTray() {
        for (i in 0 until 3) {
            if (trayShapes[i] == null || trayShapes[i]!!.placed) {
                trayShapes[i] = randomShape()
            }
        }
        if (width > 0 && height > 0) updateTrayPositions()
        checkGameOver()
    }

    private fun randomShape(): Shape {
        val matrix = SHAPES[Random.nextInt(SHAPES.size)]
        val copy = Array(matrix.size) { r -> IntArray(matrix[r].size) { c -> matrix[r][c] } }
        // 1 to 5 Color IDs
        return Shape(copy, Random.nextInt(1, 6))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Reduced padding = Bigger Box Size
        val padding = 35f 
        boardSize = w - padding * 2
        cellSize = boardSize / 8
        boardX = padding
        boardY = padding + 220f
        
        trayY = boardY + boardSize + 80f
        trayCellSize = cellSize * 0.65f // Bigger tray sizes
        
        updateTrayPositions()
    }

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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Premium Dark Gradient BG
        bgPaint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), 0xFF121B3B.toInt(), 0xFF080C1F.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Top UI
        val scoreText = "Score: $score"
        val scoreWidth = textPaint.measureText(scoreText)
        val scoreRect = RectF(50f, 70f, 50f + scoreWidth + 60f, 150f)
        canvas.drawRoundRect(scoreRect, 40f, 40f, pillPaint)
        canvas.drawRoundRect(scoreRect, 40f, 40f, boardBorderPaint)
        canvas.drawText(scoreText, 80f, 125f, textPaint)

        // Board
        val rect = RectF(boardX, boardY, boardX + boardSize, boardY + boardSize)
        canvas.drawRoundRect(rect, 24f, 24f, boardPaint)
        canvas.drawRoundRect(rect, 24f, 24f, boardBorderPaint)

        val emptyPaint = Paint().apply { color = 0x1AFFFFFF; style = Paint.Style.STROKE; strokeWidth = 4f }

        // Draw Grid & Placed Blocks
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                val cx = boardX + c * cellSize
                val cy = boardY + r * cellSize
                val cellId = grid[r][c]
                
                val cellRect = RectF(cx + 2, cy + 2, cx + cellSize - 2, cy + cellSize - 2)
                canvas.drawRoundRect(cellRect, 12f, 12f, emptyPaint)
                
                if (cellId != 0) drawBlock(canvas, cx, cy, cellSize, cellId)
            }
        }

        // Draw Hover Magnet Glow
        draggingShape?.let { shape ->
            if (canFitHover && hoverRow in 0..7 && hoverCol in 0..7) {
                for (r in 0 until shape.rows) {
                    for (c in 0 until shape.cols) {
                        if (shape.matrix[r][c] != 0) {
                            val hx = boardX + (hoverCol + c) * cellSize
                            val hy = boardY + (hoverRow + r) * cellSize
                            val hRect = RectF(hx + 2, hy + 2, hx + cellSize - 2, hy + cellSize - 2)
                            canvas.drawRoundRect(hRect, 16f, 16f, glowFillPaint)
                            canvas.drawRoundRect(hRect, 16f, 16f, glowStrokePaint)
                        }
                    }
                }
            }
        }

        // Draw Tray Shapes
        for (i in 0 until 3) {
            if (i == draggingShapeIndex) continue
            val shape = trayShapes[i]
            if (shape != null && !shape.placed) {
                drawShape(canvas, shape, shape.cx, shape.cy, trayCellSize)
            }
        }

        // INSTANT SNAPPING SYSTEM (Lag-free)
        draggingShape?.let { shape ->
            if (canFitHover && hoverRow in 0..7 && hoverCol in 0..7) {
                // Instantly snap to the grid position visually
                val snapX = boardX + hoverCol * cellSize
                val snapY = boardY + hoverRow * cellSize
                drawShape(canvas, shape, snapX, snapY, cellSize)
            } else {
                // Follow finger if not hovering over valid slot
                drawShape(canvas, shape, shape.cx, shape.cy, cellSize)
            }
        }

        if (isGameOver) {
            canvas.drawColor(0xBB000000.toInt())
            canvas.drawText("GAME OVER", width / 2f, boardY + boardSize / 2f, gameOverTextPaint)
        }
    }

    private fun drawShape(canvas: Canvas, shape: Shape, x: Float, y: Float, size: Float) {
        for (r in 0 until shape.rows) {
            for (c in 0 until shape.cols) {
                if (shape.matrix[r][c] != 0) {
                    val bx = x + c * size
                    val by = y + r * size
                    drawBlock(canvas, bx, by, size, shape.colorId)
                }
            }
        }
    }

    // 3D Premium Block Rendering
    private fun drawBlock(canvas: Canvas, x: Float, y: Float, size: Float, colorId: Int) {
        val p = 3f // Reduced padding for bulkier blocks
        val rect = RectF(x + p, y + p, x + size - p, y + size - p)
        val rad = size * 0.2f // Proportional smooth rounded corners

        // Drop Shadow
        val shadowRect = RectF(rect).apply { offset(0f, 6f) }
        canvas.drawRoundRect(shadowRect, rad, rad, blockShadowPaint)

        // Gradient Fill (Top Light, Bottom Dark)
        val (topColor, bottomColor) = getGradientColors(colorId)
        blockPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            topColor, bottomColor, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, rad, rad, blockPaint)

        // Top Inner Gloss/Highlight for 3D POP
        val glossRect = RectF(rect.left + 2f, rect.top + 2f, rect.right - 2f, rect.bottom - 4f)
        canvas.drawRoundRect(glossRect, rad - 2f, rad - 2f, blockGlossPaint)
    }

    // Premium Color Palette
    private fun getGradientColors(id: Int): Pair<Int, Int> {
        return when (id) {
            1 -> Pair(0xFFFF5E62.toInt(), 0xFFC7181E.toInt()) // Red
            2 -> Pair(0xFF00C6FF.toInt(), 0xFF0061D9.toInt()) // Blue
            3 -> Pair(0xFF38EF7D.toInt(), 0xFF11998E.toInt()) // Green
            4 -> Pair(0xFFFFFC00.toInt(), 0xFFE6A300.toInt()) // Yellow
            5 -> Pair(0xFFD537FE.toInt(), 0xFF8A00B0.toInt()) // Purple
            else -> Pair(0xFFFFFFFF.toInt(), 0xFFAAAAAA.toInt())
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tx = event.x
        val ty = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isGameOver) {
                    restartGame()
                    return true 
                }

                for (i in 0 until 3) {
                    val shape = trayShapes[i]
                    if (shape != null && !shape.placed) {
                        val w = shape.cols * trayCellSize
                        val h = shape.rows * trayCellSize
                        val hitRect = RectF(shape.cx - 30f, shape.cy - 30f, shape.cx + w + 30f, shape.cy + h + 30f)
                        if (hitRect.contains(tx, ty)) {
                            soundManager.playPick()
                            draggingShapeIndex = i
                            draggingShape = shape
                            
                            val newW = shape.cols * cellSize
                            val newH = shape.rows * cellSize
                            
                            // Center shape directly under finger for better drag accuracy
                            shape.cx = tx - newW / 2f
                            shape.cy = ty - newH - 120f
                            
                            dragTouchOffsetX = tx - shape.cx
                            dragTouchOffsetY = ty - shape.cy
                            invalidate()
                            return true
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                draggingShape?.let { shape ->
                    shape.cx = tx - dragTouchOffsetX
                    shape.cy = ty - dragTouchOffsetY
                    
                    // Improved hover detection
                    hoverCol = ((shape.cx + cellSize / 2 - boardX) / cellSize).roundToInt()
                    hoverRow = ((shape.cy + cellSize / 2 - boardY) / cellSize).roundToInt()
                    
                    canFitHover = canPlaceShape(shape, hoverRow, hoverCol)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingShape?.let { shape ->
                    if (canFitHover && hoverRow in 0..7 && hoverCol in 0..7) {
                        placeShape(shape, hoverRow, hoverCol)
                        shape.placed = true
                        soundManager.playDrop()

                        if (trayShapes.all { it == null || it.placed }) {
                            fillTray()
                        } else {
                            checkGameOver()
                        }
                    } else {
                        updateTrayPositions()
                    }
                    draggingShape = null
                    draggingShapeIndex = -1
                    hoverRow = -1
                    hoverCol = -1
                    canFitHover = false
                    invalidate()
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
                    val targetR = rOffset + r
                    val targetC = cOffset + c
                    if (targetR !in 0..7 || targetC !in 0..7) return false
                    if (grid[targetR][targetC] != 0) return false
                }
            }
        }
        return true
    }

    private fun placeShape(shape: Shape, rOffset: Int, cOffset: Int) {
        var blocksPlaced = 0
        for (r in 0 until shape.rows) {
            for (c in 0 until shape.cols) {
                if (shape.matrix[r][c] != 0) {
                    grid[rOffset + r][cOffset + c] = shape.colorId
                    blocksPlaced++
                }
            }
        }
        score += blocksPlaced * 10
        checkLines()
    }

    private fun checkLines() {
        val rowsToClear = mutableListOf<Int>()
        val colsToClear = mutableListOf<Int>()

        for (r in 0 until 8) {
            if ((0 until 8).all { c -> grid[r][c] != 0 }) rowsToClear.add(r)
        }
        for (c in 0 until 8) {
            if ((0 until 8).all { r -> grid[r][c] != 0 }) colsToClear.add(c)
        }

        if (rowsToClear.isNotEmpty() || colsToClear.isNotEmpty()) {
            soundManager.playClear()
        }

        for (r in rowsToClear) {
            for (c in 0 until 8) grid[r][c] = 0
            score += 100
        }
        for (c in colsToClear) {
            for (r in 0 until 8) grid[r][c] = 0
            score += 100
        }
    }
    
    private fun checkGameOver() {
        var canMakeMove = false
        for (shape in trayShapes) {
            if (shape != null && !shape.placed) {
                for (r in 0 until 8) {
                    for (c in 0 until 8) {
                        if (canPlaceShape(shape, r, c)) {
                            canMakeMove = true
                            break
                        }
                    }
                    if (canMakeMove) break
                }
            }
            if (canMakeMove) break
        }
        
        if (!canMakeMove) {
            isGameOver = true
            soundManager.playGameOver()
            invalidate()
        }
    }

    private fun restartGame() {
        for (r in 0 until 8) {
            for (c in 0 until 8) grid[r][c] = 0
        }
        score = 0
        isGameOver = false
        for (i in 0 until 3) trayShapes[i] = null
        fillTray()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        soundManager.release()
    }
}
