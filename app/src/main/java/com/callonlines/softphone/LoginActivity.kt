package com.callonlines.softphone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.callonlines.softphone.databinding.ActivityLoginBinding
import org.linphone.core.RegistrationState

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var creds: CredentialStore
    private var triedSavedLogin = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_CallOnLinesSoftphone)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        creds = CredentialStore(this)
        requestRuntimePermissions()

        binding.tvServer.text = creds.getDomain()
        binding.etUser.setText(creds.getUser())
        binding.etPass.setText(creds.getPass())

        binding.btnLogin.setOnClickListener {
            val user = binding.etUser.text.toString().trim()
            val pass = binding.etPass.text.toString()
            if (user.isBlank() || pass.isBlank()) {
                binding.tvStatus.text = "Escribe usuario y contraseña"
                return@setOnClickListener
            }
            doLogin(user, pass)
        }

        LinphoneManager.registrationState.observe(this) { state ->
            when (state) {
                RegistrationState.Ok -> {
                    val u = binding.etUser.text.toString().trim()
                    val p = binding.etPass.text.toString()
                    if (u.isNotBlank() && p.isNotBlank()) creds.save(u, p, creds.getDomain())
                    startActivity(Intent(this, DialerActivity::class.java))
                    finish()
                }
                RegistrationState.Progress -> binding.tvStatus.text = "Conectando…"
                RegistrationState.Failed -> binding.tvStatus.text = "Login fallido. Revisa usuario y contraseña."
                RegistrationState.Cleared -> binding.tvStatus.text = ""
                else -> {}
            }
        }

        if (creds.has() && !triedSavedLogin) {
            triedSavedLogin = true
            doLogin(creds.getUser(), creds.getPass())
        }
    }

    private fun doLogin(user: String, pass: String) {
        binding.tvStatus.text = "Conectando…"
        LinphoneManager.login(user, pass, creds.getDomain())
    }

    private fun requestRuntimePermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) needed += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) needed += Manifest.permission.POST_NOTIFICATIONS
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }
}
