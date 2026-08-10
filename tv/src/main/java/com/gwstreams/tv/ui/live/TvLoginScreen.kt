package com.gwstreams.tv.ui.live

import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gwstreams.tv.data.CrashReporter
import com.gwstreams.tv.data.Updater
import com.gwstreams.tv.ui.TvViewModel
import com.gwstreams.tv.ui.theme.Aqua
import com.gwstreams.tv.ui.theme.Coral
import com.gwstreams.tv.ui.theme.Midnight
import com.gwstreams.tv.ui.theme.Surface1
import com.gwstreams.tv.ui.theme.SurfaceHi
import com.gwstreams.tv.ui.theme.TextHi
import com.gwstreams.tv.ui.theme.TextLow
import com.gwstreams.tv.ui.theme.TextMid
import com.gwstreams.tv.ui.theme.TvType

private val DialogFocusDelayMs = 75L

internal enum class Provider(val displayName: String, val defaultHost: String, val showHostField: Boolean) {
    CCTV("CCTV", "http://cvapp.tv:8000", false),
    RUBY("Ruby", "http://ruby.iptv:80", false),
    HUSH("HUSH", "http://hush.iptv:80", false),
    GO("GO", "http://go.iptv:80", false),
    BOBERT("Bobert", "https://twistedapex.com", false),
    CUSTOM("Custom", "", true)
}

