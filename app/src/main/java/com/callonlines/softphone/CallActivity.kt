package com.callonlines.softphone

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.callonlines.softphone.databinding.ActivityCallBinding
import org.linphone.core.Call

class CallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallBinding
    private var startTimeMs = 0L
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (startTimeMs > 0) {
                val elapsed = (System.currentTimeMillis() - startTimeMs) / 1000
                val mm = elapsed / 60
                val ss = elapsed % 60
                binding.tvDuration.text = String.format("%02d:%02d", mm, ss)
            }
            timerHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnHangup.setOnClickListener { LinphoneManager.hangup() }
        binding.btnMute.setOnClickListener { updateMuteUI(LinphoneManager.toggleMute()) }
        binding.btnSpeaker.setOnClickListener { updateSpeakerUI(LinphoneManager.toggleSpeaker()) }
        binding.btnAnswer.setOnClickListener {
            LinphoneManager.currentCall.value?.let { LinphoneManager.answer(it) }
        }
        binding.btnDecline.setOnClickListener {
            LinphoneManager.currentCall.value?.let { LinphoneManager.decline(it) }
        }

        updateMuteUI(LinphoneManager.isMuted())
        updateSpeakerUI(LinphoneManager.isSpeaker())

        LinphoneManager.callState.observe(this) { state ->
            when (state) {
                Call.State.IncomingReceived -> {
                    binding.tvStatus.text = "Llamada entrante"
                    binding.incomingControls.visibility = View.VISIBLE
                    binding.activeControls.visibility = View.GONE
                    binding.btnHangup.visibility = View.GONE
                }
                Call.State.OutgoingInit,
                Call.State.OutgoingProgress -> {
                    binding.tvStatus.text = "Llamando…"
                    binding.incomingControls.visibility = View.GONE
                    binding.activeControls.visibility = View.VISIBLE
                    binding.btnHangup.visibility = View.VISIBLE
                }
                Call.State.OutgoingRinging -> binding.tvStatus.text = "Sonando…"
                Call.State.Connected,
                Call.State.StreamsRunning -> {
                    if (startTimeMs == 0L) {
                        startTimeMs = System.currentTimeMillis()
                        timerHandler.post(timerRunnable)
                    }
                    binding.tvStatus.text = "En llamada"
                    binding.incomingControls.visibility = View.GONE
                    binding.activeControls.visibility = View.VISIBLE
                    binding.btnHangup.visibility = View.VISIBLE
                }
                Call.State.End, Call.State.Released -> {
                    timerHandler.removeCallbacks(timerRunnable)
                    finish()
                }
                Call.State.Error -> {
                    binding.tvStatus.text = "Error en llamada"
                    timerHandler.removeCallbacks(timerRunnable)
                    finish()
                }
                else -> {}
            }
        }

        LinphoneManager.currentCall.observe(this) { call ->
            call?.remoteAddress?.let { addr ->
                val name = addr.displayName ?: addr.username ?: "Desconocido"
                binding.tvRemote.text = name
            }
        }
    }

    private fun updateMuteUI(muted: Boolean) {
        binding.btnMute.setImageResource(
            if (muted) R.drawable.ic_mic_off else R.drawable.ic_mic
        )
    }

    private fun updateSpeakerUI(on: Boolean) {
        binding.btnSpeaker.setImageResource(
            if (on) R.drawable.ic_speaker else R.drawable.ic_speaker_off
        )
    }

    override fun onDestroy() {
        timerHandler.removeCallbacks(timerRunnable)
        super.onDestroy()
    }
}
