package com.gwstreams.tv.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.gwstreams.tv.ui.TvViewModel
import com.gwstreams.tv.ui.theme.*

@Composable
fun TvLoginScreen(vm: TvViewModel, onLoggedIn: () -> Unit) {
    val state by vm.state.collectAsState()

    var host by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var remember by remember { mutableStateOf(true) }

    // Prefill from saved credentials once they load.
    LaunchedEffect(state.savedHost, state.savedUser, state.savedPass) {
        if (host.isEmpty() && state.savedHost.isNotEmpty()) host = state.savedHost
        if (user.isEmpty() && state.savedUser.isNotEmpty()) user = state.savedUser
        if (pass.isEmpty() && state.savedPass.isNotEmpty()) pass = state.savedPass
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
            }
            return@Box
        }

        Column(
            Modifier.width(560.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Great White Streams", style = TvType.displayMedium, color = TextHi)
            Spacer(Modifier.height(8.dp))
            Text("Sign in to your service", style = TvType.bodyLarge, color = TextMid)
            Spacer(Modifier.height(36.dp))

            TvField(host, { host = it }, "Server host (https://host:port)")
            Spacer(Modifier.height(16.dp))
            TvField(user, { user = it }, "Username")
            Spacer(Modifier.height(16.dp))
            TvField(pass, { pass = it }, "Password", isPassword = true)

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(checked = remember, onCheckedChange = { remember = it })
                Spacer(Modifier.width(12.dp))
                Text("Save login and stay signed in", style = TvType.titleMedium, color = TextHi)
            }

            state.error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = Coral, style = TvType.bodyMedium)
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { vm.login(host, user, pass, remember) { ok, _ -> if (ok) onLoggedIn() } },
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
}

@Composable
private fun TvField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Uri),
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
        modifier = Modifier.fillMaxWidth()
    )
}
