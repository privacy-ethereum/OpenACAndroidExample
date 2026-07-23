package com.example.openacandroidexample

// PTT OIDC (Zitadel) login via Authorization Code + PKCE.
// https://zitadel.com/docs/guides/integrate/login/oidc/login-users

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

data class OidcTokenResponse(
    val accessToken:  String,
    val tokenType:    String,
    val refreshToken: String?,
    val expiresIn:    Int,
    val idToken:      String?,
    val scope:        String?,
) {
    companion object {
        fun fromJson(json: JSONObject) = OidcTokenResponse(
            accessToken  = json.getString("access_token"),
            tokenType    = json.optString("token_type"),
            refreshToken = json.optString("refresh_token").takeIf { it.isNotEmpty() },
            expiresIn    = json.optInt("expires_in"),
            idToken      = json.optString("id_token").takeIf { it.isNotEmpty() },
            scope        = json.optString("scope").takeIf { it.isNotEmpty() },
        )
    }
}

sealed class OidcException(message: String) : Exception(message) {
    object Cancelled : OidcException("Login was cancelled before completing sign-in.")
    object MissingCode : OidcException("Login finished without an authorization code.")
    class TokenRequestFailed(code: Int, body: String) :
        OidcException("Token request failed ($code): $body")
}

private const val AUTHORIZE_URL = "https://api-staging.devptt.dev/api/oidc/auth"
private const val TOKEN_URL     = "https://api-staging.devptt.dev/api/oidc/token"
private const val CLIENT_ID     = "vivianapp"
private const val SCOPE         = "openid offline_access"

/** Custom-scheme redirect URI for this app, registered against the `vivianapp` client. */
const val OIDC_REDIRECT_SCHEME = "openac"
const val OIDC_REDIRECT_HOST   = "oidc-callback"
const val OIDC_REDIRECT_URI    = "$OIDC_REDIRECT_SCHEME://$OIDC_REDIRECT_HOST"

class OidcAuthService(private val application: Application) {

    /** Set while an authorize request is in flight; resolved by [handleRedirect]. */
    private var pendingAuthorize: CompletableDeferred<Uri>? = null

    suspend fun login(): OidcTokenResponse {
        val verifier  = makeCodeVerifier()
        val challenge = codeChallenge(verifier)

        val authorizeUri = Uri.parse(AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("redirect_uri", OIDC_REDIRECT_URI)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        val callbackUri = authorize(authorizeUri)
        val code = callbackUri.getQueryParameter("code") ?: throw OidcException.MissingCode
        return exchange(code = code, verifier = verifier)
    }

    suspend fun refresh(refreshToken: String): OidcTokenResponse = requestToken(
        mapOf(
            "grant_type"    to "refresh_token",
            "refresh_token" to refreshToken,
            "client_id"     to CLIENT_ID,
        )
    )

    // MARK: - Authorize (system browser / Custom Tabs)

    private suspend fun authorize(uri: Uri): Uri {
        cancelPending()
        val deferred = CompletableDeferred<Uri>()
        pendingAuthorize = deferred
        try {
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(application, uri)
            return deferred.await()
        } finally {
            if (pendingAuthorize === deferred) pendingAuthorize = null
        }
    }

    /**
     * Called by [MainActivity] when the `openac://oidc-callback` deep link arrives.
     * Returns true if this redirect was consumed as part of an in-flight login.
     */
    fun handleRedirect(uri: Uri): Boolean {
        if (uri.scheme != OIDC_REDIRECT_SCHEME || uri.host != OIDC_REDIRECT_HOST) return false
        val deferred = pendingAuthorize ?: return false
        deferred.complete(uri)
        return true
    }

    /** Dedupes a stale in-flight attempt (e.g. a fast double-tap on "Start"). */
    private fun cancelPending() {
        pendingAuthorize?.completeExceptionally(OidcException.Cancelled)
        pendingAuthorize = null
    }

    // MARK: - Token exchange

    private suspend fun exchange(code: String, verifier: String) = requestToken(
        mapOf(
            "grant_type"    to "authorization_code",
            "code"          to code,
            "client_id"     to CLIENT_ID,
            "redirect_uri"  to OIDC_REDIRECT_URI,
            "code_verifier" to verifier,
        )
    )

    private suspend fun requestToken(params: Map<String, String>): OidcTokenResponse =
        withContext(Dispatchers.IO) {
            val conn = URL(TOKEN_URL).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.doOutput = true
                conn.outputStream.use { it.write(formEncode(params).toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val body = if (code == 200) {
                    conn.inputStream.bufferedReader().readText()
                } else {
                    conn.errorStream?.bufferedReader()?.readText() ?: ""
                }
                if (code != 200) throw OidcException.TokenRequestFailed(code, body)
                OidcTokenResponse.fromJson(JSONObject(body))
            } finally {
                conn.disconnect()
            }
        }

    // MARK: - PKCE

    private fun makeCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return base64UrlEncode(bytes)
    }

    private fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8))
        return base64UrlEncode(digest)
    }

    private fun base64UrlEncode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)

    private fun formEncode(params: Map<String, String>): String =
        params.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
}
