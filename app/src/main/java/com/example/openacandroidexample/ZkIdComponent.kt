package com.example.openacandroidexample

import com.example.openacandroidexample.BuildConfig
import com.example.openacandroidexample.R
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openacandroidexample.ui.theme.VerifierAccent
import com.example.openacandroidexample.ui.theme.VerifierBackground
import com.example.openacandroidexample.ui.theme.VerifierDivider
import com.example.openacandroidexample.ui.theme.VerifierError
import com.example.openacandroidexample.ui.theme.VerifierPrimary
import com.example.openacandroidexample.ui.theme.VerifierSecondary
import com.example.openacandroidexample.ui.theme.VerifierSurface
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Root component – drives the screen flow
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ZkIdComponent(vm: ProofViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.prepareResources() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = VerifierBackground,
    ) {
        AnimatedContent(
            targetState  = vm.flowStep,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label        = "screen_transition",
        ) { step ->
            when (step) {
                is ProofViewModel.FlowStep.Intro      -> IntroScreen(vm)
                is ProofViewModel.FlowStep.Readiness  -> ReadinessScreen(vm)
                is ProofViewModel.FlowStep.Returned   -> MOICAReturnedScreen(vm)
                is ProofViewModel.FlowStep.Verifying,
                is ProofViewModel.FlowStep.Submitting -> VerificationProgressScreen(vm)
                is ProofViewModel.FlowStep.Success    -> SuccessScreen(vm)
                is ProofViewModel.FlowStep.Failure    -> ErrorScreen(vm, step.message)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen 1 – Intro
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IntroScreen(vm: ProofViewModel) {
    var showLearnMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        ScreenScaffold {
            Text(
                stringResource(R.string.intro_title),
                style      = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color      = VerifierPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.intro_body),
                style = MaterialTheme.typography.bodyLarge,
                color = VerifierSecondary,
            )
            Spacer(Modifier.height(24.dp))

            VerifierCard {
                InfoRow(stringResource(R.string.label_verifies),  stringResource(R.string.value_badge_citizen))
                VerifierDividerLine()
                InfoRow(stringResource(R.string.label_requires),  stringResource(R.string.value_requires))
                VerifierDividerLine()
                InfoRow(stringResource(R.string.label_how),       stringResource(R.string.value_how))
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(VerifierSurface)
                    .padding(16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null,
                    tint = VerifierSecondary, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                Text(
                    stringResource(R.string.intro_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = VerifierSecondary,
                )
            }

            Spacer(Modifier.height(32.dp))

            VerifierPrimaryButton(
                text    = stringResource(R.string.btn_start),
                onClick = {
                    scope.launch {
                        if (vm.signIn()) vm.startFlow()
                    }
                },
                enabled = !vm.authStatus.isRunning,
            )
            vm.authStatus.errorMessage?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text(err, style = MaterialTheme.typography.bodySmall, color = VerifierError)
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { showLearnMore = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.intro_learn_shared),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VerifierAccent,
                )
            }
        }
        if (showLearnMore) {
            LearnMoreSheet(onDismiss = { showLearnMore = false })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen 2 – Readiness Check
// ─────────────────────────────────────────────────────────────────────────────

private enum class ReadinessStatus { Ready, Loading, NotReady }

@Composable
private fun ReadinessScreen(vm: ProofViewModel) {
    var isIdHidden by remember { mutableStateOf(true) }
    var showDownloadWarning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (vm.circuitReady) {
            if (vm.isValidIdNumber) vm.checkIdAndGetTicket()
        } else if (!vm.isDownloading) {
            showDownloadWarning = true
        }
    }
    LaunchedEffect(vm.circuitReady) {
        if (vm.circuitReady && vm.isValidIdNumber) vm.checkIdAndGetTicket()
    }
    LaunchedEffect(vm.isValidIdNumber) {
        if (vm.isValidIdNumber && vm.circuitReady) vm.checkIdAndGetTicket()
    }

    val circuitLabel = when {
        vm.circuitReady  -> stringResource(R.string.status_ready)
        vm.isDownloading -> stringResource(R.string.circuit_downloading, (vm.downloadProgress * 100).toInt())
        else             -> stringResource(R.string.status_preparing)
    }
    val circuitStatus = when {
        vm.circuitReady    -> ReadinessStatus.Ready
        vm.isDownloading   -> ReadinessStatus.Loading
        else               -> ReadinessStatus.Loading
    }

    val allReady = vm.moicaAppInstalled && vm.spTicketStatus.isSuccess &&
                   vm.circuitReady && !vm.isChallengeExpired

    ScreenScaffold {
        Text(
            stringResource(R.string.readiness_title),
            style      = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color      = VerifierPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.readiness_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = VerifierSecondary,
        )
        Spacer(Modifier.height(16.dp))

        VerifierCard {
            // MOICA app row
            ReadinessRow(
                label  = stringResource(R.string.label_moica_app),
                detail = if (vm.moicaAppInstalled) stringResource(R.string.status_installed)
                         else stringResource(R.string.status_not_installed),
                status = if (vm.moicaAppInstalled) ReadinessStatus.Ready else ReadinessStatus.NotReady,
            )
            VerifierDividerLine()

            // Local verifier (circuit) row
            ReadinessRow(
                label  = stringResource(R.string.label_local_verifier),
                detail = circuitLabel,
                status = circuitStatus,
            )
            VerifierDividerLine()

            // National ID input row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.label_national_id),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VerifierSecondary,
                )
                OutlinedTextField(
                    value         = vm.idNum,
                    onValueChange = {
                        vm.idNum = it.uppercase(Locale.ROOT)
                        vm.resetIdentityCheckOnIdNumberEdit()
                    },
                    singleLine           = true,
                    visualTransformation = if (isIdHidden) PasswordVisualTransformation() else VisualTransformation.None,
                    trailingIcon         = {
                        IconButton(onClick = { isIdHidden = !isIdHidden }) {
                            Icon(
                                if (isIdHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null,
                                tint = VerifierSecondary,
                            )
                        }
                    },
                    placeholder     = { Text(stringResource(R.string.id_placeholder), color = VerifierSecondary) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction      = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        if (vm.isValidIdNumber) vm.checkIdAndGetTicket()
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor     = VerifierPrimary,
                        unfocusedTextColor   = VerifierPrimary,
                        focusedBorderColor   = VerifierAccent,
                        unfocusedBorderColor = VerifierDivider,
                        cursorColor          = VerifierAccent,
                    ),
                    modifier  = Modifier
                        .fillMaxWidth()
                        .testTag("idNumField"),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = VerifierPrimary),
                )
            }
            VerifierDividerLine()

            // Check ID row
            CheckIdRow(vm = vm)
            VerifierDividerLine()

            // Sign & Authorize row (informational)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.label_sign_authorize),
                        style = MaterialTheme.typography.bodyMedium, color = VerifierPrimary)
                    Text(stringResource(R.string.sign_authorize_sub),
                        style = MaterialTheme.typography.bodySmall, color = VerifierSecondary)
                }
                Icon(Icons.Filled.ExpandMore, contentDescription = null,
                    tint = VerifierSecondary, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(24.dp))

        VerifierPrimaryButton(
            text    = stringResource(R.string.btn_open_moica),
            onClick = { vm.openMOICA() },
            enabled = allReady,
            modifier = Modifier.testTag("fidoOpenMoicaButton"),
        )
        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick  = { vm.reset() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.btn_back),
                style = MaterialTheme.typography.bodyMedium, color = VerifierSecondary)
        }

        if (BuildConfig.DEBUG) {

        }
    }

    if (showDownloadWarning) {
        AlertDialog(
            onDismissRequest = {},
            title   = { Text(stringResource(R.string.readiness_download_warning_title)) },
            text    = { Text(stringResource(R.string.readiness_download_warning_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDownloadWarning = false
                    vm.downloadCircuit()
                }) {
                    Text(stringResource(R.string.readiness_download_warning_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDownloadWarning = false
                    vm.reset()
                }) {
                    Text(stringResource(R.string.btn_back))
                }
            },
        )
    }
}

