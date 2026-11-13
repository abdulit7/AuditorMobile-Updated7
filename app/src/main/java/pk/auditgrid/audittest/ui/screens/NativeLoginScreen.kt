package pk.auditgrid.audittest.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pk.auditgrid.audittest.api.ApiError
import pk.auditgrid.audittest.api.AuditGridApiClient
import pk.auditgrid.audittest.model.Session
import pk.auditgrid.audittest.ui.theme.*

@Composable
fun NativeLoginScreen(
    apiClient: AuditGridApiClient,
    onSignedIn: (Session) -> Unit
) {
    var server by remember { mutableStateOf(apiClient.getConfiguredServerUrl()) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Silently sync mobile users in background when login screen opens
    LaunchedEffect(Unit) {
        val base = AuditGridApiClient.normalizeBase(server)
        if (Regex("^https?://.+", RegexOption.IGNORE_CASE).matches(base)) {
            runCatching { apiClient.syncMobileUsersPreLogin(base) }
        }
    }

    fun submit() {
        if (busy) return
        error = ""
        if (username.isBlank() || password.isEmpty()) {
            error = "Please enter your username and password."
            return
        }
        val base = AuditGridApiClient.normalizeBase(server)
        if (!Regex("^https?://.+", RegexOption.IGNORE_CASE).matches(base)) {
            error = "Please enter a valid server address (e.g. http://192.168.1.20:3000)."
            return
        }

        busy = true
        scope.launch {
            try {
                apiClient.setApiBase(base)
                runCatching {
                    apiClient.syncMobileUsersPreLogin(base)
                }
                val session = apiClient.login(username.trim(), password)
                onSignedIn(session)
            } catch (err: Exception) {
                error = if (err is ApiError) err.message else "Could not sign in."
            } finally {
                busy = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AgBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Brand Header matching AuditGrid Mockup
            Surface(
                color = AgNavyHeader,
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 22.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AuditGridShieldLogo(size = 38.dp)
                        Text(
                            text = "AuditGrid",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "FIELD AUDITOR MOBILE APP",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Login Form Card
            Surface(
                color = AgCardWhite,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, AgBorderHairline),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Server Address
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = "Server",
                                tint = AgForeground,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Server address",
                                color = AgForeground,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedTextField(
                            value = server,
                            onValueChange = { server = it },
                            placeholder = {
                                Text(
                                    text = "http://192.168.1.20:3000",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    color = AgMutedText.copy(alpha = 0.6f)
                                )
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = AgForeground
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AgPrimary,
                                unfocusedBorderColor = AgBorder,
                                focusedContainerColor = AgCardWhite,
                                unfocusedContainerColor = AgCardWhite
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("server_address_input")
                        )
                    }

                    // Username
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Username",
                            color = AgForeground,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 15.sp, color = AgForeground),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AgPrimary,
                                unfocusedBorderColor = AgBorder,
                                focusedContainerColor = AgCardWhite,
                                unfocusedContainerColor = AgCardWhite
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("username_input")
                        )
                    }

                    // Password
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Password",
                            color = AgForeground,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { showPassword = !showPassword },
                                    modifier = Modifier.testTag("toggle_password_visibility")
                                ) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showPassword) "Hide password" else "Show password",
                                        tint = AgMutedText
                                    )
                                }
                            },
                            textStyle = TextStyle(fontSize = 15.sp, color = AgForeground),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AgPrimary,
                                unfocusedBorderColor = AgBorder,
                                focusedContainerColor = AgCardWhite,
                                unfocusedContainerColor = AgCardWhite
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("password_input")
                        )
                    }

                    if (error.isNotEmpty()) {
                        Surface(
                            color = AgDestructiveBg,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = error,
                                color = AgDestructiveDark,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Button(
                        onClick = { submit() },
                        enabled = !busy,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AgPrimary,
                            contentColor = Color.White,
                            disabledContainerColor = AgPrimary.copy(alpha = 0.6f),
                            disabledContentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("login_button")
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Signing in…",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "Sign in",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
