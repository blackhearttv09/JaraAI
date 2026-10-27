package com.jara.ai.vm

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jara.ai.data.ChatMsg
import com.jara.ai.data.GeminiRepository
import com.jara.ai.data.Prefs
import com.jara.ai.live.LiveClient
import com.jara.ai.live.LiveState
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class Session(val id: Long, val title: String, val messages: List<ChatMsg>)

class ChatViewModel(app: Application) : AndroidViewModel(app), LiveClient.Callback {
    private val prefs = Prefs(app)
    private val repo = GeminiRepository()
    val messages = mutableStateListOf<ChatMsg>()
    val sessions = mutableStateListOf<Session>()
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var liveState by mutableStateOf(LiveState.Idle)
    private var sessionId = System.currentTimeMillis()
    private val live = LiveClient(app, prefs, this)
    private var userBuf = StringBuilder()
    private var modelBuf = StringBuilder()

    init { loadSessions() }

    fun send(text: String) {
        val t = text.trim(); if (t.isEmpty() || busy) return
        messages.add(ChatMsg(true, t)); error = null; busy = true
        viewModelScope.launch {
            repo.chat(prefs.apiKey, prefs.textModel, messages.toList())
                .onSuccess { messages.add(ChatMsg(false, it)) }
                .onFailure { error = it.message }
            busy = false; persist()
        }
    }

    fun toggleVoice() { if (liveState == LiveState.Idle || liveState == LiveState.Error) { error = null; live.start() } else live.stop() }

    fun newChat() { live.stop(); persist(); messages.clear(); sessionId = System.currentTimeMillis(); error = null }
    fun open(s: Session) { live.stop(); persist(); messages.clear(); messages.addAll(s.messages); sessionId = s.id }
    fun delete(s: Session) { sessions.removeAll { it.id == s.id }; save(); if (s.id == sessionId) { messages.clear(); sessionId = System.currentTimeMillis() } }
    fun clearAll() { sessions.clear(); messages.clear(); save() }

    private fun persist() {
        if (messages.isEmpty()) return
        val s = Session(sessionId, messages.first().text.take(40), messages.toList())
        val i = sessions.indexOfFirst { it.id == sessionId }
        if (i >= 0) sessions[i] = s else sessions.add(0, s)
        save()
    }
    private fun save() {
        val arr = JSONArray()
        sessions.forEach { s ->
            arr.put(JSONObject().put("id", s.id).put("title", s.title).put("m", JSONArray().also { a ->
                s.messages.forEach { a.put(JSONObject().put("u", it.fromUser).put("t", it.text)) } }))
        }
        prefs.history = arr.toString()
    }
    private fun loadSessions() = runCatching {
        val arr = JSONArray(prefs.history)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i); val m = o.getJSONArray("m")
            sessions.add(Session(o.getLong("id"), o.getString("title"), (0 until m.length()).map { ChatMsg(m.getJSONObject(it).getBoolean("u"), m.getJSONObject(it).getString("t")) }))
        }
    }

    // LiveClient callbacks arrive on background threads; state writes are thread-safe in Compose snapshot system.
    override fun onState(s: LiveState) { liveState = s }
    override fun onUserText(t: String) { userBuf.append(t) }
    override fun onModelText(t: String) { modelBuf.append(t) }
    override fun onTurnComplete() {
        if (userBuf.isNotBlank()) messages.add(ChatMsg(true, userBuf.toString().trim()))
        if (modelBuf.isNotBlank()) messages.add(ChatMsg(false, modelBuf.toString().trim()))
        userBuf = StringBuilder(); modelBuf = StringBuilder(); persist()
    }
    override fun onError(m: String) { error = m }
    override fun onCleared() { live.stop() }
}
