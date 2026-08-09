@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gwstreams.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.gwstreams.app.data.model.Category
import com.gwstreams.tv.BuildConfig
import com.gwstreams.tv.data.Updater
import com.gwstreams.tv.ui.TvSection
import com.gwstreams.tv.ui.TvViewModel
import com.gwstreams.tv.ui.theme.Aqua
import com.gwstreams.tv.ui.theme.Coral
import com.gwstreams.tv.ui.theme.Midnight
import com.gwstreams.tv.ui.theme.Surface1
import com.gwstreams.tv.ui.theme.Surface2
import com.gwstreams.tv.ui.theme.SurfaceHi
import com.gwstreams.tv.ui.theme.TextHi
import com.gwstreams.tv.ui.theme.TextLow
import com.gwstreams.tv.ui.theme.TextMid
import com.gwstreams.tv.ui.theme.TvType

@Composable
fun TvSettingsScreen(vm: TvViewModel, onLogout: () -> Unit) {
    val state by vm.state.collectAsState()
    val s = state.settings
    val appUpdate = state.appUpdate
    val context = LocalContext.current

    LazyColumn(
        contentPadding = PaddingValues(40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(Midnight)
    ) {
        item {
            Text("Settings", style = TvType.displayMedium, color = TextHi)
            Spacer(Modifier.height(20.dp))
        }

        item {
            SettingSection("App updates")
        }
        item {
            Surface(
                color = Surface1,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Current version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = TvType.titleMedium,
                        color = TextHi
                    )
                    Text(
                        text = "Manually check GitHub for the latest TV APK and launch the installer from here.",
                        style = TvType.bodyMedium,
                        color = TextMid
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FocusButton(
                            label = when {
                                appUpdate.inProgress -> "Updating…"
                                appUpdate.checking -> "Checking…"
                                appUpdate.installPermissionRequired -> "Update again"
                                else -> "Check for updates"
                            },
                            onClick = {
                                when {
                                    appUpdate.inProgress || appUpdate.checking -> Unit
                                    appUpdate.availableUpdate != null -> vm.beginAppUpdate(context)
                                    else -> vm.checkForAppUpdate(force = true)
                                }
                            }
                        )
                        if (appUpdate.checking || appUpdate.inProgress) {
                            CircularProgressIndicator(color = Aqua, strokeWidth = 2.dp)
                        }
                    }
                    appUpdate.downloadPercent?.let { percent ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(
                                progress = { percent / 100f },
                                color = Aqua,
                                trackColor = Surface2,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Download progress: $percent%", style = TvType.bodyMedium, color = TextMid)
                        }
                    }
                    appUpdate.statusMessage?.let {
                        Text(it, style = TvType.bodyMedium, color = TextMid)
                    }
                    appUpdate.errorMessage?.let {
                        Text(it, style = TvType.bodyMedium, color = Coral)
                    }
                }
            }
        }

        item {
            SettingSection("Programme guide (EPG)")
        }
        item {
            ToggleRow(
                label = "Auto-fetch EPG when opening Live",
                checked = s.autoFetchEpg,
                onToggle = { vm.setAutoFetchEpg(!s.autoFetchEpg) }
            )
        }
        item {
            StepperRow(
                label = "EPG refresh interval",
                value = "${s.epgRefreshMinutes} min",
                onLess = { vm.setEpgRefreshMinutes((s.epgRefreshMinutes - 5).coerceAtLeast(5)) },
                onMore = { vm.setEpgRefreshMinutes((s.epgRefreshMinutes + 5).coerceAtMost(120)) }
            )
        }

        item {
            Spacer(Modifier.height(10.dp))
            SettingSection("Playback")
        }
        item {
            StepperRow(
                label = "Buffer size",
                value = "${s.bufferSeconds} sec",
                onLess = { vm.setBufferSeconds((s.bufferSeconds - 5).coerceAtLeast(5)) },
                onMore = { vm.setBufferSeconds((s.bufferSeconds + 5).coerceAtMost(120)) }
            )
        }

        item {
            Spacer(Modifier.height(10.dp))
            SettingSection("TV Experience & Safety")
        }
        item {
            ToggleRow(
                label = "Cable Box Mode (Auto-play Live TV on startup)",
                checked = s.cableBoxMode,
                onToggle = { vm.setCableBoxMode(!s.cableBoxMode) }
            )
        }
        item {
            ToggleRow(
                label = "Mom Mode (Simplified Interface)",
                checked = s.momMode,
                onToggle = { vm.setMomMode(!s.momMode) }
            )
        }

        item {
            Spacer(Modifier.height(10.dp))
            SettingSection("Live categories — select to hide / show")
        }
        val liveCats = vm.allCategoriesForSection(TvSection.LIVE)
        items(liveCats, key = { it.categoryId }) { cat ->
            CategoryToggle(
                cat = cat,
                hidden = cat.categoryId in s.hiddenCategories,
                onToggle = { vm.toggleCategoryHidden(cat.categoryId) }
            )
        }

        item {
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FocusButton("Log out", onLogout)
                FocusButton("Nuke It (Clear Cache & Restart)", onClick = { vm.nukeAndRestart(context) })
            }
        }

        item {
            Spacer(Modifier.height(10.dp))
            var reportSent by remember { mutableStateOf(false) }
            FocusButton(
                if (reportSent) "Report Sent ✓" else "Send Error Report to Rob",
                onClick = {
                    vm.sendErrorReport()
                    reportSent = true
                }
            )
        }
    }

    val visibleUpdate = appUpdate.availableUpdate?.takeIf {
        appUpdate.dismissedVersionCode != it.versionCode
    }
    if (visibleUpdate != null) {
        SettingsUpdateDialog(
            info = visibleUpdate,
            downloading = appUpdate.inProgress,
            progress = appUpdate.downloadPercent ?: 0,
            status = appUpdate.statusMessage,
            error = appUpdate.errorMessage,
            installPermissionRequired = appUpdate.installPermissionRequired,
            onDismiss = { vm.dismissUpdatePrompt() },
            onUpdate = { vm.beginAppUpdate(context) }
        )
    }
}