@Composable
private fun CheckIdRow(vm: ProofViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.label_check_id),
                style = MaterialTheme.typography.bodyMedium, color = VerifierPrimary)
            when (val s = vm.spTicketStatus) {
                is ProofViewModel.StepStatus.Idle -> {
                    when (val tbs = vm.tbsStatus) {
                        is ProofViewModel.StepStatus.Failure ->
                            Text(tbs.message,
                                style = MaterialTheme.typography.bodySmall, color = VerifierError, maxLines = 2)
                        else -> when {
                            vm.idNum.isEmpty() ->
                                Text(stringResource(R.string.check_id_hint_enter),
                                    style = MaterialTheme.typography.bodySmall, color = VerifierSecondary)
                            !vm.isValidIdNumber ->
                                Text(stringResource(R.string.check_id_hint_invalid),
                                    style = MaterialTheme.typography.bodySmall, color = VerifierAccent)
                            !vm.circuitReady ->
                                Text(stringResource(R.string.check_id_waiting_circuit),
                                    style = MaterialTheme.typography.bodySmall, color = VerifierSecondary)
                            tbs is ProofViewModel.StepStatus.Running ->
                                Text(stringResource(R.string.check_id_fetching_challenge),
                                    style = MaterialTheme.typography.bodySmall, color = VerifierSecondary)
                            else ->
                                Text(stringResource(R.string.status_preparing),
                                    style = MaterialTheme.typography.bodySmall, color = VerifierSecondary)
                        }
                    }
                }
                is ProofViewModel.StepStatus.Running ->
                    Text(stringResource(R.string.check_id_fetching_ticket),
                        style = MaterialTheme.typography.bodySmall, color = VerifierSecondary)
                is ProofViewModel.StepStatus.Success ->
                    Text(stringResource(R.string.status_ready),
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFF34C759))
                is ProofViewModel.StepStatus.Failure ->
                    Text(s.message,
                        style = MaterialTheme.typography.bodySmall, color = VerifierError, maxLines = 2)
            }
        }
        Spacer(Modifier.width(12.dp))
        when (vm.spTicketStatus) {
            is ProofViewModel.StepStatus.Success ->
                Icon(Icons.Filled.CheckCircle, contentDescription = null,
                    tint = Color(0xFF34C759), modifier = Modifier.size(22.dp))
            is ProofViewModel.StepStatus.Running ->
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp), color = VerifierAccent, strokeWidth = 2.dp)
            else -> {
                val tbsRunning = vm.tbsStatus is ProofViewModel.StepStatus.Running
                if (tbsRunning || (vm.isValidIdNumber && vm.circuitReady)) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp), color = VerifierAccent, strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint     = VerifierSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChallengeExpiryCountdown(expiresAt: Date) {
    var remaining by remember { mutableLongStateOf(expiresAt.time - System.currentTimeMillis()) }
    LaunchedEffect(expiresAt) {
        while (remaining > 0) {
            delay(1000)
            remaining = expiresAt.time - System.currentTimeMillis()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VerifierSurface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Timer, contentDescription = null, tint = VerifierAccent, modifier = Modifier.size(18.dp))
        if (remaining > 0) {
            Text(stringResource(R.string.countdown_label),
                style = MaterialTheme.typography.bodySmall, color = VerifierSecondary)
            Text(
                formatCountdown(remaining),
                style      = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.SemiBold,
                color      = VerifierPrimary,
            )
        } else {
            Text(
                stringResource(R.string.countdown_expired),
                style = MaterialTheme.typography.bodySmall,
                color = VerifierError,
            )
        }
    }
}

