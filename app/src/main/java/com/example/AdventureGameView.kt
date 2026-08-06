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

    val soundManager = SoundManager(context)
    private val prefs = context.getSharedPreferences("BoxBlastPrefs", Context.MODE_PRIVATE)
    
    private val grid = Array(8) { IntArray(8) { 0 } }
    
    private var currentLevel = prefs.getInt("AdventureLevel", 1)
    private var targetGems = 10 + (currentLevel * 2) 
    private var gemsCollected = 0
    private var isGameOver = false
    private var isLevelComplete = false

    data class FlyingGem(var startX: Float, var startY: Float, var progress: Float = 0f)
    private val flyingGems = mutableListOf<FlyingGem>()
    
    data class BlastParticle(var cx: Float, var cy: Float, var radius: Float, var alpha: Int, val color: Int)
    private val blasts = mutableListOf<BlastParticle>()

    private val bgPaint = Paint().apply { style = Paint.Style.FILL }
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B264A.toInt(); style = Paint.Style.FILL }
    private val boardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF354B8B.toInt(); style = Paint.Style.STROKE; strokeWidth = 12f }
    
    private val blockBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val blockLightEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF; style = Paint.Style.FILL }
    private val blockDarkEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000; style = Paint.Style.FILL }

    private val targetTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 65f; typeface = Typeface.DEFAULT_BOLD; setShadowLayer(10f, 0f, 0f, Color.BLACK) }
    private val levelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD700.toInt(); textSize = 45f; typeface = Typeface.DEFAULT_BOLD }
    private val overlayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF38EF7D.toInt(); textSize = 90f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; setShadowLayer(15f, 0f, 10f, Color.BLACK) }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2CD04E.toInt(); style = Paint.Style.FILL }
    
    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        color = Color.WHITE
        textSize = 50f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
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

    init {
        initLevel()
    }

    private fun initLevel() {
        targetGems = 10 + (currentLevel * 2)
        gemsCollected = 0
        isGameOver = false
        isLevelComplete = false
        flyingGems.clear()
        blasts.clear()

        for (r in 0 until 8) { for (c in 0 until 8) grid[r][c] = 0 }
        
        var spawned = 0
        val initialGems = minOf(targetGems, 12) 
        while(spawned < initialGems) {
            val r = Random.nextInt(8)
            val c = Random.nextInt(8)
            if (grid[r][c] == 0) {
                grid[r][c] = 10 
                spawned++
            }
        }
        for (i in 0 until 3) trayShapes[i] = null
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
        val rawMatrix = SHAPES[Random.nextInt(SHAPES.size)]
        val colorId = Random.nextInt(1, 6)
        val copy = Array(rawMatrix.size) { r -> 
            IntArray(rawMatrix[r].size) { c -> 
                if (rawMatrix[r][c] == 1) colorId else 0 
            } 
        }
        
        if (gemsCollected < targetGems && Random.nextFloat() < 0.35f) {
            val validCoords = mutableListOf<Pair<Int, Int>>()
            for (r in copy.indices) {
                for (c in copy[0].indices) {
                    if (copy[r][c] != 0) validCoords.add(Pair(r, c))
                }
            }
            if (validCoords.isNotEmpty()) {
                val (gr, gc) = validCoords.random()
                copy[gr][gc] = 10 
            }
        }
        return Shape(copy)
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
        
        val bw = 400f
        val bh = 120f
        nextLevelBtnRect.set(w/2f - bw/2f, boardY + boardSize/2f + 100f, w/2f + bw/2f, boardY + boardSize/2f + 100f + bh)
        
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
        bgPaint.color = 0xFF2A3A6A.toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        canvas.drawText("LEVEL $currentLevel", 40f, 80f, levelTextPaint)
        drawStarGem(canvas, targetUiX - 60f, targetUiY - 40f, 50f)
        val targetText = "$gemsCollected / $targetGems"
        canvas.drawText(targetText, targetUiX + 10f, targetUiY + 10f, targetTextPaint)

        val rect = RectF(boardX, boardY, boardX + boardSize, boardY + boardSize)
        canvas.drawRoundRect(rect, 16f, 16f, boardPaint)
        canvas.drawRoundRect(rect, 16f, 16f, boardBorderPaint)

        val emptyPaint = Paint().apply { color = 0x1AFFFFFF; style = Paint.Style.STROKE; strokeWidth = 3f }

        for (r in 0 until 8) {
            for (c in 0 until 8) {
                val cx = boardX + c * cellSize
                val cy = boardY + r * cellSize
                val cellId = grid[r][c]
                val cellRect = RectF(cx + 2, cy + 2, cx + cellSize - 2, cy + cellSize - 2)
                canvas.drawRoundRect(cellRect, 8f, 8f, emptyPaint)
                if (cellId != 0) draw3DBlock(canvas, cx, cy, cellSize, cellId, alpha = 255)
            }
        }

        draggingShape?.let { shape ->
            if (canFitHover && hoverRow in 0..7 && hoverCol in 0..7) {
                drawShape(canvas, shape, boardX + hoverCol * cellSize, boardY + hoverRow * cellSize, cellSize, alpha = 90)
            }
        }

        for (i in 0 until 3) {
            if (i == draggingShapeIndex) continue
            val shape = trayShapes[i]
            if (shape != null && !shape.placed) drawShape(canvas, shape, shape.cx, shape.cy, trayCellSize, alpha = 255)
        }

        draggingShape?.let { shape ->
            drawShape(canvas, shape, shape.cx, shape.cy, cellSize, alpha = 255)
        }

        if (blasts.isNotEmpty()) {
            val iterator = blasts.iterator()
            val blastPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            var animatingBlasts = false
            while (iterator.hasNext()) {
                val p = iterator.next()
                blastPaint.color = p.color
                blastPaint.alpha = p.alpha
                canvas.drawCircle(p.cx, p.cy, p.radius, blastPaint)
                
                p.radius += 8f
                p.alpha -= 15
                if (p.alpha <= 0) iterator.remove() else animatingBlasts = true
            }
            if (animatingBlasts) invalidate()
        }

        if (flyingGems.isNotEmpty()) {
            val iterator = flyingGems.iterator()
            var animatingGems = false
            while (iterator.hasNext()) {
                val gem = iterator.next()
                gem.progress += 0.05f 
                if (gem.progress >= 1f) {
                    gemsCollected++
                    if (gemsCollected >= targetGems && !isLevelComplete) {
                        isLevelComplete = true
                        soundManager.playClear() 
                    }
                    soundManager.playPick() 
                    iterator.remove()
                } else {
                    animatingGems = true
                    val currentX = gem.startX + (targetUiX - 60f - gem.startX) * gem.progress
                    val currentY = gem.startY + (targetUiY - 40f - gem.startY) * gem.progress
                    val flySize = cellSize * 0.7f * (1f - (gem.progress * 0.3f))
                    drawStarGem(canvas, currentX, currentY, flySize)
                }
            }
            if (animatingGems) invalidate() 
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

    private fun drawShape(canvas: Canvas, shape: Shape, x: Float, y: Float, size: Float, alpha: Int) {
        for (r in 0 until shape.rows) {
            for (c in 0 until shape.cols) {
                if (shape.matrix[r][c] != 0) {
                    draw3DBlock(canvas, x + c * size, y + r * size, size, shape.matrix[r][c], alpha)
                }
            }
        }
    }

    private fun draw3DBlock(canvas: Canvas, x: Float, y: Float, size: Float, colorId: Int, alpha: Int) {
        val p = 1.5f
        val rect = RectF(x + p, y + p, x + size - p, y + size - p)
        
        blockBasePaint.alpha = alpha
        blockLightEdgePaint.alpha = if (alpha < 255) 0 else 85
        blockDarkEdgePaint.alpha = if (alpha < 255) 0 else 85

        if (colorId == 10) {
            blockBasePaint.color = 0xFF141E3A.toInt() 
            canvas.drawRoundRect(rect, 12f, 12f, blockBasePaint)
            
            val innerRect = RectF(rect.left + 5f, rect.top + 5f, rect.right - 5f, rect.bottom - 5f)
            blockBasePaint.color = 0xFF0D152B.toInt()
            canvas.drawRoundRect(innerRect, 8f, 8f, blockBasePaint)

            val gemSize = size * 0.65f
            val gX = x + (size - gemSize)/2f
            val gY = y + (size - gemSize)/2f
            drawStarGem(canvas, gX, gY, gemSize, alpha)
            return
        }

        blockBasePaint.color = getBaseColor(colorId)
        canvas.drawRoundRect(rect, 12f, 12f, blockBasePaint)

        if (alpha == 255) {
            val bevelSize = size * 0.15f
            val lightPath = Path().apply {
                moveTo(rect.left, rect.top); lineTo(rect.right, rect.top)
                lineTo(rect.right - bevelSize, rect.top + bevelSize); lineTo(rect.left + bevelSize, rect.top + bevelSize)
                lineTo(rect.left + bevelSize, rect.bottom - bevelSize); lineTo(rect.left, rect.bottom); close()
            }
            canvas.drawPath(lightPath, blockLightEdgePaint)

            val darkPath = Path().apply {
                moveTo(rect.right, rect.bottom); lineTo(rect.left, rect.bottom)
                lineTo(rect.left + bevelSize, rect.bottom - bevelSize); lineTo(rect.right - bevelSize, rect.bottom - bevelSize)
                lineTo(rect.right - bevelSize, rect.top + bevelSize); lineTo(rect.right, rect.top); close()
            }
            canvas.drawPath(darkPath, blockDarkEdgePaint)
        }
    }

    private fun drawStarGem(canvas: Canvas, x: Float, y: Float, size: Float, alpha: Int = 255) {
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

        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD700.toInt(); style = Paint.Style.FILL; this.alpha = alpha }
        canvas.drawPath(path, starPaint)
        starPaint.color = 0xAAFFFFFF.toInt()
        starPaint.alpha = if (alpha == 255) 170 else 0
        canvas.drawCircle(cx - size*0.1f, cy - size*0.1f, size*0.15f, starPaint)
    }

    private fun getBaseColor(id: Int): Int {
        return when (id) {
            1 -> 0xFFD82835.toInt()
            2 -> 0xFF35A3FF.toInt()
            3 -> 0xFF5DD932.toInt()
            4 -> 0xFFFFC20A.toInt()
            5 -> 0xFF9E42F5.toInt()
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
                invalidate()
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
                    
                    val centerCol = (shape.cx + (shape.cols * cellSize)/2f - boardX) / cellSize
                    val centerRow = (shape.cy + (shape.rows * cellSize)/2f - boardY) / cellSize
                    
                    hoverCol = (centerCol - shape.cols/2f).roundToInt()
                    hoverRow = (centerRow - shape.rows/2f).roundToInt()
                    
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
            soundManager.playComboVoice(totalLines)
            
            for (r in rowsToClear) {
                for (c in 0 until 8) {
                    val colorId = grid[r][c]
                    val bX = boardX + c * cellSize + cellSize/2f
                    val bY = boardY + r * cellSize + cellSize/2f
                    
                    blasts.add(BlastParticle(bX, bY, cellSize/2f, 255, getBaseColor(colorId)))
                    
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
                        blasts.add(BlastParticle(bX, bY, cellSize/2f, 255, getBaseColor(colorId)))
                        if (colorId == 10) flyingGems.add(FlyingGem(bX - cellSize/2f, bY - cellSize/2f))
                        grid[r][c] = 0
                    }
                }
            }
            
            invalidate() 
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
