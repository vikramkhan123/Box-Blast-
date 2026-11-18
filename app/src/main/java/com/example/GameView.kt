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
    private val board = Array(gridSize) { IntArray(gridSize) { 0 } }
    
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

    // Particle system for high-end blast animations
    private val particles = mutableListOf<Particle>()
    private val handler = Handler(Looper.getMainLooper())
    private var shakeOffset = 0f

    init {
        generateNewTrayPieces()
        startAnimationLoop()
    }

    private fun startAnimationLoop() {
        handler.object = object : Runnable {
            override fun run() {
                updateParticles()
                invalidate()
                handler.postDelayed(this, 16) // ~60 FPS smooth rendering
            }
        }.also { handler.post(it) }
    }

    private class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var color: Int, var alpha: Float,
        var size: Float
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

    private fun updateParticles() {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx
            p.y += p.vy
            p.alpha -= 0.03f
            if (p.alpha <= 0f) {
                iterator.remove()
            }
        }
        if (shakeOffset > 0f) {
            shakeOffset *= 0.8f
        }
    }

    private fun triggerBlastEffect(centerX: Float, centerY: Float) {
        shakeOffset = 15f // Micro screen-shake
        for (i in 0..25) {
            val angle = Random.nextDouble(0.0, 2 * Math.PI)
            val speed = Random.nextFloat() * 8f + 2f
            particles.add(
                Particle(
                    x = centerX,
                    y = centerY,
                    vx = (cos(angle) * speed).toFloat(),
                    vy = (sin(angle) * speed).toFloat(),
                    color = if (Random.nextBoolean()) Color.parseColor("#06B6D4") else Color.parseColor("#F59E0B"),
                    alpha = 1f,
                    size = Random.nextFloat() * 12f + 6f
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

        // Background Glassmorphism Gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                Color.parseColor("#0B1026"), Color.parseColor("#1B2347"),
                Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Draw Score
        paint.color = Color.WHITE
        paint.textSize = 50f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("SCORE: $score", boardStartX, boardStartY - 40f, paint)

        // Draw Grid Board
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                val left = boardStartX + c * cellSize
                val top = boardStartY + r * cellSize
                
                paint.color = if (board[r][c] != 0) Color.parseColor("#06B6D4") else Color.parseColor("#1E293B")
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(left + 4f, top + 4f, left + cellSize - 4f, top + cellSize - 4f, 16f, 16f, paint)
            }
        }

        // Draw Particles (Crystal Shards & Sparks)
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
                val pWidth = piece[0].size * (cellSize * 0.6f)
                val pHeight = piece.size * (cellSize * 0.6f)
                val startX = traySlotWidth * i + (traySlotWidth - pWidth) / 2f
                
                if (i != selectedPieceIndex) {
                    drawPiecePreview(canvas, piece, startX, trayY, cellSize * 0.6f)
                }
            }
        }

        // Draw Dragged Piece following touch
        if (selectedPieceIndex != -1 && selectedPieceIndex < trayPieces.size) {
            drawPiecePreview(canvas, trayPieces[selectedPieceIndex], touchX - 50f, touchY - 100f, cellSize)
        }

        canvas.restore()
    }

    private fun drawPiecePreview(canvas: Canvas, piece: List<List<Int>>, startX: Float, startY: Float, size: Float) {
        paint.color = Color.parseColor("#F59E0B") // Glossy Gold/Yellow Theme Accent
        paint.style = Paint.Style.FILL
        for (r in piece.indices) {
            for (c in piece[r].indices) {
                if (piece[r][c] == 1) {
                    val l = startX + c * size
                    val t = startY + r * size
                    canvas.drawRoundRect(l + 2f, t + 2f, l + size - 2f, t + size - 2f, 10f, 10f, paint)
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
                                    board[dropRow + r][dropCol + c] = 1
                                    val blockCenterX = boardStartX + (dropCol + c) * cellSize + cellSize / 2f
                                    val blockCenterY = boardStartY + (dropRow + r) * cellSize + cellSize / 2f
                                    triggerBlastEffect(blockCenterX, blockCenterY)
                                }
                            }
                        }
                        score += 20
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