private fun formatCountdown(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen 3 – Return from MOICA
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MOICAReturnedScreen(vm: ProofViewModel) {
    ScreenScaffold {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint     = Color(0xFF34C759),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .size(48.dp)
                .align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.returned_title),
            style      = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color      = VerifierPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.returned_body),
            style = MaterialTheme.typography.bodyLarge,
            color = VerifierSecondary,
        )
        Spacer(Modifier.height(24.dp))

        VerifierCard {
            InfoRow(stringResource(R.string.label_verifies),   stringResource(R.string.value_badge_citizen))
            VerifierDividerLine()
            InfoRow(stringResource(R.string.label_credential), stringResource(R.string.value_credential))
            VerifierDividerLine()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.label_signing_status),
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = VerifierSecondary,
                    modifier = Modifier.weight(1f),
                )
                SigningStatusView(vm.athResultStatus)
            }
            VerifierDividerLine()
            InfoRow(stringResource(R.string.returned_label_next), stringResource(R.string.returned_value_next))
        }

        Spacer(Modifier.height(24.dp))

        VerifierPrimaryButton(
            text    = stringResource(R.string.btn_continue),
            onClick = { vm.runLocalVerification() },
            enabled = vm.athResultStatus.isSuccess,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick  = { vm.reset() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.btn_back),
                style = MaterialTheme.typography.bodyMedium, color = VerifierSecondary)
        }
    }
}

