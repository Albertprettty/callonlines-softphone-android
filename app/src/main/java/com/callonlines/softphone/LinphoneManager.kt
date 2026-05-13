package com.callonlines.softphone

import android.content.Context
import android.util.Log
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

    private const val TAG = "LinphoneManager"

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
            Log.d(TAG, "Registration -> $state ($message)")
            registrationState.postValue(state ?: RegistrationState.None)
        }

        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            Log.d(TAG, "Call -> $state ($message)")
            callState.postValue(state ?: Call.State.Idle)
            currentCall.postValue(call)
        }
    }

    fun init(context: Context) {
        if (initialized) return
        try {
            factory = Factory.instance()
            factory.setDebugMode(false, "CallOnLinesSoftphone")
            core = factory.createCore(null, null, context)

            core.isNetworkReachable = true
            core.isKeepAliveEnabled = true
            core.isAutoIterateEnabled = true
            core.isNativeRingingEnabled = true
            core.mediaEncryption = MediaEncryption.None

            core.addListener(coreListener)
            core.start()
            initialized = true
            Log.i(TAG, "Linphone Core started (version ${core.version})")
        } catch (e: Exception) {
            Log.e(TAG, "init failed", e)
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
            true
        } catch (e: Exception) {
            Log.e(TAG, "login failed", e)
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
        if (!initialized) return null
        return try {
            val account = core.defaultAccount ?: return null
            val domain = account.params.serverAddress?.domain ?: return null
            val target = if (number.contains("@")) "sip:$number" else "sip:$number@$domain"
            val address = factory.createAddress(target) ?: return null
            val params = core.createCallParams(null) ?: return null
            params.isVideoEnabled = false
            params.mediaEncryption = MediaEncryption.None
            core.inviteAddressWithParams(address, params)
        } catch (e: Exception) {
            Log.e(TAG, "call failed", e)
            null
        }
    }

    fun answer(call: Call) {
        try {
            val params = core.createCallParams(call)
            params?.isVideoEnabled = false
            call.acceptWithParams(params)
        } catch (e: Exception) {
            Log.e(TAG, "answer failed", e)
        }
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
        } catch (_: Exception) {}
    }
}
