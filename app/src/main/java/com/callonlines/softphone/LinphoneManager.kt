package com.callonlines.softphone

import android.content.Context
import androidx.lifecycle.MutableLiveData
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
    val callReason = MutableLiveData<String>("")
    val currentCall = MutableLiveData<Call?>(null)

    private val coreListener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState?,
            message: String
        ) {
            DiagLog.i("REG state=$state msg=$message")
            registrationState.postValue(state ?: RegistrationState.None)
        }

        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            val errReason = try { call.errorInfo?.reason?.toString() } catch (_: Exception) { null }
            val errMsg = try { call.errorInfo?.phrase } catch (_: Exception) { null }
            DiagLog.i("CALL state=$state msg=$message reason=$errReason phrase=$errMsg")
            callState.postValue(state ?: Call.State.Idle)
            callReason.postValue(buildString {
                append(state?.toString() ?: "Idle")
                if (!message.isNullOrBlank()) append(" - ").append(message)
                if (!errReason.isNullOrBlank()) append(" [").append(errReason).append("]")
                if (!errMsg.isNullOrBlank()) append(" \"").append(errMsg).append("\"")
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

            core.isNetworkReachable = true
            core.isKeepAliveEnabled = true
            core.isAutoIterateEnabled = true
            core.isNativeRingingEnabled = true
            core.mediaEncryption = MediaEncryption.None

            core.addListener(coreListener)
            core.start()
            initialized = true
            DiagLog.i("Linphone Core started (version=${core.version})")
        } catch (t: Throwable) {
            DiagLog.e("init failed", t)
        }
    }

    fun login(
        username: String,
        password: String,
        domain: String,
        transport: TransportType = TransportType.Udp
    ): Boolean {
        if (!initialized) return false
        return try {
            core.clearAccounts()
            core.clearAllAuthInfo()

            val authInfo = factory.createAuthInfo(username, null, password, null, null, domain)
            core.addAuthInfo(authInfo)

            val params = core.createAccountParams()
            val identity = factory.createAddress("sip:$username@$domain") ?: return false
            params.identityAddress = identity

            val serverAddr = factory.createAddress("sip:$domain") ?: return false
            serverAddr.transport = transport
            params.serverAddress = serverAddr
            params.isRegisterEnabled = true

            val account = core.createAccount(params)
            core.addAccount(account)
            core.defaultAccount = account
            configureCodecs()
            DiagLog.i("login submitted user=$username domain=$domain transport=$transport")
            true
        } catch (t: Throwable) {
            DiagLog.e("login failed", t)
            false
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
            DiagLog.w("call() ignored, core not initialized")
            return null
        }
        return try {
            val account = core.defaultAccount
            if (account == null) {
                DiagLog.w("call(): no default account")
                return null
            }
            val domain = account.params.serverAddress?.domain ?: run {
                DiagLog.w("call(): no domain")
                return null
            }
            val target = if (number.contains("@")) "sip:$number" else "sip:$number@$domain"
            DiagLog.i("call() target=$target")
            val address = factory.createAddress(target)
            if (address == null) {
                DiagLog.w("call(): could not parse address $target")
                return null
            }
            val params = core.createCallParams(null)
            if (params == null) {
                DiagLog.w("call(): createCallParams returned null")
                return null
            }
            params.isVideoEnabled = false
            params.mediaEncryption = MediaEncryption.None
            val call = core.inviteAddressWithParams(address, params)
            DiagLog.i("call() inviteAddressWithParams returned ${call != null}")
            call
        } catch (t: Throwable) {
            DiagLog.e("call() threw", t)
            null
        }
    }

    fun answer(call: Call) {
        try {
            val params = core.createCallParams(call)
            params?.isVideoEnabled = false
            call.acceptWithParams(params)
        } catch (t: Throwable) { DiagLog.e("answer failed", t) }
    }

    fun decline(call: Call) {
        try { call.terminate() } catch (t: Throwable) { DiagLog.e("decline failed", t) }
    }

    fun hangup() {
        try {
            core.currentCall?.terminate() ?: core.terminateAllCalls()
        } catch (t: Throwable) { DiagLog.e("hangup failed", t) }
    }

    fun toggleMute(): Boolean {
        return try {
            core.isMicEnabled = !core.isMicEnabled
            !core.isMicEnabled
        } catch (_: Exception) { false }
    }

    fun isMuted(): Boolean = try { !core.isMicEnabled } catch (_: Exception) { false }

    fun toggleSpeaker(): Boolean {
        return try {
            val current = core.outputAudioDevice
            val targetType =
                if (current?.type == AudioDevice.Type.Speaker) AudioDevice.Type.Earpiece
                else AudioDevice.Type.Speaker
            val target = core.audioDevices.firstOrNull { it.type == targetType }
            if (target != null) core.outputAudioDevice = target
            targetType == AudioDevice.Type.Speaker
        } catch (_: Exception) { false }
    }

    fun isSpeaker(): Boolean = try {
        core.outputAudioDevice?.type == AudioDevice.Type.Speaker
    } catch (_: Exception) { false }

    fun sendDtmf(digit: Char) {
        try { core.currentCall?.sendDtmf(digit) } catch (_: Exception) {}
    }

    private fun configureCodecs() {
        try {
            val keep = setOf("opus", "pcmu", "pcma")
            for (pt in core.audioPayloadTypes) {
                pt.enable(keep.contains(pt.mimeType.lowercase()))
            }
            DiagLog.i("codecs configured: opus, pcmu, pcma")
        } catch (t: Throwable) { DiagLog.e("configureCodecs failed", t) }
    }
}
