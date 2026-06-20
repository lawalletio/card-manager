package io.lawallet.cardactivation.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Scanner overlay: dims everything except a centered square "reticle" with teal
 * corner brackets. No barcode laser line — a clean QR target box.
 */
class SquareFrameView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : View(context, attrs) {
        private val mask = Paint().apply { color = Color.argb(150, 0, 0, 0) }
        private val corner =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#00C7A0") // TealBright
                style = Paint.Style.STROKE
                strokeWidth = dp(4f)
                strokeCap = Paint.Cap.ROUND
            }

        private fun dp(v: Float) = v * resources.displayMetrics.density

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val side = min(w, h) * 0.66f
            val left = (w - side) / 2f
            val top = (h - side) / 2f
            val right = left + side
            val bottom = top + side

            // Dim everything outside the square.
            canvas.drawRect(0f, 0f, w, top, mask)
            canvas.drawRect(0f, top, left, bottom, mask)
            canvas.drawRect(right, top, w, bottom, mask)
            canvas.drawRect(0f, bottom, w, h, mask)

            // Corner brackets.
            val c = dp(30f)
            canvas.drawLine(left, top, left + c, top, corner)
            canvas.drawLine(left, top, left, top + c, corner)
            canvas.drawLine(right, top, right - c, top, corner)
            canvas.drawLine(right, top, right, top + c, corner)
            canvas.drawLine(left, bottom, left + c, bottom, corner)
            canvas.drawLine(left, bottom, left, bottom - c, corner)
            canvas.drawLine(right, bottom, right - c, bottom, corner)
            canvas.drawLine(right, bottom, right, bottom - c, corner)
        }
    }
