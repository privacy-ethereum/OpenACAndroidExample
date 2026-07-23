package com.example.openacandroidexample

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import com.example.openacandroidexample.BuildConfig
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uniffi.mopro.generateCertChainRs4096Input
import uniffi.mopro.proveCertChainRs4096
import uniffi.mopro.proveUserSigRs2048
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.GZIPInputStream

private const val CERT_CHAIN_PROVING_KEY_URL =
    "https://github.com/privacy-ethereum/zkID/releases/download/RSA-X.509-Cert-latest/cert_chain_rs4096_proving.key.gz"
private const val USER_SIG_PROVING_KEY_URL =
    "https://github.com/privacy-ethereum/zkID/releases/download/RSA-X.509-Cert-latest/user_sig_rs2048_proving.key.gz"
private const val SMT_SNAPSHOT_URL =
    "https://github.com/privacy-ethereum/moica-revocation-smt/releases/download/snapshot-latest/g3-tree-snapshot.json.gz"

private const val CERT_CHAIN_PROVING_KEY_NAME = "cert_chain_rs4096_proving.key"
private const val USER_SIG_PROVING_KEY_NAME = "user_sig_rs2048_proving.key"
private const val SMT_SNAPSHOT_NAME           = "g3-tree-snapshot.json.gz"

private const val SERVER_URL      = "https://api-staging.devptt.dev/challenge"
private const val LINK_VERIFY_URL = "https://api-staging.devptt.dev/link-verify"

const val RETURN_SCHEME = "openac"
const val RETURN_URL    = "$RETURN_SCHEME://callback"

class ProofViewModel(application: Application) : AndroidViewModel(application) {

    // ── Step status ────────────────────────────────────────────────────────────

    sealed class StepStatus {
        object Idle    : StepStatus()
        object Running : StepStatus()
        data class Success(val message: String) : StepStatus()
        data class Failure(val message: String) : StepStatus()

        val isSuccess: Boolean get() = this is Success
        val isRunning: Boolean get() = this is Running
        val errorMessage: String? get() = (this as? Failure)?.message
    }

    // ── Flow navigation ────────────────────────────────────────────────────────

    sealed class FlowStep {
        object Intro      : FlowStep()
        object Readiness  : FlowStep()
        object Returned   : FlowStep()
        object Verifying  : FlowStep()
        object Submitting : FlowStep()
        object Success    : FlowStep()
        data class Failure(val message: String) : FlowStep()
    }

    var flowStep: FlowStep by mutableStateOf(FlowStep.Intro); private set

    // ── Pipeline step states ───────────────────────────────────────────────────

    var proveStatus:  StepStatus by mutableStateOf(StepStatus.Idle); private set
    var verifyStatus: StepStatus by mutableStateOf(StepStatus.Idle); private set
    var isRunning:    Boolean    by mutableStateOf(false);           private set

    var verificationStartTime:    Long?   = null
    var totalVerificationSeconds: Double? by mutableStateOf(null); private set
    var verifyMilliseconds:       Int?    by mutableStateOf(null); private set

    // ── OIDC Auth ──────────────────────────────────────────────────────────────

    private val authService = OidcAuthService(application)
    var authStatus: StepStatus by mutableStateOf(StepStatus.Idle); private set
    private var accessToken:    String? = null
    private var refreshToken:   String? = null
    private var tokenExpiresAt: Long?   = null

    /**
     * Runs the OIDC login (if needed) from a stable, user-initiated UI context
     * (e.g. a direct button tap) rather than implicitly inside a `LaunchedEffect`,
     * since launching the Custom Tab mid-transition-animation can surface as a
     * spurious cancellation.
     */
    suspend fun signIn(): Boolean = ensureAccessToken() != null

