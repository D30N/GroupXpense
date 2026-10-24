package com.deon.groupexpense

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View

// Saturation/brightness square + hue strip colour picker.
class SvPickerView(ctx: Context) : View(ctx) {
    var hue = 174f
    var sat = 0.8f
    var bright = 0.85f
    var onChange: (() -> Unit)? = null

    private fun dpf(v: Float): Float = v * resources.displayMetrics.density

    fun setColor(c: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hue = hsv[0]; sat = hsv[1]; bright = hsv[2]
        invalidate()
    }

    fun color(): Int = Color.HSVToColor(floatArrayOf(hue, sat, bright))

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val hueH = dpf(36f)
        val gap = dpf(14f)
        val svH = height - hueH - gap
        if (svH <= 0) return
        val base = Paint().apply { color = Color.HSVToColor(floatArrayOf(hue, 1f, 1f)) }
        canvas.drawRect(0f, 0f, w, svH, base)
        val wg = Paint().apply {
            shader = LinearGradient(0f, 0f, w, 0f, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, svH, wg)
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, svH, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, svH, bg)
        val cx = sat * w
        val cy = (1 - bright) * svH
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = dpf(2.5f); color = Color.WHITE
        }
        canvas.drawCircle(cx, cy, dpf(9f), ring)
        val ring2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = dpf(1f); color = 0x66000000
        }
        canvas.drawCircle(cx, cy, dpf(10.5f), ring2)
        val y0 = svH + gap
        val cols = IntArray(73) { Color.HSVToColor(floatArrayOf(it * 5f, 1f, 1f)) }
        val hp = Paint().apply {
            shader = LinearGradient(0f, 0f, w, 0f, cols, null, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(0f, y0, w, y0 + hueH, dpf(10f), dpf(10f), hp)
        val hx = (hue / 360f) * w
        val hr = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = dpf(2.5f); color = Color.WHITE
        }
        canvas.drawRoundRect(hx - dpf(7f), y0 - dpf(2f), hx + dpf(7f), y0 + hueH + dpf(2f), dpf(8f), dpf(8f), hr)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val w = width.toFloat()
        val hueH = dpf(36f)
        val gap = dpf(14f)
        val svH = height - hueH - gap
        if (svH <= 0) return true
        if (e.y <= svH) {
            sat = (e.x / w).coerceIn(0f, 1f)
            bright = (1 - e.y / svH).coerceIn(0f, 1f)
        } else {
            hue = (e.x / w).coerceIn(0f, 1f) * 360f
        }
        invalidate()
        onChange?.invoke()
        parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }
}