@Composable
private fun SettingsUpdateDialog(
    info: Updater.UpdateInfo,
    downloading: Boolean,
    progress: Int,
    status: String?,
    error: String?,
    installPermissionRequired: Boolean,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    Dialog(onDismissRequest = { if (!info.mandatory && !downloading) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SurfaceHi,
            modifier = Modifier.width(560.dp)
        ) {
            Column(
                Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Update available", style = TvType.headlineMedium, color = TextHi)
                Text("Great White Streams TV v${info.versionName}", style = TvType.titleMedium, color = Aqua)
                Text(info.releaseNotes, style = TvType.bodyMedium, color = TextMid)
                if (installPermissionRequired) {
                    Text(
                        "Android blocked the installer. Enable 'Install unknown apps' for this app, come back here, then press Update again.",
                        style = TvType.bodyMedium,
                        color = Coral
                    )
                }
                status?.let {
                    Text(it, style = TvType.bodyMedium, color = TextMid)
                }
                error?.let {
                    Text(it, style = TvType.bodyMedium, color = Coral)
                }
                if (downloading) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        color = Aqua,
                        trackColor = Surface1,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Downloading… $progress%", style = TvType.bodyMedium, color = TextMid)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (!info.mandatory) {
                        Button(
                            onClick = onDismiss,
                            enabled = !downloading,
                            colors = ButtonDefaults.buttonColors(containerColor = Surface1, contentColor = TextHi),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Later")
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    Button(
                        onClick = onUpdate,
                        enabled = !downloading,
                        colors = ButtonDefaults.buttonColors(containerColor = Aqua, contentColor = Midnight),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (installPermissionRequired) "Update again" else if (downloading) "Working…" else "Update")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSection(title: String) {
    Text(title, style = TvType.labelLarge, color = Aqua, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = onToggle,
        interactionSource = interaction,
        color = if (focused) SurfaceHi else Surface1,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focused) Modifier.border(2.dp, Aqua, RoundedCornerShape(10.dp)) else Modifier)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = TvType.titleMedium, color = TextHi, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun StepperRow(label: String, value: String, onLess: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Surface1, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = TvType.titleMedium, color = TextHi, modifier = Modifier.weight(1f))
        FocusButton("-", onLess, compact = true)
        Text(
            value,
            style = TvType.titleMedium,
            color = Aqua,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(min = 70.dp)
        )
        FocusButton("+", onMore, compact = true)
    }
}

@Composable
private fun CategoryToggle(cat: Category, hidden: Boolean, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = onToggle,
        interactionSource = interaction,
        color = if (focused) SurfaceHi else Surface1,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focused) Modifier.border(2.dp, Aqua, RoundedCornerShape(8.dp)) else Modifier)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (hidden) Icons.Filled.CheckBoxOutlineBlank else Icons.Filled.CheckBox,
                contentDescription = null,
                tint = if (hidden) TextLow else Aqua
            )
            Spacer(Modifier.width(12.dp))
            Text(
                cat.categoryName,
                style = TvType.titleMedium,
                color = if (hidden) TextLow else TextHi
            )
        }
    }
}

@Composable
private fun FocusButton(label: String, onClick: () -> Unit, compact: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        color = if (focused) Aqua else Surface2,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.then(if (focused) Modifier.border(2.dp, Aqua, RoundedCornerShape(10.dp)) else Modifier)
    ) {
        Text(
            label,
            style = TvType.labelLarge,
            color = if (focused) Midnight else TextHi,
            modifier = Modifier.padding(
                horizontal = if (compact) 18.dp else 28.dp,
                vertical = if (compact) 8.dp else 14.dp
            )
        )
    }
}
