package com.example.inkwell

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    private lateinit var editor: EditText
    private lateinit var editorFrame: LinearLayout
    private lateinit var status: TextView
    private lateinit var fileName: TextView
    private lateinit var undoButton: ImageButton
    private lateinit var redoButton: ImageButton
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var changingText = false
    private var savedText = ""
    private var currentUri: Uri? = null
    private val networkExecutor = Executors.newSingleThreadExecutor()

    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            advanceNoteNumber()
            writeDocument(uri)
        }
    }
    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readDocument(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(10))
            setBackgroundColor(BG)
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val mark = TextView(this).apply {
            text = "T"
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setTextColor(BG)
            background = rounded(ACCENT, 12)
        }
        header.addView(mark, LinearLayout.LayoutParams(dp(38), dp(38)))
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        titles.addView(TextView(this).apply {
            text = "Текст"
            textSize = 17f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(WHITE)
        })
        fileName = TextView(this).apply { text = "Без названия.txt"; textSize = 12f; setTextColor(MUTED) }
        titles.addView(fileName)
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(iconButton("Открыть файл", ICON_OPEN, 44).apply {
            setOnClickListener { openDocument.launch(arrayOf("text/plain", "text/*")) }
        })
        header.addView(iconButton("Сохранить файл", ICON_SAVE, 44).apply { setOnClickListener { save() } })
        header.addView(iconButton("Отправить по Wi-Fi", ICON_WIFI, 44).apply { setOnClickListener { sendOverWifi() } })
        root.addView(header)

        val tools = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(SURFACE, 16)
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        undoButton = toolButton("Отменить", ICON_UNDO) { undo() }
        redoButton = toolButton("Повторить", ICON_REDO) { redo() }
        tools.addView(undoButton); tools.addView(redoButton); addDivider(tools)
        tools.addView(textTool("Вырезать", true) { cutSelection() })
        tools.addView(textTool("Копировать", false) { copySelection() })
        tools.addView(textTool("Вставить", false) { pasteSelection() })
        root.addView(tools, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(20) })

        editorFrame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(SURFACE, 18)
            setPadding(dp(16), dp(14), dp(16), dp(12))
        }
        val label = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        label.addView(View(this).apply { background = rounded(ACCENT, 8) }, LinearLayout.LayoutParams(dp(7), dp(7)))
        label.addView(TextView(this).apply {
            text = "Редактор"; textSize = 10f; letterSpacing = .04f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(MUTED); setPadding(dp(8), 0, 0, 0)
        })
        editorFrame.addView(label)
        editor = EditText(this).apply {
            setTextColor(EDITOR_TEXT); setHintTextColor(HINT); hint = "Начните писать…"
            textSize = 16f; typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            gravity = Gravity.TOP or Gravity.START; setPadding(0, dp(20), 0, dp(8))
            background = null; setSingleLine(false); isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS; setTextIsSelectable(true)
        }
        editorFrame.addView(editor, LinearLayout.LayoutParams(-1, 0, 1f))
        editor.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!changingText) {
                    val value = s?.toString().orEmpty()
                    if (undoStack.lastOrNull() != value) undoStack.addLast(value)
                    if (undoStack.size > 100) undoStack.removeFirst()
                    redoStack.clear(); refreshStatus(); refreshHistoryButtons()
                }
            }
        })
        root.addView(editorFrame, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(14) })

        val footer = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(2), dp(14), dp(2), 0)
        }
        status = TextView(this).apply { textSize = 12f; setTextColor(MUTED) }
        footer.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        footer.addView(TextView(this).apply {
            text = "TXT  ·  UTF-8"; textSize = 11f; letterSpacing = .04f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setTextColor(MUTED)
        })
        root.addView(footer)
        setContentView(root)
        undoStack.addLast(""); refreshStatus(); refreshHistoryButtons()
    }

    private fun textTool(label: String, destructive: Boolean, action: () -> Unit) = TextView(this).apply {
        text = label; textSize = 12f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (destructive) ACCENT else SOFT); gravity = Gravity.CENTER
        setPadding(dp(8), 0, dp(8), 0); minHeight = dp(40); isClickable = true; isFocusable = true
        setOnClickListener { action() }
    }

    private fun toolButton(description: String, path: String, action: () -> Unit) = iconButton(description, path, 38).apply {
        layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)); setPadding(dp(10), dp(10), dp(10), dp(10)); translationY = dp(3).toFloat(); setOnClickListener { action() }
    }

    private fun iconButton(description: String, path: String, size: Int) = ImageButton(this).apply {
        contentDescription = description; setImageDrawable(VectorIcon(path, SOFT))
        background = RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), rounded(0x00000000, 12), null)
        scaleType = android.widget.ImageView.ScaleType.CENTER; setPadding(dp(10), dp(10), dp(10), dp(10))
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    private fun addDivider(parent: LinearLayout) {
        parent.addView(View(this).apply { setBackgroundColor(STROKE) },
            LinearLayout.LayoutParams(dp(1), dp(22)).apply { marginStart = dp(4); marginEnd = dp(4) })
    }

    private fun refreshStatus() {
        val text = editor.text?.toString().orEmpty()
        val words = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
        val dirty = text != savedText
        status.text = "$words ${plural(words)}   ·   ${text.length} зн." + if (dirty) "   ·   Изменено" else ""
    }

    private fun plural(n: Int) = when {
        n % 100 in 11..14 -> "слов"
        n % 10 == 1 -> "слово"
        n % 10 in 2..4 -> "слова"
        else -> "слов"
    }

    private fun refreshHistoryButtons() {
        undoButton.isEnabled = undoStack.size > 1; redoButton.isEnabled = redoStack.isNotEmpty()
        undoButton.alpha = if (undoButton.isEnabled) 1f else .38f
        redoButton.alpha = if (redoButton.isEnabled) 1f else .38f
    }

    private fun applyHistory(value: String) {
        changingText = true; editor.setText(value); editor.setSelection(value.length); changingText = false
        refreshStatus(); refreshHistoryButtons()
    }

    private fun undo() {
        if (undoStack.size <= 1) return
        redoStack.addLast(undoStack.removeLast()); applyHistory(undoStack.last())
    }

    private fun redo() {
        if (redoStack.isEmpty()) return
        val value = redoStack.removeLast(); undoStack.addLast(value); applyHistory(value)
    }

    private fun save() {
        currentUri?.let { writeDocument(it) } ?: createDocument.launch(nextNoteName())
    }

    private fun sendOverWifi() {
        val name = nextNoteName()
        val body = editor.text.toString()
        Toast.makeText(this, "Отправляю на компьютер…", Toast.LENGTH_SHORT).show()
        networkExecutor.execute {
            try {
                check(BuildConfig.WIFI_SERVER_IP.isNotBlank() && BuildConfig.UPLOAD_TOKEN.isNotBlank() && BuildConfig.CERT_PIN.isNotBlank()) {
                    "Сначала настрой адрес и ключи сервера в локальном gradle.properties"
                }
                val connection = (URL("https://${BuildConfig.WIFI_SERVER_IP}:8765/upload").openConnection() as HttpsURLConnection)
                val trust = object : X509TrustManager {
                    override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String?) {
                        val digest = MessageDigest.getInstance("SHA-256").digest(chain[0].encoded)
                            .joinToString("") { "%02x".format(it) }
                        check(digest == BuildConfig.CERT_PIN) { "Сертификат сервера не совпал" }
                    }
                }
                connection.sslSocketFactory = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }.socketFactory
                connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { host, _ -> host == BuildConfig.WIFI_SERVER_IP }
                connection.requestMethod = "POST"
                connection.connectTimeout = 5000
                connection.readTimeout = 7000
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer ${BuildConfig.UPLOAD_TOKEN}")
                connection.setRequestProperty("X-File-Name", java.net.URLEncoder.encode(name, "UTF-8"))
                connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                check(connection.responseCode == 201) { "Сервер ответил ${connection.responseCode}" }
                connection.disconnect()
                advanceNoteNumber()
                runOnUiThread { toast("Отправлено: $name") }
            } catch (e: Exception) {
                runOnUiThread { toast("Не удалось отправить: ${e.localizedMessage ?: "проверь сервер и Wi-Fi"}") }
            }
        }
    }

    private fun nextNoteName(): String {
        val prefs = getSharedPreferences("notes", MODE_PRIVATE)
        val next = prefs.getString("next_number", "0") ?: "0"
        return if (next == "0") "Заметка.txt" else "Заметка $next.txt"
    }

    private fun writeDocument(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                it.write(editor.text.toString())
            } ?: error("Не удалось открыть файл для записи")
            currentUri = uri; savedText = editor.text.toString()
            fileName.text = displayName(uri); refreshStatus(); toast("Файл сохранён")
        } catch (e: Exception) { toast("Не удалось сохранить файл: ${e.localizedMessage ?: "ошибка записи"}") }
    }

    private fun advanceNoteNumber() {
        val prefs = getSharedPreferences("notes", MODE_PRIVATE)
        val number = java.math.BigInteger(prefs.getString("next_number", "0") ?: "0")
        prefs.edit().putString("next_number", (number + java.math.BigInteger.ONE).toString()).apply()
    }

    private fun readDocument(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            } ?: error("Не удалось открыть файл")
            currentUri = uri; savedText = text
            undoStack.clear(); undoStack.addLast(text); redoStack.clear()
            changingText = true; editor.setText(text); editor.setSelection(text.length); changingText = false
            fileName.text = displayName(uri); refreshStatus(); refreshHistoryButtons()
        } catch (e: Exception) { toast("Не удалось открыть файл: ${e.localizedMessage ?: "ошибка чтения"}") }
    }

    private fun displayName(uri: Uri) = uri.lastPathSegment?.substringAfterLast('/') ?: "Текстовый файл.txt"
    private fun cutSelection() { if (editor.hasSelection()) { copySelection(); editor.text?.delete(editor.selectionStart, editor.selectionEnd) } }
    private fun copySelection() {
        if (editor.hasSelection()) {
            val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clip.setPrimaryClip(ClipData.newPlainText("Текст", editor.text?.substring(editor.selectionStart, editor.selectionEnd)))
        }
    }
    private fun pasteSelection() {
        val clip = (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        val value = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString() ?: return
        val start = minOf(editor.selectionStart.coerceAtLeast(0), editor.selectionEnd.coerceAtLeast(0))
        val end = maxOf(editor.selectionStart.coerceAtLeast(0), editor.selectionEnd.coerceAtLeast(0))
        editor.text?.replace(start, end, value)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val BG = 0xFF111315.toInt()
        private const val SURFACE = 0xFF191C1F.toInt()
        private const val STROKE = 0xFF2A2E32.toInt()
        private const val WHITE = 0xFFF2F2EF.toInt()
        private const val EDITOR_TEXT = 0xFFE0E2DE.toInt()
        private const val SOFT = 0xFFB8BDB9.toInt()
        private const val MUTED = 0xFF777E7B.toInt()
        private const val HINT = 0xFF5B625F.toInt()
        private const val ACCENT = 0xFFA9C5B1.toInt()
        private const val ICON_OPEN = "M4 4h7l2 2h7v13H4z|M8 12h8m-4-4v8"
        private const val ICON_SAVE = "M5 3h12l3 3v15H4V3zm2 0v6h10V3m-8 18v-7h8v7"
        private const val ICON_UNDO = "M9 14 4 9l5-5M4 9h10a6 6 0 0 1 0 12h-2"
        private const val ICON_REDO = "m15 14 5-5-5-5m5 5H10a6 6 0 0 0 0 12h2"
        private const val ICON_WIFI = "M3 8C8 3 16 3 21 8|M6 11C9.5 8 14.5 8 18 11|M9 14C10.7 12.7 13.3 12.7 15 14|M11.5 18C11.5 17 12.5 17 12.5 18"
    }
}

