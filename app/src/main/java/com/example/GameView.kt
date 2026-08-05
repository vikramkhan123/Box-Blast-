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
    private val grid = Array(8) { IntArray(8) { 0 } }
    private var score = 0
    private var isGameOver = false

    private val bgPaint = Paint().apply { style = Paint.Style.FILL }
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1D3273.toInt()
        style = Paint.Style.FILL
    }
    private val boardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4EA8FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val blockShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66000000
        style = Paint.Style.FILL
    }
    private val glowStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFAD02C.toInt() 
        style = Paint.Style.STROKE
        strokeWidth = 10f
        setShadowLayer(15f, 0f, 0f, 0xFFFAD02C.toInt())
    }
    private val glowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FAD02C 
        style = Paint.Style.FILL
    }
    private val gameOverTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF5E62.toInt()
        textSize = 100f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        setShadowLayer(15f, 0f, 10f, Color.BLACK)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 50f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1D3273.toInt()
        style = Paint.Style.FILL
    }
    private val restartBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var cellSize = 0f
    private var boardSize = 0f
    private var boardX = 0f
    private var boardY = 0f
    private var trayY = 0f
    private var trayCellSize = 0f

    private val colors = intArrayOf(
        0xFFFF5E62.toInt(), 0xFF00C6FF.toInt(), 0xFF11998E.toInt(),
        0xFFFFFC00.toInt(), 0xFFB92B27.toInt()
    )

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

    class Shape(val matrix: Array<IntArray>, val color: Int) {
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
    private val restartRect = RectF()
    private val settingsRect = RectF()

    init {
        fillTray()
    }

    private fun fillTray() {
        for (i in 0 until 3) {
            if (trayShapes[i] == null || trayShapes[i]!!.placed) {
                trayShapes[i] = randomShape()
            }
        }
        if (width > 0 && height > 0) {
            updateTrayPositions()
        }
        checkGameOver()
    }

    private fun randomShape(): Shape {
        val matrix = SHAPES[Random.nextInt(SHAPES.size)]
        val copy = Array(matrix.size) { r -> IntArray(matrix[r].size) { c -> matrix[r][c] } }
        return Shape(copy, colors[Random.nextInt(colors.size)])
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padding = 60f
        boardSize = w - padding * 2
        cellSize = boardSize / 8
        boardX = padding
        boardY = padding + 250f
        trayY = boardY + boardSize + 80f
        trayCellSize = cellSize * 0.6f
        updateTrayPositions()
        restartBtnPaint.shader = LinearGradient(0f, 0f, 0f, 120f,
            0xFF4CD964.toInt(), 0xFF2CD04E.toInt(), Shader.TileMode.CLAMP)
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
        bgPaint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), 0xFF162456.toInt(), 0xFF0A0D24.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val scoreText = "Score: $score"
        val scoreWidth = textPaint.measureText(scoreText)
        val scoreRect = RectF(60f, 80f, 60f + scoreWidth + 60f, 160f)
        canvas.drawRoundRect(scoreRect, 40f, 40f, pillPaint)
        canvas.drawRoundRect(scoreRect, 40f, 40f, boardBorderPaint)
        canvas.drawText(scoreText, 90f, 135f, textPaint)

        settingsRect.set(width - 160f, 80f, width - 60f, 160f)
        canvas.drawRoundRect(settingsRect, 40f, 40f, pillPaint)
        canvas.drawRoundRect(settingsRect, 40f, 40f, boardBorderPaint)
        canvas.drawCircle(settingsRect.centerX(), settingsRect.centerY(), 15f, boardBorderPaint)

        val rect = RectF(boardX, boardY, boardX + boardSize, boardY + boardSize)
        canvas.drawRoundRect(rect, 30f, 30f, boardPaint)
        canvas.drawRoundRect(rect, 30f, 30f, boardBorderPaint)

        val emptyPaint = Paint().apply { color = 0x22FFFFFF; style = Paint.Style.STROKE; strokeWidth = 3f }

        for (r in 0 until 8) {
            for (c in 0 until 8) {
                val cx = boardX + c * cellSize
                val cy = boardY + r * cellSize
                val cellColor = grid[r][c]
                val cellRect = RectF(cx + 4, cy + 4, cx + cellSize - 4, cy + cellSize - 4)
                canvas.drawRoundRect(cellRect, 12f, 12f, emptyPaint)
                if (cellColor != 0) drawBlock(canvas, cx, cy, cellSize, cellColor)
            }
        }

        draggingShape?.let { shape ->
            if (canFitHover && hoverRow in 0..7 && hoverCol in 0..7) {
                for (r in 0 until shape.rows) {
                    for (c in 0 until shape.cols) {
                        if (shape.matrix[r][c] != 0) {
                            val targetR = hoverRow + r
                            val targetC = hoverCol + c
                            if (targetR in 0..7 && targetC in 0..7) {
                                val hx = boardX + targetC * cellSize
                                val hy = boardY + targetR * cellSize
                                val hRect = RectF(hx + 2, hy + 2, hx + cellSize - 2, hy + cellSize - 2)
                                canvas.drawRoundRect(hRect, 16f, 16f, glowFillPaint)
                                canvas.drawRoundRect(hRect, 16f, 16f, glowStrokePaint)
                            }
                        }
                    }
                }
            }
        }

        for (i in 0 until 3) {
            if (i == draggingShapeIndex) continue
            val shape = trayShapes[i]
            if (shape != null && !shape.placed) {
                drawShape(canvas, shape, shape.cx, shape.cy, trayCellSize)
            }
        }

        val restartW = 300f
        restartRect.set((width - restartW)/2f, height - 200f, (width + restartW)/2f, height - 100f)
        val shadow = RectF(restartRect).apply { offset(0f, 10f) }
        canvas.drawRoundRect(shadow, 40f, 40f, blockShadowPaint)
        canvas.drawRoundRect(restartRect, 40f, 40f, restartBtnPaint)
        val rText = "Restart"
        val tw = textPaint.measureText(rText)
        canvas.drawText(rText, restartRect.centerX() - tw/2f, restartRect.centerY() + 15f, textPaint)

        draggingShape?.let { shape -> drawShape(canvas, shape, shape.cx, shape.cy, cellSize) }

        if (isGameOver) {
            canvas.drawColor(0x99000000.toInt())
            canvas.drawText("OUT OF MOVES!", width / 2f, boardY + boardSize / 2f, gameOverTextPaint)
        }
    }

    private fun drawShape(canvas: Canvas, shape: Shape, x: Float, y: Float, size: Float) {
        for (r in 0 until shape.rows) {
            for (c in 0 until shape.cols) {
                if (shape.matrix[r][c] != 0) {
                    val bx = x + c * size
                    val by = y + r * size
                    drawBlock(canvas, bx, by, size, shape.color)
                }
            }
        }
    }

    private fun drawBlock(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
        val p = 4f
        val rect = RectF(x + p, y + p, x + size - p, y + size - p)
        val rad = 16f
        val shadowRect = RectF(rect).apply { offset(3f, 5f) }
        canvas.drawRoundRect(shadowRect, rad, rad, blockShadowPaint)
        blockPaint.color = color
        canvas.drawRoundRect(rect, rad, rad, blockPaint)
        val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x55FFFFFF; style = Paint.Style.STROKE; strokeWidth = 3f }
        val innerRect = RectF(rect.left + 2, rect.top + 2, rect.right - 2, rect.bottom - 2)
        canvas.drawRoundRect(innerRect, rad-2, rad-2, glossPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tx = event.x
        val ty = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (restartRect.contains(tx, ty)) {
                    restartGame()
                    return true
                }
                if (isGameOver) return true 

                for (i in 0 until 3) {
                    val shape = trayShapes[i]
                    if (shape != null && !shape.placed) {
                        val w = shape.cols * trayCellSize
                        val h = shape.rows * trayCellSize
                        val hitRect = RectF(shape.cx - 30f, shape.cy - 30f, shape.cx + w + 30f, shape.cy + h + 30f)
                        if (hitRect.contains(tx, ty)) {
                            soundManager.playPick() // PICK SOUND ADDED HERE
                            draggingShapeIndex = i
                            draggingShape = shape
                            val newW = shape.cols * cellSize
                            val newH = shape.rows * cellSize
                            shape.cx = tx - newW / 2f
                            shape.cy = ty - newH - 80f
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
                        soundManager.playDrop() // DROP SOUND ADDED HERE

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
                    grid[rOffset + r][cOffset + c] = shape.color
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
            soundManager.playClear() // CLEAR/BLAST SOUND ADDED HERE
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
            soundManager.playGameOver() // GAME OVER SOUND ADDED HERE
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
        soundManager.release() // Resource free karein jab view close ho
    }
}
