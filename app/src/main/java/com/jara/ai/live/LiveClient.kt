package com.jara.ai.live

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.util.Base64
import com.jara.ai.data.Prefs
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

enum class LiveState { Idle, Connecting, Listening, Error }

/** Gemini Live API over raw WebSocket. 16 kHz PCM in, 24 kHz PCM out. */
class LiveClient(private val ctx: Context, private val prefs: Prefs, private val cb: Callback) {
    interface Callback {
        fun onState(s: LiveState)
        fun onUserText(t: String)
        fun onModelText(t: String)
        fun onTurnComplete()
        fun onError(m: String)
    }

    private val http = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    private var ws: WebSocket? = null
    private var rec: AudioRecord? = null
    private var track: AudioTrack? = null
    private var aec: AcousticEchoCanceler? = null
    private val player = Executors.newSingleThreadExecutor()
    private val toolExec = Executors.newSingleThreadExecutor()
    @Volatile private var running = false

    fun start() {
        if (running) return
        val key = prefs.apiKey
        if (key.isBlank()) { cb.onError("আগে API কী সেটিংসে Gemini API Key দিন"); return }
        running = true
        cb.onState(LiveState.Connecting)
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$key"
        ws = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send(setupJson().toString()) }
            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(bytes.utf8())
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!running) return
                val code = response?.code
                fail(if (code == 403 || code == 401) "API কী অবৈধ ($code)" else "সংযোগ ব্যর্থ: ${t.message}")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (running) fail("সেশন বন্ধ হয়েছে ($code) ${reason}".trim())
            }
        })
    }

    private fun setupJson(): JSONObject {
        val setup = JSONObject()
            .put("model", "models/${prefs.liveModel}")
            .put("responseModalities", JSONArray().put("AUDIO"))
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text",
                "You are Jara, a friendly voice assistant. Speak in the user's language (Bengali, Hindi or English). Keep replies short. " +
                "Use tools only when the user clearly asks for a phone action, and report honestly if a tool fails."))))
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
        if (prefs.screenControl) setup.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", DeviceActions.declarations)))
        return JSONObject().put("setup", setup)
    }

    private fun handle(raw: String) {
        val m = runCatching { JSONObject(raw) }.getOrNull() ?: return
        if (m.has("setupComplete")) { startAudio(); cb.onState(LiveState.Listening) }
        m.optJSONObject("serverContent")?.let { sc ->
            sc.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
                for (i in 0 until parts.length()) {
                    val d = parts.getJSONObject(i).optJSONObject("inlineData")?.optString("data") ?: continue
                    val pcm = Base64.decode(d, Base64.DEFAULT)
                    player.execute { track?.write(pcm, 0, pcm.size) }
                }
            }
            if (sc.optBoolean("interrupted")) player.execute { track?.pause(); track?.flush(); track?.play(); applyPlaybackParams() }
            sc.optJSONObject("inputTranscription")?.optString("text")?.let { if (it.isNotEmpty()) cb.onUserText(it) }
            sc.optJSONObject("outputTranscription")?.optString("text")?.let { if (it.isNotEmpty()) cb.onModelText(it) }
            if (sc.optBoolean("turnComplete")) cb.onTurnComplete()
        }
        m.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let { calls ->
            toolExec.execute {
                val out = JSONArray()
                for (i in 0 until calls.length()) {
                    val c = calls.getJSONObject(i)
                    val r = DeviceActions.execute(ctx, c.optString("name"), c.optJSONObject("args") ?: JSONObject(), prefs.screenControl)
                    out.put(JSONObject().put("id", c.optString("id")).put("name", c.optString("name")).put("response", r))
                }
                ws?.send(JSONObject().put("toolResponse", JSONObject().put("functionResponses", out)).toString())
            }
        }
    }

    private fun applyPlaybackParams() {
        runCatching { track?.playbackParams = PlaybackParams().setSpeed(prefs.speed).setPitch(prefs.pitch) }
    }

    @SuppressLint("MissingPermission")
    private fun startAudio() {
        val minIn = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minIn, 6400))
        if (r.state != AudioRecord.STATE_INITIALIZED) { fail("মাইক্রোফোন চালু করা যায়নি"); return }
        rec = r
        if (AcousticEchoCanceler.isAvailable()) aec = AcousticEchoCanceler.create(r.audioSessionId)?.also { it.enabled = true }
        val minOut = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(minOut, 48000)).setTransferMode(AudioTrack.MODE_STREAM).build()
        track?.play(); applyPlaybackParams()
        r.startRecording()
        Thread {
            val buf = ByteArray(3200)
            while (running) {
                val n = r.read(buf, 0, buf.size)
                if (n > 0) {
                    val msg = JSONObject().put("realtimeInput", JSONObject().put("audio", JSONObject()
                        .put("data", Base64.encodeToString(buf, 0, n, Base64.NO_WRAP)).put("mimeType", "audio/pcm;rate=16000")))
                    ws?.send(msg.toString())
                }
            }
        }.start()
    }

    private fun fail(msg: String) { stop(); cb.onError(msg); cb.onState(LiveState.Error) }

    fun stop() {
        running = false
        runCatching { ws?.close(1000, "bye") }; ws = null
        runCatching { rec?.stop() }; runCatching { rec?.release() }; rec = null
        runCatching { aec?.release() }; aec = null
        runCatching { track?.stop() }; runCatching { track?.release() }; track = null
        cb.onState(LiveState.Idle)
    }
}
