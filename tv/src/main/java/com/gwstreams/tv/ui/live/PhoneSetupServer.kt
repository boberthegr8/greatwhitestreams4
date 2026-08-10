package com.gwstreams.tv.ui.live

import android.os.Handler
import android.os.Looper
import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

internal data class PhoneSetupSubmission(
    val providerName: String?,
    val host: String,
    val user: String,
    val pass: String,
    val saveLogin: Boolean,
    val autoSubmit: Boolean
)

internal data class PhoneSetupSession(
    val url: String,
    val hostAddress: String,
    val port: Int
)

internal class PhoneSetupServer(
    private val providers: List<Provider>,
    private val selectedProvider: Provider,
    private val initialHost: String,
    private val onSubmission: (PhoneSetupSubmission) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var server: SetupHttpServer? = null
    private val secureRandom = SecureRandom()

    fun start(): Result<PhoneSetupSession> {
        stop()
        val hostAddress = findLocalIpv4Address()
            ?: return Result.failure(IllegalStateException("Couldn't find a LAN IP for this TV. Connect it to Wi‑Fi/Ethernet and try again."))

        val ports = listOf(18080, 18081, 18082, 18083)
        var lastError: Throwable? = null
        for (port in ports) {
            try {
                val token = generateSessionToken()
                val candidate = SetupHttpServer(port = port, token = token)
                candidate.start(SOCKET_READ_TIMEOUT, false)
                server = candidate
                return Result.success(
                    PhoneSetupSession(
                        url = "http://$hostAddress:${candidate.listeningPort}${candidate.pairPath}",
                        hostAddress = hostAddress,
                        port = candidate.listeningPort
                    )
                )
            } catch (error: Throwable) {
                lastError = error
            }
        }

        return Result.failure(lastError ?: IllegalStateException("Couldn't start the phone setup server."))
    }

    fun stop() {
        server?.stop()
        server = null
    }

    private inner class SetupHttpServer(
        port: Int,
        private val token: String
    ) : NanoHTTPD(port) {
        val pairPath: String = "/pair/$token"
        private val submissionConsumed = AtomicBoolean(false)

        override fun serve(session: IHTTPSession): Response {
            if (session.uri != pairPath) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
            if (submissionConsumed.get()) {
                return newFixedLengthResponse(Response.Status.GONE, MIME_PLAINTEXT, "This setup link has already been used.")
            }
            return when (session.method) {
                Method.GET -> newFixedLengthResponse(Response.Status.OK, MIME_HTML, buildSetupPage(formAction = pairPath))
                Method.POST -> handleSubmit(session)
                else -> newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "Method not allowed")
            }
        }

        private fun handleSubmit(session: IHTTPSession): Response {
            return try {
                val files = mutableMapOf<String, String>()
                session.parseBody(files)
                val params = session.parameters.mapValues { it.value.firstOrNull().orEmpty() }
                val submission = PhoneSetupSubmission(
                    providerName = params["provider"].orEmpty().ifBlank { null },
                    host = params["host"].orEmpty().trim(),
                    user = params["user"].orEmpty().trim(),
                    pass = params["pass"].orEmpty(),
                    saveLogin = params.containsKey("saveLogin"),
                    autoSubmit = params.containsKey("autoSubmit")
                )
                if (submission.host.isBlank() || submission.user.isBlank() || submission.pass.isBlank()) {
                    return newFixedLengthResponse(
                        Response.Status.BAD_REQUEST,
                        MIME_HTML,
                        buildSetupPage(formAction = pairPath, errorMessage = "Please fill every field before submitting.")
                    )
                }
                if (!submissionConsumed.compareAndSet(false, true)) {
                    return newFixedLengthResponse(Response.Status.GONE, MIME_PLAINTEXT, "This setup link has already been used.")
                }
                mainHandler.post { onSubmission(submission) }
                mainHandler.post { this@PhoneSetupServer.stop() }
                newFixedLengthResponse(Response.Status.OK, MIME_HTML, buildSuccessPage())
            } catch (error: Throwable) {
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    MIME_HTML,
                    buildSetupPage(formAction = pairPath, errorMessage = "Submit failed. Close this page and try again from the TV.")
                )
            }
        }
    }

    private fun buildSetupPage(
        formAction: String,
        errorMessage: String? = null
    ): String {
        val providerOptions = providers.joinToString(separator = "") { provider ->
            val selected = if (provider == selectedProvider) " selected" else ""
            "<option value=\"${provider.name}\"$selected>${htmlEscape(provider.displayName)}</option>"
        }
        val defaultHost = selectedProvider.defaultHost.ifBlank { initialHost }
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>Great White Streams TV setup</title>
              <style>
                body { font-family: sans-serif; background: #0f1115; color: #f5f7fb; margin: 0; padding: 24px; }
                .card { max-width: 720px; margin: 0 auto; background: #1b1f27; border-radius: 16px; padding: 24px; box-shadow: 0 12px 32px rgba(0,0,0,.35); }
                h1 { margin-top: 0; font-size: 1.6rem; }
                p { color: #c6cfda; }
                label { display: block; margin-top: 16px; margin-bottom: 8px; font-weight: 600; }
                input, select { width: 100%; box-sizing: border-box; padding: 14px; border-radius: 10px; border: 1px solid #3f4755; background: #11151c; color: #fff; font-size: 16px; }
                .checks { margin-top: 18px; display: grid; gap: 12px; }
                .check { display: flex; align-items: center; gap: 12px; color: #e1e6ef; }
                .check input { width: auto; transform: scale(1.3); }
                button { margin-top: 22px; width: 100%; padding: 14px; border: 0; border-radius: 12px; font-size: 17px; font-weight: 700; background: #45d9e7; color: #081018; }
                .hint { margin-top: 14px; font-size: .95rem; color: #97a3b6; }
                .error { padding: 12px 14px; border-radius: 10px; background: #402124; color: #ffb4ab; margin-top: 16px; }
              </style>
            </head>
            <body>
              <div class="card">
                <h1>Great White Streams TV setup</h1>
                <p>Send the TV your DNS/server URL, username, and password from this phone. You can either fill the TV login fields or have the TV try the login immediately.</p>
                ${errorMessage?.let { "<div class=\"error\">$it</div>" } ?: ""}
                <form method="post" action="${htmlAttribute(formAction)}">
                  <label for="provider">Provider preset</label>
                  <select id="provider" name="provider">$providerOptions</select>

                  <label for="host">DNS / server URL</label>
                  <input id="host" name="host" type="url" value="${htmlAttribute(initialHost.ifBlank { defaultHost })}" placeholder="https://host:port" required />

                  <label for="user">Username</label>
                  <input id="user" name="user" value="" autocomplete="username" required />

                  <label for="pass">Password</label>
                  <input id="pass" name="pass" type="password" value="" autocomplete="current-password" required />

                  <div class="checks">
                    <label class="check"><input type="checkbox" name="saveLogin" checked /> Save login on TV</label>
                    <label class="check"><input type="checkbox" name="autoSubmit" checked /> Sign in on TV after submit</label>
                  </div>

                  <button type="submit">Send to TV</button>
                </form>
                <div class="hint">Keep this page open until the TV confirms it received the setup.</div>
              </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildSuccessPage(): String = """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <title>Sent to TV</title>
          <style>
            body { font-family: sans-serif; background: #0f1115; color: #f5f7fb; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; padding: 24px; }
            .card { max-width: 560px; background: #1b1f27; border-radius: 16px; padding: 24px; text-align: center; }
            h1 { margin-top: 0; }
            p { color: #c6cfda; }
          </style>
        </head>
        <body>
          <div class="card">
            <h1>Sent to TV</h1>
            <p>Your Great White Streams TV should update its login screen in a second or two.</p>
            <p>You can close this page now.</p>
          </div>
        </body>
        </html>
    """.trimIndent()

    private fun htmlEscape(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            append(
                when (ch) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&#39;"
                    else -> ch
                }
            )
        }
    }

    private fun htmlAttribute(value: String): String = htmlEscape(value)

    private fun generateSessionToken(): String {
        val bytes = ByteArray(18)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun findLocalIpv4Address(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { iface -> iface.inetAddresses.toList().asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && (it.isSiteLocalAddress || it.hostAddress?.startsWith("192.168.") == true || it.hostAddress?.startsWith("10.") == true || it.hostAddress?.startsWith("172.") == true) }
                ?.hostAddress
        } catch (_: SocketException) {
            null
        }
    }

    private fun <T> java.util.Enumeration<T>.toList(): List<T> = buildList {
        while (hasMoreElements()) add(nextElement())
    }

    companion object {
        private const val SOCKET_READ_TIMEOUT = 5_000
    }
}