@Composable
private fun SigningStatusView(status: ProofViewModel.StepStatus) {
    when (status) {
        is ProofViewModel.StepStatus.Success ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF34C759), modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.signing_status_done),
                    style = MaterialTheme.typography.bodyMedium, color = Color(0xFF34C759))
            }
        is ProofViewModel.StepStatus.Running ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = VerifierAccent, strokeWidth = 2.dp)
                Text(stringResource(R.string.signing_status_checking),
                    style = MaterialTheme.typography.bodyMedium, color = VerifierSecondary)
            }
        is ProofViewModel.StepStatus.Failure ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.Close, null, tint = VerifierError, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.signing_status_incomplete),
                    style = MaterialTheme.typography.bodyMedium, color = VerifierError)
            }
        else ->
            Text(stringResource(R.string.signing_status_waiting),
                style = MaterialTheme.typography.bodyMedium, color = VerifierSecondary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screens 4 & 5 – Verification Progress
// ─────────────────────────────────────────────────────────────────────────────

private enum class ProgressItemState { Done, Active, Pending }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VerificationProgressScreen(vm: ProofViewModel) {
    val isSubmitting = vm.flowStep is ProofViewModel.FlowStep.Submitting
    var showLearnMore by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        ScreenScaffold {
            if (isSubmitting) {
                Text(stringResource(R.string.progress_submitting_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold, color = VerifierPrimary)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.progress_submitting_body),
                    style = MaterialTheme.typography.bodyLarge, color = VerifierSecondary)
            } else {
                Text(stringResource(R.string.progress_verifying_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold, color = VerifierPrimary)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.progress_verifying_body),
                    style = MaterialTheme.typography.bodyLarge, color = VerifierSecondary)
            }
            Spacer(Modifier.height(24.dp))

            VerifierCard {
                ProgressRow(stringResource(R.string.progress_step1), ProgressItemState.Done)
                VerifierDividerLine()
                ProgressRow(stringResource(R.string.progress_step2), ProgressItemState.Done)
                VerifierDividerLine()
                ProgressRow(stringResource(R.string.progress_step3), ProgressItemState.Done)
                VerifierDividerLine()
                ProgressRow(stringResource(R.string.progress_step4), progressItemState(vm.generateInputStatus))
                VerifierDividerLine()
                ProgressRow(stringResource(R.string.progress_step5), progressItemState(vm.proveStatus))
                VerifierDividerLine()
                ProgressRow(stringResource(R.string.progress_step6), progressItemState(vm.verifyStatus))
            }

            if (isSubmitting || vm.proveStatus is ProofViewModel.StepStatus.Running || vm.proveStatus is ProofViewModel.StepStatus.Success) {
                Spacer(Modifier.height(16.dp))
                VerifierCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(stringResource(R.string.privacy_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = VerifierPrimary,
                        )
                        Text(stringResource(R.string.privacy_body),
                            style = MaterialTheme.typography.bodySmall, color = VerifierSecondary)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement   = Arrangement.spacedBy(8.dp),
                        ) {
                            PrivacyChip(stringResource(R.string.chip_no_id),    Icons.Filled.CheckCircle, highlighted = true)
                            PrivacyChip(stringResource(R.string.chip_no_cert),  Icons.Filled.Lock, highlighted = true)
                            PrivacyChip(stringResource(R.string.chip_proof_only), Icons.Filled.PhoneAndroid, highlighted = true)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { showLearnMore = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.progress_learn_tech),
                        style = MaterialTheme.typography.bodyMedium,
                        color = VerifierAccent,
                    )
                }
            }
        }
        if (showLearnMore) {
            LearnMoreSheet(onDismiss = { showLearnMore = false })
        }
    }
}

