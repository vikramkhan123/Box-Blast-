package com.example

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class GameView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val gridSize = 8
    private val board = Array(gridSize) { IntArray(gridSize) { 0 } } // 0 means empty, 1-17 means tile index
    
    // 3 pieces tray system
    private val trayPieces = mutableListOf<List<List<Int>>>()
    private var selectedPieceIndex = -1
    private var touchX = 0f
    private var touchY = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cellSize = 0f
    private var boardStartX = 0f
    private var boardStartY = 0f

    private var score = 0
    private var isGameOver = false
    private var onGameOverListener: (() -> Unit)? = null

    // Candy Crush style juicy particle & pop animation system
    private val particles = mutableListOf<CandyParticle>()
    private val handler = Handler(Looper.getMainLooper())
    private var shakeOffset = 0f

    init {
        generateNewTrayPieces()
        startAnimationLoop()
    }

    private fun startAnimationLoop() {
        handler.post(object : Runnable {
            override fun run() {
                updateAnimations()
                invalidate()
                handler.postDelayed(this, 16) // ~60 FPS
            }
        })
    }

    private class CandyParticle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var alpha: Float, var size: Float,
        var color: Int
    )

    fun setOnGameOverListener(listener: () -> Unit) {
        onGameOverListener = listener
    }

    private fun generateNewTrayPieces() {
        trayPieces.clear()
        val availableShapes = listOf(
            listOf(listOf(1)),
            listOf(listOf(1, 1)),
            listOf(listOf(1), listOf(1)),
            listOf(listOf(1, 1), listOf(1, 1)),
            listOf(listOf(1, 1, 1)),
            listOf(listOf(1, 1), listOf(1, 0))
        )

        for (i in 0 until 3) {
            trayPieces.add(availableShapes.random())
        }

        if (!canAnyPieceFit()) {
            isGameOver = true
            onGameOverListener?.invoke()
        }
    }

    private fun canAnyPieceFit(): Boolean {
        for (piece in trayPieces) {
            for (r in 0 until gridSize) {
                for (c in 0 until gridSize) {
                    if (canPlacePiece(piece, r, c)) return true
                }
            }
        }
        return trayPieces.isEmpty()
    }

    private fun canPlacePiece(piece: List<List<Int>>, startRow: Int, startCol: Int): Boolean {
        val pRows = piece.size
        val pCols = piece[0].size
        if (startRow + pRows > gridSize || startCol + pCols > gridSize) return false

        for (r in 0 until pRows) {
            for (c in 0 until pCols) {
                if (piece[r][c] == 1 && board[startRow + r][startCol + c] != 0) {
                    return false
                }
            }
        }
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val availableWidth = w * 0.9f
        cellSize = availableWidth / gridSize
        boardStartX = (w - availableWidth) / 2f
        boardStartY = h * 0.15f
    }

    private fun updateAnimations() {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx
            p.y += p.vy
            p.alpha -= 0.04f
            p.size *= 0.95f
            if (p.alpha <= 0f) {
                iterator.remove()
            }
        }
        if (shakeOffset > 0f) {
            shakeOffset *= 0.75f
        }
    }

    // Candy Crush style juicy blast effect
    private fun triggerCandyBlast(centerX: Float, centerY: Float) {
        shakeOffset = 12f // Juicy subtle screen micro-shake
        for (i in 0..20) {
            val angle = Random.nextDouble(0.0, 2 * Math.PI)
            val speed = Random.nextFloat() * 10f + 3f
            particles.add(
                CandyParticle(
                    x = centerX,
                    y = centerY,
                    vx = (cos(angle) * speed).toFloat(),
                    vy = (sin(angle) * speed).toFloat(),
                    alpha = 1f,
                    size = Random.nextFloat() * 16f + 8f,
                    color = Color.parseColor("#F59E0B") // Golden glossy sparkle
                )
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.save()
        if (shakeOffset > 0f) {
            val sx = (Random.nextFloat() - 0.5f) * shakeOffset
            val sy = (Random.nextFloat() - 0.5f) * shakeOffset
            canvas.translate(sx, sy)
        }

        // Clean Light Cream / Glassmorphic Background (matching user screenshots)
        val bgPaint = Paint().apply {
            color = Color.parseColor("#FDF6F0")
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Draw Score & Best Info Header
        paint.color = Color.parseColor("#1E293B")
        paint.textSize = 42f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("SCORE", boardStartX, boardStartY - 60f, paint)
        paint.textSize = 56f
        canvas.drawText("$score", boardStartX, boardStartY - 10f, paint)

        // Draw Grid Board (Rounded smooth slots)
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                val left = boardStartX + c * cellSize
                val top = boardStartY + r * cellSize
                
                paint.color = Color.parseColor("#F1E4D8") // Empty slot background
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(left + 6f, top + 6f, left + cellSize - 6f, top + cellSize - 6f, 16f, 16f, paint)

                // If block exists, render with tile style (Using Tile 1 to 17 glossy look)
                if (board[r][c] != 0) {
                    drawTile(canvas, left + 6f, top + 6f, cellSize - 12f, board[r][c])
                }
            }
        }

        // Draw Candy Crush Style Juicy Particles
        for (p in particles) {
            paint.color = p.color
            paint.alpha = (p.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(p.x, p.y, p.size, paint)
        }
        paint.alpha = 255

        // Draw Bottom Tray Pieces
        val trayY = boardStartY + gridSize * cellSize + 80f
        val traySlotWidth = width / 3f

        for (i in trayPieces.indices) {
            if (trayPieces[i].isNotEmpty()) {
                val piece = trayPieces[i]
                val pWidth = piece[0].size * (cellSize * 0.55f)
                val startX = traySlotWidth * i + (traySlotWidth - pWidth) / 2f
                
                if (i != selectedPieceIndex) {
                    drawPiecePreview(canvas, piece, startX, trayY, cellSize * 0.55f)
                }
            }
        }

        // Draw Dragged Piece following touch
        if (selectedPieceIndex != -1 && selectedPieceIndex < trayPieces.size) {
            drawPiecePreview(canvas, trayPieces[selectedPieceIndex], touchX - 60f, touchY - 120f, cellSize * 0.85f)
        }

        canvas.restore()
    }

    // Helper to render individual tile with glossy orange/yellow styling (Tile 1 - 17 matching screenshots)
    private fun drawTile(canvas: Canvas, left: Float, top: Float, size: Float, tileId: Int) {
        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                left, top, left, top + size,
                Color.parseColor("#FBBF24"), // Glossy Yellow/Orange Top
                Color.parseColor("#D97706"), // Deep Orange Bottom
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(left, top, size, size, 14f, 14f, tilePaint)

        // Inner glossy highlight reflection
        val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 60
        }
        canvas.drawRoundRect(left + 4f, top + 4f, size - 4f, top + size * 0.35f, 10f, 10f, glossPaint)
    }

    private fun drawPiecePreview(canvas: Canvas, piece: List<List<Int>>, startX: Float, startY: Float, size: Float) {
        for (r in piece.indices) {
            for (c in piece[r].indices) {
                if (piece[r][c] == 1) {
                    val l = startX + c * size
                    val t = startY + r * size
                    drawTile(canvas, l, t, size - 4f, 1)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isGameOver) return true

        val trayY = boardStartY + gridSize * cellSize + 80f
        val traySlotWidth = width / 3f

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchX = event.x
                touchY = event.y
                if (touchY > trayY) {
                    val index = (touchX / traySlotWidth).toInt()
                    if (index in trayPieces.indices && trayPieces[index].isNotEmpty()) {
                        selectedPieceIndex = index
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                touchX = event.x
                touchY = event.y
                if (selectedPieceIndex != -1) invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (selectedPieceIndex != -1) {
                    val dropRow = ((touchY - boardStartY) / cellSize).toInt()
                    val dropCol = ((touchX - boardStartX) / cellSize).toInt()

                    val piece = trayPieces[selectedPieceIndex]
                    if (canPlacePiece(piece, dropRow, dropCol)) {
                        for (r in piece.indices) {
                            for (c in piece[r].indices) {
                                if (piece[r][c] == 1) {
                                    board[dropRow + r][dropCol + c] = 15 // Using tile style
                                    val blockCenterX = boardStartX + (dropCol + c) * cellSize + cellSize / 2f
                                    val blockCenterY = boardStartY + (dropRow + r) * cellSize + cellSize / 2f
                                    triggerCandyBlast(blockCenterX, blockCenterY)
                                }
                            }
                        }
                        score += 30
                        trayPieces[selectedPieceIndex] = emptyList()

                        if (trayPieces.all { it.isEmpty() }) {
                            generateNewTrayPieces()
                        }
                    }
                    selectedPieceIndex = -1
                    invalidate()
                }
            }
        }
        return true
    }
}
