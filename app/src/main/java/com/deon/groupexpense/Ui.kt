package com.deon.groupexpense

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object P {
    var dark = true
    var bg = 0
    var bgTop = 0
    var card = 0
    var cardStroke = 0
    var ink = 0
    var sub = 0
    var faint = 0
    var field = 0
    var fieldStroke = 0
    var accent = 0xFF2DD4BF.toInt()
    var accentDark = 0
    var onAccent = 0xFF03211B.toInt()
    var pos = 0
    var neg = 0
    var gold = 0xFFF5C97B.toInt()
    var navBg = 0

    fun hsv(h: Float, s: Float, v: Float): Int {
        return Color.HSVToColor(floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))
    }

    fun apply(ctx: Context, mode: String, accentColor: Int) {
        dark = when (mode) {
            "LIGHT" -> false
            "SYSTEM" -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            else -> true
        }
        accent = accentColor
        accentDark = scale(accent, 0.55f)
        val lum = 0.299 * Color.red(accent) + 0.587 * Color.green(accent) + 0.114 * Color.blue(accent)
        onAccent = if (lum < 100) 0xFFF8FAFC.toInt() else 0xFF03211B.toInt()
        // The whole palette is tinted by the chosen accent hue, so the
        // background follows the theme colour instead of staying green.
        val hsvArr = FloatArray(3)
        Color.colorToHSV(accent, hsvArr)
        val h = hsvArr[0]
        val s = if (hsvArr[1] < 0.08f) hsvArr[1] else maxOf(hsvArr[1], 0.5f)
        if (dark) {
            bgTop = hsv(h, s * 0.9f, 0.085f)
            bg = hsv(h, s * 0.95f, 0.05f)
            card = hsv(h, s * 0.8f, 0.105f)
            cardStroke = hsv(h, s * 0.7f, 0.22f)
            field = hsv(h, s * 0.75f, 0.14f)
            fieldStroke = hsv(h, s * 0.65f, 0.3f)
            ink = hsv(h, s * 0.16f, 0.96f)
            sub = hsv(h, s * 0.32f, 0.72f)
            faint = hsv(h, s * 0.3f, 0.5f)
            navBg = hsv(h, s * 0.95f, 0.038f)
            pos = 0xFF34D399.toInt(); neg = 0xFFFB7185.toInt()
        } else {
            bgTop = hsv(h, s * 0.38f, 0.93f)
            bg = hsv(h, s * 0.28f, 0.965f)
            card = 0xFFFFFFFF.toInt()
            cardStroke = hsv(h, s * 0.32f, 0.87f)
            field = hsv(h, s * 0.3f, 0.95f)
            fieldStroke = hsv(h, s * 0.35f, 0.84f)
            ink = hsv(h, s * 0.55f, 0.16f)
            sub = hsv(h, s * 0.3f, 0.44f)
            faint = hsv(h, s * 0.25f, 0.62f)
            navBg = 0xFFFFFFFF.toInt()
            pos = 0xFF059669.toInt(); neg = 0xFFE11D48.toInt()
        }
    }

    fun scale(c: Int, f: Float): Int {
        val r = (Color.red(c) * f).toInt().coerceIn(0, 255)
        val g = (Color.green(c) * f).toInt().coerceIn(0, 255)
        val b = (Color.blue(c) * f).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    fun lighten(c: Int, f: Float): Int {
        val r = (Color.red(c) + (255 - Color.red(c)) * f).toInt()
        val g = (Color.green(c) + (255 - Color.green(c)) * f).toInt()
        val b = (Color.blue(c) + (255 - Color.blue(c)) * f).toInt()
        return Color.rgb(r, g, b)
    }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun roundRect(color: Int, radiusDp: Float, ctx: Context, strokeColor: Int = 0, strokeDp: Int = 0): GradientDrawable {
    val d = GradientDrawable()
    d.shape = GradientDrawable.RECTANGLE
    d.cornerRadius = ctx.dpf(radiusDp)
    d.setColor(color)
    if (strokeColor != 0) d.setStroke(ctx.dp(strokeDp.coerceAtLeast(1)), strokeColor)
    return d
}

fun gradAccent(ctx: Context, radiusDp: Float): GradientDrawable {
    val d = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(P.lighten(P.accent, 0.15f), P.accentDark))
    d.shape = GradientDrawable.RECTANGLE
    d.cornerRadius = ctx.dpf(radiusDp)
    return d
}

