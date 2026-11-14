package com.aistudio.akmessenger.admin.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.akmessenger.admin.R
import com.aistudio.akmessenger.admin.ui.theme.*
import com.aistudio.akmessenger.admin.ui.viewmodel.AdminViewModel
import com.aistudio.akmessenger.shared.ui.AkFonts
import kotlinx.coroutines.delay

@Composable
fun AdminSplashScreen(
    isAuthenticated: Boolean,
    onFinished: (Boolean) -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.75f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "admin_splash"
    )
    LaunchedEffect(Unit) {
        visible = true
        delay(1100)
        onFinished(isAuthenticated)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AdminBlack, AdminNavy, AdminBlack)))
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(scale)) {
            Image(
                painter = painterResource(id = R.drawable.ic_ak_admin_fg),
                contentDescription = "AK Messenger Admin Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .border(2.dp, NeonCyan.copy(alpha = 0.65f), RoundedCornerShape(26.dp))
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "AK MESSENGER ADMIN",
                fontFamily = AkFonts.SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                letterSpacing = 2.sp,
                color = Color.White
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "COMMAND CENTER • SERVER-VERIFIED RBAC",
                fontFamily = AkFonts.JetBrainsMono,
                fontSize = 11.sp,
                color = NeonCyan
            )
        }
    }
}

@Composable
fun AdminLoginScreen(
    viewModel: AdminViewModel,
    onAuthenticated: () -> Unit
) {
    val session by viewModel.sessionFlow.collectAsStateWithLifecycle()
    val busy by viewModel.authBusy.collectAsStateWithLifecycle()
    val errorMsg by viewModel.authError.collectAsStateWithLifecycle()
    val toast by viewModel.statusToast.collectAsStateWithLifecycle()

    var email by remember(session.rememberAdminEmail) { mutableStateOf(session.rememberAdminEmail) }
    var password by remember { mutableStateOf("") }
    var twoStepPin by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(session.rememberAdminEmail.isNotBlank()) }
    var showForgotDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AdminBlack, AdminNavy, Color(0xFF04070E))))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_ak_admin_fg),
                contentDescription = "AK Messenger Admin",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.5.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            )
            Spacer(Modifier.height(14.dp))
            Text("Admin Console Login", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(
                text = "Firebase Project: nagad-5b149",
                fontFamily = AkFonts.JetBrainsMono,
                fontSize = 12.sp,
                color = NeonCyan
            )
            Spacer(Modifier.height(20.dp))

            AnimatedVisibility(visible = errorMsg != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AdminAlert.copy(alpha = 0.16f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.GppBad, null, tint = AdminAlert)
                        Spacer(Modifier.width(8.dp))
                        Text(errorMsg.orEmpty(), color = AdminAlert, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            AnimatedVisibility(visible = toast != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NeonCyan.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Text(toast.orEmpty(), color = NeonCyan, modifier = Modifier.padding(12.dp))
                }
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AdminNavyCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; viewModel.clearError() },
                        label = { Text("Admin Email") },
                        leadingIcon = { Icon(Icons.Filled.AdminPanelSettings, null, tint = NeonCyan) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_email_input")
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; viewModel.clearError() },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Filled.Lock, null, tint = NeonCyan) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle password")
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_password_input")
                    )
                    OutlinedTextField(
                        value = twoStepPin,
                        onValueChange = { twoStepPin = it },
                        label = { Text("Two-Step Security PIN (if enabled)") },
                        leadingIcon = { Icon(Icons.Filled.VerifiedUser, null, tint = NeonCyan) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = rememberMe, onCheckedChange = { rememberMe = it })
                            Text("Remember Me", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { showForgotDialog = true }) {
                            Text("Forgot Password?", color = NeonCyan)
                        }
                    }
                    Button(
                        onClick = { viewModel.loginAdmin(email, password, twoStepPin, rememberMe, onAuthenticated) },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = AdminBlack),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("admin_login_button")
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = AdminBlack, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Security, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Verify & Enter Admin Panel", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Security: Admin privileges are verified server-side at /admins/{uid} in Firebase RTDB. No admin passwords are stored in this APK.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showForgotDialog) {
        var resetEmail by remember { mutableStateOf(email) }
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = { Text("Reset Admin Password") },
            text = { OutlinedTextField(value = resetEmail, onValueChange = { resetEmail = it }, label = { Text("Admin Email") }) },
            confirmButton = {
                Button(onClick = {
                    viewModel.sendForgotPassword(resetEmail)
                    showForgotDialog = false
                }) { Text("Send Reset Link") }
            },
            dismissButton = { TextButton(onClick = { showForgotDialog = false }) { Text("Cancel") } }
        )
    }
}
