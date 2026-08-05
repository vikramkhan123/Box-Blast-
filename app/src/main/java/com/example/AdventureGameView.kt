package com.example

import android.content.Context
import android.graphics.*
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

    private val soundManager = SoundManager(context)
    private val grid = Array(8) { IntArray(8) { 0 } }
    
    // Level & Gems Logic
    private var targetGems = 20
    private var gemsCollected = 0
    private var isGameOver = false
    private var isLevelComplete = false

    // Flying Animation Data
    data class FlyingGem(var startX: Float, var startY: Float, var progress: Float = 0f)
    private val flyingGems = mutableListOf<FlyingGem>()

    // Paints
    private val bgPaint = Paint().apply { style = Paint.Style.FILL }
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B264A.toInt(); style = Paint.Style.FILL }
    private val boardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF354B8B.toInt(); style = Paint.Style.STROKE; strokeWidth = 12f }
    
    private val blockBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val blockLightEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF; style = Paint.Style.FILL }
    private val blockDarkEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000; style = Paint.Style.FILL }

    private val glowStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF42E5FF.toInt(); style = Paint.Style.STROKE; strokeWidth = 8f; setShadowLayer(25f, 0f, 0f, 0xFF42E5FF.toInt()) }
    private val glowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x4442E5FF; style = Paint.Style.FILL }
    
    private val targetTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 65f; typeface = Typeface.DEFAULT_BOLD; setShadowLayer(10f, 0f, 0f, Color.BLACK) }
    private val overlayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF38EF7D.toInt(); textSize = 90f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; setShadowLayer(15f, 0f, 10f, Color.BLACK) }

    private var cellSize = 0f
    private var boardSize = 0f
    private var boardX = 0f
    private var boardY = 0f
    private var trayY = 0f
    private var trayCellSize = 0f
    
    private var targetUiX = 0f
    private var targetUiY = 120f

    val SHAPES = listOf(
        arrayOf(intArrayOf(1)), arrayOf(intArrayOf(1, 1)), arrayOf(intArrayOf(1), intArrayOf(1)),
        arrayOf(intArrayOf(1, 1), intArrayOf(1, 1)), arrayOf(intArrayOf(1, 1, 1)),
        arrayOf(intArrayOf(1), intArrayOf(1), intArrayOf(1)), arrayOf(intArrayOf(1, 1, 1, 1)),
        arrayOf(intArrayOf(1, 0), intArrayOf(1, 1)), arrayOf(intArrayOf(0, 1), intArrayOf(1, 1)), 
        arrayOf(intArrayOf(1, 1), intArrayOf(1, 0)), arrayOf(intArrayOf(1, 1), intArrayOf(0, 1))
    )

    class Shape(val matrix: Array<IntArray>, val colorId: Int) {
        val rows = matrix.size; val cols = matrix[0].size
        var cx = 0f; var cy = 0f; var placed = false
    }

    private val trayShapes = arrayOfNulls<Shape>(3)
    private var draggingShapeIndex = -1
    private var draggingShape: Shape? = null
    private var dragTouchOffsetX = 0f
    private var dragTouchOffsetY = 0f
    private var hoverRow = -1; private var hoverCol = -1; private var canFitHover = false

    init {
        initLevel()
    }

    private fun initLevel() {
        for (r in 0 until 8) { for (c in 0 until 8) grid[r][c] = 0 }
        
        // Spawn Gems (ID 10 = Star)
        var spawned = 0
        while(spawned < 8) {
            val r = Random.nextInt(8)
            val c = Random.nextInt(8)
            if (grid[r][c] == 0) {
                grid[r][c] = 10 
                spawned++
            }
        }
        fillTray()
    }

    private fun fillTray() {
        for (i in 0 until 3) {
            if (trayShapes[i] == null || trayShapes[i]!!.placed) trayShapes[i] = randomShape()
        }
        if (width > 0 && height > 0) updateTrayPositions()
        checkGameOver()
    }

    private fun randomShape(): Shape {
        val matrix = SHAPES[Random.nextInt(SHAPES.size)]
        val copy = Array(matrix.size) { r -> IntArray(matrix[r].size) { c -> matrix[r][c] } }
        return Shape(copy, Random.nextInt(1, 6))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padding = 30f 
        boardSize = w - padding * 2
        cellSize = boardSize / 8
        boardX = padding
        boardY = padding + 220f
        trayY = boardY + boardSize + 100f
        trayCellSize = cellSize * 0.65f
        
        targetUiX = w / 2f
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
        // Video Jaisa Dark Blue Background
        bgPaint.color = 0xFF2A3A6A.toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Target UI Top Center
        drawStarGem(canvas, targetUiX - 60f, targetUiY - 40f, 50f)
        val targetText = "$gemsCollected / $targetGems"
        canvas.drawText(targetText, targetUiX + 10f, targetUiY + 10f, targetTextPaint)

        // Board Background
        val rect = RectF(boardX, boardY, boardX + boardSize, boardY + boardSize)
        canvas.drawRoundRect(rect, 16f, 16f, boardPaint)
        canvas.drawRoundRect(rect, 16f, 16f, boardBorderPaint)

        val emptyPaint = Paint().apply { color = 0x1AFFFFFF; style = Paint.Style.STROKE; strokeWidth = 3f }

        // Draw Grid and Blocks
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                val cx = boardX + c * cellSize
                val cy = boardY + r * cellSize
                val cellId = grid[r][c]
                val cellRect = RectF(cx + 2, cy + 2, cx + cellSize - 2, cy + cellSize - 2)
                canvas.drawRoundRect(cellRect, 8f, 8f, emptyPaint)
                
                if (cellId != 0) {
                    draw3DBlock(canvas, cx, cy, cellSize, cellId)
                }
            }
        }

        // Magnet Snap Highlight
        draggingShape?.let { shape ->
            if (canFitHover && hoverRow in 0..7 && hoverCol in 0..7) {
                for (r in 0 until shape.rows) {
                    for (c in 0 until shape.cols) {
                        if (shape.matrix[r][c] != 0) {
                            val hx = boardX + (hoverCol + c) * cellSize
                            val hy = boardY + (hoverRow + r) * cellSize
                            val hRect = RectF(hx, hy, hx + cellSize, hy + cellSize)
                            canvas.drawRoundRect(hRect, 8f, 8f, glowFillPaint)
                            canvas.drawRoundRect(hRect, 8f, 8f, glowStrokePaint)
                        }
                    }
                }
            }
        }

        // Draw Tray Shapes
        for (i in 0 until 3) {
            if (i == draggingShapeIndex) continue
            val shape = trayShapes[i]
            if (shape != null && !shape.placed) drawShape(canvas, shape, shape.cx, shape.cy, trayCellSize)
        }

        // Draw Dragging Shape
        draggingShape?.let { shape ->
            if (canFitHover && hoverRow in 0..7 && hoverCol in 0..7) {
                drawShape(canvas, shape, boardX + hoverCol * cellSize, boardY + hoverRow * cellSize, cellSize)
            } else {
                drawShape(canvas, shape, shape.cx, shape.cy, cellSize)
            }
        }

        // Fly Animations Render
        if (flyingGems.isNotEmpty()) {
            val iterator = flyingGems.iterator()
            while (iterator.hasNext()) {
                val gem = iterator.next()
                gem.progress += 0.04f // Speed of flying
                
                if (gem.progress >= 1f) {
                    gemsCollected++
                    if (gemsCollected >= targetGems) isLevelComplete = true
                    soundManager.playPick() // Tink sound when it reaches target
                    iterator.remove()
                } else {
                    // Smooth lerp towards Target UI
                    val currentX = gem.startX + (targetUiX - 60f - gem.startX) * gem.progress
                    val currentY = gem.startY + (targetUiY - 40f - gem.startY) * gem.progress
                    drawStarGem(canvas, currentX, currentY, cellSize * 0.7f)
                }
            }
            invalidate() // Keep rendering while flying
        }

        // Overlays
        if (isLevelComplete && flyingGems.isEmpty()) {
            canvas.drawColor(0xDD000000.toInt())
            canvas.drawText("WELL DONE!", width / 2f, boardY + boardSize / 2f, overlayTextPaint)
        } else if (isGameOver) {
            overlayTextPaint.color = 0xFFFF5E62.toInt()
            canvas.drawColor(0xDD000000.toInt())
            canvas.drawText("NO MOVES!", width / 2f, boardY + boardSize / 2f, overlayTextPaint)
        }
    }

    private fun drawShape(canvas: Canvas, shape: Shape, x: Float, y: Float, size: Float) {
        for (r in 0 until shape.rows) {
            for (c in 0 until shape.cols) {
                if (shape.matrix[r][c] != 0) draw3DBlock(canvas, x + c * size, y + r * size, size, shape.colorId)
            }
        }
    }

    // Video-style 3D Beveled Blocks
    private fun draw3DBlock(canvas: Canvas, x: Float, y: Float, size: Float, colorId: Int) {
        val p = 1f
        val rect = RectF(x + p, y + p, x + size - p, y + size - p)
        
        if (colorId == 10) {
            // Draw Gem Block
            blockBasePaint.color = 0xFFE0A800.toInt() // Golden Block
            canvas.drawRoundRect(rect, 12f, 12f, blockBasePaint)
            drawStarGem(canvas, x + size/2 - size*0.35f, y + size/2 - size*0.35f, size * 0.7f)
            return
        }

        // 1. Draw Base Color
        blockBasePaint.color = getBaseColor(colorId)
        canvas.drawRoundRect(rect, 12f, 12f, blockBasePaint)

        // 2. Draw 3D Light Top-Left Bevel
        val bevelSize = size * 0.15f
        val lightPath = Path().apply {
            moveTo(rect.left, rect.top)
            lineTo(rect.right, rect.top)
            lineTo(rect.right - bevelSize, rect.top + bevelSize)
            lineTo(rect.left + bevelSize, rect.top + bevelSize)
            lineTo(rect.left + bevelSize, rect.bottom - bevelSize)
            lineTo(rect.left, rect.bottom)
            close()
        }
        canvas.drawPath(lightPath, blockLightEdgePaint)

        // 3. Draw 3D Dark Bottom-Right Bevel
        val darkPath = Path().apply {
            moveTo(rect.right, rect.bottom)
            lineTo(rect.left, rect.bottom)
            lineTo(rect.left + bevelSize, rect.bottom - bevelSize)
            lineTo(rect.right - bevelSize, rect.bottom - bevelSize)
            lineTo(rect.right - bevelSize, rect.top + bevelSize)
            lineTo(rect.right, rect.top)
            close()
        }
        canvas.drawPath(darkPath, blockDarkEdgePaint)
    }

    // Custom Star Drawing for Gems
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

        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD700.toInt(); style = Paint.Style.FILL }
        canvas.drawPath(path, starPaint)
        
        // Inner highlight
        starPaint.color = 0xAAFFFFFF.toInt()
        canvas.drawCircle(cx - size*0.1f, cy - size*0.1f, size*0.15f, starPaint)
    }

    private fun getBaseColor(id: Int): Int {
        return when (id) {
            1 -> 0xFFD82835.toInt() // Red
            2 -> 0xFF35A3FF.toInt() // Light Blue
            3 -> 0xFF5DD932.toInt() // Green
            4 -> 0xFFFFC20A.toInt() // Yellow
            5 -> 0xFF9E42F5.toInt() // Purple
            else -> 0xFFFFFFFF.toInt()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isGameOver || isLevelComplete) return true

        val tx = event.x
        val ty = event.y

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
                            shape.cy = ty - (shape.rows * cellSize) - 150f
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
                        soundManager.playDrop()

                        if (trayShapes.all { it == null || it.placed }) fillTray() else checkGameOver()
                    } else {
                        updateTrayPositions()
                    }
                    draggingShape = null; draggingShapeIndex = -1; hoverRow = -1; hoverCol = -1; canFitHover = false
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
        for (r in 0 until shape.rows) {
            for (c in 0 until shape.cols) {
                if (shape.matrix[r][c] != 0) grid[rOffset + r][cOffset + c] = shape.colorId
            }
        }
        checkLines()
    }

    private fun checkLines() {
        val rowsToClear = mutableListOf<Int>()
        val colsToClear = mutableListOf<Int>()

        for (r in 0 until 8) { if ((0 until 8).all { c -> grid[r][c] != 0 }) rowsToClear.add(r) }
        for (c in 0 until 8) { if ((0 until 8).all { r -> grid[r][c] != 0 }) colsToClear.add(c) }

        if (rowsToClear.isNotEmpty() || colsToClear.isNotEmpty()) {
            soundManager.playClear()
            
            // Collect Gems Logic with Flying Animation
            for (r in rowsToClear) {
                for (c in 0 until 8) {
                    if (grid[r][c] == 10) {
                        flyingGems.add(FlyingGem(boardX + c * cellSize, boardY + r * cellSize))
                    }
                    grid[r][c] = 0
                }
            }
            for (c in colsToClear) {
                for (r in 0 until 8) {
                    if (grid[r][c] == 10) {
                        flyingGems.add(FlyingGem(boardX + c * cellSize, boardY + r * cellSize))
                    }
                    grid[r][c] = 0
                }
            }
            
            if(flyingGems.isNotEmpty()) invalidate() // Trigger animation loop
        }
    }

    private fun checkGameOver() {
        if(isLevelComplete) return
        var canMakeMove = false
        for (shape in trayShapes) {
            if (shape != null && !shape.placed) {
                for (r in 0 until 8) {
                    for (c in 0 until 8) {
                        if (canPlaceShape(shape, r, c)) { canMakeMove = true; break }
                    }
                    if (canMakeMove) break
                }
            }
            if (canMakeMove) break
        }
        if (!canMakeMove) { isGameOver = true; soundManager.playGameOver(); invalidate() }
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); soundManager.release() }
}
