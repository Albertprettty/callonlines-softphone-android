package com.callonlines.softphone

import android.content.Context
import androidx.lifecycle.MutableLiveData
import org.linphone.core.AVPFMode
import org.linphone.core.Account
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.MediaEncryption
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType

object LinphoneManager {

    private lateinit var core: Core
    private lateinit var factory: Factory
    private var initialized = false

    val registrationState = MutableLiveData(RegistrationState.None)
    val callState = MutableLiveData(Call.State.Idle)
    val callReason = MutableLiveData("")
    val currentCall = MutableLiveData<Call?>(null)

    private val coreListener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core, account: Account, state: RegistrationState?, message: String
        ) {
            DiagLog.i("REG state=$state msg=$message")
            registrationState.postValue(state ?: RegistrationState.None)
        }

        override fun onCallStateChanged(
            core: Core, call: Call, state: Call.State?, message: String
        ) {
            val reason  = try { call.errorInfo?.reason?.toString() } catch (_: Exception) { null }
            val phrase  = try { call.errorInfo?.phrase } catch (_: Exception) { null }
            val protoCode = try { call.errorInfo?.protocolCode } catch (_: Exception) { 0 }
            DiagLog.i("CALL state=$state msg=$message reason=$reason proto=$protoCode phrase=$phrase")
            callState.postValue(state ?: Call.State.Idle)
            callReason.postValue(buildString {
                append(state?.toString() ?: "Idle")
                if (!message.isNullOrBlank()) append(" - ").append(message)
                if (protoCode != null && protoCode > 0) append(" [").append(protoCode).append("]")
                if (!reason.isNullOrBlank()) append(" {").append(reason).append("}")
                if (!phrase.isNullOrBlank()) append(" \"").append(phrase).append("\"")
            })
            currentCall.postValue(call)
        }
    }

    fun init(context: Context) {
        if (initialized) return
        try {
            factory = Factory.instance()
            factory.setDebugMode(true, "CallOnLinesSoftphone")
            core = factory.createCore(null, null, context)

            // --- Compatibility tuning for MagnusBilling / Asterisk 13.x ---
            core.mediaEncryption = MediaEncryption.None
            core.isMediaEncryptionMandatory = false
            try { core.avpfMode = AVPFMode.Disabled } catch (t: Throwable) { DiagLog.w("avpf set: $t") }

            val nat = core.createNatPolicy()
            nat.stunServer = "stun.l.google.com:19302"
            nat.isStunEnabled = true
            nat.isIceEnabled = true
            nat.isUpnpEnabled = false
            nat.isTurnEnabled = false
            core.natPolicy = nat

            core.audioPort = 7078
            core.useRfc2833ForDtmf = true
            core.useInfoForDtmf = false

            core.isNetworkReachable = true
            core.isKeepAliveEnabled = true
            core.isAutoIterateEnabled = true
            core.isNativeRingingEnabled = true

            core.addListener(coreListener)
            core.start()
            configureCodecs()
            initialized = true
            DiagLog.i("Linphone Core started ver=${core.version}")
        } catch (t: Throwable) {
            DiagLog.e("init failed", t)
        }
    }

    fun login(
        username: String, password: String, domain: String,
        transport: TransportType = TransportType.Udp
    ): Boolean {
        if (!initialized) return false
        return try {
            core.clearAccounts()
            core.clearAllAuthInfo()

            val auth = factory.createAuthInfo(username, null, password, null, null, domain)
            core.addAuthInfo(auth)

            val params = core.createAccountParams()
            params.identityAddress = factory.createAddress("sip:$username@$domain") ?: return false
            val server = factory.createAddress("sip:$domain") ?: return false
            server.transport = transport
            params.serverAddress = server
            params.isRegisterEnabled = true
            params.avpfMode = AVPFMode.Disabled
            try { params.natPolicy = core.natPolicy } catch (_: Throwable) {}

            val account = core.createAccount(params)
            core.addAccount(account)
            core.defaultAccount = account
            DiagLog.i("login submitted user=$username domain=$domain transport=$transport")
            true
        } catch (t: Throwable) {
            DiagLog.e("login failed", t); false
        }
    }

    fun logout() {
        try {
            core.clearAccounts()
            core.clearAllAuthInfo()
            DiagLog.i("logout done")
        } catch (t: Throwable) { DiagLog.e("logout failed", t) }
    }

    fun call(number: String): Call? {
        if (!initialized) {
            DiagLog.w("call() core not initialized"); return null
        }
        return try {
            val account = core.defaultAccount ?: run { DiagLog.w("call() no default account"); return null }
            val domain = account.params.serverAddress?.domain ?: run {
                DiagLog.w("call() no domain"); return null
            }
            val target = if (number.contains("@")) "sip:$number" else "sip:$number@$domain"
            DiagLog.i("call() target=$target")
            val address = factory.createAddress(target) ?: run {
                DiagLog.w("call() bad address $target"); return null
            }
            val params = core.createCallParams(null) ?: run {
                DiagLog.w("call() null params"); return null
            }
            params.isVideoEnabled = false
            params.mediaEncryption = MediaEncryption.None
            params.avpfEnabled = false
            params.isEarlyMediaSendingEnabled = false
            val call = core.inviteAddressWithParams(address, params)
            DiagLog.i("call() invite returned=${call != null}")
            call
        } catch (t: Throwable) {
            DiagLog.e("call() threw", t); null
        }
    }

    fun answer(call: Call) {
        try {
            val params = core.createCallParams(call)
            params?.isVideoEnabled = false
            call.acceptWithParams(params)
        } catch (t: Throwable) { DiagLog.e("answer failed", t) }
    }
    fun decline(call: Call) { try { call.terminate() } catch (t: Throwable) { DiagLog.e("decline", t) } }
    fun hangup() {
        try { core.currentCall?.terminate() ?: core.terminateAllCalls() }
        catch (t: Throwable) { DiagLog.e("hangup", t) }
    }
    fun toggleMute(): Boolean = try {
        core.isMicEnabled = !core.isMicEnabled; !core.isMicEnabled
    } catch (_: Exception) { false }
    fun isMuted(): Boolean = try { !core.isMicEnabled } catch (_: Exception) { false }
    fun toggleSpeaker(): Boolean = try {
        val cur = core.outputAudioDevice
        val target = if (cur?.type == AudioDevice.Type.Speaker) AudioDevice.Type.Earpiece
        else AudioDevice.Type.Speaker
        core.audioDevices.firstOrNull { it.type == target }?.let { core.outputAudioDevice = it }
        target == AudioDevice.Type.Speaker
    } catch (_: Exception) { false }
    fun isSpeaker(): Boolean = try {
        core.outputAudioDevice?.type == AudioDevice.Type.Speaker
    } catch (_: Exception) { false }
    fun sendDtmf(digit: Char) { try { core.currentCall?.sendDtmf(digit) } catch (_: Exception) {} }

    private fun configureCodecs() {
        try {
            val keep = listOf("PCMU", "PCMA", "opus")
            for (pt in core.audioPayloadTypes) {
                val on = keep.any { it.equals(pt.mimeType, ignoreCase = true) }
                pt.enable(on)
            }
            val enabled = core.audioPayloadTypes.filter { it.enabled() }.joinToString { "${it.mimeType}/${it.clockRate}" }
            DiagLog.i("codecs enabled: $enabled")
        } catch (t: Throwable) { DiagLog.e("codecs", t) }
    }
}
