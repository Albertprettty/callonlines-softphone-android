package com.callonlines.softphone

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagLog {
    private const val TAG = "CL_Diag"
    private const val FILE_NAME = "softphone_diag.log"
    private const val MAX_BYTES = 200_000L
    private var file: File? = null

    fun init(ctx: Context) {
        try {
            file = File(ctx.filesDir, FILE_NAME)
            file?.let {
                if (!it.exists()) it.createNewFile()
                if (it.length() > MAX_BYTES) it.writeText("")
            }
            i("--- DiagLog init ---")
        } catch (e: Exception) {
            Log.w(TAG, "init failed", e)
        }
    }

    fun i(msg: String) = write("I", msg)
    fun w(msg: String) = write("W", msg)
    fun e(msg: String, t: Throwable? = null) = write("E", msg + (t?.let { "\n" + stack(it) } ?: ""))

    fun stack(t: Throwable): String {
        val sw = StringWriter(); t.printStackTrace(PrintWriter(sw)); return sw.toString()
    }

    fun read(): String = try {
        file?.readText() ?: "(no log file)"
    } catch (e: Exception) { "read error: ${e.message}" }

    fun clear() { try { file?.writeText("") } catch (_: Exception) {} }

    private val df = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private fun write(level: String, msg: String) {
        try {
            val line = "${df.format(Date())} $level $msg\n"
            Log.println(if (level == "E") Log.ERROR else Log.INFO, TAG, msg)
            val f = file ?: return
            f.appendText(line)
            if (f.length() > MAX_BYTES) {
                val txt = f.readText()
                f.writeText(txt.takeLast(MAX_BYTES.toInt() / 2))
            }
        } catch (_: Exception) {}
    }
}
