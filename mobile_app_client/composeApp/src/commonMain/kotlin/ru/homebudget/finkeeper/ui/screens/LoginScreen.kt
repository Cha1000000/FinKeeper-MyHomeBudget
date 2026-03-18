package ru.homebudget.finkeeper.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Shape
import ru.homebudget.finkeeper.ui.Strings
import ru.homebudget.finkeeper.ui.components.AppButton
import ru.homebudget.finkeeper.ui.components.AppTextField
import ru.homebudget.finkeeper.ui.components.GlassyButtonStyle
import ru.homebudget.finkeeper.ui.components.GlassyCard
import ru.homebudget.finkeeper.ui.viewmodel.AuthState
import ru.homebudget.finkeeper.util.isDesktop

@Composable
fun LoginScreen(
    state: AuthState,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onLoginWithSocial: (String) -> Unit,
    onRequestPasswordRecovery: (String) -> Unit,
    onConfirmPasswordRecovery: (String, String, () -> Unit) -> Unit,
    onClearError: () -> Unit,
    onServerUrlChange: (String) -> Unit,
    currentServerUrl: String,
    showServerSettingsEnabled: Boolean,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var recoveryEmail by remember { mutableStateOf("") }
    var recoveryToken by remember { mutableStateOf("") }
    var recoveryPassword by remember { mutableStateOf("") }
    var isRegisterMode by remember { mutableStateOf(false) }
    var isRecoveryMode by remember { mutableStateOf(false) }
    var showServerSettings by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf(currentServerUrl) }

    val trimmedUsername = username.trim()
    val trimmedRecoveryEmail = recoveryEmail.trim().lowercase()
    val trimmedRecoveryToken = recoveryToken.trim()
    val usernameError = if (trimmedUsername.length > 64) Strings.USERNAME_TOO_LONG else null
    val passwordError = if (password.isNotEmpty() && password.length < 6) Strings.PASSWORD_TOO_SHORT else null
    val recoveryPasswordError =
        if (recoveryPassword.isNotEmpty() && recoveryPassword.length < 6) Strings.PASSWORD_TOO_SHORT else null
    val canSubmit = trimmedUsername.isNotBlank() && usernameError == null && password.length >= 6 && !state.isLoading
    val canRequestRecovery = trimmedRecoveryEmail.isNotBlank() && !state.isLoading
    val canConfirmRecovery =
        trimmedRecoveryToken.isNotBlank() &&
            recoveryPassword.length >= 6 &&
            recoveryPasswordError == null &&
            !state.isLoading
    val socialProviders = state.socialProviders.associateBy { it.id }
    val isGoogleEnabled = socialProviders["google"]?.enabled == true
    val isYandexEnabled = socialProviders["yandex"]?.enabled == true
    val hasEnabledSocialProviders = isGoogleEnabled || isYandexEnabled
    val isSocialVisible = !isRegisterMode && !isRecoveryMode && hasEnabledSocialProviders

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (isDesktop) Modifier.padding(horizontal = 40.dp) else Modifier.padding(24.dp),
                    ).verticalScroll(rememberScrollState())
                    .safeContentPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "💰",
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = Strings.APP_TITLE,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = Strings.APP_SUBTITLE,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 32.dp),
            )

            GlassyCard(
                modifier =
                    Modifier
                        .widthIn(max = 500.dp)
                        .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                baseColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text =
                            when {
                                isRecoveryMode -> Strings.PASSWORD_RECOVERY
                                isRegisterMode -> Strings.REGISTER_TITLE
                                else -> Strings.LOGIN_TITLE
                            },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isRecoveryMode) {
                        AppTextField(
                            value = recoveryEmail,
                            onValueChange = {
                                recoveryEmail = it
                                onClearError()
                            },
                            label = Strings.PASSWORD_RECOVERY_EMAIL_LABEL,
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = Strings.LOGIN_RECOVERY_HELPER,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        AppTextField(
                            value = recoveryToken,
                            onValueChange = {
                                recoveryToken = it
                                onClearError()
                            },
                            label = Strings.PASSWORD_RECOVERY_TOKEN_LABEL,
                            imeAction = ImeAction.Next,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        AppTextField(
                            value = recoveryPassword,
                            onValueChange = {
                                recoveryPassword = it
                                onClearError()
                            },
                            label = Strings.PASSWORD_RECOVERY_NEW_PASSWORD_LABEL,
                            isPassword = true,
                            imeAction = ImeAction.Done,
                            onImeAction = {
                                if (canConfirmRecovery) {
                                    onConfirmPasswordRecovery(trimmedRecoveryToken, recoveryPassword) {
                                        recoveryToken = ""
                                        recoveryPassword = ""
                                    }
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = recoveryPasswordError ?: Strings.PASSWORD_HELPER,
                            color = if (recoveryPasswordError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        AppTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                onClearError()
                            },
                            label = Strings.USERNAME_LABEL,
                            imeAction = ImeAction.Next,
                        )
                        Text(
                            text = usernameError ?: Strings.USERNAME_HELPER,
                            color = if (usernameError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        AppTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                onClearError()
                            },
                            label = Strings.PASSWORD_LABEL,
                            isPassword = true,
                            imeAction = ImeAction.Done,
                            onImeAction = {
                                if (canSubmit) {
                                    username = trimmedUsername
                                    if (isRegisterMode) onRegister(trimmedUsername, password) else onLogin(trimmedUsername, password)
                                }
                            },
                        )
                        Text(
                            text = passwordError ?: Strings.PASSWORD_HELPER,
                            color = if (passwordError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                        )

                        if (!isRegisterMode) {
                            TextButton(
                                onClick = {
                                    isRecoveryMode = true
                                    onClearError()
                                },
                            ) {
                                Text(
                                    text = Strings.FORGOT_PASSWORD,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        AnimatedVisibility(visible = true) {
                            Text(
                                text = if (isRegisterMode) Strings.REGISTER_RECOVERY_HELPER else Strings.LOGIN_RECOVERY_HELPER,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                            )
                        }

                        AnimatedVisibility(visible = isRegisterMode) {
                            Text(
                                text = Strings.REGISTER_HELPER,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                            )
                        }
                    }

                    AnimatedVisibility(visible = state.error != null) {
                        GlassyCard(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                            shape = RoundedCornerShape(16.dp),
                            baseColor = MaterialTheme.colorScheme.errorContainer,
                            highlightColor = MaterialTheme.colorScheme.error,
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = state.error ?: "",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                )
                                if (state.error == Strings.CONNECTION_ERROR) {
                                    Text(
                                        text = Strings.CONNECTION_HELPER,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = state.infoMessage != null) {
                        GlassyCard(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                            shape = RoundedCornerShape(16.dp),
                            baseColor = MaterialTheme.colorScheme.primaryContainer,
                            highlightColor = MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                text = state.infoMessage ?: "",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }

                    AnimatedVisibility(visible = isSocialVisible) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = Strings.SOCIAL_LOGIN_HELPER,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            if (isDesktop) {
                                SocialDesktopButton(
                                    text = Strings.LOGIN_WITH_GOOGLE,
                                    onClick = { onLoginWithSocial("google") },
                                    enabled = !state.isLoading && isGoogleEnabled,
                                    isLoading = state.socialLoginProvider == "google",
                                    modifier = Modifier.fillMaxWidth(),
                                    icon = { GoogleSocialIcon(modifier = Modifier.size(22.dp)) },
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                SocialDesktopButton(
                                    text = Strings.LOGIN_WITH_YANDEX,
                                    onClick = { onLoginWithSocial("yandex") },
                                    enabled = !state.isLoading && isYandexEnabled,
                                    isLoading = state.socialLoginProvider == "yandex",
                                    modifier = Modifier.fillMaxWidth(),
                                    icon = { YandexSocialIcon(modifier = Modifier.size(22.dp)) },
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SocialMobileIconButton(
                                        onClick = { onLoginWithSocial("google") },
                                        enabled = !state.isLoading && isGoogleEnabled,
                                        isLoading = state.socialLoginProvider == "google",
                                        iconContainerModifier = Modifier.size(30.dp),
                                        icon = {
                                            GoogleSocialIcon(
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        },
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    SocialMobileIconButton(
                                        onClick = { onLoginWithSocial("yandex") },
                                        enabled = !state.isLoading && isYandexEnabled,
                                        isLoading = state.socialLoginProvider == "yandex",
                                        iconContainerModifier = Modifier.fillMaxSize(),
                                        icon = {
                                            YandexSocialIcon(
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = state.recoveryDebugToken != null && isRecoveryMode) {
                        GlassyCard(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                            shape = RoundedCornerShape(16.dp),
                            baseColor = MaterialTheme.colorScheme.secondaryContainer,
                            highlightColor = MaterialTheme.colorScheme.secondary,
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = Strings.PASSWORD_RECOVERY_DEBUG,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = state.recoveryDebugToken?.token ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = { recoveryToken = state.recoveryDebugToken?.token ?: "" },
                                ) {
                                    Text(Strings.PASSWORD_RECOVERY_OPEN_DEBUG)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    AppButton(
                        text =
                            when {
                                isRecoveryMode -> Strings.PASSWORD_RECOVERY_REQUEST
                                isRegisterMode -> Strings.REGISTER_BUTTON
                                else -> Strings.LOGIN_BUTTON
                            },
                        onClick = {
                            if (isRecoveryMode) {
                                onRequestPasswordRecovery(trimmedRecoveryEmail)
                            } else {
                                username = trimmedUsername
                                if (isRegisterMode) onRegister(trimmedUsername, password) else onLogin(trimmedUsername, password)
                            }
                        },
                        enabled = if (isRecoveryMode) canRequestRecovery else canSubmit,
                        isLoading = state.isLoading,
                        contentColor = MaterialTheme.colorScheme.primary,
                        style = GlassyButtonStyle.Glassy,
                    )

                    AnimatedVisibility(visible = isRecoveryMode) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(modifier = Modifier.height(12.dp))
                            AppButton(
                                text = Strings.PASSWORD_RECOVERY_CONFIRM,
                                onClick = {
                                    onConfirmPasswordRecovery(trimmedRecoveryToken, recoveryPassword) {
                                        recoveryToken = ""
                                        recoveryPassword = ""
                                    }
                                },
                                enabled = canConfirmRecovery,
                                isLoading = state.isLoading,
                                contentColor = MaterialTheme.colorScheme.primary,
                                style = GlassyButtonStyle.Glassy,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = {
                            if (isRecoveryMode) {
                                isRecoveryMode = false
                                onClearError()
                            } else {
                                isRegisterMode = !isRegisterMode
                                onClearError()
                            }
                        },
                    ) {
                        Text(
                            text =
                                if (isRecoveryMode) {
                                    Strings.PASSWORD_RECOVERY_BACK_TO_LOGIN
                                } else {
                                    if (isRegisterMode) Strings.ALREADY_HAVE_ACCOUNT else Strings.NO_ACCOUNT
                                },
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (showServerSettingsEnabled) {
                TextButton(
                    onClick = { showServerSettings = !showServerSettings },
                ) {
                    Text(
                        text = Strings.SERVER_SETTINGS,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                AnimatedVisibility(visible = showServerSettings) {
                    GlassyCard(
                        modifier =
                            Modifier
                                .widthIn(max = 500.dp)
                                .fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        baseColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        highlightColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                        ) {
                            AppTextField(
                                value = serverUrl,
                                onValueChange = { serverUrl = it },
                                label = Strings.SERVER_URL_LABEL,
                                placeholder = Strings.SERVER_URL_PLACEHOLDER,
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done,
                                onImeAction = {
                                    val normalizedUrl = serverUrl.trim()
                                    serverUrl = normalizedUrl
                                    onServerUrlChange(normalizedUrl)
                                },
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            AppButton(
                                text = Strings.SAVE,
                                onClick = {
                                    val normalizedUrl = serverUrl.trim()
                                    serverUrl = normalizedUrl
                                    onServerUrlChange(normalizedUrl)
                                },
                                containerColor = MaterialTheme.colorScheme.secondary,
                                style = GlassyButtonStyle.Glassy,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialDesktopButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val borderColor =
        if (enabled) {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        }
    val contentColor =
        if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        }
    val containerColor =
        if (enabled) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        }

    Box(
        modifier =
            modifier
                .shadow(10.dp, shape, clip = false)
                .clip(shape)
                .background(containerColor)
                .border(1.dp, borderColor, shape)
                .then(if (enabled && !isLoading) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 20.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
            } else {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    icon()
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                color = contentColor,
                style = LocalTextStyle.current.merge(
                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                ),
            )
        }
    }
}

@Composable
private fun SocialMobileIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    iconContainerModifier: Modifier = Modifier.size(24.dp),
    icon: @Composable () -> Unit,
) {
    val shape: Shape = CircleShape
    val containerColor =
        if (enabled) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        }
    val progressColor =
        if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        }
    Box(
        modifier =
            modifier
                .size(42.dp)
                .shadow(6.dp, shape, clip = false)
                .clip(shape)
                .background(containerColor)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f), shape)
                .then(if (enabled && !isLoading) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = progressColor,
            )
        } else {
            Box(
                modifier = iconContainerModifier,
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
        }
    }
}

@Composable
private fun GoogleSocialIcon(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val scaleFactor = minOf(this.size.width, this.size.height) / 24f
        val offsetX = (this.size.width - 24f * scaleFactor) / 2f
        val offsetY = (this.size.height - 24f * scaleFactor) / 2f
        withTransform({
            translate(left = offsetX, top = offsetY)
            scale(scaleFactor, scaleFactor, pivot = Offset.Zero)
        }) {
            drawPath(path = googleBluePath, color = Color(0xFF4285F4))
            drawPath(path = googleGreenPath, color = Color(0xFF34A853))
            drawPath(path = googleYellowPath, color = Color(0xFFFBBC05))
            drawPath(path = googleRedPath, color = Color(0xFFEA4335))
        }
    }
}

@Composable
private fun YandexSocialIcon(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val iconSize = minOf(this.size.width, this.size.height)
        val glyphScale = iconSize / 44f
        drawCircle(
            color = Color(0xFFFC3F1D),
            radius = iconSize / 2f,
            center = center,
        )
        val offsetX = (this.size.width - 44f * glyphScale) / 2f
        val offsetY = (this.size.height - 44f * glyphScale) / 2f
        withTransform({
            translate(left = offsetX, top = offsetY)
            scale(glyphScale, glyphScale, pivot = Offset.Zero)
        }) {
            drawPath(path = yandexWhitePath, color = Color.White)
        }
    }
}

@Composable
private fun YandexMobileIcon(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(Color(0xFFFC3F1D)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Я",
            color = Color.White,
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
        )
    }
}

private val googleBluePath: Path by lazy {
    PathParser().parsePathString("M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z").toPath()
}

private val googleGreenPath: Path by lazy {
    PathParser().parsePathString("M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z").toPath()
}

private val googleYellowPath: Path by lazy {
    PathParser().parsePathString("M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z").toPath()
}

private val googleRedPath: Path by lazy {
    PathParser().parsePathString("M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z").toPath()
}

private val yandexWhitePath: Path by lazy {
    PathParser().parsePathString("M24.7407 33.9778H29.0889V9.04443H22.7592C16.3929 9.04443 13.0538 12.303 13.0538 17.1176C13.0538 21.2731 15.2187 23.6163 19.0532 26.1609L21.3832 27.6987L18.3927 25.1907L12.4667 33.9778H17.1818L23.5115 24.5317L21.3098 23.0671C18.6496 21.2731 17.3469 19.8818 17.3469 16.8613C17.3469 14.2068 19.2183 12.4128 22.7776 12.4128H24.7223V33.9778H24.7407Z").toPath()
}