    /**
     * Returns a valid access token, reusing the cached one, refreshing it, or
     * running the interactive PKCE login flow as needed.
     */
    private suspend fun ensureAccessToken(): String? {
        val token = accessToken
        val expiresAt = tokenExpiresAt
        if (token != null && expiresAt != null && System.currentTimeMillis() < expiresAt) {
            return token
        }
        refreshToken?.let { rt ->
            try {
                store(authService.refresh(refreshToken = rt))
                return accessToken
            } catch (_: Exception) {
                // Fall back to interactive login below.
            }
        }
        authStatus = StepStatus.Running
        return try {
            store(authService.login())
            authStatus = StepStatus.Success("logged in")
            accessToken
        } catch (e: Exception) {
            authStatus = StepStatus.Failure(e.message ?: "Sign-in failed")
            null
        }
    }

    private fun store(tokens: OidcTokenResponse) {
        accessToken    = tokens.accessToken
        refreshToken   = tokens.refreshToken ?: refreshToken
        tokenExpiresAt = System.currentTimeMillis() + (tokens.expiresIn * 1000L) - 30_000L
    }

    /** Routes the `openac://oidc-callback` deep link to the in-flight login, if any. */
    fun handleOidcRedirect(uri: Uri): Boolean = authService.handleRedirect(uri)

    // ── Circuit download state ─────────────────────────────────────────────────

    var circuitReady:     Boolean by mutableStateOf(false); private set
    var isDownloading:    Boolean by mutableStateOf(false); private set
    var downloadProgress: Double  by mutableStateOf(0.0);   private set
    var downloadError:    String? by mutableStateOf(null);  private set
    var downloadSeconds:  Double? by mutableStateOf(null);  private set

    // ── SP Ticket / MOICA ──────────────────────────────────────────────────────

    var idNum:             String     by mutableStateOf("")
    var tbs:               String     by mutableStateOf("")
    var challenge:         String     by mutableStateOf("")
    var tbsStatus:         StepStatus by mutableStateOf(StepStatus.Idle); private set
    var spTicketStatus:    StepStatus by mutableStateOf(StepStatus.Idle); private set
    var spTicket:          String?    by mutableStateOf(null);             private set
    var rtnVal:            String?    by mutableStateOf(null);             private set
    var athResultStatus:   StepStatus by mutableStateOf(StepStatus.Idle); private set
    var athResponseString: String?    by mutableStateOf(null);             private set
    var athIssuerCert:     String?    by mutableStateOf(null);             private set
    var challengeExpiresAt: Date?     by mutableStateOf(null);             private set

    var generateInputStatus: StepStatus by mutableStateOf(StepStatus.Idle); private set
    var generatedInputPath:  String?    by mutableStateOf(null);             private set
    var inputJson:           String?    by mutableStateOf(null);             private set

    private var identityCheckEpoch: Int = 0

    val isChallengeExpired: Boolean
        get() = challengeExpiresAt?.let { System.currentTimeMillis() > it.time } ?: false

    val isValidIdNumber: Boolean
        get() {
            val id = idNum.trim()
            return id.length == 10 && id[0].isUpperCase() && id.drop(1).all { it.isDigit() }
        }

    val moicaAppInstalled: Boolean
        get() {
            val pm = getApplication<Application>().packageManager
            // Try the exact URI first (matches apps that declare the full host in their intent-filter)
            val fullUri = Intent(Intent.ACTION_VIEW,
                Uri.parse("mobilemoica://moica.moi.gov.tw/a2a/verifySign"))
            if (pm.queryIntentActivities(fullUri, 0).isNotEmpty()) return true
            // Fallback: scheme-only (matches apps that declare just the scheme)
            val schemeUri = Intent(Intent.ACTION_VIEW, Uri.parse("mobilemoica://callback"))
            return pm.queryIntentActivities(schemeUri, 0).isNotEmpty()
        }

    // ── Paths ──────────────────────────────────────────────────────────────────

    private val workDir: File
        get() = File(getApplication<Application>().filesDir, "ZKVectors")

    val documentsPath: String get() = workDir.absolutePath
    val keysDir:       File   get() = File(workDir, "keys")

    init { checkCircuitReady() }

    private fun checkCircuitReady() {
        circuitReady = File(keysDir, CERT_CHAIN_PROVING_KEY_NAME).exists()
            && File(keysDir, USER_SIG_PROVING_KEY_NAME).exists()
            && File(workDir, SMT_SNAPSHOT_NAME).exists()
    }

    // ── Resource setup ─────────────────────────────────────────────────────────

