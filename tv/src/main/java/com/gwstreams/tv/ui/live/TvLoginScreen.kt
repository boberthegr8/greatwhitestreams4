package com.gwstreams.tv.ui.live

import androidx.compose.foundation.background
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.gwstreams.tv.BuildConfig
import com.gwstreams.tv.data.Updater
import com.gwstreams.tv.ui.TvViewModel
import com.gwstreams.tv.ui.theme.*
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()

    var selectedProvider by remember { mutableStateOf(Provider.BOBERT) }
    var customHost by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var saveLogin by remember { mutableStateOf(true) }
    var showProviderDialog by remember { mutableStateOf(false) }

    var updateInfo by remember { mutableStateOf<Updater.UpdateInfo?>(null) }
    var checkingUpdate by remember { mutableStateOf(true) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var dismissedOptionalUpdate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        checkingUpdate = true
        updateInfo = Updater.checkForUpdate(BuildConfig.VERSION_CODE)
        checkingUpdate = false
    }

    // Prefill from saved credentials once they load.
    LaunchedEffect(state.savedHost, state.savedUser, state.savedPass) {
        if (customHost.isEmpty() && state.savedHost.isNotEmpty()) customHost = state.savedHost
        if (user.isEmpty() && state.savedUser.isNotEmpty()) user = state.savedUser
        if (pass.isEmpty() && state.savedPass.isNotEmpty()) pass = state.savedPass
    }

    // Auto-fill Bobert credentials
    LaunchedEffect(selectedProvider) {
        if (selectedProvider == Provider.BOBERT) {
            user = "Saralam1028"
            pass = "NBkuyhVoRT"
        } else {
            user = ""
            pass = ""
        }
    }

    // Ensure we start with Bobert pre-filled if it's the initial default
    LaunchedEffect(Unit) {
        if (selectedProvider == Provider.BOBERT && user.isEmpty()) {
            user = "Saralam1028"
            pass = "NBkuyhVoRT"
        }
    }

    Box(
        Modifier.fillMaxSize().background(Midnight),
        contentAlignment = Alignment.Center
    ) {
        if (state.autoLoggingIn) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Great White Streams", style = TvType.displayMedium, color = TextHi)
                Spacer(Modifier.height(20.dp))
                CircularProgressIndicator(color = Aqua)
                Spacer(Modifier.height(16.dp))
                Text("Signing in\u2026", style = TvType.bodyLarge, color = TextMid)
                if (checkingUpdate) {
                    Spacer(Modifier.height(8.dp))
                    Text("Checking for app updates…", style = TvType.bodyMedium, color = TextLow)
                }
            }
        } else {
            Column(
                Modifier.width(560.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Great White Streams", style = TvType.displayMedium, color = TextHi)
                Spacer(Modifier.height(8.dp))
                Text("Sign in to your service", style = TvType.bodyLarge, color = TextMid)
                if (checkingUpdate) {
                    Spacer(Modifier.height(8.dp))
                    Text("Checking for app updates…", style = TvType.bodyMedium, color = TextLow)
                }
                Spacer(Modifier.height(28.dp))

                Button(
                    onClick = { showProviderDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Surface1, contentColor = TextHi),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("Service: ${selectedProvider.displayName}", style = TvType.titleMedium)
                }
                Spacer(Modifier.height(16.dp))

                if (selectedProvider.showHostField) {
                    TvField(customHost, { customHost = it }, "Server host (https://host:port)")
                    Spacer(Modifier.height(16.dp))
                }

                TvField(user, { user = it }, "Username")
                Spacer(Modifier.height(16.dp))
                TvField(pass, { pass = it }, "Password", isPassword = true, imeAction = ImeAction.Done)

                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(checked = saveLogin, onCheckedChange = { saveLogin = it })
                    Spacer(Modifier.width(12.dp))
                    Text("Save login and stay signed in", style = TvType.titleMedium, color = TextHi)
                }

                state.error?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, color = Coral, style = TvType.bodyMedium)
                }

                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = {
                        val finalHost = if (selectedProvider.showHostField) customHost else selectedProvider.defaultHost
                        vm.login(finalHost, user, pass, saveLogin) { ok, _ -> if (ok) onLoggedIn() }
                    },
                    enabled = !state.loading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Aqua, contentColor = Midnight),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
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
            Dialog(onDismissRequest = { showProviderDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceHi,
                    modifier = Modifier.width(400.dp)
                ) {
                    Column(Modifier.padding(24.dp)) {
                        Text("Select Provider", style = TvType.headlineMedium, color = TextHi)
                        Spacer(Modifier.height(16.dp))
                        Provider.values().forEach { provider ->
                            Button(
                                onClick = {
                                    selectedProvider = provider
                                    showProviderDialog = false
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedProvider == provider) Aqua else Surface1,
                                    contentColor = if (selectedProvider == provider) Midnight else TextHi
                                ),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(48.dp)
                            ) {
                                Text(provider.displayName)
                            }
                        }
                    }
                }
            }
        }

        val visibleUpdate = updateInfo?.takeUnless { dismissedOptionalUpdate && !it.mandatory }
        if (visibleUpdate != null) {
            UpdateDialog(
                info = visibleUpdate,
                downloading = downloadingUpdate,
                progress = downloadProgress,
                error = updateError,
                onSkip = {
                    if (!visibleUpdate.mandatory) dismissedOptionalUpdate = true
                },
                onUpdate = {
                    updateError = null
                    if (!Updater.canInstallPackages(context)) {
                        updateError = "Turn on 'Install unknown apps' for GWS StartupShow, then come back and press Update again."
                        Updater.openUnknownAppSettings(context)
                    } else {
                        downloadingUpdate = true
                        downloadProgress = 0
                        scope.launch {
                            val result = Updater.downloadAndInstall(context, visibleUpdate) { pct ->
                                downloadProgress = pct
                            }
                            result.exceptionOrNull()?.let { error ->
                                updateError = error.message ?: "Update download failed."
                            }
                            downloadingUpdate = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun UpdateDialog(
    info: Updater.UpdateInfo,
    downloading: Boolean,
    progress: Int,
    error: String?,
    onSkip: () -> Unit,
    onUpdate: () -> Unit
) {
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
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Skip") }
                        Spacer(Modifier.width(12.dp))
                    }
                    Button(
                        onClick = onUpdate,
                        enabled = !downloading,
                        colors = ButtonDefaults.buttonColors(containerColor = Aqua, contentColor = Midnight),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(if (downloading) "Working…" else "Update") }
                }
            }
        }
    }
}

@Composable
private fun TvField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Next
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
            onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
            onDone = { focusManager.clearFocus() }
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
        modifier = Modifier.fillMaxWidth().onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown) {
                when (it.key) {
                    Key.DirectionDown -> {
                        focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
                        true
                    }
                    Key.DirectionUp -> {
                        focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Up)
                        true
                    }
                    else -> false
                }
            } else false
        }
    )
}
