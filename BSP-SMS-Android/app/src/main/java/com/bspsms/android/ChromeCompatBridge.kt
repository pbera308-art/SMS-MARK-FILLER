package com.bspsms.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

class ChromeCompatBridge(
    private val context: Context,
    private val portalWebViewProvider: () -> WebView?,
    private val toolWebViewProvider: () -> WebView?,
    private val onRequestRecordAudio: ((Boolean) -> Unit) -> Unit,
    private val onHubAction: (String) -> Unit
) {
    private val prefs = context.getSharedPreferences("bsp_sms_chrome_storage", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    @JavascriptInterface
    fun storageGet(keysJson: String): String {
        val result = JSONObject()
        val all = prefs.all

        if (keysJson.isBlank() || keysJson == "null" || keysJson == "undefined") {
            for ((k, v) in all) {
                result.put(k, parseStoredValue(v as? String))
            }
            return result.toString()
        }

        val trimmed = keysJson.trim()
        when {
            trimmed.startsWith("[") -> {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    val key = arr.optString(i)
                    if (all.containsKey(key)) {
                        result.put(key, parseStoredValue(all[key] as? String))
                    }
                }
            }
            trimmed.startsWith("{") -> {
                val defaults = JSONObject(trimmed)
                val keys = defaults.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (all.containsKey(key)) {
                        result.put(key, parseStoredValue(all[key] as? String))
                    } else {
                        result.put(key, defaults.get(key))
                    }
                }
            }
            else -> {
                val key = trimmed.removeSurrounding("\"")
                if (all.containsKey(key)) {
                    result.put(key, parseStoredValue(all[key] as? String))
                }
            }
        }
        return result.toString()
    }

    @JavascriptInterface
    fun storageSet(itemsJson: String) {
        val obj = JSONObject(itemsJson)
        val editor = prefs.edit()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val wrapped = JSONObject().put("v", obj.get(key))
            editor.putString(key, wrapped.toString())
        }
        editor.apply()
    }

    @JavascriptInterface
    fun storageRemove(keysJson: String) {
        val editor = prefs.edit()
        val trimmed = keysJson.trim()
        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            for (i in 0 until arr.length()) {
                editor.remove(arr.optString(i))
            }
        } else {
            editor.remove(trimmed.removeSurrounding("\""))
        }
        editor.apply()
    }

    @JavascriptInterface
    fun storageClear() {
        prefs.edit().clear().apply()
    }

    @JavascriptInterface
    fun executeScriptInPortal(callbackId: String, payloadJson: String) {
        mainHandler.post {
            val portal = portalWebViewProvider()
            if (portal == null) {
                resolveToolCallback(callbackId, "[]", "Portal WebView is not ready")
                return@post
            }

            try {
                val payload = JSONObject(payloadJson)
                val files = payload.optJSONArray("files")
                val code = payload.optString("code", "")
                val allFrames = payload.optBoolean("allFrames", false)

                val scriptBuilder = StringBuilder()
                if (files != null) {
                    for (i in 0 until files.length()) {
                        val assetRel = files.optString(i).trimStart('/')
                        val assetText = readExtensionAssetText(assetRel)
                        if (assetText != null) {
                            scriptBuilder.append(assetText).append("\n")
                        } else {
                            resolveToolCallback(
                                callbackId,
                                "[]",
                                "Missing extension asset: $assetRel"
                            )
                            return@post
                        }
                    }
                }
                if (code.isNotBlank()) {
                    scriptBuilder.append(code)
                }

                val runnerExpr = "(function(){ ${scriptBuilder} })"
                val finalScript = if (allFrames) {
                    """
                    (function(){
                      try {
                        var __results = [];
                        var __src = ${JSONObject.quote("($runnerExpr)()")};
                        function __runInWin(w) {
                          try {
                            var r = w.eval(__src);
                            __results.push({ result: r === undefined ? null : r });
                          } catch(e) {}
                          try {
                            var frames = w.document ? w.document.querySelectorAll('iframe, frame') : [];
                            for (var i = 0; i < frames.length; i++) {
                              try {
                                if (frames[i].contentWindow && frames[i].contentDocument) {
                                  __runInWin(frames[i].contentWindow);
                                }
                              } catch(ignore) {}
                            }
                          } catch(ignore) {}
                        }
                        __runInWin(window);
                        return JSON.stringify(__results);
                      } catch(e) {
                        return JSON.stringify({ __error: String(e && e.message ? e.message : e) });
                      }
                    })();
                    """.trimIndent()
                } else {
                    "(function(){ try { var __r = $runnerExpr(); return JSON.stringify([{ result: __r === undefined ? null : __r }]); } catch(e) { return JSON.stringify({ __error: String(e && e.message ? e.message : e) }); } })();"
                }

                portal.evaluateJavascript(finalScript) { rawResult ->
                    val decoded = decodeEvaluateJavascriptResult(rawResult)
                    if (decoded.startsWith("{\"__error\":")) {
                        val errMsg = JSONObject(decoded).optString("__error", "Script error")
                        resolveToolCallback(callbackId, "[]", errMsg)
                    } else {
                        resolveToolCallback(callbackId, decoded, null)
                    }
                }
            } catch (e: Exception) {
                resolveToolCallback(callbackId, "[]", e.message ?: "Execution failed")
            }
        }
    }

    @JavascriptInterface
    fun sendTabMessage(callbackId: String, messageJson: String) {
        mainHandler.post {
            val portal = portalWebViewProvider()
            if (portal == null) {
                resolveToolCallback(callbackId, "null", "Portal WebView is not ready")
                return@post
            }
            val quotedCb = JSONObject.quote(callbackId)
            val quotedMsg = JSONObject.quote(messageJson)
            val dispatchJs = """
                (function() {
                    if (window.__bspDispatchRuntimeMessage) {
                        window.__bspDispatchRuntimeMessage($quotedCb, JSON.parse($quotedMsg));
                    } else if (window.BspAndroidBridge) {
                        window.BspAndroidBridge.resolveMessageFromPortal($quotedCb, "null");
                    }
                })();
            """.trimIndent()
            portal.evaluateJavascript(dispatchJs, null)
        }
    }

    @JavascriptInterface
    fun resolveMessageFromPortal(callbackId: String, resultJson: String) {
        mainHandler.post {
            resolveToolCallback(callbackId, resultJson, null)
        }
    }

    @JavascriptInterface
    fun getActiveTabInfo(): String {
        val result = JSONObject()
        val latch = java.util.concurrent.CountDownLatch(1)
        mainHandler.post {
            val portal = portalWebViewProvider()
            result.put("id", 1)
            result.put("active", true)
            result.put("url", portal?.url ?: MainActivity.PORTAL_URL)
            result.put("title", portal?.title ?: "Banglarshiksha SMS")
            latch.countDown()
        }
        latch.await(1, java.util.concurrent.TimeUnit.SECONDS)
        return result.toString()
    }

    @JavascriptInterface
    fun notifyHubAction(actionType: String) {
        mainHandler.post {
            onHubAction(actionType)
        }
    }

    @JavascriptInterface
    fun startNativeSpeech(languageTag: String) {
        mainHandler.post {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                onRequestRecordAudio { allow ->
                    if (allow) {
                        beginListening(languageTag)
                    } else {
                        emitSpeechError("not-allowed")
                        emitSpeechEnd()
                    }
                }
                return@post
            }
            beginListening(languageTag)
        }
    }

    @JavascriptInterface
    fun stopNativeSpeech() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            }
            speechRecognizer = null
        }
    }

    private fun beginListening(languageTag: String) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            emitSpeechError("service-not-allowed")
            emitSpeechEnd()
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        val code = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "audio-capture"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "not-allowed"
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network"
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no-speech"
                            else -> "aborted"
                        }
                        emitSpeechError(code)
                        emitSpeechEnd()
                    }

                    override fun onResults(results: Bundle?) {
                        val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val transcript = list?.firstOrNull()
                        if (!transcript.isNullOrBlank()) {
                            emitSpeechResult(transcript)
                        }
                        emitSpeechEnd()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag.ifBlank { "bn-IN" })
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            emitSpeechError("aborted")
            emitSpeechEnd()
        }
    }

    private fun emitSpeechResult(transcript: String) {
        val portal = portalWebViewProvider() ?: return
        portal.evaluateJavascript(
            "window.__bspOnSpeechResult && window.__bspOnSpeechResult(${JSONObject.quote(transcript)});",
            null
        )
    }

    private fun emitSpeechError(errorCode: String) {
        val portal = portalWebViewProvider() ?: return
        portal.evaluateJavascript(
            "window.__bspOnSpeechError && window.__bspOnSpeechError(${JSONObject.quote(errorCode)});",
            null
        )
    }

    private fun emitSpeechEnd() {
        val portal = portalWebViewProvider() ?: return
        portal.evaluateJavascript(
            "window.__bspOnSpeechEnd && window.__bspOnSpeechEnd();",
            null
        )
    }

    fun assetExists(relativePath: String): Boolean {
        return try {
            context.assets.open("extension/${relativePath.trimStart('/')}").use { true }
        } catch (_: Exception) {
            false
        }
    }

    fun readExtensionAssetText(relativePath: String): String? {
        return try {
            context.assets.open("extension/${relativePath.trimStart('/')}")
                .bufferedReader()
                .use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveToolCallback(callbackId: String, resultJson: String, errorMsg: String?) {
        val tool = toolWebViewProvider() ?: return
        val errArg = if (errorMsg != null) JSONObject.quote(errorMsg) else "null"
        val safeResult = if (resultJson.isBlank()) "null" else resultJson
        val js = "window.__bspResolveCallback && window.__bspResolveCallback(${JSONObject.quote(callbackId)}, $safeResult, $errArg);"
        tool.evaluateJavascript(js, null)
    }

    private fun parseStoredValue(raw: String?): Any? {
        if (raw == null) return JSONObject.NULL
        return try {
            val wrapper = JSONObject(raw)
            wrapper.get("v")
        } catch (_: Exception) {
            raw
        }
    }

    private fun decodeEvaluateJavascriptResult(raw: String?): String {
        if (raw == null || raw == "null") return "null"
        return try {
            if (raw.startsWith("\"") && raw.endsWith("\"")) {
                JSONArray("[$raw]").getString(0)
            } else {
                raw
            }
        } catch (_: Exception) {
            raw
        }
    }
}