    fun prepareResources() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            workDir.mkdirs()
            val inputDst = File(workDir, "input.json")
            if (!inputDst.exists()) {
                try {
                    app.assets.open("input.json")
                        .use { src -> inputDst.outputStream().use { src.copyTo(it) } }
                } catch (_: Exception) {}
            }
            val certDst = File(workDir, "MOICA-G3.cer")
            if (!certDst.exists()) {
                try {
                    app.assets.open("MOICA-G3.cer")
                        .use { src -> certDst.outputStream().use { src.copyTo(it) } }
                } catch (_: Exception) {}
            }
            checkCircuitReady()
        }
    }

    // ── Flow navigation helpers ────────────────────────────────────────────────

    fun startFlow() {
        flowStep = FlowStep.Readiness
    }

    fun reset() {
        identityCheckEpoch++
        isRunning            = false
        flowStep             = FlowStep.Intro
        generateInputStatus  = StepStatus.Idle
        generatedInputPath   = null
        inputJson            = null
        proveStatus          = StepStatus.Idle
        verifyStatus         = StepStatus.Idle
        athResultStatus      = StepStatus.Idle
        athResponseString    = null
        athIssuerCert        = null
        spTicket             = null
        spTicketStatus       = StepStatus.Idle
        tbs                  = ""
        challenge            = ""
        tbsStatus            = StepStatus.Idle
        rtnVal               = null
        challengeExpiresAt   = null
        verificationStartTime    = null
        totalVerificationSeconds = null
        verifyMilliseconds       = null
        idNum                = ""
    }

    fun resetToReadiness() {
        flowStep        = FlowStep.Readiness
        spTicketStatus  = StepStatus.Idle
        tbsStatus       = StepStatus.Idle
        proveStatus     = StepStatus.Idle
        verifyStatus    = StepStatus.Idle
        generateInputStatus = StepStatus.Idle
    }

    fun resetIdentityCheckOnIdNumberEdit() {
        identityCheckEpoch++
        spTicketStatus    = StepStatus.Idle
        spTicket          = null
        rtnVal            = null
        challengeExpiresAt = null
    }

    // ── Download Circuit ───────────────────────────────────────────────────────

    fun downloadCircuit() {
        if (isDownloading) return
        viewModelScope.launch {
            isDownloading    = true
            downloadProgress = 0.0
            downloadError    = null
            downloadSeconds  = null

            val certKeyExists  = File(keysDir, CERT_CHAIN_PROVING_KEY_NAME).exists()
            val devKeyExists   = File(keysDir, USER_SIG_PROVING_KEY_NAME).exists()
            val snapshotExists = File(workDir, SMT_SNAPSHOT_NAME).exists()

            if (certKeyExists && devKeyExists && snapshotExists) {
                circuitReady  = true
                isDownloading = false
                return@launch
            }

            try {
                keysDir.mkdirs()
                val t0 = System.currentTimeMillis()

                data class Job(
                    val url: String, val name: String, val dest: File,
                    val exists: Boolean, val decompress: Boolean,
                )
                val jobs = listOf(
                    Job(CERT_CHAIN_PROVING_KEY_URL, CERT_CHAIN_PROVING_KEY_NAME, File(keysDir, CERT_CHAIN_PROVING_KEY_NAME), certKeyExists, true),
                    Job(USER_SIG_PROVING_KEY_URL, USER_SIG_PROVING_KEY_NAME, File(keysDir, USER_SIG_PROVING_KEY_NAME), devKeyExists, true),
                    Job(SMT_SNAPSHOT_URL, SMT_SNAPSHOT_NAME, File(workDir, SMT_SNAPSHOT_NAME), snapshotExists, false),
                )
                val slice = 1.0 / jobs.size
                for ((i, job) in jobs.withIndex()) {
                    val base = i * slice
                    if (job.exists) { downloadProgress = base + slice; continue }
                    if (job.decompress) {
                        val tmp = File(getApplication<Application>().cacheDir, "${job.name}.gz")
                        downloadWithProgress(job.url, tmp) { downloadProgress = base + it * slice }
                        decompressGz(tmp, job.dest)
                    } else {
                        downloadWithProgress(job.url, job.dest) { downloadProgress = base + it * slice }
                    }
                }
                downloadSeconds = (System.currentTimeMillis() - t0) / 1000.0
                checkCircuitReady()
            } catch (e: Exception) {
                downloadError = e.message
            } finally {
                isDownloading = false
            }
        }
    }

    private suspend fun downloadWithProgress(
        url: String, dest: File, onProgress: (Double) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connect()
            val total = conn.contentLengthLong
            var received = 0L
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(65536)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        received += n
                        if (total > 0) onProgress(received.toDouble() / total.toDouble())
                    }
                }
            }
        } finally { conn.disconnect() }
    }

    private suspend fun decompressGz(gzFile: File, dest: File) = withContext(Dispatchers.IO) {
        if (dest.exists()) dest.delete()
        GZIPInputStream(gzFile.inputStream().buffered()).use { gis ->
            dest.outputStream().use { gis.copyTo(it) }
        }
        gzFile.delete()
    }

    // ── TBS Challenge ──────────────────────────────────────────────────────────

    fun regenerateTBS() { viewModelScope.launch { doRegenerateTBS() } }

    private suspend fun doRegenerateTBS() {
        tbsStatus = StepStatus.Running
        val token = ensureAccessToken()
        if (token == null) {
            challengeExpiresAt = null
            tbsStatus = StepStatus.Failure(authStatus.errorMessage ?: "Server error")
            return
        }
        try {
            val raw = withContext(Dispatchers.IO) {
                val conn = URL(SERVER_URL).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("ngrok-skip-browser-warning", "true")
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Connection", "close")
                conn.doOutput = true
                conn.outputStream.use { it.write("{}".toByteArray()) }
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                text
            }
            val json         = JSONObject(raw)
            val appId        = json.optString("app_id")
            val challengeStr = json.optString("challenge")
            if (appId.isEmpty() || challengeStr.isEmpty()) {
                challengeExpiresAt = null
                tbsStatus = StepStatus.Failure("Server error: missing app_id or challenge")
                return
            }
            tbs       = appId
            challenge = challengeStr
            val expiresAtStr = json.optString("expires_at")
            challengeExpiresAt = if (expiresAtStr.isNotEmpty()) parseIso8601(expiresAtStr) else null
            tbsStatus = StepStatus.Success("challenge received")
        } catch (e: Exception) {
            challengeExpiresAt = null
            tbsStatus = StepStatus.Failure(e.message ?: "unknown error")
        }
    }

    // ── SP Ticket / MOICA ──────────────────────────────────────────────────────

    fun computeSPTicket() { viewModelScope.launch { doComputeSPTicket() } }

    private suspend fun doComputeSPTicket() {
        val epochAtStart = identityCheckEpoch
        spTicketStatus = StepStatus.Running
        spTicket = null
        rtnVal   = null
        try {
            val raw = getSpTicket(
                params = SpTicketParams(
                    transactionID = UUID.randomUUID().toString(),
                    idNum         = idNum,
                    opCode        = "SIGN",
                    opMode        = "APP2APP",
                    hint          = getApplication<Application>().getString(R.string.vm_hint_sign_data),
                    timeLimit     = "600",
                    signData      = Base64.encodeToString(tbs.toByteArray(Charsets.UTF_8), Base64.NO_WRAP),
                    signType      = "PKCS#1",
                    hashAlgorithm = "SHA256",
                    tbsEncoding   = "base64",
                )
            )
            if (epochAtStart != identityCheckEpoch) return
            val ticket = JSONObject(raw)
                .optJSONObject("result")
                ?.optString("sp_ticket")
                ?.takeIf { it.isNotEmpty() }
            if (epochAtStart != identityCheckEpoch) return
            spTicket = ticket
            spTicketStatus = if (ticket != null)
                StepStatus.Success("ticket received")
            else
                StepStatus.Failure("sp_ticket not found in response: $raw")
        } catch (e: Exception) {
            if (epochAtStart != identityCheckEpoch) return
            spTicketStatus = StepStatus.Failure(e.message ?: "unknown error")
        }
    }

    fun checkIdAndGetTicket() {
        if (spTicketStatus !is StepStatus.Idle) return
        viewModelScope.launch {
            val shouldRegen = when {
                tbsStatus !is StepStatus.Success -> true
                else -> {
                    val end = challengeExpiresAt
                    end != null && System.currentTimeMillis() >= end.time
                }
            }
            if (shouldRegen) doRegenerateTBS()
            if (tbsStatus is StepStatus.Failure) return@launch
            doComputeSPTicket()
        }
    }

    fun openMOICA() {
        if (isChallengeExpired) return
        val ticket = spTicket ?: return
        val rtnUrlBase64 = Base64.encodeToString(RETURN_URL.toByteArray(), Base64.NO_WRAP)
        val uri = Uri.Builder()
            .scheme("mobilemoica")
            .authority("moica.moi.gov.tw")
            .path("/a2a/verifySign")
            .appendQueryParameter("sp_ticket", ticket)
            .appendQueryParameter("rtn_url",   rtnUrlBase64)
            .appendQueryParameter("rtn_val",   "")
            .build()
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            getApplication<Application>().startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            flowStep = FlowStep.Failure("MOICA app not installed")
        }
    }

    fun pollAthResult() {
        viewModelScope.launch { doAthResult() }
    }

    private suspend fun doAthResult() {
        athResultStatus = StepStatus.Running
        val ticket = spTicket
        if (ticket == null) {
            athResultStatus = StepStatus.Failure("No sp_ticket available")
            return
        }
        try {
            val result = pollSignResult(spTicket = ticket)
            athResponseString = result.result?.signedResponse
            athIssuerCert     = result.result?.cert
            athResultStatus   = StepStatus.Success("result received")
        } catch (e: Exception) {
            athResultStatus = StepStatus.Failure(e.message ?: "unknown error")
        }
    }

    fun handleCallback(uri: Uri) {
        if (uri.scheme != RETURN_SCHEME) return
        rtnVal   = uri.getQueryParameter("rtn_val")
        flowStep = FlowStep.Returned
        pollAthResult()
    }

    // ── Pipeline ───────────────────────────────────────────────────────────────

    fun runAll() {
        if (isRunning) return
        viewModelScope.launch {
            isRunning = true
            generateInputStatus = StepStatus.Idle
            proveStatus         = StepStatus.Idle
            verifyStatus        = StepStatus.Idle
            doProve()
            if (!proveStatus.isSuccess) { isRunning = false; return@launch }
            doVerify()
            isRunning = false
        }
    }

    fun runLocalVerification() {
        viewModelScope.launch {
            verificationStartTime    = System.currentTimeMillis()
            totalVerificationSeconds = null
            verifyMilliseconds       = null
            flowStep  = FlowStep.Verifying
            isRunning = true

            doGenerateInput()
            if (!generateInputStatus.isSuccess) {
                flowStep  = FlowStep.Failure(generateInputStatus.errorMessage ?: "Prepare input failed")
                isRunning = false
                return@launch
            }

            doProve()
            if (!proveStatus.isSuccess) {
                flowStep  = FlowStep.Failure(proveStatus.errorMessage ?: "Prove failed")
                isRunning = false
                return@launch
            }

            if (isChallengeExpired) {
                flowStep  = FlowStep.Failure("Challenge expired")
                isRunning = false
                return@launch
            }

            flowStep = FlowStep.Submitting
            doVerify()

            if (verifyStatus.isSuccess) {
                totalVerificationSeconds =
                    (System.currentTimeMillis() - (verificationStartTime ?: 0L)) / 1000.0
                flowStep = FlowStep.Success
            } else {
                flowStep = FlowStep.Failure(verifyStatus.errorMessage ?: "Verify failed")
            }
            isRunning = false
        }
    }

    fun runGenerateInput() {
        viewModelScope.launch { doGenerateInput() }
    }

    fun runProve() {
        if (isRunning) return
        viewModelScope.launch {
            isRunning   = true
            proveStatus = StepStatus.Idle
            doProve()
            isRunning = false
        }
    }

    fun runVerify() {
        if (isRunning) return
        viewModelScope.launch {
            isRunning    = true
            verifyStatus = StepStatus.Idle
            doVerify()
            isRunning = false
        }
    }

    private suspend fun doGenerateInput() {
        generateInputStatus = StepStatus.Running
        val certb64        = athIssuerCert
        val signedResponse = athResponseString
        if (certb64 == null || signedResponse == null) {
            generateInputStatus = StepStatus.Failure("Missing ATH result")
            return
        }
        val tbsCapture       = tbs
        val challengeCapture = challenge
        val outDir           = workDir.absolutePath
        val issuerCertPath   = File(workDir, "MOICA-G3.cer").absolutePath
        val smtSnapshotPath  = File(workDir, SMT_SNAPSHOT_NAME).absolutePath
        try {
            val resultPath = withContext(Dispatchers.Default) {
                generateCertChainRs4096Input(
                    certb64         = certb64,
                    signedResponse  = signedResponse,
                    tbs             = tbsCapture,
                    issuerCertPath  = issuerCertPath,
                    smtSnapshotPath = smtSnapshotPath,
                    outputDir       = outDir,
                    challenge       = challengeCapture,
                )
            }
            generatedInputPath  = resultPath
            inputJson           = try { File(resultPath).readText() } catch (_: Exception) { null }
            generateInputStatus = StepStatus.Success(resultPath)
        } catch (e: Exception) {
            generateInputStatus = StepStatus.Failure(e.message ?: "unknown error")
        }
    }

    private suspend fun doProve() {
        proveStatus = StepStatus.Running
        val dp = documentsPath
        try {
            val ms = withContext(Dispatchers.Default) {
                val t0 = System.currentTimeMillis()
                proveCertChainRs4096(documentsPath = dp)
                proveUserSigRs2048(documentsPath = dp)
                System.currentTimeMillis() - t0
            }
            proveStatus = StepStatus.Success("$ms ms")
        } catch (e: Exception) {
            proveStatus = StepStatus.Failure(e.message ?: "unknown error")
        }
    }

    private suspend fun doVerify() {
        verifyStatus = StepStatus.Running
        val token = ensureAccessToken()
        if (token == null) {
            verifyStatus = StepStatus.Failure(authStatus.errorMessage ?: "Server error")
            return
        }
        val kd = keysDir
        try {
            val (ccProof, dsProof) = withContext(Dispatchers.Default) {
                val cc = File(kd, "cert_chain_rs4096_proof.bin").readBytes()
                val ds = File(kd, "user_sig_rs2048_proof.bin").readBytes()
                cc to ds
            }

            val verifyStart = System.currentTimeMillis()
            val (responseCode, raw) = withContext(Dispatchers.IO) {
                val conn = URL(LINK_VERIFY_URL).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("ngrok-skip-browser-warning", "true")
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Connection", "close")
                conn.doOutput = true
                val body = JSONObject().apply {
                    put("cert_chain_type",  "rs4096")
                    put("cert_chain_proof", Base64.encodeToString(ccProof, Base64.NO_WRAP))
                    put("user_sig_proof", Base64.encodeToString(dsProof, Base64.NO_WRAP))
                }
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = conn.responseCode
                val text = try { conn.inputStream.bufferedReader().readText() }
                           catch (_: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "" }
                conn.disconnect()
                code to text
            }

            if (responseCode != 200) {
                verifyStatus = StepStatus.Failure("link-verify failed ($responseCode): $raw")
                return
            }
            verifyMilliseconds = (System.currentTimeMillis() - verifyStart).toInt()
            verifyStatus = StepStatus.Success("All proofs valid")
        } catch (e: Exception) {
            verifyStatus = StepStatus.Failure(e.message ?: "unknown error")
        } finally {
            withContext(Dispatchers.IO) {
                keysDir.deleteRecursively()
                File(workDir, SMT_SNAPSHOT_NAME).delete()
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun parseIso8601(str: String): Date? {
        val normalized = str.replace(Regex("(\\.\\d{3})\\d+"), "$1")
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).parse(normalized)
        } catch (_: Exception) {
            try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(normalized)
            } catch (_: Exception) { null }
        }
    }
}
