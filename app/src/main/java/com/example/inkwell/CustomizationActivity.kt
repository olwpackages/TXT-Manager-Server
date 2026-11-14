package com.example.inkwell

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class CustomizationActivity : Activity() {
    private var hue = 145f
    private var saturation = 0.16f
    private var value = 0.77f
    private lateinit var preview: View
    private lateinit var colorLabel: TextView
    private lateinit var svPicker: SaturationValuePicker
    private lateinit var hueSlider: GradientPickerSlider
    private lateinit var brightnessSlider: GradientPickerSlider
    private lateinit var resetButton: TextView
    private lateinit var saveButton: TextView
    private lateinit var glassToggle: CapsuleToggleView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        val prefs = getSharedPreferences("appearance", MODE_PRIVATE)
        val hsv = FloatArray(3)
        Color.colorToHSV(prefs.getInt("accent_color", MainActivity.DEFAULT_ACCENT), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(24))
            setBackgroundColor(BG)
        }
        root.addView(TextView(this).apply {
            text = "Кастомизация"
            textSize = 24f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(WHITE)
        })

        val glassRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(SURFACE, 16)
            setPadding(dp(14), dp(10), dp(12), dp(10))
        }
        val glassLabels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        glassLabels.addView(TextView(this).apply {
            text = "Жидкое стекло"
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(WHITE)
        })
        glassLabels.addView(TextView(this).apply {
            text = "Полупрозрачное меню с мягким blur"
            textSize = 11f
            setTextColor(MUTED)
            setPadding(0, dp(3), 0, 0)
        })
        glassRow.addView(glassLabels, LinearLayout.LayoutParams(0, -2, 1f))
        glassToggle = CapsuleToggleView(
            context = this,
            checked = prefs.getBoolean("glass_enabled", false),
            thumbColor = prefs.getInt("accent_color", MainActivity.DEFAULT_ACCENT),
            trackColor = 0xFF282B2E.toInt()
        ) { enabled -> prefs.edit().putBoolean("glass_enabled", enabled).apply() }
        glassRow.addView(glassToggle, LinearLayout.LayoutParams(dp(76), dp(44)))
        root.addView(glassRow, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(18)
            bottomMargin = dp(14)
        })
        root.addView(TextView(this).apply {
            text = "Акцентный цвет"
            textSize = 14f
            setTextColor(MUTED)
            setPadding(0, dp(5), 0, dp(16))
        })

        val pickerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(SURFACE, 22)
            setPadding(dp(15), dp(15), dp(15), dp(14))
        }
        val previewRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        preview = View(this).apply { background = rounded(currentColor(), 13) }
        previewRow.addView(preview, LinearLayout.LayoutParams(dp(44), dp(44)))
        colorLabel = TextView(this).apply {
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(12), 0, 0, 0)
        }
        previewRow.addView(colorLabel)
        pickerCard.addView(previewRow)

        svPicker = SaturationValuePicker(this, hue, saturation, value) { sat, brightness ->
            saturation = sat
            value = brightness
        this@CustomizationActivity.brightnessSlider.setProgress(brightness)
            updatePreview()
        }
        pickerCard.addView(svPicker, LinearLayout.LayoutParams(-1, dp(168)).apply { topMargin = dp(14) })

        pickerCard.addView(sliderLabel("Оттенок"), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(18)
        })
        hueSlider = GradientPickerSlider(this, intArrayOf(
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, 0xFFFF00FF.toInt(), Color.RED
        ), hue / 359f) { progress ->
            hue = progress * 359f
            updatePickerColors()
        }
        pickerCard.addView(hueSlider, LinearLayout.LayoutParams(-1, dp(28)))

        pickerCard.addView(sliderLabel("Яркость"), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        })
        brightnessSlider = GradientPickerSlider(this, intArrayOf(Color.BLACK, Color.HSVToColor(floatArrayOf(hue, saturation, 1f))), value) { progress ->
            value = progress
            svPicker.setColorState(hue, saturation, value)
            updatePreview()
        }
        pickerCard.addView(brightnessSlider, LinearLayout.LayoutParams(-1, dp(28)))
        root.addView(pickerCard, LinearLayout.LayoutParams(-1, -2))

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, dp(18), 0, 0)
        }
        resetButton = actionButton("Сбросить", false) {
            prefs.edit().remove("accent_color").apply()
            setResult(RESULT_OK)
            finish()
        }
        buttons.addView(resetButton, LinearLayout.LayoutParams(-2, dp(40)).apply { marginEnd = dp(8) })
        saveButton = actionButton("Сохранить", true) {
            prefs.edit().putInt("accent_color", currentColor()).apply()
            setResult(RESULT_OK)
            finish()
        }
        buttons.addView(saveButton, LinearLayout.LayoutParams(-2, dp(40)))
        root.addView(buttons)
        setContentView(root)
        updatePreview()
    }

    private fun sliderLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 10f
        letterSpacing = .02f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(MUTED)
        setPadding(0, 0, 0, dp(2))
    }

    private fun actionButton(text: String, primary: Boolean, action: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(dp(15), 0, dp(15), 0)
        setTextColor(if (primary) BG else MainActivity.DEFAULT_ACCENT)
        background = rounded(if (primary) MainActivity.DEFAULT_ACCENT else SURFACE, 12)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun updatePickerColors() {
        svPicker.setColorState(hue, saturation, value)
        brightnessSlider.setColors(intArrayOf(Color.BLACK, Color.HSVToColor(floatArrayOf(hue, saturation, 1f))))
        brightnessSlider.setThumbColor(currentColor())
        updatePreview()
    }

    private fun currentColor() = Color.HSVToColor(floatArrayOf(hue, saturation, value))
    private fun updatePreview() {
        val color = currentColor()
        preview.background = rounded(color, 13)
        colorLabel.text = String.format("#%06X", 0xFFFFFF and color)
        colorLabel.setTextColor(color)
        if (::resetButton.isInitialized) resetButton.setTextColor(color)
        if (::saveButton.isInitialized) {
            saveButton.background = rounded(color, 12)
            saveButton.setTextColor(contrastingText(color))
        }
        if (::glassToggle.isInitialized) glassToggle.setThumbColor(color)
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun contrastingText(color: Int) = if (
        (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000 > 150
    ) Color.BLACK else Color.WHITE

    companion object {
        private const val BG = 0xFF111315.toInt()
        private const val SURFACE = 0xFF191C1F.toInt()
        private const val WHITE = 0xFFF2F2EF.toInt()
        private const val MUTED = 0xFF777E7B.toInt()
    }
}

private class SaturationValuePicker(
    context: android.content.Context,
    hue: Float,
    saturation: Float,
    value: Float,
    private val onChange: (Float, Float) -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private var colorField: Bitmap? = null
    private var hueValue = hue
    private var sat = saturation
    private var brightness = value

    init { isFocusable = true }

    fun setColorState(hue: Float, saturation: Float, value: Float) {
        hueValue = hue
        sat = saturation
        brightness = value
        colorField = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        val radius = 12f * resources.displayMetrics.density
        val bitmap = colorField?.takeIf { it.width == width && it.height == height } ?: run {
            val field = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val fieldCanvas = Canvas(field)
            paint.shader = null
            paint.color = Color.HSVToColor(floatArrayOf(hueValue, 1f, 1f))
            fieldCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f, Color.WHITE, 0x00FFFFFF, Shader.TileMode.CLAMP)
            fieldCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), 0x00000000, Color.BLACK, Shader.TileMode.CLAMP)
            fieldCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
            colorField = field
            field
        }
        val save = canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) })
        paint.color = Color.WHITE
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        val x = sat * width
        val y = (1f - brightness) * height
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * resources.displayMetrics.density
        paint.color = Color.WHITE
        canvas.drawCircle(x, y, 7f * resources.displayMetrics.density, paint)
        paint.style = Paint.Style.FILL
        canvas.restoreToCount(save)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                sat = (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                brightness = (1f - event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
                onChange(sat, brightness)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> return true
        }
        return true
    }
}

