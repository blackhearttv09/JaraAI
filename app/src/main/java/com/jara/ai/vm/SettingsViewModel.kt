package com.jara.ai.vm

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jara.ai.data.GeminiRepository
import com.jara.ai.data.Prefs
import kotlinx.coroutines.launch

sealed interface KeyStatus {
    object Idle : KeyStatus
    object Checking : KeyStatus
    object Ok : KeyStatus
    data class Fail(val msg: String) : KeyStatus
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)
    private val repo = GeminiRepository()

    var apiKey by mutableStateOf(prefs.apiKey)
    var keyStatus by mutableStateOf<KeyStatus>(KeyStatus.Idle)
    var liveModel by mutableStateOf(prefs.liveModel)
    var textModel by mutableStateOf(prefs.textModel)
    var speed by mutableStateOf(prefs.speed)
    var pitch by mutableStateOf(prefs.pitch)
    var screenControl by mutableStateOf(prefs.screenControl)
    var saved by mutableStateOf(false)

    fun saveKey() {
        val k = apiKey.trim()

        if (k.isEmpty()) {
            keyStatus = KeyStatus.Fail("API কী খালি")
            return
        }

        keyStatus = KeyStatus.Checking

        viewModelScope.launch {
            repo.validateKey(k)
                .onSuccess {
                    prefs.apiKey = k
                    keyStatus = KeyStatus.Ok
                }
                .onFailure {
                    keyStatus = KeyStatus.Fail(
                        it.message ?: "যাচাই ব্যর্থ"
                    )
                }
        }
    }

    fun clearKey() {
        prefs.apiKey = ""
        apiKey = ""
        keyStatus = KeyStatus.Idle
    }

    fun saveVoice() {
        prefs.liveModel =
            liveModel.trim().ifEmpty { prefs.liveModel }

        prefs.textModel =
            textModel.trim().ifEmpty { prefs.textModel }

        prefs.speed = speed
        prefs.pitch = pitch
        saved = true
    }

    fun updateScreenControl(v: Boolean) {
        screenControl = v
        prefs.screenControl = v
    }
}
