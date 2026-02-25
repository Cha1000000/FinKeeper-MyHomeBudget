package ru.homebudget.finkeeper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.homebudget.finkeeper.ui.Strings
import ru.homebudget.finkeeper.util.isDesktop

fun Modifier.neonGlow(
    color: Color,
    radius: Dp = 12.dp,
    shape: Shape = RoundedCornerShape(16.dp)
): Modifier = composed {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Even softer and more diffuse glow
    val alpha = if (isDark) 0.45f else 0.25f
    
    this.shadow(
        elevation = radius,
        shape = shape,
        spotColor = color.copy(alpha = alpha),
        ambientColor = color.copy(alpha = alpha)
    )
}

@Composable
fun SummaryCard(
    title: String,
    value: String,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleStyle: androidx.compose.ui.text.TextStyle? = null,
    onClick: (() -> Unit)? = null
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val isPrimary = backgroundColor == MaterialTheme.colorScheme.primary
    val cardContentPadding = if (isDesktop) Modifier.padding(20.dp) else
        Modifier.padding(14.dp, 14.dp, 4.dp, 14.dp)
    
    // Если карточка использует основной цвет темы (как "Всего активов"),
    // мы делаем её чуть более прозрачной для эффекта стекла, но сохраняем акцент.
    val finalBaseColor = if (isPrimary && isDark) {
        backgroundColor.copy(alpha = 0.8f)
    } else {
        backgroundColor
    }

    GlassyCard(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(24.dp),
        baseColor = finalBaseColor,
        highlightColor = contentColor.copy(alpha = 0.2f)
    ) {
        Column(
            modifier = cardContentPadding
        ) {
            Text(
                text = title,
                style = titleStyle ?: MaterialTheme.typography.titleMedium,
                color = contentColor,
                lineHeight = titleStyle?.lineHeight ?: MaterialTheme.typography.titleMedium.lineHeight
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
    height: Int = 8
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(height.dp / 2))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .neonGlow(color, radius = 6.dp, shape = RoundedCornerShape(height.dp / 2))
                .clip(RoundedCornerShape(height.dp / 2))
                .background(color)
        )
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    singleLine: Boolean = true,
    enabled: Boolean = true,
    placeholder: String? = null,
    debounceMs: Long? = null,
    onImmediateValueChange: ((String) -> Unit)? = null
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Более "стеклянный" фон для инпутов
    val containerColor = if (isDark) 
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f) 
    else 
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        
    val updatedOnValueChange by rememberUpdatedState(onValueChange)
    val updatedOnImmediateValueChange by rememberUpdatedState(onImmediateValueChange)
    var internalValue by remember { mutableStateOf(value) }
    var hasUserInput by remember { mutableStateOf(false) }

    LaunchedEffect(value, debounceMs) {
        if (debounceMs == null) return@LaunchedEffect
        if (value != internalValue) {
            internalValue = value
            hasUserInput = false
        }
    }

    LaunchedEffect(internalValue, debounceMs, hasUserInput) {
        if (debounceMs == null || !hasUserInput) return@LaunchedEffect
        delay(debounceMs)
        updatedOnValueChange(internalValue)
    }

    OutlinedTextField(
        value = if (debounceMs == null) value else internalValue,
        onValueChange = { newValue ->
            if (debounceMs == null) {
                updatedOnValueChange(newValue)
            } else {
                internalValue = newValue
                hasUserInput = true
                updatedOnImmediateValueChange?.invoke(newValue)
            }
        },
        label = { Text(label) },
        placeholder = if (placeholder != null) {{ Text(placeholder) }} else null,
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onDone = { onImeAction() },
            onNext = { onImeAction() }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            disabledContainerColor = containerColor.copy(alpha = 0.2f),
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        ),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = singleLine,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().height(56.dp),
    enabled: Boolean = true,
    isLoading: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    style: GlassyButtonStyle = GlassyButtonStyle.Solid,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium
) {
    GlassyButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        isLoading = isLoading,
        color = containerColor,
        textColor = contentColor,
        style = style,
        textStyle = textStyle
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = Strings.YES,
    dismissText: String = Strings.CANCEL,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = false,
    isLoading: Boolean = false
) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        content = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = true, onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                GlassyCard(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(16.dp)
                        .clickable(enabled = true, onClick = {}),
                    shape = RoundedCornerShape(24.dp),
                    baseColor = MaterialTheme.colorScheme.surface,
                    highlightColor = (if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = 0.15f)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AppButton(
                                text = dismissText,
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f).height(48.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = GlassyButtonStyle.Glassy
                            )
                            AppButton(
                                text = confirmText,
                                onClick = onConfirm,
                                modifier = Modifier.weight(1f).height(48.dp),
                                containerColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                contentColor = Color.White,
                                isLoading = isLoading,
                                style = GlassyButtonStyle.Glassy
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        action?.invoke()
    }
}

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun SourceConfirmDialog(
    sourceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.NEW_INCOME_SOURCE_DIALOG) },
        text = { Text(Strings.SOURCE_NOT_FOUND_DIALOG.replace("\"%1\$s\"", "\"$sourceName\"")) },
        confirmButton = {
            TextButton(
                onClick = onConfirm
            ) {
                Text("Да")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