private class GradientPickerSlider(
    context: android.content.Context,
    colors: IntArray,
    progress: Float,
    private val onChange: (Float) -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = RectF()
    private var stops = colors
    private var amount = progress.coerceIn(0f, 1f)
    private var thumbColor = Color.WHITE

    init { isFocusable = true }

    fun setColors(colors: IntArray) { stops = colors; invalidate() }
    fun setThumbColor(color: Int) { thumbColor = color; invalidate() }
    fun setProgress(progress: Float) { amount = progress.coerceIn(0f, 1f); invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = 12f * resources.displayMetrics.density
        val centerY = height / 2f
        track.set(pad, centerY - 5f * resources.displayMetrics.density, width - pad, centerY + 5f * resources.displayMetrics.density)
        paint.shader = LinearGradient(track.left, 0f, track.right, 0f, stops, null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(track, track.height() / 2f, track.height() / 2f, paint)
        paint.shader = null
        val x = track.left + amount * track.width()
        paint.color = thumbColor
        canvas.drawCircle(x, centerY, 6f * resources.displayMetrics.density, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val pad = 12f * resources.displayMetrics.density
                amount = ((event.x - pad) / (width - 2f * pad).coerceAtLeast(1f)).coerceIn(0f, 1f)
                onChange(amount)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> return true
        }
        return true
    }
}

internal class CapsuleToggleView(
    context: android.content.Context,
    checked: Boolean,
    private var thumbColor: Int,
    private val trackColor: Int,
    private val onToggle: (Boolean) -> Unit
) : View(context) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val track = RectF()
    private var enabled = checked
    private var thumbPosition = if (checked) 1f else 0f
    private val density = resources.displayMetrics.density

    init {
        isClickable = true
        isFocusable = true
        contentDescription = stateDescription()
        setOnClickListener {
            enabled = !enabled
            contentDescription = stateDescription()
            onToggle(enabled)
            animate().cancel()
            val from = thumbPosition
            val to = if (enabled) 1f else 0f
            android.animation.ValueAnimator.ofFloat(from, to).apply {
                duration = 170
                addUpdateListener {
                    thumbPosition = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    fun setThumbColor(color: Int) {
        thumbColor = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val trackWidth = minOf(width.toFloat() - 4f * density, 72f * density)
        val trackHeight = 27f * density
        val left = (width - trackWidth) / 2f
        val top = (height - trackHeight) / 2f
        track.set(left, top, left + trackWidth, top + trackHeight)
        paint.color = trackColor
        canvas.drawRoundRect(track, trackHeight / 2f, trackHeight / 2f, paint)
        val thumbWidth = 25f * density
        val thumbHeight = 16f * density
        val inset = 4f * density
        val minX = track.left + inset + thumbWidth / 2f
        val maxX = track.right - inset - thumbWidth / 2f
        val x = minX + (maxX - minX) * thumbPosition
        paint.color = thumbColor
        val thumb = RectF(x - thumbWidth / 2f, track.centerY() - thumbHeight / 2f,
            x + thumbWidth / 2f, track.centerY() + thumbHeight / 2f)
        canvas.drawRoundRect(thumb, thumbHeight / 2f, thumbHeight / 2f, paint)
    }

    private fun stateDescription() = if (enabled) "Жидкое стекло включено" else "Жидкое стекло выключено"
}
