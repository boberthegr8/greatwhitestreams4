package com.gwstreams.tv.ui.live

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import com.gwstreams.tv.data.CrashReporter
import com.gwstreams.tv.data.Updater
import com.gwstreams.tv.ui.TvViewModel
import com.gwstreams.tv.ui.theme.*

private val DialogFocusDelayMs = 75L

enum class Provider(val displayName: String, val defaultHost: String, val showHostField: Boolean) {
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
    var customHost by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var saveLogin by remember { mutableStateOf(true) }
    var showProviderDialog by remember { mutableStateOf(false) }
    var restoredSavedProvider by remember { mutableStateOf(false) }

    val providerRequester = remember { FocusRequester() }
    val hostRequester = remember { FocusRequester() }
    val userRequester = remember { FocusRequester() }
    val passRequester = remember { FocusRequester() }
    val saveRequester = remember { FocusRequester() }
    val signInRequester = remember { FocusRequester() }

    val appUpdate = state.appUpdate
    val visibleUpdate = appUpdate.availableUpdate?.takeIf { appUpdate.isDialogVisible }
    val finalHost = if (selectedProvider.showHostField) customHost.trim() else selectedProvider.defaultHost
    val canSubmit = finalHost.isNotBlank() && user.isNotBlank() && pass.isNotBlank() && !state.loading

    fun submitLogin() {
        if (!canSubmit) return
        vm.login(finalHost, user.trim(), pass, saveLogin) { ok, _ -> if (ok) onLoggedIn() }
    }

    LaunchedEffect(Unit) {
        vm.checkForAppUpdate()
    }

    // Prefill from saved credentials once they load.
    LaunchedEffect(state.savedHost, state.savedUser, state.savedPass) {
        if (!restoredSavedProvider && state.savedHost.isNotBlank()) {
            val savedProvider = Provider.values().firstOrNull { !it.showHostField && it.defaultHost.equals(state.savedHost, ignoreCase = true) }
            if (savedProvider != null) {
                selectedProvider = savedProvider
                customHost = ""
            } else {
                selectedProvider = Provider.CUSTOM
                customHost = state.savedHost
            }
            restoredSavedProvider = true
        }
        if (user.isEmpty() && state.savedUser.isNotEmpty()) user = state.savedUser
        if (pass.isEmpty() && state.savedPass.isNotEmpty()) pass = state.savedPass
    }

    LaunchedEffect(selectedProvider.showHostField, state.autoLoggingIn, visibleUpdate != null) {
        if (!state.autoLoggingIn && visibleUpdate == null) {
            when {
                selectedProvider.showHostField -> hostRequester.requestFocus()
                else -> providerRequester.requestFocus()
            }
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
                        .focusProperties {
                            down = if (selectedProvider.showHostField) hostRequester else userRequester
                        }
                ) {
                    Text("Service: ${selectedProvider.displayName}", style = TvType.titleMedium)
                }
                Spacer(Modifier.height(16.dp))

                if (selectedProvider.showHostField) {
                    TvField(
                        value = customHost,
                        onChange = { customHost = it },
                        label = "Server host (https://host:port)",
                        focusRequester = hostRequester,
                        upRequester = providerRequester,
                        downRequester = userRequester,
                        onSubmit = { userRequester.requestFocus() }
                    )
                    Spacer(Modifier.height(16.dp))
                }

                TvField(
                    value = user,
                    onChange = { user = it },
                    label = "Username",
                    focusRequester = userRequester,
                    upRequester = if (selectedProvider.showHostField) hostRequester else providerRequester,
                    downRequester = passRequester,
                    onSubmit = { passRequester.requestFocus() }
                )
                Spacer(Modifier.height(16.dp))
                TvField(
                    value = pass,
                    onChange = { pass = it },
                    label = "Password",
                    isPassword = true,
                    imeAction = ImeAction.Done,
                    focusRequester = passRequester,
                    upRequester = userRequester,
                    downRequester = saveRequester,
                    onSubmit = {
                        if (canSubmit) submitLogin() else signInRequester.requestFocus()
                    }
                )

                Spacer(Modifier.height(16.dp))
                LoginToggleRow(
                    checked = saveLogin,
                    onToggle = { saveLogin = !saveLogin },
                    focusRequester = saveRequester,
                    upRequester = passRequester,
                    downRequester = signInRequester
                )

                state.error?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, color = Coral, style = TvType.bodyMedium)
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
                        .focusProperties { up = saveRequester }
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
                    if (!provider.showHostField) customHost = ""
                    showProviderDialog = false
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
    val providerRequesters = remember { Provider.values().associateWith { FocusRequester() } }
    val firstProvider = remember { Provider.values().first() }

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
                Provider.values().forEachIndexed { index, provider ->
                    val upProvider = Provider.values().getOrNull(index - 1)
                    val downProvider = Provider.values().getOrNull(index + 1)
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
                Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
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
                        ) { Text("Skip") }
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
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Uri,
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