@Composable
fun TvLoginScreen(vm: TvViewModel, onLoggedIn: () -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    var selectedProvider by remember { mutableStateOf(Provider.BOBERT) }
    var hostInput by remember { mutableStateOf(Provider.BOBERT.defaultHost) }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var saveLogin by remember { mutableStateOf(true) }
    var showProviderDialog by remember { mutableStateOf(false) }
    var showPhoneSetupDialog by remember { mutableStateOf(false) }
    var restoredSavedProvider by remember { mutableStateOf(false) }
    var phoneSetupStatus by remember { mutableStateOf<String?>(null) }

    val providerRequester = remember { FocusRequester() }
    val hostRequester = remember { FocusRequester() }
    val userRequester = remember { FocusRequester() }
    val passRequester = remember { FocusRequester() }
    val saveRequester = remember { FocusRequester() }
    val phoneSetupRequester = remember { FocusRequester() }
    val signInRequester = remember { FocusRequester() }

    val appUpdate = state.appUpdate
    val visibleUpdate = appUpdate.availableUpdate?.takeIf { appUpdate.isDialogVisible }
    val finalHost = hostInput.trim()
    val canSubmit = finalHost.isNotBlank() && user.isNotBlank() && pass.isNotBlank() && !state.loading

    fun submitLogin() {
        if (!canSubmit) return
        phoneSetupStatus = null
        vm.login(
            host = finalHost,
            user = user.trim(),
            pass = pass,
            remember = saveLogin,
            provider = selectedProvider.name
        ) { ok, _ -> if (ok) onLoggedIn() }
    }

    LaunchedEffect(Unit) {
        vm.checkForAppUpdate()
    }

    LaunchedEffect(state.savedHost, state.savedUser, state.savedPass, state.savedProvider) {
        if (!restoredSavedProvider && state.savedHost.isNotBlank()) {
            val providerFromState = state.savedProvider
                ?.let { savedName -> runCatching { Provider.valueOf(savedName) }.getOrNull() }
            val providerFromHost = Provider.entries.firstOrNull {
                !it.showHostField && it.defaultHost.equals(state.savedHost, ignoreCase = true)
            }
            val restoredProvider = providerFromState ?: providerFromHost ?: Provider.CUSTOM
            selectedProvider = restoredProvider
            hostInput = state.savedHost
            restoredSavedProvider = true
        }
        if (user.isEmpty() && state.savedUser.isNotBlank()) user = state.savedUser
        if (pass.isEmpty() && state.savedPass.isNotBlank()) pass = state.savedPass
    }

    LaunchedEffect(state.autoLoggingIn, visibleUpdate != null) {
        if (!state.autoLoggingIn && visibleUpdate == null) {
            providerRequester.requestFocus()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Midnight),
        contentAlignment = Alignment.Center
    ) {
        if (state.autoLoggingIn) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                state.lastCrashSummary?.let { summary ->
                    CrashNoticeCard(summary = summary, onClear = vm::clearCrashReport)
                    Spacer(Modifier.height(20.dp))
                }
                Text("Great White Streams", style = TvType.displayMedium, color = TextHi)
                Spacer(Modifier.height(20.dp))
                CircularProgressIndicator(color = Aqua)
                Spacer(Modifier.height(16.dp))
                Text("Signing in…", style = TvType.bodyLarge, color = TextMid)
                if (appUpdate.checking) {
                    Spacer(Modifier.height(8.dp))
                    Text("Checking for app updates…", style = TvType.bodyMedium, color = TextLow)
                }
            }
        } else {
            Column(
                Modifier.width(560.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                state.lastCrashSummary?.let { summary ->
                    CrashNoticeCard(
                        summary = summary,
                        onClear = vm::clearCrashReport,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(20.dp))
                }
                Text("Great White Streams", style = TvType.displayMedium, color = TextHi)
                Spacer(Modifier.height(8.dp))
                Text("Sign in to your service", style = TvType.bodyLarge, color = TextMid)
                if (appUpdate.checking) {
                    Spacer(Modifier.height(8.dp))
                    Text("Checking for app updates…", style = TvType.bodyMedium, color = TextLow)
                }
                Spacer(Modifier.height(28.dp))

                Button(
                    onClick = { showProviderDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Surface1, contentColor = TextHi),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .focusRequester(providerRequester)
                        .focusProperties { down = hostRequester }
                ) {
                    Text("Service preset: ${selectedProvider.displayName}", style = TvType.titleMedium)
                }
                Spacer(Modifier.height(16.dp))

                TvField(
                    value = hostInput,
                    onChange = { hostInput = it },
                    label = "DNS / server URL (editable for every provider)",
                    keyboardType = KeyboardType.Uri,
                    focusRequester = hostRequester,
                    upRequester = providerRequester,
                    downRequester = userRequester,
                    onSubmit = { userRequester.requestFocus() }
                )
                Spacer(Modifier.height(16.dp))

                TvField(
                    value = user,
                    onChange = { user = it },
                    label = "Username",
                    keyboardType = KeyboardType.Text,
                    focusRequester = userRequester,
                    upRequester = hostRequester,
                    downRequester = passRequester,
                    onSubmit = { passRequester.requestFocus() }
                )
                Spacer(Modifier.height(16.dp))

                TvField(
                    value = pass,
                    onChange = { pass = it },
                    label = "Password",
                    isPassword = true,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    focusRequester = passRequester,
                    upRequester = userRequester,
                    downRequester = saveRequester,
                    onSubmit = {
                        if (canSubmit) submitLogin() else saveRequester.requestFocus()
                    }
                )

                Spacer(Modifier.height(16.dp))
                LoginToggleRow(
                    checked = saveLogin,
                    onToggle = { saveLogin = !saveLogin },
                    focusRequester = saveRequester,
                    upRequester = passRequester,
                    downRequester = phoneSetupRequester
                )

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        phoneSetupStatus = null
                        showPhoneSetupDialog = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Surface1, contentColor = TextHi),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .focusRequester(phoneSetupRequester)
                        .focusProperties {
                            up = saveRequester
                            down = signInRequester
                        }
                ) {
                    Text("Phone / QR setup", style = TvType.titleMedium)
                }

                state.error?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, color = Coral, style = TvType.bodyMedium)
                }
                phoneSetupStatus?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Aqua, style = TvType.bodyMedium)
                }

                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = ::submitLogin,
                    enabled = !state.loading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Aqua, contentColor = Midnight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .focusRequester(signInRequester)
                        .focusProperties { up = phoneSetupRequester }
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(color = Midnight, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Sign in", style = TvType.labelLarge)
                    }
                }
            }
        }

        if (showProviderDialog) {
            ProviderDialog(
                selectedProvider = selectedProvider,
                onDismiss = { showProviderDialog = false },
                onSelect = { provider ->
                    selectedProvider = provider
                    hostInput = when {
                        provider.showHostField -> hostInput
                        else -> provider.defaultHost
                    }
                    showProviderDialog = false
                    hostRequester.requestFocus()
                }
            )
        }

        if (showPhoneSetupDialog) {
            PhoneSetupDialog(
                selectedProvider = selectedProvider,
                host = hostInput,
                user = user,
                pass = pass,
                onDismiss = { showPhoneSetupDialog = false },
                onApplySubmission = { submission ->
                    val providerFromSubmission = submission.providerName
                        ?.let { submittedName -> runCatching { Provider.valueOf(submittedName) }.getOrNull() }
                    val providerFromHost = Provider.entries.firstOrNull {
                        !it.showHostField && it.defaultHost.equals(submission.host, ignoreCase = true)
                    }
                    selectedProvider = providerFromSubmission ?: providerFromHost ?: Provider.CUSTOM
                    hostInput = submission.host
                    user = submission.user
                    pass = submission.pass
                    saveLogin = submission.saveLogin
                    phoneSetupStatus = if (submission.autoSubmit) {
                        "Phone submitted credentials. Signing in…"
                    } else {
                        "Phone submitted credentials. Review on TV, then press Sign in."
                    }
                    showPhoneSetupDialog = false
                    if (submission.autoSubmit) {
                        phoneSetupStatus = null
                        vm.login(
                            host = submission.host,
                            user = submission.user.trim(),
                            pass = submission.pass,
                            remember = submission.saveLogin,
                            provider = selectedProvider.name
                        ) { ok, _ -> if (ok) onLoggedIn() }
                    } else {
                        signInRequester.requestFocus()
                    }
                }
            )
        }

        if (visibleUpdate != null) {
            UpdateDialog(
                info = visibleUpdate,
                downloading = appUpdate.inProgress,
                progress = appUpdate.downloadPercent ?: 0,
                status = appUpdate.statusMessage,
                error = appUpdate.errorMessage,
                installPermissionRequired = appUpdate.installPermissionRequired,
                onSkip = { vm.dismissUpdatePrompt() },
                onUpdate = { vm.beginAppUpdate(context) }
            )
        }
    }
}