private fun progressItemState(status: ProofViewModel.StepStatus): ProgressItemState = when (status) {
    is ProofViewModel.StepStatus.Success -> ProgressItemState.Done
    is ProofViewModel.StepStatus.Running -> ProgressItemState.Active
    else                                 -> ProgressItemState.Pending
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen 6 – Success
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SuccessScreen(vm: ProofViewModel) {
    var showTechInfo by remember { mutableStateOf(false) }

    ScreenScaffold {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint     = VerifierAccent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .size(56.dp)
                .align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.success_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold, color = VerifierPrimary)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.success_body),
            style = MaterialTheme.typography.bodyLarge, color = VerifierSecondary)
        Spacer(Modifier.height(24.dp))

        VerifierCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.success_status),
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = Color(0xFF34C759),
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                Icon(Icons.Filled.CheckCircle, null,
                    tint = Color(0xFF34C759), modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        VerifierCard {
            InfoRow(stringResource(R.string.success_label_result),     stringResource(R.string.success_value_result))
            VerifierDividerLine()
            InfoRow(stringResource(R.string.success_label_badge),      stringResource(R.string.value_badge_citizen))
            VerifierDividerLine()
            InfoRow(stringResource(R.string.label_credential),         stringResource(R.string.value_credential))
            vm.totalVerificationSeconds?.let { t ->
                VerifierDividerLine()
                InfoRow(
                    stringResource(R.string.success_label_total_time),
                    stringResource(R.string.success_total_time_fmt, t),
                )
            }
            (vm.proveStatus as? ProofViewModel.StepStatus.Success)?.message?.let { detail ->
                VerifierDividerLine()
                InfoRow(stringResource(R.string.success_label_eligibility), detail)
            }
            vm.verifyMilliseconds?.let { ms ->
                VerifierDividerLine()
                InfoRow(stringResource(R.string.success_label_submit_time), "$ms ms")
            }
        }

        Spacer(Modifier.height(12.dp))

        // Technical details disclosure
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(VerifierSurface)
                .clickable { showTechInfo = !showTechInfo }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.success_label_tech_details),
                    style = MaterialTheme.typography.bodyMedium, color = VerifierSecondary)
                Icon(
                    if (showTechInfo) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = VerifierSecondary, modifier = Modifier.size(16.dp),
                )
            }
            AnimatedVisibility(visible = showTechInfo) {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (vm.proveStatus as? ProofViewModel.StepStatus.Success)?.message?.let {
                        TechRow(stringResource(R.string.success_tech_prove), it)
                    }
                    (vm.verifyStatus as? ProofViewModel.StepStatus.Success)?.message?.let {
                        TechRow(stringResource(R.string.success_tech_verify), it)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        VerifierPrimaryButton(text = stringResource(R.string.btn_done), onClick = { vm.reset() })
    }
}

@Composable
private fun TechRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = VerifierSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = VerifierPrimary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen 7 – Error
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorScreen(vm: ProofViewModel, message: String) {
    val clipboard       = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    val errorTitle = when {
        "MOICA" in message || "mobilemoica" in message -> stringResource(R.string.error_moica_title)
        "cancel" in message || "取消" in message       -> stringResource(R.string.error_cancel_title)
        "nullifier already registered" in message      -> stringResource(R.string.error_nullifier_title)
        "challenge expired" in message                 -> stringResource(R.string.error_challenge_title)
        else                                           -> stringResource(R.string.error_screen_title)
    }
    val explanation = when {
        "MOICA" in message || "mobilemoica" in message -> stringResource(R.string.error_moica_body)
        "cancel" in message || "取消" in message       -> stringResource(R.string.error_cancel_body)
        "nullifier already registered" in message      -> stringResource(R.string.error_nullifier_body)
        "challenge expired" in message                 -> stringResource(R.string.error_challenge_body)
        else                                           -> stringResource(R.string.error_default_body)
    }

    ScreenScaffold {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint     = VerifierError,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .size(48.dp)
                .align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.error_screen_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold, color = VerifierPrimary)
        Spacer(Modifier.height(24.dp))

        VerifierCard {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(errorTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VerifierPrimary,
                )
                Text(explanation,
                    style = MaterialTheme.typography.bodySmall, color = VerifierSecondary)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Error details disclosure
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(VerifierSurface)
                .clickable { showDetails = !showDetails }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.label_error_details),
                    style = MaterialTheme.typography.bodySmall, color = VerifierSecondary)
                Icon(
                    if (showDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = VerifierSecondary, modifier = Modifier.size(16.dp),
                )
            }
            AnimatedVisibility(visible = showDetails) {
                Text(
                    message,
                    modifier = Modifier.padding(top = 6.dp),
                    style    = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color    = VerifierSecondary,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        VerifierPrimaryButton(text = stringResource(R.string.btn_try_again), onClick = { vm.resetToReadiness() })
        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick  = {
                clipboard.setText(AnnotatedString(message))
                copied = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (copied) stringResource(R.string.status_copied)
                else        stringResource(R.string.btn_copy_error),
                style = MaterialTheme.typography.bodyMedium,
                color = VerifierAccent,
            )
        }
        TextButton(
            onClick  = { vm.reset() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.btn_back_to_start),
                style = MaterialTheme.typography.bodyMedium, color = VerifierSecondary)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared UI components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScreenScaffold(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VerifierBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(vertical = 56.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
private fun VerifierCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VerifierSurface),
    ) { content() }
}