fun bgGradient(ctx: Context): GradientDrawable {
    val d = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(P.bgTop, P.bg))
    return d
}

fun txt(ctx: Context, s: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView {
    val t = TextView(ctx)
    t.text = s
    t.textSize = sizeSp
    t.setTextColor(color)
    if (bold) t.typeface = Typeface.DEFAULT_BOLD
    return t
}

fun cardBox(ctx: Context): LinearLayout {
    val l = LinearLayout(ctx)
    l.orientation = LinearLayout.VERTICAL
    l.background = roundRect(P.card, 20f, ctx, P.cardStroke, 1)
    l.setPadding(ctx.dp(16), ctx.dp(16), ctx.dp(16), ctx.dp(16))
    return l
}

fun fieldBox(ctx: Context, hint: String): EditText {
    val e = EditText(ctx)
    e.hint = hint
    e.textSize = 15f
    e.setTextColor(P.ink)
    e.setHintTextColor(P.faint)
    e.background = roundRect(P.field, 13f, ctx, P.fieldStroke, 1)
    e.setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))
    e.setSingleLine(true)
    return e
}

fun primaryBtn(ctx: Context, label: String): TextView {
    val b = txt(ctx, label, 15f, P.onAccent, true)
    b.gravity = Gravity.CENTER
    b.background = gradAccent(ctx, 15f)
    b.setPadding(ctx.dp(18), ctx.dp(13), ctx.dp(18), ctx.dp(13))
    b.isClickable = true
    return b
}

fun ghostBtn(ctx: Context, label: String): TextView {
    val b = txt(ctx, label, 14f, P.accent, true)
    b.gravity = Gravity.CENTER
    b.background = roundRect(Color.TRANSPARENT, 13f, ctx, P.accent, 1)
    b.setPadding(ctx.dp(16), ctx.dp(11), ctx.dp(16), ctx.dp(11))
    b.isClickable = true
    return b
}

fun iconBtn(ctx: Context, resId: Int, tint: Int, sizeDp: Int = 38): ImageButton {
    val b = ImageButton(ctx)
    b.setImageResource(resId)
    b.setColorFilter(tint)
    b.background = roundRect(P.field, 12f, ctx, P.fieldStroke, 1)
    b.scaleType = ImageView.ScaleType.CENTER_INSIDE
    b.setPadding(ctx.dp(9), ctx.dp(9), ctx.dp(9), ctx.dp(9))
    val lp = LinearLayout.LayoutParams(ctx.dp(sizeDp), ctx.dp(sizeDp))
    b.layoutParams = lp
    return b
}

val AV_PAIRS = listOf(
    intArrayOf(0xFF5EEAD4.toInt(), 0xFF14B8A6.toInt()),
    intArrayOf(0xFFF5C97B.toInt(), 0xFFF59E0B.toInt()),
    intArrayOf(0xFF93C5FD.toInt(), 0xFF3B82F6.toInt()),
    intArrayOf(0xFFFDA4AF.toInt(), 0xFFF43F5E.toInt()),
    intArrayOf(0xFFC4B5FD.toInt(), 0xFF8B5CF6.toInt()),
    intArrayOf(0xFF86EFAC.toInt(), 0xFF22C55E.toInt())
)

fun avatarView(ctx: Context, name: String, sizeDp: Int = 36): TextView {
    val pair = AV_PAIRS[(name.hashCode().mod(AV_PAIRS.size))]
    val d = GradientDrawable(GradientDrawable.Orientation.TL_BR, pair)
    d.shape = GradientDrawable.OVAL
    val t = txt(ctx, name.trim().firstOrNull()?.uppercase() ?: "?", (sizeDp * 0.42f), 0xFF03211B.toInt(), true)
    t.gravity = Gravity.CENTER
    t.background = d
    t.layoutParams = LinearLayout.LayoutParams(ctx.dp(sizeDp), ctx.dp(sizeDp))
    return t
}

fun animateIn(v: View, delayMs: Long = 0) {
    v.alpha = 0f
    v.translationY = 26f
    v.animate().alpha(1f).translationY(0f).setStartDelay(delayMs).setDuration(380).start()
}

fun Activity.toast(s: String) {
    Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}

fun vSpace(ctx: Context, hDp: Int): View {
    val v = View(ctx)
    v.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(hDp))
    return v
}

fun hLine(ctx: Context): View {
    val v = View(ctx)
    v.setBackgroundColor(P.cardStroke)
    v.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(1))
    return v
}
