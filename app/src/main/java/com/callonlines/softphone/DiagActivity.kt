package com.callonlines.softphone

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class DiagActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (resources.displayMetrics.density * 16).toInt()

        val title = TextView(this).apply {
            text = "Diagnostico CallOnLines"
            textSize = 18f
            setPadding(0, 0, 0, pad / 2)
            setTextColor(0xFFFFFFFF.toInt())
        }
        val info = TextView(this).apply {
            text = "Comparte este log para que podamos ver el motivo del cierre."
            textSize = 12f
            setPadding(0, 0, 0, pad)
            setTextColor(0xFFB8C5D6.toInt())
        }
        val logText = TextView(this).apply {
            text = DiagLog.read().ifBlank { "(log vacio)" }
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 10f
            setTextColor(0xFFE5E7EB.toInt())
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            addView(logText)
        }
        val btnCopy = Button(this).apply {
            text = "Copiar al portapapeles"
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("CallOnLines diag", logText.text))
                Toast.makeText(this@DiagActivity, "Copiado", Toast.LENGTH_SHORT).show()
            }
        }
        val btnClear = Button(this).apply {
            text = "Borrar log"
            setOnClickListener {
                DiagLog.clear()
                logText.text = "(log vacio)"
            }
        }

        val rowButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(btnCopy, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(btnClear, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            setPadding(0, pad / 2, 0, 0)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF03060D.toInt())
            setPadding(pad, pad, pad, pad)
            addView(title)
            addView(info)
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(rowButtons)
        }
        setContentView(root)
    }
}
