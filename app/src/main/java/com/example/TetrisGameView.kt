package com.example

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.random.Random

class TetrisGameView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val soundManager = SoundManager(context)
    
    // Tetris Grid: 10 Columns, 20 Rows
    private val COLS = 10
    private val ROWS = 20
    private val grid = Array(ROWS) { IntArray(COLS) { 0 } }
    
    private var score = 0
    private var isGameOver = false

    // Paints
    private val bgPaint = Paint().apply { style = Paint.Style.FILL }
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF141E30.toInt(); style = Paint.Style.FILL }
    private val boardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF42E5FF.toInt(); style = Paint.Style.STROKE; strokeWidth = 10f }
    
    private val blockBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val blockLightEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF; style = Paint.Style.FILL }
    private val blockDarkEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000; style = Paint.Style.FILL }
    private val emptyPaint = Paint().apply { color = 0x1AFFFFFF; style = Paint.Style.STROKE; strokeWidth = 3f }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 60f; typeface = Typeface.DEFAULT_BOLD; setShadowLayer(10f, 0f, 0f, Color.BLACK) }
    private val overlayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF5E62.toInt(); textSize = 90f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; setShadowLayer(15f, 0f, 10f, Color.BLACK) }
    
    // Control Button Paints
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88000000.toInt(); style = Paint.Style.FILL }
    private val btnStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF42E5FF.toInt(); style = Paint.Style.STROKE; strokeWidth = 5f }
    private val btnIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 70f; textAlign = Paint.Align.CENTER }

    // Layout Dimensions
    private var cellSize = 0f
    private var boardSizeW = 0f
    private var boardSizeH = 0f
    private var boardX = 0f
    private var boardY = 0f

    // Buttons Rectangles
    private val btnLeft = RectF()
    private val btnRotate = RectF()
    private val btnDown = RectF()
    private val btnRight = RectF()

    // Tetromino Shapes
    val SHAPES = listOf(
        arrayOf(intArrayOf(1, 1, 1, 1)), // I
        arrayOf(intArrayOf(1, 1), intArrayOf(1, 1)), // O
        arrayOf(intArrayOf(0, 1, 0), intArrayOf(1, 1, 1)), // T
        arrayOf(intArrayOf(1, 0, 0), intArrayOf(1, 1, 1)), // L
        arrayOf(intArrayOf(0, 0, 1), intArrayOf(1, 1, 1)), // J
        arrayOf(intArrayOf(0, 1, 1), intArrayOf(1, 1, 0)), // S
        arrayOf(intArrayOf(1, 1, 0), intArrayOf(0, 1, 1))  // Z
    )

    class Tetromino(var matrix: Array<IntArray>, val colorId: Int) {
        var x = 4
        var y = 0
    }

    private var currentPiece: Tetromino? = null
    
    // Game Loop Timer
    private val handler = Handler(Looper.getMainLooper())
    private var speedMs = 600L
    private val gameLoop = object : Runnable {
        override fun run() {
            if (!isGameOver) {
                moveDown()
                invalidate()
                handler.postDelayed(this, speedMs)
            }
        }
    }

    init {
        spawnPiece()
        handler.postDelayed(gameLoop, speedMs)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padding = 40f
        boardSizeW = w - padding * 2
        cellSize = boardSizeW / COLS
        boardSizeH = cellSize * ROWS
        
        boardX = padding
        boardY = 150f

        val btnY = boardY + boardSizeH + 80f
        val bw = (w - padding * 2) / 4f
        
        btnLeft.set(padding, btnY, padding + bw - 10f, btnY + 140f)
        btnRotate.set(padding + bw, btnY, padding + bw * 2 - 10f, btnY + 140f)
        btnDown.set(padding + bw * 2, btnY, padding + bw * 3 - 10f, btnY + 140f)
        btnRight.set(padding + bw * 3, btnY, padding + bw * 4 - 10f, btnY + 140f)
    }

    private fun spawnPiece() {
        val matrix = SHAPES[Random.nextInt(SHAPES.size)]
        val copy = Array(matrix.size) { r -> IntArray(matrix[r].size) { c -> matrix[r][c] } }
        currentPiece = Tetromino(copy, Random.nextInt(1, 6))
        
        if (!isValidPosition(currentPiece!!.matrix, currentPiece!!.x, currentPiece!!.y)) {
            isGameOver = true
            soundManager.playGameOver()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bgPaint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), 0xFF0B1021.toInt(), 0xFF060913.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        canvas.drawText("SCORE: $score", boardX, 100f, textPaint)

        val rect = RectF(boardX, boardY, boardX + boardSizeW, boardY + boardSizeH)
        canvas.drawRect(rect, boardPaint)
        canvas.drawRect(rect, boardBorderPaint)

        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                val cx = boardX + c * cellSize
                val cy = boardY + r * cellSize
                val cellId = grid[r][c]
                
                val cellRect = RectF(cx, cy, cx + cellSize, cy + cellSize)
                canvas.drawRect(cellRect, emptyPaint)
                
                if (cellId != 0) {
                    draw3DBlock(canvas, cx, cy, cellSize, cellId)
                }
            }
        }

        currentPiece?.let { piece ->
            for (r in 0 until piece.matrix.size) {
                for (c in 0 until piece.matrix[0].size) {
                    if (piece.matrix[r][c] != 0) {
                        val bx = boardX + (piece.x + c) * cellSize
                        val by = boardY + (piece.y + r) * cellSize
                        draw3DBlock(canvas, bx, by, cellSize, piece.colorId)
                    }
                }
            }
        }

        drawControlButton(canvas, btnLeft, "◀")
        drawControlButton(canvas, btnRotate, "↻")
        drawControlButton(canvas, btnDown, "▼")
        drawControlButton(canvas, btnRight, "▶")

        if (isGameOver) {
            canvas.drawColor(0xCC000000.toInt())
            canvas.drawText("GAME OVER!", width / 2f, height / 2f, overlayTextPaint)
        }
    }

    private fun drawControlButton(canvas: Canvas, rect: RectF, icon: String) {
        canvas.drawRoundRect(rect, 20f, 20f, btnPaint)
        canvas.drawRoundRect(rect, 20f, 20f, btnStrokePaint)
        val textOffset = (btnIconPaint.descent() + btnIconPaint.ascent()) / 2f
        canvas.drawText(icon, rect.centerX(), rect.centerY() - textOffset, btnIconPaint)
    }

    private fun draw3DBlock(canvas: Canvas, x: Float, y: Float, size: Float, colorId: Int) {
        val p = 1.5f
        val rect = RectF(x + p, y + p, x + size - p, y + size - p)
        
        blockBasePaint.color = getBaseColor(colorId)
        canvas.drawRoundRect(rect, 8f, 8f, blockBasePaint)

        val bevel = size * 0.15f
        val lightPath = Path().apply {
            moveTo(rect.left, rect.top); lineTo(rect.right, rect.top)
            lineTo(rect.right - bevel, rect.top + bevel); lineTo(rect.left + bevel, rect.top + bevel)
            lineTo(rect.left + bevel, rect.bottom - bevel); lineTo(rect.left, rect.bottom); close()
        }
        canvas.drawPath(lightPath, blockLightEdgePaint)

        val darkPath = Path().apply {
            moveTo(rect.right, rect.bottom); lineTo(rect.left, rect.bottom)
            lineTo(rect.left + bevel, rect.bottom - bevel); lineTo(rect.right - bevel, rect.bottom - bevel)
            lineTo(rect.right - bevel, rect.top + bevel); lineTo(rect.right, rect.top); close()
        }
        canvas.drawPath(darkPath, blockDarkEdgePaint)
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
        if (isGameOver || event.action != MotionEvent.ACTION_DOWN) return true
        
        val tx = event.x
        val ty = event.y

        when {
            btnLeft.contains(tx, ty) -> moveLeft()
            btnRight.contains(tx, ty) -> moveRight()
            btnRotate.contains(tx, ty) -> rotatePiece()
            btnDown.contains(tx, ty) -> {
                soundManager.playPick()
                moveDown()
            }
        }
        invalidate()
        return true
    }

    private fun moveLeft() {
        currentPiece?.let {
            if (isValidPosition(it.matrix, it.x - 1, it.y)) it.x--
        }
    }

    private fun moveRight() {
        currentPiece?.let {
            if (isValidPosition(it.matrix, it.x + 1, it.y)) it.x++
        }
    }

    private fun rotatePiece() {
        currentPiece?.let {
            val rows = it.matrix.size
            val cols = it.matrix[0].size
            val newMatrix = Array(cols) { IntArray(rows) }
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    newMatrix[c][rows - 1 - r] = it.matrix[r][c]
                }
            }
            if (isValidPosition(newMatrix, it.x, it.y)) {
                it.matrix = newMatrix
                soundManager.playPick()
            }
        }
    }

    private fun moveDown() {
        currentPiece?.let {
            if (isValidPosition(it.matrix, it.x, it.y + 1)) {
                it.y++
            } else {
                lockPiece()
            }
        }
    }

    private fun lockPiece() {
        currentPiece?.let { piece ->
            soundManager.playDrop()
            for (r in 0 until piece.matrix.size) {
                for (c in 0 until piece.matrix[0].size) {
                    if (piece.matrix[r][c] != 0) {
                        val gridY = piece.y + r
                        val gridX = piece.x + c
                        if (gridY in 0 until ROWS && gridX in 0 until COLS) {
                            grid[gridY][gridX] = piece.colorId
                        }
                    }
                }
            }
            checkLines()
            spawnPiece()
        }
    }

    // Yahan While Loop se fix kiya gaya hai
    private fun checkLines() {
        var linesCleared = 0
        var r = ROWS - 1
        
        while (r >= 0) {
            var isFull = true
            for (c in 0 until COLS) {
                if (grid[r][c] == 0) { isFull = false; break }
            }
            
            if (isFull) {
                linesCleared++
                for (shiftR in r downTo 1) {
                    for (c in 0 until COLS) { grid[shiftR][c] = grid[shiftR - 1][c] }
                }
                for (c in 0 until COLS) grid[0][c] = 0
                // r-- nahi kiya taaki upar se jo line aayi hai wo bhi wapas check ho jaye
            } else {
                r--
            }
        }
        
        if (linesCleared > 0) {
            soundManager.playClear()
            score += (linesCleared * 100) * linesCleared
            speedMs = maxOf(150L, speedMs - 10L)
        }
    }

    private fun isValidPosition(matrix: Array<IntArray>, x: Int, y: Int): Boolean {
        for (r in 0 until matrix.size) {
            for (c in 0 until matrix[0].size) {
                if (matrix[r][c] != 0) {
                    val gridX = x + c
                    val gridY = y + r
                    if (gridX < 0 || gridX >= COLS || gridY >= ROWS) return false
                    if (gridY >= 0 && grid[gridY][gridX] != 0) return false
                }
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(gameLoop)
        soundManager.release()
    }
}
