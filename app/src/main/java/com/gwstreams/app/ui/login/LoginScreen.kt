package com.gwstreams.app.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gwstreams.app.ui.components.GwLogo
import com.gwstreams.app.ui.theme.*

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    vm: LoginViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    var showPass by remember { mutableStateOf(false) }

    LaunchedEffect(state.success) { if (state.success) onLoggedIn() }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Surface2, Midnight, Midnight))
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))
            GwLogo(size = 76)
            Spacer(Modifier.height(20.dp))
            Text("GWStreams", style = MaterialTheme.typography.displaySmall, color = TextHi)
            Text(
                "Sign in to your service",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMid
            )
            Spacer(Modifier.height(40.dp))

            Field(state.host, vm::onHost, "Server host", "http://example.com:8080")
            Spacer(Modifier.height(14.dp))
            Field(state.username, vm::onUser, "Username", "")
            Spacer(Modifier.height(14.dp))
            Field(
                value = state.password,
                onChange = vm::onPass,
                label = "Password",
                placeholder = "",
                visual = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(
                            if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "Toggle password",
                            tint = TextLow
                        )
                    }
                }
            )

            AnimatedVisibility(state.error != null) {
                Text(
                    state.error ?: "",
                    color = Coral,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                )
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = vm::login,
                enabled = !state.loading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Aqua, contentColor = Midnight),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        color = Midnight, strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text("Sign in", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String,
    visual: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, color = TextLow) },
        singleLine = true,
        visualTransformation = visual,
        trailingIcon = trailing,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        shape = RoundedCornerShape(14.dp),
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
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
    )
}
