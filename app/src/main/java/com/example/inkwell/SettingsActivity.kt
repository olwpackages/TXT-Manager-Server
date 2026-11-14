package com.example.inkwell

import android.app.Activity
import android.graphics.Typeface
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.WindowManager

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.decorView.systemUiVisibility = 0
        window.statusBarColor = BG
        window.navigationBarColor = BG
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(24))
            setBackgroundColor(BG)
        }
        val titleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(BackArrowView(this).apply {
            contentDescription = "Назад"
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(32), dp(42)))
        titleRow.addView(TextView(this).apply {
            text = "Настройки"
            textSize = 24f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(WHITE)
            setPadding(dp(1), 0, 0, 0)
        })
        root.addView(titleRow)
        root.addView(TextView(this).apply {
            text = "Версия ${appVersionName()}"
            textSize = 12f
            setTextColor(MUTED)
            setPadding(dp(33), 0, 0, 0)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(SURFACE, 16)
            setPadding(dp(14), dp(10), dp(12), dp(10))
        }
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(TextView(this).apply {
            text = "Проверять обновления при запуске"
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(WHITE)
        })
        copy.addView(TextView(this).apply {
            text = "Проверка только при полном открытии приложения"
            textSize = 11f
            setTextColor(MUTED)
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(CapsuleToggleView(
            this,
            prefs.getBoolean("auto_update_check", true),
            getSharedPreferences("appearance", MODE_PRIVATE).getInt("accent_color", MainActivity.DEFAULT_ACCENT),
            0xFF282B2E.toInt()
        ) { enabled -> prefs.edit().putBoolean("auto_update_check", enabled).apply() },
            LinearLayout.LayoutParams(dp(76), dp(44))
        )
        root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(root)
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        setContentView(scroll)
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    @Suppress("DEPRECATION")
    private fun appVersionName(): String = packageManager.getPackageInfo(packageName, 0).versionName ?: "—"

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val BG = 0xFF111315.toInt()
        private const val SURFACE = 0xFF191C1F.toInt()
        private const val WHITE = 0xFFF2F2EF.toInt()
        private const val MUTED = 0xFF777E7B.toInt()
    }
}

private class BackArrowView(context: android.content.Context) : android.view.View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = context.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE)
            .getInt("accent_color", MainActivity.DEFAULT_ACCENT)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = resources.displayMetrics.density
        val x = width / 2f + 2f * scale
        val y = height / 2f
        val path = Path().apply {
            moveTo(x + 5f * scale, y)
            lineTo(x - 5f * scale, y)
            moveTo(x - 5f * scale, y)
            lineTo(x, y - 5f * scale)
            moveTo(x - 5f * scale, y)
            lineTo(x, y + 5f * scale)
        }
        canvas.drawPath(path, paint)
    }
}
