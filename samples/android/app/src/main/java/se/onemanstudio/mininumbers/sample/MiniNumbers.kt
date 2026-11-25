package se.onemanstudio.mininumbers.sample

import android.os.Build
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.SecureRandom

/**
 * A complete Mini Numbers tracker for a native app. One file, no third-party library:
 * `HttpURLConnection` for the network and `org.json` (part of Android) for the body.
 *
 * Copy this file into your app, call [configure] once, then [screen] when a screen appears and
 * [event] / [purchase] / [refund] / [outbound] when something happens.
 *
 * The rules the server cares about:
 *  - `sessionId` goes on every event: 32 random lowercase hex characters, made when the app starts and again on
 *    [newSession]. It lives in memory only, so there is no persistent visitor identifier.
 *  - `properties` is a STRING that holds JSON (`"{\"plan\":\"free\"}"`), not a nested object. Left out when empty.
 *  - Never send `referrer`. Never send `eventName` on a `pageview` or `heartbeat`: the server rejects it.
 *  - `path` starts with `/` and has no spaces.
 *
 * The two traps:
 *  1. USER-AGENT. The server works out device and OS from the User-Agent header. Java's default one shows up as
 *     "Unknown", and a name that contains "bot", "crawl" or "spider" is answered with 202 and silently dropped.
 *     So we send our own, shaped like a browser's: `MiniNumbersSample/1.0 (Linux; Android 15; Pixel 8a) Mobile`.
 *  2. OPT-OUT. While [optOut] is on, nothing at all leaves the phone; the log just says "not sent".
 */
object MiniNumbers {
    data class LogEntry(val time: String, val type: String, val label: String, val status: String)

    private var baseUrl = ""
    private var projectKey = ""
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeat: Job? = null

    /** The path of the screen being shown. Events and heartbeats are reported against it. */
    @Volatile private var currentPath = "/shop"

    @Volatile var optOut = false

    @Volatile var sessionId: String = newSessionId()
        private set

    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())

    /** Newest first, at most 50 entries. */
    val log: StateFlow<List<LogEntry>> = _log

    private val _sent = MutableStateFlow(0)

    /** Events the server answered with 202 during this run. */
    val sent: StateFlow<Int> = _sent

    fun configure(baseUrl: String, projectKey: String) {
        this.baseUrl = baseUrl.trim().trimEnd('/')
        this.projectKey = projectKey.trim()
    }

    fun newSession() {
        sessionId = newSessionId()
    }

    fun screen(path: String) {
        currentPath = path
        send("pageview", path)
    }

    fun event(name: String, properties: Map<String, Any> = emptyMap()) =
        send("custom", currentPath, eventName = name, properties = properties)

    fun purchase(amount: Double, currency: String) =
        send("custom", currentPath, "purchase", mapOf("revenue" to amount, "currency" to currency))

    /** A refund is a purchase with a negative amount. */
    fun refund(amount: Double, currency: String) =
        send("custom", currentPath, "refund", mapOf("revenue" to -amount, "currency" to currency))

    fun outbound(url: String) =
        send("outbound", currentPath, eventName = hostOf(url), targetUrl = url)

    /** Call when the app comes to the foreground. Sends a heartbeat every 30 seconds. */
    fun startHeartbeat() {
        if (heartbeat?.isActive == true) return
        heartbeat = scope.launch {
            while (isActive) {
                delay(30_000)
                send("heartbeat", currentPath)
            }
        }
    }

    /** Call when the app leaves the foreground. */
    fun stopHeartbeat() {
        heartbeat?.cancel()
        heartbeat = null
    }

    private fun send(
        type: String,
        path: String,
        eventName: String? = null,
        properties: Map<String, Any> = emptyMap(),
        targetUrl: String? = null,
    ) {
        val label = eventName ?: path
        val body = buildPayload(type, path, sessionId, eventName, properties, targetUrl, projectKey, optOut)
        if (body == null) {
            addLog(type, label, if (optOut) "not sent" else "no key")
            return
        }
        scope.launch {
            val status = post(body.toString())
            if (status == "202") _sent.update { it + 1 }
            addLog(type, label, status)
        }
    }

    /** Returns the HTTP status as text, or a short error. Failures are shown in the log and not retried. */
    private fun post(json: String): String = try {
        val connection = URL("$baseUrl/collect").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Project-Key", projectKey)
            connection.setRequestProperty("User-Agent", userAgent())
            connection.outputStream.use { it.write(json.toByteArray()) }
            connection.responseCode.toString()
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        e.javaClass.simpleName
    }

    private fun addLog(type: String, label: String, status: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _log.update { (listOf(LogEntry(time, type, label, status)) + it).take(50) }
    }

    private fun userAgent() =
        "MiniNumbersSample/1.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) Mobile"

    private fun hostOf(url: String) = runCatching { URI(url).host }.getOrNull() ?: url

    private fun newSessionId(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Builds the JSON body for `POST /collect`, or returns null when nothing may be sent (the user opted out, or there
 * is no project key). Pure: no network, no clock, no state, so it is easy to unit-test.
 */
fun buildPayload(
    type: String,
    path: String,
    sessionId: String,
    eventName: String? = null,
    properties: Map<String, Any> = emptyMap(),
    targetUrl: String? = null,
    projectKey: String,
    optOut: Boolean,
): JSONObject? {
    if (optOut || projectKey.isBlank()) return null
    val body = JSONObject()
        .put("type", type)
        .put("path", path)
        .put("sessionId", sessionId)
    // pageview and heartbeat carry no eventName; the server rejects them if they do.
    if (type != "pageview" && type != "heartbeat" && eventName != null) body.put("eventName", eventName)
    if (targetUrl != null) body.put("targetUrl", targetUrl)
    // properties is a string holding JSON, not a nested object.
    if (properties.isNotEmpty()) body.put("properties", JSONObject(properties).toString())
    return body
}
