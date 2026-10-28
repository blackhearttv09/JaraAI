package com.jara.ai.live

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.media.audiofx.AcousticEchoCanceler
import android.util.Base64
import com.jara.ai.data.Prefs
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class LiveState {
    Idle, Connecting, Listening, Error
}

/**
 * Gemini Live API WebSocket client.
 * Input: 16 kHz, mono, PCM 16-bit.
 * Output: 24 kHz, mono, PCM 16-bit.
 */
class LiveClient(
    private val ctx: Context,
    private val prefs: Prefs,
    private val cb: Callback
) {
    interface Callback {
        fun onState(s: LiveState)
        fun onUserText(t: String)
        fun onModelText(t: String)
        fun onTurnComplete()
        fun onError(m: String)
    }

    // Disable OkHttp's automatic ping. The Live API handles
    // its own application-level connection messages.
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null
    private var rec: AudioRecord? = null
    private var track: AudioTrack? = null
    private var aec: AcousticEchoCanceler? = null

    private val player = Executors.newSingleThreadExecutor()
    private val toolExec = Executors.newSingleThreadExecutor()
    private val audioExec = Executors.newSingleThreadExecutor()

    @Volatile
    private var running = false

    private val audioStarted = AtomicBoolean(false)

    fun start() {
        if (running) return

        val key = prefs.apiKey.trim()
        if (key.isBlank()) {
            cb.onError("আগে সেটিংসে Gemini API Key দিন")
            cb.onState(LiveState.Error)
            return
        }

        val model = prefs.liveModel.trim()
        if (model.isBlank()) {
            cb.onError("Live Voice Model-এর নাম সেট করুন")
            cb.onState(LiveState.Error)
            return
        }

        running = true
        audioStarted.set(false)
        cb.onState(LiveState.Connecting)

        val url =
            "wss://generativelanguage.googleapis.com/ws/" +
            "google.ai.generativelanguage.v1beta.GenerativeService." +
            "BidiGenerateContent?key=$key"

        val request = Request.Builder()
            .url(url)
            .build()

        ws = http.newWebSocket(
            request,
            object : WebSocketListener() {

                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response
                ) {
                    if (!running) {
                        webSocket.close(1000, "Cancelled")
                        return
                    }

                    val sent = webSocket.send(setupJson().toString())
                    if (!sent) {
                        fail("Live API setup বার্তা পাঠানো যায়নি")
                    }
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String
                ) {
                    if (running) handle(text)
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    bytes: ByteString
                ) {
                    if (running) handle(bytes.utf8())
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?
                ) {
                    if (!running) return

                    val code = response?.code
                    val message = when (code) {
                        401, 403 ->
                            "API Key বা API permission সমস্যা ($code)"
                        404 ->
                            "Live Model পাওয়া যায়নি (404)। মডেল আইডি পরীক্ষা করুন"
                        429 ->
                            "API quota শেষ বা request বেশি হয়েছে (429)"
                        else ->
                            "Live সংযোগ ব্যর্থ" +
                            (code?.let { " ($it)" } ?: "") +
                            ": ${t.message ?: "অজানা নেটওয়ার্ক সমস্যা"}"
                    }

                    fail(message)
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String
                ) {
                    if (running) {
                        fail("Live সেশন বন্ধ হয়েছে ($code): $reason")
                    }
                }
            }
        )
    }

    private fun setupJson(): JSONObject {
        val setup = JSONObject()
            .put("model", "models/${prefs.liveModel.trim()}")
            .put(
                "generationConfig",
                JSONObject().put(
                    "responseModalities",
                    JSONArray().put("AUDIO")
                )
            )
            .put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(
                        JSONObject().put(
                            "text",
                            "You are Jara, a friendly voice assistant. " +
                            "Speak in the user's language, including Bengali, " +
                            "Hindi or English. Keep replies short and natural. " +
                            "Use phone-action tools only when the user clearly " +
                            "asks for an action. Never claim an action succeeded " +
                            "unless it actually succeeded."
                        )
                    )
                )
            )
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())

        if (prefs.screenControl) {
            setup.put(
                "tools",
                JSONArray().put(
                    JSONObject().put(
                        "functionDeclarations",
                        DeviceActions.declarations
                    )
                )
            )
        }

        return JSONObject().put("setup", setup)
    }

    private fun handle(raw: String) {
        val message = runCatching {
            JSONObject(raw)
        }.getOrNull() ?: return

        // The server may report setup or protocol errors.
        message.optJSONObject("setupComplete")?.let {
            if (running && audioStarted.compareAndSet(false, true)) {
                try {
                    startAudio()
                    if (running) cb.onState(LiveState.Listening)
                } catch (e: Exception) {
                    fail("অডিও চালু করা যায়নি: ${e.message}")
                }
            }
        }

        message.optJSONObject("error")?.let { error ->
            val code = error.optInt("code", -1)
            val detail = error.optString("message", "অজানা API Error")
            fail("Gemini Live API Error ($code): $detail")
            return
        }

        message.optJSONObject("serverContent")?.let { content ->
            content.optJSONObject("modelTurn")
                ?.optJSONArray("parts")
                ?.let { parts ->
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i) ?: continue
                        val inline = part.optJSONObject("inlineData")
                            ?: part.optJSONObject("inline_data")
                            ?: continue

                        val data = inline.optString("data", "")
                        if (data.isBlank()) continue

                        val pcm = runCatching {
                            Base64.decode(data, Base64.DEFAULT)
                        }.getOrNull() ?: continue

                        if (running) {
                            player.execute {
                                val audioTrack = track
                                if (running && audioTrack != null) {
                                    runCatching {
                                        audioTrack.write(
                                            pcm,
                                            0,
                                            pcm.size
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

            if (content.optBoolean("interrupted")) {
                player.execute {
                    runCatching {
                        track?.pause()
                        track?.flush()
                        if (running) track?.play()
                        applyPlaybackParams()
                    }
                }
            }

            content.optJSONObject("inputTranscription")
                ?.optString("text")
                ?.takeIf { it.isNotBlank() }
                ?.let { cb.onUserText(it) }

            content.optJSONObject("outputTranscription")
                ?.optString("text")
                ?.takeIf { it.isNotBlank() }
                ?.let { cb.onModelText(it) }

            if (content.optBoolean("turnComplete")) {
                cb.onTurnComplete()
            }
        }

        message.optJSONObject("toolCall")
            ?.optJSONArray("functionCalls")
            ?.let { calls ->
                toolExec.execute {
                    if (!running) return@execute

                    val responses = JSONArray()

                    for (i in 0 until calls.length()) {
                        val call = calls.optJSONObject(i) ?: continue
                        val name = call.optString("name", "")
                        val id = call.optString("id", "")

                        val result = runCatching {
                            DeviceActions.execute(
                                ctx,
                                name,
                                call.optJSONObject("args") ?: JSONObject(),
                                prefs.screenControl
                            )
                        }.getOrElse { error ->
                            JSONObject().put(
                                "error",
                                error.message ?: "Action failed"
                            )
                        }

                        responses.put(
                            JSONObject()
                                .put("id", id)
                                .put("name", name)
                                .put("response", result)
                        )
                    }

                    if (running && responses.length() > 0) {
                        val responseMessage = JSONObject().put(
                            "toolResponse",
                            JSONObject().put(
                                "functionResponses",
                                responses
                            )
                        )
                        ws?.send(responseMessage.toString())
                    }
                }
            }
    }

    private fun applyPlaybackParams() {
        runCatching {
            track?.playbackParams = PlaybackParams()
                .setSpeed(prefs.speed.coerceIn(0.5f, 2.0f))
                .setPitch(prefs.pitch.coerceIn(0.5f, 2.0f))
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudio() {
        if (!running) return

        val minInput = AudioRecord.getMinBufferSize(
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minInput <= 0) {
            fail("মাইক্রোফোনের audio buffer তৈরি করা যায়নি")
            return
        }

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minInput, 6400)
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            fail("মাইক্রোফোন চালু করা যায়নি। Permission পরীক্ষা করুন")
            return
        }

        val minOutput = AudioTrack.getMinBufferSize(
            24000,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minOutput <= 0) {
            audioRecord.release()
            fail("অডিও আউটপুট তৈরি করা যায়নি")
            return
        }

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(24000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minOutput, 48000))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        rec = audioRecord
        track = audioTrack

        if (AcousticEchoCanceler.isAvailable()) {
            aec = runCatching {
                AcousticEchoCanceler.create(
                    audioRecord.audioSessionId
                )?.also { it.enabled = true }
            }.getOrNull()
        }

        try {
            audioTrack.play()
            applyPlaybackParams()
            audioRecord.startRecording()
        } catch (e: Exception) {
            runCatching { audioRecord.release() }
            runCatching { audioTrack.release() }
            rec = null
            track = null
            fail("মাইক্রোফোন বা স্পিকার চালু করা যায়নি: ${e.message}")
            return
        }

        audioExec.execute {
            val buffer = ByteArray(3200)

            while (running) {
                val recorder = rec ?: break

                val count = try {
                    recorder.read(buffer, 0, buffer.size)
                } catch (_: Exception) {
                    break
                }

                if (count > 0 && running) {
                    val audioData = Base64.encodeToString(
                        buffer,
                        0,
                        count,
                        Base64.NO_WRAP
                    )

                    val audioMessage = JSONObject().put(
                        "realtimeInput",
                        JSONObject().put(
                            "audio",
                            JSONObject()
                                .put("data", audioData)
                                .put("mimeType", "audio/pcm;rate=16000")
                        )
                    )

                    val sent = ws?.send(audioMessage.toString()) ?: false

                    if (!sent && running) {
                        fail("লাইভ সংযোগে অডিও পাঠানো যাচ্ছে না")
                        break
                    }
                } else if (count < 0) {
                    if (running) fail("মাইক্রোফোন থেকে অডিও পড়া যাচ্ছে না")
                    break
                }
            }
        }
    }

    private fun fail(message: String) {
        if (!running) {
            cb.onError(message)
            cb.onState(LiveState.Error)
            return
        }

        running = false
        cleanup()
        cb.onError(message)
        cb.onState(LiveState.Error)
    }

    fun stop() {
        running = false
        cleanup()
        cb.onState(LiveState.Idle)
    }

    private fun cleanup() {
        runCatching { ws?.close(1000, "Session stopped") }
        ws = null

        runCatching { rec?.stop() }
        runCatching { rec?.release() }
        rec = null

        runCatching { aec?.release() }
        aec = null

        runCatching { track?.pause() }
        runCatching { track?.flush() }
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
    }
}
