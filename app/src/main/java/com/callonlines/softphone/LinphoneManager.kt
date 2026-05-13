package com.callonlines.softphone

import android.content.Context
import androidx.lifecycle.MutableLiveData
import org.linphone.core.Account
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType

object LinphoneManager {

    private lateinit var core: Core
    private lateinit var factory: Factory
    private var initialized = false

    val registrationState = MutableLiveData(RegistrationState.None)
    val callState = MutableLiveData(Call.State.Idle)
    val currentCall = MutableLiveData<Call?>(null)

    private val coreListener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState?,
            message: String
        ) {
            registrationState.postValue(state ?: RegistrationState.None)
        }

        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            callState.postValue(state ?: Call.State.Idle)
            currentCall.postValue(call)
        }
    }

    fun init(context: Context) {
        if (initialized) return
        factory = Factory.instance()
        factory.setDebugMode(false, "CallOnLinesSoftphone")
        core = factory.createCore(null, null, context)
        core.isNetworkReachable = true
        core.isKeepAliveEnabled = true
        core.isAutoIterateEnabled = true
        core.addListener(coreListener)
        core.start()
        initialized = true
    }

    fun login(
        username: String,
        password: String,
        domain: String,
        transport: TransportType = TransportType.Udp
    ): Boolean {
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
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun logout() {
        try {
            core.clearAccounts()
            core.clearAllAuthInfo()
        } catch (_: Exception) {}
    }

    fun call(number: String): Call? {
        val account = core.defaultAccount ?: return null
        val domain = account.params.serverAddress?.domain ?: return null
        val address = factory.createAddress("sip:$number@$domain") ?: return null
        val params = core.createCallParams(null) ?: return null
        params.isVideoEnabled = false
        return core.inviteAddressWithParams(address, params)
    }

    fun answer(call: Call) {
        try {
            val params = core.createCallParams(call)
            params?.isVideoEnabled = false
            call.acceptWithParams(params)
        } catch (_: Exception) {}
    }

    fun decline(call: Call) {
        try { call.terminate() } catch (_: Exception) {}
    }

    fun hangup() {
        try {
            core.currentCall?.terminate() ?: core.terminateAllCalls()
        } catch (_: Exception) {}
    }

    fun toggleMute(): Boolean {
        core.isMicEnabled = !core.isMicEnabled
        return !core.isMicEnabled
    }

    fun isMuted(): Boolean = !core.isMicEnabled

    fun toggleSpeaker(): Boolean {
        val current = core.outputAudioDevice
        val targetType =
            if (current?.type == AudioDevice.Type.Speaker) AudioDevice.Type.Earpiece
            else AudioDevice.Type.Speaker
        val target = core.audioDevices.firstOrNull { it.type == targetType }
        if (target != null) core.outputAudioDevice = target
        return targetType == AudioDevice.Type.Speaker
    }

    fun isSpeaker(): Boolean = core.outputAudioDevice?.type == AudioDevice.Type.Speaker

    fun sendDtmf(digit: Char) {
        try { core.currentCall?.sendDtmf(digit) } catch (_: Exception) {}
    }

    private fun configureCodecs() {
        try {
            val keep = setOf("opus", "pcmu", "pcma")
            for (pt in core.audioPayloadTypes) {
                pt.enable(keep.contains(pt.mimeType.lowercase()))
            }
        } catch (_: Exception) {}
    }
}
