@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gwstreams.tv.ui.settings


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gwstreams.app.data.model.Category
import com.gwstreams.tv.ui.TvSection
import com.gwstreams.tv.ui.TvViewModel
import com.gwstreams.tv.ui.theme.*

@Composable
fun TvSettingsScreen(vm: TvViewModel, onLogout: () -> Unit) {
    val state by vm.state.collectAsState()
    val s = state.settings

    LazyColumn(
        contentPadding = PaddingValues(40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().background(Midnight)
    ) {
        item {
            Text("Settings", style = TvType.displayMedium, color = TextHi)
            Spacer(Modifier.height(20.dp))
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
            FocusButton("Log out", onLogout)
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
        modifier = Modifier.fillMaxWidth()
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Surface1)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = TvType.titleMedium, color = TextHi, modifier = Modifier.weight(1f))
        FocusButton("-", onLess, compact = true)
        Text(value, style = TvType.titleMedium, color = Aqua,
            modifier = Modifier.padding(horizontal = 16.dp).widthIn(min = 70.dp))
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
        modifier = Modifier.fillMaxWidth()
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