@Composable
private fun PhoneSetupDialog(
    selectedProvider: Provider,
    host: String,
    user: String,
    pass: String,
    onDismiss: () -> Unit,
    onApplySubmission: (PhoneSetupSubmission) -> Unit
) {
    var session by remember { mutableStateOf<PhoneSetupSession?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var submissionReceived by remember { mutableStateOf(false) }

    DisposableEffect(selectedProvider, host, user, pass) {
        val server = PhoneSetupServer(
            providers = Provider.entries,
            selectedProvider = selectedProvider,
            initialHost = host,
            onSubmission = {
                submissionReceived = true
                onApplySubmission(it)
            }
        )
        server.start()
            .onSuccess {
                session = it
                errorMessage = null
            }
            .onFailure {
                session = null
                errorMessage = it.message ?: "Couldn't start the phone setup server."
            }
        onDispose {
            server.stop()
        }
    }

    val qrBitmap = remember(session?.url) {
        session?.url?.let { generateQrCodeBitmap(it) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceHi,
            modifier = Modifier.width(920.dp)
        ) {
            Column(
                Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Phone / QR setup", style = TvType.headlineMedium, color = TextHi)
                Spacer(Modifier.height(10.dp))
                Text(
                    "While this window stays open, the TV hosts a local setup page on your home network. Scan the QR code or type the URL on your phone, then enter the DNS/server URL, username, and password. Existing saved credentials are not sent to the phone page.",
                    style = TvType.bodyMedium,
                    color = TextMid
                )
                Spacer(Modifier.height(20.dp))

                if (errorMessage != null) {
                    Text(errorMessage!!, color = Coral, style = TvType.bodyMedium)
                } else if (session == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = Aqua, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Starting local phone setup page…", color = TextMid, style = TvType.bodyMedium)
                    }
                } else {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        qrBitmap?.let {
                            Surface(
                                color = androidx.compose.ui.graphics.Color.White,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "QR code for phone setup",
                                    modifier = Modifier
                                        .size(320.dp)
                                        .padding(16.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("URL for your phone", style = TvType.titleMedium, color = TextHi)
                            Spacer(Modifier.height(8.dp))
                            Text(session!!.url, style = TvType.bodyLarge, color = Aqua)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "TV LAN IP: ${session!!.hostAddress}:${session!!.port}",
                                style = TvType.bodyMedium,
                                color = TextMid
                            )
                            Spacer(Modifier.height(18.dp))
                            Text("How it works", style = TvType.titleMedium, color = TextHi)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "1. Connect your phone to the same Wi‑Fi/LAN as the TV.\n" +
                                    "2. Open the QR link.\n" +
                                    "3. Enter provider preset, DNS/server URL, username, and password.\n" +
                                    "4. Leave 'Sign in on TV after submit' checked to start login immediately, or uncheck it to only fill the TV form.",
                                style = TvType.bodyMedium,
                                color = TextMid
                            )
                            if (submissionReceived) {
                                Spacer(Modifier.height(16.dp))
                                Text("Credentials received from phone.", color = Aqua, style = TvType.bodyMedium)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Surface1, contentColor = TextHi),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashNoticeCard(
    summary: CrashReporter.CrashSummary,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceHi,
        modifier = modifier
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Recovered from an app crash", style = TvType.titleLarge, color = Coral)
            Spacer(Modifier.height(8.dp))
            Text(summary.displayMessage, style = TvType.bodyMedium, color = TextHi)
            Spacer(Modifier.height(6.dp))
            Text(
                "${summary.timestamp} • ${summary.threadName} • ${summary.appVersion}",
                style = TvType.labelSmall,
                color = TextLow
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "A redacted report is saved locally on this device only.",
                style = TvType.labelSmall,
                color = TextMid
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onClear,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Surface1, contentColor = TextHi)
            ) {
                Text("Clear crash notice")
            }
        }
    }
}

@Composable
private fun ProviderDialog(
    selectedProvider: Provider,
    onDismiss: () -> Unit,
    onSelect: (Provider) -> Unit
) {
    val providerRequesters = remember { Provider.entries.associateWith { FocusRequester() } }
    val firstProvider = remember { Provider.entries.first() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(DialogFocusDelayMs)
        providerRequesters[selectedProvider]?.requestFocus() ?: providerRequesters[firstProvider]?.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceHi,
            modifier = Modifier.width(400.dp)
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Select Provider", style = TvType.headlineMedium, color = TextHi)
                Spacer(Modifier.height(16.dp))
                Provider.entries.forEachIndexed { index, provider ->
                    val upProvider = Provider.entries.getOrNull(index - 1)
                    val downProvider = Provider.entries.getOrNull(index + 1)
                    Button(
                        onClick = { onSelect(provider) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedProvider == provider) Aqua else Surface1,
                            contentColor = if (selectedProvider == provider) Midnight else TextHi
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .height(48.dp)
                            .focusRequester(providerRequesters.getValue(provider))
                            .focusProperties {
                                upProvider?.let { up = providerRequesters.getValue(it) }
                                downProvider?.let { down = providerRequesters.getValue(it) }
                            }
                    ) {
                        Text(provider.displayName)
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateDialog(
    info: Updater.UpdateInfo,
    downloading: Boolean,
    progress: Int,
    status: String?,
    error: String?,
    installPermissionRequired: Boolean,
    onSkip: () -> Unit,
    onUpdate: () -> Unit
) {
    val skipRequester = remember { FocusRequester() }
    val updateRequester = remember { FocusRequester() }

    LaunchedEffect(info.versionCode, downloading, info.mandatory) {
        kotlinx.coroutines.delay(DialogFocusDelayMs)
        updateRequester.requestFocus()
    }

    Dialog(onDismissRequest = { if (!info.mandatory && !downloading) onSkip() }) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SurfaceHi,
            modifier = Modifier.width(560.dp)
        ) {
            Column(
                Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Text("Update available", style = TvType.headlineMedium, color = TextHi)
                Spacer(Modifier.height(8.dp))
                Text("GWS StartupShow v${info.versionName}", style = TvType.titleMedium, color = Aqua)
                Spacer(Modifier.height(12.dp))
                Text(info.releaseNotes, style = TvType.bodyMedium, color = TextMid)
                if (info.mandatory) {
                    Spacer(Modifier.height(10.dp))
                    Text("Mandatory update — this one cannot be skipped.", style = TvType.bodyMedium, color = Coral)
                }
                if (installPermissionRequired) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Android blocked the installer. Enable 'Install unknown apps' for this app, come back here, then press Update again.",
                        style = TvType.bodyMedium,
                        color = Coral
                    )
                }
                status?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = TvType.bodyMedium, color = TextMid)
                }
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = TvType.bodyMedium, color = Coral)
                }
                if (downloading) {
                    Spacer(Modifier.height(18.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        color = Aqua,
                        trackColor = Surface1,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Downloading… $progress%", style = TvType.bodyMedium, color = TextMid)
                }
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (!info.mandatory) {
                        Button(
                            onClick = onSkip,
                            enabled = !downloading,
                            colors = ButtonDefaults.buttonColors(containerColor = Surface1, contentColor = TextHi),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .focusRequester(skipRequester)
                                .focusProperties { right = updateRequester }
                        ) {
                            Text("Skip")
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    Button(
                        onClick = onUpdate,
                        enabled = !downloading,
                        colors = ButtonDefaults.buttonColors(containerColor = Aqua, contentColor = Midnight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .focusRequester(updateRequester)
                            .focusProperties {
                                if (!info.mandatory) left = skipRequester
                            }
                    ) {
                        Text(
                            if (installPermissionRequired) "Update again"
                            else if (downloading) "Working…"
                            else "Update"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginToggleRow(
    checked: Boolean,
    onToggle: () -> Unit,
    focusRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Surface(
        onClick = onToggle,
        interactionSource = interaction,
        color = if (focused) SurfaceHi else Surface1,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusProperties {
                up = upRequester
                down = downRequester
            }
            .then(if (focused) Modifier.border(2.dp, Aqua, RoundedCornerShape(10.dp)) else Modifier)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Save login and stay signed in", style = TvType.titleMedium, color = TextHi, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = null, thumbContent = null)
        }
    }
}

@Composable
private fun TvField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    focusRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
    onSubmit: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { onSubmit() },
            onDone = { onSubmit() }
        ),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Aqua,
            unfocusedBorderColor = SurfaceHi,
            focusedContainerColor = Surface1,
            unfocusedContainerColor = Surface1,
            focusedLabelColor = Aqua,
            unfocusedLabelColor = TextLow,
            cursorColor = Aqua,
            focusedTextColor = TextHi,
            unfocusedTextColor = TextHi
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusProperties {
                up = upRequester
                down = downRequester
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> {
                        downRequester.requestFocus()
                        true
                    }
                    Key.DirectionUp -> {
                        upRequester.requestFocus()
                        true
                    }
                    Key.DirectionCenter,
                    Key.Enter,
                    Key.NumPadEnter -> {
                        if (imeAction == ImeAction.Done) {
                            onSubmit()
                            true
                        } else {
                            false
                        }
                    }
                    Key.Back -> {
                        focusManager.clearFocus(force = true)
                        false
                    }
                    else -> false
                }
            }
    )
}
