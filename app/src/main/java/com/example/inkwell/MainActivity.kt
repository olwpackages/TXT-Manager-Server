package com.example.inkwell

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
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
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import org.json.JSONObject
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
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
    private var accentMark: TextView? = null
    private var accentDot: View? = null
    private val accentTextViews = mutableListOf<TextView>()
    private var screenContent: View? = null
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var changingText = false
    private var savedText = ""
    private var currentUri: Uri? = null
    private var draftBaseName: String? = null
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
    private val openFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) createFolderIn(uri)
    }
    private val openCustomization = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) applyAccentToScreen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        buildScreen()
        if (getSharedPreferences("settings", MODE_PRIVATE).getBoolean("auto_update_check", true)) {
            editor.post { checkUpdatesSilently() }
        }
    }

    private fun buildScreen() {
        val compact = resources.configuration.screenHeightDp < 760
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(if (compact) 6 else 12), dp(20), dp(if (compact) 4 else 10))
            setBackgroundColor(BG)
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val mark = TextView(this).apply {
            text = "T"
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setTextColor(BG)
            background = rounded(accentColor(), 12)
        }
        accentMark = mark
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
        fileName = TextView(this).apply { text = nextNoteName(); textSize = 12f; setTextColor(MUTED) }
        fileName.isClickable = true
        fileName.isFocusable = true
        fileName.contentDescription = "Изменить имя файла"
        fileName.setOnClickListener { showRenameDialog() }
        val fileTitleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fileTitleRow.addView(fileName)
        fileTitleRow.addView(iconButton("Переименовать заметку", ICON_EDIT, 28).apply {
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setOnClickListener { showRenameDialog() }
        }, LinearLayout.LayoutParams(dp(28), dp(28)))
        titles.addView(fileTitleRow)
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(iconButton("Открыть меню", ICON_MENU, 44).apply { setOnClickListener { showActionMenu(this) } })
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
        root.addView(tools, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(if (compact) 8 else 20) })

        editorFrame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(SURFACE, 18)
            setPadding(dp(16), dp(14), dp(16), dp(12))
        }
        val label = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        accentDot = View(this).apply { background = rounded(accentColor(), 8) }
        label.addView(accentDot, LinearLayout.LayoutParams(dp(7), dp(7)))
        label.addView(TextView(this).apply {
            text = "Редактор"; textSize = 10f; letterSpacing = .04f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(MUTED); setPadding(dp(8), 0, 0, 0)
        })
        editorFrame.addView(label)
        editor = EditText(this).apply {
            setTextColor(EDITOR_TEXT); setHintTextColor(HINT); hint = "Начните писать…"
            textSize = 16f; typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            gravity = Gravity.TOP or Gravity.START; setPadding(0, dp(if (compact) 10 else 20), 0, dp(8))
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
        root.addView(editorFrame, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(if (compact) 8 else 14) })

        val footer = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(2), dp(if (compact) 6 else 14), dp(2), 0)
        }
        status = TextView(this).apply { textSize = 12f; setTextColor(MUTED) }
        footer.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        footer.addView(TextView(this).apply {
            text = "txt  ·  utf-8"; textSize = 11f; letterSpacing = .04f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setTextColor(MUTED)
        })
        root.addView(footer)
        screenContent = root
        setContentView(root)
        undoStack.addLast(""); refreshStatus(); refreshHistoryButtons()
    }

    private fun textTool(label: String, destructive: Boolean, action: () -> Unit) = TextView(this).apply {
        text = label; textSize = 12f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(accentColor()); gravity = Gravity.CENTER
        setPadding(dp(8), 0, dp(8), 0); minHeight = dp(40); isClickable = true; isFocusable = true
        setOnClickListener { action() }
        accentTextViews.add(this)
    }

    private fun toolButton(description: String, path: String, action: () -> Unit) = iconButton(description, path, 38).apply {
        layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)); setPadding(dp(10), dp(10), dp(10), dp(10)); translationY = dp(3).toFloat(); setOnClickListener { action() }
    }

    private fun iconButton(description: String, path: String, size: Int) = ImageButton(this).apply {
        contentDescription = description
        when (path) {
            ICON_OPEN -> setImageResource(R.drawable.icons8_open_document)
            ICON_PALETTE -> setImageResource(R.drawable.icons8_paint_brush)
            ICON_SETTINGS -> setImageResource(R.drawable.icons8_settings)
            else -> setImageDrawable(VectorIcon(path, SOFT))
        }
        imageTintList = ColorStateList.valueOf(SOFT)
        background = RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), rounded(0x00000000, 12), null)
        scaleType = if (path == ICON_OPEN || path == ICON_PALETTE || path == ICON_SETTINGS)
            android.widget.ImageView.ScaleType.CENTER_INSIDE
        else android.widget.ImageView.ScaleType.CENTER
        setPadding(dp(10), dp(10), dp(10), dp(10))
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    private fun showActionMenu(anchor: View) {
        val glassEnabled = getSharedPreferences("appearance", MODE_PRIVATE).getBoolean("glass_enabled", false)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(SURFACE, 18)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            elevation = dp(12).toFloat()
        }
        panel.addView(TextView(this).apply {
            text = "Действия"
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(MUTED)
            setPadding(dp(14), dp(8), dp(14), dp(10))
        })
        fun row(label: String, icon: String, action: () -> Unit) {
            val item = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                background = RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), rounded(0x00000000, 12), null)
                setPadding(dp(6), dp(3), dp(12), dp(3))
            }
            val itemIcon = iconButton(label, icon, 42)
            if (label == "Кастомизация") itemIcon.translationY = dp(2).toFloat()
            item.addView(itemIcon, LinearLayout.LayoutParams(dp(42), dp(42)))
            item.addView(View(this).apply { setBackgroundColor(accentDividerColor()) }, LinearLayout.LayoutParams(dp(1), dp(20)).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            })
            item.addView(TextView(this).apply {
                text = label
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setTextColor(WHITE)
                setPadding(dp(8), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, -1, 1f))
            item.setOnClickListener { action() }
            panel.addView(item, LinearLayout.LayoutParams(-1, dp(48)))
        }
        lateinit var menuDialog: Dialog
        row("Открыть файл", ICON_OPEN) { menuDialog.dismiss(); openDocument.launch(arrayOf("text/plain", "text/*")) }
        row("Сохранить", ICON_SAVE) { menuDialog.dismiss(); save() }
        row("Сохранить как…", ICON_SAVE) { menuDialog.dismiss(); createDocument.launch(saveDocumentName()) }
        row("Отправить заметку", ICON_WIFI) { menuDialog.dismiss(); sendOverWifi() }
        row("Проверить / скачать APK", ICON_UPDATE) { menuDialog.dismiss(); checkForUpdates() }
        row("Кастомизация", ICON_PALETTE) {
            menuDialog.dismiss()
            openCustomization.launch(Intent(this, CustomizationActivity::class.java))
        }
        row("Настройки", ICON_SETTINGS) {
            menuDialog.dismiss()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        row("Создать папку", ICON_FOLDER) { menuDialog.dismiss(); openFolder.launch(null) }

        val menuWidth = dp(276)
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(menuWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(resources.displayMetrics.heightPixels, View.MeasureSpec.AT_MOST)
        )
        val menuSurface = if (glassEnabled) {
            val content = screenContent
            if (content != null) captureGlassSurface(content, anchor, menuWidth, panel.measuredHeight)
                ?: rounded(0xCC191C1F.toInt(), 18)
            else rounded(0xCC191C1F.toInt(), 18)
        } else rounded(SURFACE, 18)
        panel.background = ColorDrawable(Color.TRANSPARENT)

        menuDialog = Dialog(this)
        menuDialog.setContentView(panel)
        menuDialog.setCanceledOnTouchOutside(true)
        menuDialog.window?.apply {
            setBackgroundDrawable(menuSurface)
            setLayout(menuWidth, -2)
            setGravity(Gravity.TOP or Gravity.END)
            setDimAmount(0f)
            attributes = attributes.apply {
                val location = IntArray(2)
                anchor.getLocationOnScreen(location)
                x = dp(20)
                y = location[1] + anchor.height - dp(24)
                windowAnimations = R.style.MenuPopupAnimation
            }
        }
        menuDialog.show()
        menuDialog.window?.setLayout(menuWidth, -2)
        panel.alpha = 0f
        panel.translationX = dp(18).toFloat()
        panel.animate().alpha(1f).translationX(0f).setDuration(180).start()
    }

    private fun captureGlassSurface(root: View, anchor: View, width: Int, height: Int): android.graphics.drawable.Drawable? {
        if (root.width <= 0 || root.height <= 0 || width <= 0 || height <= 0) return null
        val rootPosition = IntArray(2)
        val anchorPosition = IntArray(2)
        root.getLocationOnScreen(rootPosition)
        anchor.getLocationOnScreen(anchorPosition)
        val left = (root.width - width - dp(20)).coerceIn(0, root.width - 1)
        val top = (anchorPosition[1] + anchor.height - dp(24) - rootPosition[1]).coerceIn(0, root.height - 1)
        val cropWidth = minOf(width, root.width - left)
        val cropHeight = minOf(height, root.height - top)
        if (cropWidth <= 0 || cropHeight <= 0) return null

        val scale = 0.38f
        val bitmap = Bitmap.createBitmap(
            maxOf(1, (cropWidth * scale).toInt()),
            maxOf(1, (cropHeight * scale).toInt()),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        canvas.translate(-left.toFloat(), -top.toFloat())
        root.draw(canvas)
        blurBitmap(bitmap, 12)
        return GlassSurfaceDrawable(bitmap, 0x66191C1F.toInt(), dp(18).toFloat())
    }

    private fun blurBitmap(bitmap: Bitmap, radius: Int) {
        val width = bitmap.width
        val height = bitmap.height
        val source = IntArray(width * height)
        val temp = IntArray(source.size)
        bitmap.getPixels(source, 0, width, 0, 0, width, height)
        for (y in 0 until height) for (x in 0 until width) {
            var red = 0; var green = 0; var blue = 0; var alpha = 0; var count = 0
            for (dx in -radius..radius) {
                val px = (x + dx).coerceIn(0, width - 1)
                val color = source[y * width + px]
                alpha += Color.alpha(color); red += Color.red(color); green += Color.green(color); blue += Color.blue(color); count++
            }
            temp[y * width + x] = Color.argb(alpha / count, red / count, green / count, blue / count)
        }
        for (y in 0 until height) for (x in 0 until width) {
            var red = 0; var green = 0; var blue = 0; var alpha = 0; var count = 0
            for (dy in -radius..radius) {
                val py = (y + dy).coerceIn(0, height - 1)
                val color = temp[py * width + x]
                alpha += Color.alpha(color); red += Color.red(color); green += Color.green(color); blue += Color.blue(color); count++
            }
            source[y * width + x] = Color.argb(alpha / count, red / count, green / count, blue / count)
        }
        bitmap.setPixels(source, 0, width, 0, 0, width, height)
    }

    private fun createFolderIn(treeUri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) { }
        val nameInput = EditText(this).apply {
            hint = "Например: Учёба"
            setSingleLine(true)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Новая папка")
            .setMessage("Выбери место, затем укажи имя папки")
            .setView(nameInput)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Создать") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty() || name == "." || name == ".." || name.contains('/') || name.contains('\\')) {
                    toast("Укажи корректное имя папки")
                    return@setPositiveButton
                }
                networkExecutor.execute {
                    try {
                        val parent = DocumentsContract.buildDocumentUriUsingTree(
                            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
                        )
                        val created = DocumentsContract.createDocument(
                            contentResolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name
                        )
                        runOnUiThread { toast(if (created != null) "Папка создана: $name" else "Не удалось создать папку") }
                    } catch (e: Exception) {
                        runOnUiThread { toast("Не удалось создать папку: ${e.localizedMessage ?: "ошибка доступа"}") }
                    }
                }
            }.show()
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
        currentUri?.let { writeDocument(it) } ?: createDocument.launch(saveDocumentName())
    }

    private fun saveDocumentName() = "${draftBaseName?.takeIf { it.isNotBlank() } ?: nextNoteName().removeSuffix(".txt")}.txt"

    private fun showRenameDialog() {
        val dialog = Dialog(this)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(SURFACE); cornerRadius = dp(20).toFloat() }
            setPadding(dp(20), dp(18), dp(20), dp(16))
        }
        card.addView(TextView(this).apply {
            text = "Имя заметки"
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(accentColor())
        })
        card.addView(TextView(this).apply {
            text = "Изменяется только имя файла"
            textSize = 13f
            setTextColor(MUTED)
            setPadding(0, dp(5), 0, dp(14))
        })
        val nameRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(BG, 12)
            setPadding(dp(12), 0, dp(12), 0)
        }
        val input = EditText(this).apply {
            setSingleLine(true)
            setTextColor(WHITE)
            setHintTextColor(HINT)
            hint = "Название"
            background = null
            setText(currentBaseName())
            selectAll()
        }
        nameRow.addView(input, LinearLayout.LayoutParams(0, dp(48), 1f))
        nameRow.addView(TextView(this).apply { text = ".txt"; textSize = 14f; setTextColor(MUTED) })
        card.addView(nameRow)
        val actions = LinearLayout(this).apply { gravity = Gravity.END; setPadding(0, dp(14), 0, 0) }
        val cancel = TextView(this).apply {
            text = "Отмена"; gravity = Gravity.CENTER; setTextColor(MUTED); setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener { dialog.dismiss() }
        }
        val confirm = TextView(this).apply {
            text = "Готово"; gravity = Gravity.CENTER; setTextColor(BG); setPadding(dp(16), dp(10), dp(16), dp(10))
            background = rounded(accentColor(), 11)
            setOnClickListener {
                val newBase = input.text.toString().trim()
                val oldBase = currentBaseName()
                if (!validBaseName(newBase)) { toast("Введите корректное имя"); return@setOnClickListener }
                val known = getSharedPreferences("notes", MODE_PRIVATE).getStringSet("known_names", emptySet()).orEmpty()
                if (!newBase.equals(oldBase, true) && known.any { it.equals(newBase, true) }) {
                    toast("Такое название уже используется. Выберите другое.")
                    return@setOnClickListener
                }
                val existingUri = currentUri
                if (existingUri != null && !newBase.equals(oldBase, true)) {
                    try {
                        val renamed = DocumentsContract.renameDocument(contentResolver, existingUri, "$newBase.txt")
                        if (renamed == null) { toast("Хранилище не смогло переименовать файл"); return@setOnClickListener }
                        currentUri = renamed
                        val prefs = getSharedPreferences("notes", MODE_PRIVATE)
                        val known = prefs.getStringSet("known_names", emptySet()).orEmpty().toMutableSet()
                        known.removeAll { it.equals(oldBase, true) }
                        known.add(newBase)
                        prefs.edit().putStringSet("known_names", known).apply()
                    } catch (e: Exception) {
                        toast("Не удалось переименовать: ${e.localizedMessage ?: "проверьте, занято ли имя"}")
                        return@setOnClickListener
                    }
                }
                draftBaseName = newBase
                rememberName(newBase)
                fileName.text = "$newBase.txt"
                dialog.dismiss()
            }
        }
        actions.addView(cancel)
        actions.addView(confirm, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6) })
        card.addView(actions)
        dialog.setContentView(card)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(resources.displayMetrics.widthPixels - dp(40), -2)
        }
    }

    private fun currentBaseName() = (draftBaseName ?: fileName.text.toString()).removeSuffix(".txt")
    private fun validBaseName(name: String) = name.isNotBlank() && name !in setOf(".", "..") &&
        name.none { it in "\\/:*?\"<>|" }
    private fun rememberName(name: String) {
        val prefs = getSharedPreferences("notes", MODE_PRIVATE)
        val known = prefs.getStringSet("known_names", emptySet()).orEmpty().toMutableSet()
        known.add(name)
        prefs.edit().putStringSet("known_names", known).apply()
    }

    private fun checkForUpdates() {
        val prefs = getSharedPreferences("updates", MODE_PRIVATE)
        val savedServer = prefs.getString("server_url", null)
            ?.takeIf { it.startsWith("https://") && it.endsWith(":8765") }
        val serverAddress = savedServer.orEmpty()
        val input = EditText(this).apply {
            hint = "https://<IP компьютера>:8765"
            setSingleLine(true)
            setText(serverAddress)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Обновления")
            .setMessage("Введите адрес единого Node.js сервера на компьютере")
            .setView(input)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Проверить") { _, _ ->
                val base = input.text.toString().trim().trimEnd('/')
                if (!base.startsWith("https://")) {
                    toast("Укажите адрес сервера, начинающийся с https://")
                    return@setPositiveButton
                }
                prefs.edit().putString("server_url", base).apply()
                fetchUpdateInfo(base)
            }.show()
    }

    private fun fetchUpdateInfo(base: String) {
        Toast.makeText(this, "Проверяю обновления…", Toast.LENGTH_SHORT).show()
        networkExecutor.execute {
            try {
                val connection = pinnedConnection("$base/update.json")
                connection.connectTimeout = 5000
                connection.readTimeout = 7000
                check(connection.responseCode == 200) { "Сервер ответил ${connection.responseCode}" }
                val info = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                connection.disconnect()
                val remoteCode = info.getInt("versionCode")
                @Suppress("DEPRECATION")
                val currentCode = packageManager.getPackageInfo(packageName, 0).versionCode
                runOnUiThread {
                    if (remoteCode <= currentCode) toast("Установлена последняя версия")
                    else {
                        val apkPath = info.optString("apk", "/Tekst.apk")
                        val downloadUrl = URL(URL(base), apkPath).toString()
                        showUpdateDialog(info.optString("versionName"), info.optString("notes"), downloadUrl)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { toast("Проверка не удалась: ${e.localizedMessage ?: "ошибка сети"}") }
            }
        }
    }

    private fun checkUpdatesSilently() {
        val prefs = getSharedPreferences("updates", MODE_PRIVATE)
        val saved = prefs.getString("server_url", null)
            ?.takeIf { it.startsWith("https://") && it.endsWith(":8765") }
            ?: return
        val base = saved.trimEnd('/')
        networkExecutor.execute {
            try {
                val connection = pinnedConnection("$base/update.json")
                connection.connectTimeout = 5000
                connection.readTimeout = 7000
                if (connection.responseCode != 200) return@execute
                val info = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                connection.disconnect()
                @Suppress("DEPRECATION")
                val currentCode = packageManager.getPackageInfo(packageName, 0).versionCode
                if (info.optInt("versionCode", 0) > currentCode) {
                    runOnUiThread {
                        val apkPath = info.optString("apk", "/Tekst.apk")
                        val downloadUrl = URL(URL(base), apkPath).toString()
                        showUpdateDialog(info.optString("versionName"), info.optString("notes"), downloadUrl)
                    }
                }
            } catch (_: Exception) {
                // Сервер может быть выключен; фоновая проверка не должна мешать запуску редактора.
            }
        }
    }

    private fun downloadUpdate(url: String) {
        Toast.makeText(this, "Скачиваю APK…", Toast.LENGTH_SHORT).show()
        networkExecutor.execute {
            try {
                val connection = pinnedConnection(url)
                connection.connectTimeout = 5000
                connection.readTimeout = 30000
                check(connection.responseCode == 200) { "Сервер ответил ${connection.responseCode}" }
                val dir = File(getExternalFilesDir(null), "updates").apply { mkdirs() }
                val apk = File(dir, "Tekst-update.apk")
                connection.inputStream.use { input -> apk.outputStream().use { input.copyTo(it) } }
                connection.disconnect()
                val uri = FileProvider.getUriForFile(this, "$packageName.files", apk)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread { startActivity(intent) }
            } catch (e: Exception) {
                runOnUiThread { toast("Не удалось скачать APK: ${e.localizedMessage ?: "ошибка сети"}") }
            }
        }
    }

    private fun showUpdateDialog(version: String, notes: String, downloadUrl: String) {
        val dialog = Dialog(this)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(SURFACE)
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), accentDividerColor())
            }
            setPadding(dp(22), dp(20), dp(22), dp(18))
        }
        card.addView(TextView(this).apply {
            text = "Доступно обновление${version.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""}"
            textSize = 19f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(accentColor())
        })
        card.addView(TextView(this).apply {
            text = notes.ifBlank { "Скачать и установить обновление?" }
            textSize = 14f
            setTextColor(WHITE)
            setPadding(0, dp(12), 0, dp(20))
        })
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        val later = TextView(this).apply {
            text = "Позже"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(accentColor())
            setPadding(dp(16), dp(11), dp(16), dp(11))
            isClickable = true
            isFocusable = true
            background = RippleDrawable(ColorStateList.valueOf(0x224CAF50), rounded(0x00000000, 12), null)
            setOnClickListener { dialog.dismiss() }
        }
        val download = TextView(this).apply {
            text = "Скачать APK"
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setTextColor(BG)
            setPadding(dp(16), dp(11), dp(16), dp(11))
            isClickable = true
            isFocusable = true
            background = RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), rounded(accentColor(), 12), null)
            setOnClickListener { dialog.dismiss(); downloadUpdate(downloadUrl) }
        }
        buttons.addView(later)
        buttons.addView(download, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        card.addView(buttons)
        dialog.setContentView(card)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(true)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(resources.displayMetrics.widthPixels - dp(40), -2)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.55f }
        }
    }

    private fun sendOverWifi() {
        val body = editor.text.toString()
        if (body.isBlank()) {
            toast("Пустую заметку отправлять незачем")
            return
        }
        val customName = draftBaseName?.takeIf { it.isNotBlank() }
        val name = "${customName ?: nextNoteName().removeSuffix(".txt")}.txt"
        Toast.makeText(this, "Отправляю на компьютер…", Toast.LENGTH_SHORT).show()
        networkExecutor.execute {
            try {
                check(BuildConfig.WIFI_SERVER_IP.isNotBlank() && BuildConfig.UPLOAD_TOKEN.isNotBlank() && BuildConfig.CERT_PIN.isNotBlank()) {
                    "Сначала настрой адрес и ключи сервера в локальном gradle.properties"
                }
                val serverBase = "https://${BuildConfig.WIFI_SERVER_IP}:8765"
                val connection = pinnedConnection("$serverBase/upload")
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
                if (customName == null) advanceNoteNumber()
                runOnUiThread {
                    if (customName == null) fileName.text = nextNoteName()
                    toast("Отправлено: $name")
                }
            } catch (e: Exception) {
                runOnUiThread { toast("Не удалось отправить: ${e.localizedMessage ?: "проверь сервер и Wi-Fi"}") }
            }
        }
    }

    private fun pinnedConnection(url: String): HttpsURLConnection {
        val connection = URL(url).openConnection() as HttpsURLConnection
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
        return connection
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
            fileName.text = displayName(uri); draftBaseName = fileName.text.toString().removeSuffix(".txt")
            rememberName(draftBaseName.orEmpty()); refreshStatus(); toast("Файл сохранён")
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
            fileName.text = displayName(uri); draftBaseName = fileName.text.toString().removeSuffix(".txt")
            rememberName(draftBaseName.orEmpty()); refreshStatus(); refreshHistoryButtons()
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
    private fun accentColor() = getSharedPreferences("appearance", MODE_PRIVATE).getInt("accent_color", DEFAULT_ACCENT)
    private fun accentDividerColor(): Int {
        val color = accentColor()
        return Color.rgb(Color.red(color) * 42 / 100, Color.green(color) * 42 / 100, Color.blue(color) * 42 / 100)
    }
    private fun applyAccentToScreen() {
        val color = accentColor()
        accentMark?.background = rounded(color, 12)
        accentDot?.background = rounded(color, 8)
        accentTextViews.forEach { it.setTextColor(color) }
    }
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
        const val DEFAULT_ACCENT = 0xFFA9C5B1.toInt()
        private const val ICON_OPEN = "icons8_open_document"
        private const val ICON_SAVE = "M5 3h12l3 3v15H4V3zm2 0v6h10V3m-8 18v-7h8v7"
        private const val ICON_UNDO = "M9 14 4 9l5-5M4 9h10a6 6 0 0 1 0 12h-2"
        private const val ICON_REDO = "m15 14 5-5-5-5m5 5H10a6 6 0 0 0 0 12h2"
        private const val ICON_UPDATE = "M20 8Q16 2 10 4Q5 5 4 10M4 10V5M4 10h5M4 16Q8 22 14 20Q19 19 20 14M20 14v5M20 14h-5"
        private const val ICON_WIFI = "M3 9Q12 1 21 9M6 12Q12 7 18 12M9 15Q12 13 15 15M12 19h.01"
        private const val ICON_MENU = "M4 6h16M4 12h16M4 18h16"
        private const val ICON_FOLDER = "M3 6h7l2 2h9v11H3z"
        private const val ICON_EDIT = "M4 16.5V20h3.5L18 9.5 14.5 6 4 16.5z|M13.5 7 17 10.5"
        private const val ICON_PALETTE = "ART_BRUSH"
        private const val ICON_SETTINGS = "icons8_settings"
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
        if (pathData == "ART_BRUSH") drawPaintBrush(canvas)
        else pathData.split('|').forEach { data -> canvas.drawPath(parsePath(data), paint) }
        canvas.restoreToCount(save)
    }
    private fun drawPaintBrush(canvas: Canvas) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paint.color; style = Paint.Style.FILL }
        val handle = Path().apply {
            moveTo(12.5f, 10f); lineTo(18.5f, 3.5f); quadTo(20f, 2f, 21.5f, 3.5f)
            quadTo(23f, 5f, 21.5f, 6.5f); lineTo(15f, 13f); close()
        }
        val ferrule = Path().apply {
            moveTo(9f, 13.5f); lineTo(12.5f, 10f); lineTo(15f, 12.5f); lineTo(11.5f, 16f); close()
        }
        val bristles = Path().apply {
            moveTo(9f, 13.5f); quadTo(7f, 13f, 5.5f, 15f); lineTo(2.8f, 20.8f)
            quadTo(2.5f, 22f, 4f, 21.6f); lineTo(10f, 19f); quadTo(12f, 17.5f, 11.5f, 16f); close()
        }
        canvas.drawPath(handle, fill)
        fill.color = Color.GRAY
        canvas.drawPath(ferrule, fill)
        fill.color = paint.color
        canvas.drawPath(bristles, fill)
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

private class GlassSurfaceDrawable(
    private val bitmap: Bitmap,
    private val tint: Int,
    private val cornerRadius: Float
) : android.graphics.drawable.Drawable() {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint }
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        val save = canvas.save()
        val path = Path().apply { addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW) }
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, null, rect, bitmapPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, tintPaint)
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) { bitmapPaint.alpha = alpha; tintPaint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { bitmapPaint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}





