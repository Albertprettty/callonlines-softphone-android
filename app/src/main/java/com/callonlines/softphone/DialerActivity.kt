package com.callonlines.softphone

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.callonlines.softphone.databinding.ActivityDialerBinding
import org.linphone.core.Call
import org.linphone.core.RegistrationState

class DialerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDialerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDialerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pad = listOf(
            binding.key1 to "1", binding.key2 to "2", binding.key3 to "3",
            binding.key4 to "4", binding.key5 to "5", binding.key6 to "6",
            binding.key7 to "7", binding.key8 to "8", binding.key9 to "9",
            binding.keyStar to "*", binding.key0 to "0", binding.keyHash to "#"
        )
        pad.forEach { (btn, digit) ->
            btn.setOnClickListener {
                btn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                val cur = binding.etNumber.text?.toString() ?: ""
                binding.etNumber.setText(cur + digit)
                binding.etNumber.setSelection(binding.etNumber.text?.length ?: 0)
            }
        }

        binding.btnBack.setOnClickListener {
            val cur = binding.etNumber.text?.toString() ?: ""
            if (cur.isNotEmpty()) {
                binding.etNumber.setText(cur.dropLast(1))
                binding.etNumber.setSelection(binding.etNumber.text?.length ?: 0)
                binding.btnBack.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
        binding.btnBack.setOnLongClickListener {
            binding.etNumber.setText("")
            true
        }

        binding.btnCall.setOnClickListener {
            val number = binding.etNumber.text?.toString()?.trim().orEmpty()
            if (number.isBlank()) {
                Toast.makeText(this, "Escribe un numero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (LinphoneManager.registrationState.value != RegistrationState.Ok) {
                Toast.makeText(this, "Espera a estar Conectado", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                DiagLog.i("UI: btnCall pressed number=$number")
                val call = LinphoneManager.call(number)
                if (call != null) {
                    startActivity(Intent(this, CallActivity::class.java))
                } else {
                    Toast.makeText(this, "No se pudo iniciar la llamada — ver Diagnostico", Toast.LENGTH_LONG).show()
                }
            } catch (e: Throwable) {
                DiagLog.e("UI: btnCall threw", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Cerrar sesión")
                .setMessage("¿Desconectar tu cuenta SIP de este dispositivo?")
                .setPositiveButton("Cerrar sesión") { _, _ ->
                    CredentialStore(this).clear()
                    LinphoneManager.logout()
                    startActivity(Intent(this, LoginActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    finish()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        // Long-press the logout icon to open the diagnostic screen.
        binding.btnLogout.setOnLongClickListener {
            startActivity(Intent(this, DiagActivity::class.java))
            true
        }

        LinphoneManager.registrationState.observe(this) { state ->
            val (text, color) = when (state) {
                RegistrationState.Ok -> "Conectado" to R.color.status_ok
                RegistrationState.Failed -> "Sin conexión" to R.color.status_fail
                RegistrationState.Progress -> "Conectando…" to R.color.status_pending
                RegistrationState.Cleared -> "Desconectado" to R.color.status_fail
                else -> "…" to R.color.status_pending
            }
            binding.tvStatus.text = text
            binding.statusDot.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, color))
        }

        LinphoneManager.callState.observe(this) { state ->
            if (state == Call.State.IncomingReceived) {
                startActivity(Intent(this, CallActivity::class.java))
            }
        }
    }
}