@Composable
private fun VerifierDividerLine() {
    HorizontalDivider(color = VerifierDivider, thickness = 0.5.dp)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = VerifierSecondary,
            modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = VerifierPrimary,
            textAlign = TextAlign.End, modifier = Modifier.weight(0.6f))
    }
}

@Composable
private fun ReadinessRow(label: String, detail: String, status: ReadinessStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = VerifierPrimary)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = when (status) {
                    ReadinessStatus.Ready    -> Color(0xFF34C759)
                    ReadinessStatus.NotReady -> VerifierError
                    ReadinessStatus.Loading  -> VerifierSecondary
                },
            )
        }
        when (status) {
            ReadinessStatus.Ready ->
                Icon(Icons.Filled.CheckCircle, null,
                    tint = Color(0xFF34C759), modifier = Modifier.size(20.dp))
            ReadinessStatus.NotReady ->
                Icon(Icons.Filled.Close, null,
                    tint = VerifierError, modifier = Modifier.size(20.dp))
            ReadinessStatus.Loading ->
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp), color = VerifierAccent, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun ProgressRow(label: String, state: ProgressItemState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (state) {
            ProgressItemState.Done ->
                Icon(Icons.Filled.CheckCircle, null,
                    tint = Color(0xFF34C759), modifier = Modifier.size(20.dp))
            ProgressItemState.Active ->
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp), color = VerifierAccent, strokeWidth = 2.dp)
            ProgressItemState.Pending ->
                Icon(Icons.Filled.Check, null,
                    tint = VerifierDivider, modifier = Modifier.size(20.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (state == ProgressItemState.Pending) VerifierSecondary else VerifierPrimary,
        )
    }
}

@Composable
private fun PrivacyChip(label: String, icon: ImageVector, highlighted: Boolean = false) {
    val bg = if (highlighted) VerifierAccent else VerifierDivider
    val fg = if (highlighted) VerifierBackground else VerifierSecondary
    Surface(shape = RoundedCornerShape(50), color = bg) {
        Row(
            modifier = Modifier.padding(horizontal = if (highlighted) 10.dp else 8.dp, vertical = if (highlighted) 6.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(10.dp))
            Text(
                label,
                style = if (highlighted) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                color = fg,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LearnMoreSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VerifierBackground,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.learn_more_nav_title),
                style = MaterialTheme.typography.titleMedium,
                color = VerifierPrimary,
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.learn_more_done), color = VerifierAccent)
            }
        }
        HorizontalDivider(color = VerifierDivider, thickness = 0.5.dp)
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(R.string.learn_more_body),
                style = MaterialTheme.typography.bodyMedium,
                color = VerifierSecondary,
            )
            LearnMoreSection(
                title = stringResource(R.string.learn_more_s1_title),
                body  = stringResource(R.string.learn_more_s1_body),
            )
            LearnMoreSection(
                title = stringResource(R.string.learn_more_s2_title),
                body  = stringResource(R.string.learn_more_s2_body),
            )
            LearnMoreSection(
                title = stringResource(R.string.learn_more_s3_title),
                body  = stringResource(R.string.learn_more_s3_body),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LearnMoreSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color      = VerifierPrimary,
        )
        Text(body, style = MaterialTheme.typography.bodySmall, color = VerifierSecondary)
    }
}

@Composable
private fun VerifierPrimaryButton(
    text:     String,
    onClick:  () -> Unit,
    enabled:  Boolean  = true,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick  = onClick,
        enabled  = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape  = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor         = VerifierAccent,
            contentColor           = VerifierBackground,
            disabledContainerColor = VerifierDivider,
            disabledContentColor   = VerifierSecondary,
        ),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}