private class VectorIcon(private val pathData: String, tint: Int) : android.graphics.drawable.Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint; style = Paint.Style.STROKE; strokeWidth = 1.7f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    override fun draw(canvas: Canvas) {
        val save = canvas.save(); val b = bounds
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        val side = minOf(b.width(), b.height()).toFloat(); canvas.scale(side / 24f, side / 24f)
        pathData.split('|').forEach { data -> canvas.drawPath(parsePath(data), paint) }
        canvas.restoreToCount(save)
    }
    private fun parsePath(data: String): Path {
        val path = Path(); val tokens = Regex("[A-Za-z]|[-+]?(?:\\d*\\.)?\\d+").findAll(data).map { it.value }.toList()
        var i = 0; var command = 'M'; var x = 0f; var y = 0f; var startX = 0f; var startY = 0f
        fun number() = tokens[i++].toFloat()
        while (i < tokens.size) {
            if (tokens[i].length == 1 && tokens[i][0].isLetter()) command = tokens[i++][0]
            when (command) {
                'M', 'm' -> {
                    val rel = command == 'm'; val nx = number(); val ny = number()
                    x = if (rel) x + nx else nx; y = if (rel) y + ny else ny
                    path.moveTo(x, y); startX = x; startY = y; command = if (rel) 'l' else 'L'
                }
                'L', 'l' -> { val rel = command == 'l'; val nx = number(); val ny = number(); x = if (rel) x + nx else nx; y = if (rel) y + ny else ny; path.lineTo(x, y) }
                'H', 'h' -> { val n = number(); x = if (command == 'h') x + n else n; path.lineTo(x, y) }
                'V', 'v' -> { val n = number(); y = if (command == 'v') y + n else n; path.lineTo(x, y) }
                'C', 'c' -> {
                    val rel = command == 'c'; val a = number(); val b = number(); val c = number(); val d = number(); val e = number(); val f = number()
                    val ox = if (rel) x else 0f; val oy = if (rel) y else 0f
                    path.cubicTo(a + ox, b + oy, c + ox, d + oy, e + ox, f + oy); x = e + ox; y = f + oy
                }
                'Q', 'q' -> {
                    val rel = command == 'q'; val a = number(); val b = number(); val c = number(); val d = number()
                    val ox = if (rel) x else 0f; val oy = if (rel) y else 0f
                    path.quadTo(a + ox, b + oy, c + ox, d + oy); x = c + ox; y = d + oy
                }
                'Z', 'z' -> { path.close(); x = startX; y = startY; command = 'M' }
                else -> { i++ }
            }
        }
        return path
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}



