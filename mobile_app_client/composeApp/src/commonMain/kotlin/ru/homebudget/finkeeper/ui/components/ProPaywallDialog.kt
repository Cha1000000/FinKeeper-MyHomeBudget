package ru.homebudget.finkeeper.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import ru.homebudget.finkeeper.data.model.Entitlements
import ru.homebudget.finkeeper.ui.Strings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProPaywallDialog(
    entitlements: Entitlements,
    highlightedFeatureKey: String?,
    isBusy: Boolean,
    errorMessage: String?,
    successMessage: String?,
    onStartTrial: () -> Unit,
    onRedeemPromo: (String) -> Unit,
    onClearFeedback: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    var showPromoDialog by remember { mutableStateOf(false) }
    val emeraldAccent = Color(0xFF10B981)
    val amberAccent = Color(0xFFF59E0B)
    val isLifetimePro = entitlements.isLifetime || (entitlements.isPro && entitlements.expiresAt == null)

    Dialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .safeDrawingPadding()
                .imePadding()
                .clickable(enabled = !isBusy, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            GlassyCard(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable(enabled = true, onClick = {}),
                shape = RoundedCornerShape(24.dp),
                baseColor = MaterialTheme.colorScheme.surface,
                highlightColor = emeraldAccent.copy(alpha = 0.32f),
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(emeraldAccent.copy(alpha = 0.16f))
                                .border(1.dp, emeraldAccent.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconProCrownGlass(size = 24.dp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                ProPillBadge(
                                    text = Strings.PRO_FAMILY_TITLE,
                                    accentColor = emeraldAccent,
                                )
                                if (entitlements.canStartTrial) {
                                    ProPillBadge(
                                        text = Strings.PRO_TRIAL_14_BADGE,
                                        accentColor = amberAccent,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = Strings.PRO_PAYWALL_SUBTITLE,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        TextButton(onClick = onDismiss, enabled = !isBusy) {
                            Text(
                                text = Strings.CANCEL,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }

                    // Цена и условия
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                RoundedCornerShape(16.dp),
                            )
                            .padding(14.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = Strings.PRO_PRICE_LABEL,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                ProPillBadge(
                                    text = Strings.PRO_NO_CARD_BADGE,
                                    accentColor = emeraldAccent,
                                )
                            }
                            Text(
                                text = Strings.PRO_FREE_NOTE,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // 4 преимущества PRO
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProFeatureRow(
                            icon = { IconBurnFlameGlass(size = 20.dp) },
                            title = Strings.PRO_FEATURE_ANALYTICS_TITLE,
                            description = Strings.PRO_FEATURE_ANALYTICS_DESC,
                            isHighlighted = highlightedFeatureKey == "extendedAnalytics",
                        )
                        ProFeatureRow(
                            icon = { IconExportSheetGlass(size = 20.dp) },
                            title = Strings.PRO_FEATURE_EXPORT_TITLE,
                            description = Strings.PRO_FEATURE_EXPORT_DESC,
                            isHighlighted = highlightedFeatureKey == "exportReports",
                        )
                        ProFeatureRow(
                            icon = { IconPinGlass(active = true, size = 18.dp) },
                            title = Strings.PRO_FEATURE_BACKUPS_TITLE,
                            description = Strings.PRO_FEATURE_BACKUPS_DESC,
                            isHighlighted = highlightedFeatureKey == "extendedBackups",
                        )
                        ProFeatureRow(
                            icon = { IconDevicesGlass(size = 20.dp) },
                            title = Strings.PRO_FEATURE_DEVICES_TITLE,
                            description = Strings.PRO_FEATURE_DEVICES_DESC,
                            isHighlighted = highlightedFeatureKey == "unlimitedDevices",
                        )
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    if (successMessage != null) {
                        Text(
                            text = successMessage,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = emeraldAccent,
                        )
                    }

                    when {
                        entitlements.canStartTrial -> {
                            AppButton(
                                text = Strings.PRO_START_TRIAL_CTA,
                                onClick = onStartTrial,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                enabled = !isBusy,
                                isLoading = isBusy,
                                containerColor = emeraldAccent,
                                contentColor = Color.White,
                                style = GlassyButtonStyle.Solid,
                                textStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                shape = RoundedCornerShape(14.dp),
                            )
                        }
                        entitlements.isPro -> {
                            val badgeText = when {
                                entitlements.isTrial -> Strings.PRO_TRIAL_ACTIVE_FORMAT.replace(
                                    "%1\$d",
                                    (entitlements.daysRemaining ?: 0).toString(),
                                )
                                entitlements.isLifetime || entitlements.expiresAt == null -> Strings.PRO_LIFETIME_BADGE
                                else -> Strings.PRO_ACTIVE_BADGE
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(emeraldAccent.copy(alpha = 0.14f))
                                    .border(1.dp, emeraldAccent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = emeraldAccent,
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = Strings.PRO_TRIAL_ALREADY_USED_NOTE,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (!isLifetimePro) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            TextButton(
                                onClick = {
                                    onClearFeedback()
                                    showPromoDialog = true
                                },
                                enabled = !isBusy,
                            ) {
                                Text(
                                    text = Strings.PRO_HAVE_PROMO,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPromoDialog) {
        PromoCodeDialog(
            isBusy = isBusy,
            errorMessage = errorMessage,
            successMessage = successMessage,
            onApply = onRedeemPromo,
            onDismiss = {
                onClearFeedback()
                showPromoDialog = false
            },
        )
    }
}

@Composable
fun PromoCodeDialog(
    isBusy: Boolean,
    errorMessage: String?,
    successMessage: String?,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var promoCode by remember { mutableStateOf("") }
    val emeraldAccent = Color(0xFF10B981)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .safeDrawingPadding()
                .imePadding()
                .clickable(enabled = !isBusy, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            GlassyCard(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable(enabled = true, onClick = {}),
                shape = RoundedCornerShape(22.dp),
                baseColor = MaterialTheme.colorScheme.surface,
                highlightColor = emeraldAccent.copy(alpha = 0.30f),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(emeraldAccent.copy(alpha = 0.16f))
                                .border(1.dp, emeraldAccent.copy(alpha = 0.35f), RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconProCrownGlass(size = 20.dp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Strings.PRO_PROMO_DIALOG_TITLE,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = Strings.PRO_PROMO_DIALOG_SUBTITLE,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    AppTextField(
                        value = promoCode,
                        onValueChange = { promoCode = it.uppercase() },
                        label = Strings.PRO_PROMO_PLACEHOLDER,
                        modifier = Modifier.focusRequester(focusRequester),
                        imeAction = ImeAction.Done,
                        enabled = !isBusy && successMessage == null,
                        onImeAction = {
                            if (promoCode.isNotBlank() && !isBusy && successMessage == null) {
                                onApply(promoCode)
                            }
                        },
                    )

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    if (successMessage != null) {
                        Text(
                            text = successMessage,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = emeraldAccent,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AppButton(
                            text = Strings.CANCEL,
                            onClick = onDismiss,
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).height(48.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = GlassyButtonStyle.Glassy,
                            textStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            shape = RoundedCornerShape(14.dp),
                        )
                        AppButton(
                            text = Strings.PRO_PROMO_APPLY,
                            onClick = { onApply(promoCode) },
                            enabled = promoCode.isNotBlank() && !isBusy && successMessage == null,
                            isLoading = isBusy,
                            modifier = Modifier.weight(1.2f).height(48.dp),
                            containerColor = emeraldAccent,
                            contentColor = Color.White,
                            style = GlassyButtonStyle.Solid,
                            textStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            shape = RoundedCornerShape(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProFeatureRow(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    isHighlighted: Boolean,
) {
    val emeraldAccent = Color(0xFF10B981)
    val bgColor = if (isHighlighted) {
        emeraldAccent.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val borderColor = if (isHighlighted) {
        emeraldAccent.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(emeraldAccent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ProPillBadge(
    text: String,
    accentColor: Color = Color(0xFF10B981),
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accentColor.copy(alpha = 0.16f))
            .border(1.dp, accentColor.copy(alpha = 0.38f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ExportReportDialog(
    isExporting: Boolean,
    onExport: (format: String, period: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedFormat by remember { mutableStateOf("xlsx") }
    var selectedPeriod by remember { mutableStateOf("month") }
    val emeraldAccent = Color(0xFF10B981)

    Dialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .safeDrawingPadding()
                .clickable(enabled = !isExporting, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            GlassyCard(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable(enabled = true, onClick = {}),
                shape = RoundedCornerShape(22.dp),
                baseColor = MaterialTheme.colorScheme.surface,
                highlightColor = emeraldAccent.copy(alpha = 0.25f),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconExportSheetGlass(size = 22.dp)
                    Text(
                        text = Strings.PRO_EXPORT_DIALOG_TITLE,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "xlsx" to Strings.PRO_EXPORT_FORMAT_XLSX,
                        "csv" to Strings.PRO_EXPORT_FORMAT_CSV,
                    ).forEach { (fmt, label) ->
                        val active = selectedFormat == fmt
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (active) emeraldAccent.copy(alpha = 0.16f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .border(
                                    1.dp,
                                    if (active) emeraldAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { selectedFormat = fmt }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "month" to Strings.PRO_EXPORT_PERIOD_MONTH,
                        "quarter" to Strings.PRO_EXPORT_PERIOD_QUARTER,
                    ).forEach { (periodId, label) ->
                        val active = selectedPeriod == periodId
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (active) emeraldAccent.copy(alpha = 0.16f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .border(
                                    1.dp,
                                    if (active) emeraldAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { selectedPeriod = periodId }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "year" to Strings.PRO_EXPORT_PERIOD_YEAR,
                        "all" to Strings.PRO_EXPORT_PERIOD_ALL,
                    ).forEach { (periodId, label) ->
                        val active = selectedPeriod == periodId
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (active) emeraldAccent.copy(alpha = 0.16f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .border(
                                    1.dp,
                                    if (active) emeraldAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { selectedPeriod = periodId }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppButton(
                        text = Strings.CANCEL,
                        onClick = onDismiss,
                        enabled = !isExporting,
                        modifier = Modifier.weight(1f).height(48.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = GlassyButtonStyle.Glassy,
                        textStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        shape = RoundedCornerShape(14.dp),
                    )
                    AppButton(
                        text = Strings.PRO_EXPORT_DOWNLOAD,
                        onClick = { onExport(selectedFormat, selectedPeriod) },
                        enabled = !isExporting,
                        isLoading = isExporting,
                        modifier = Modifier.weight(1.3f).height(48.dp),
                        containerColor = emeraldAccent,
                        contentColor = Color.White,
                        style = GlassyButtonStyle.Solid,
                        textStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        shape = RoundedCornerShape(14.dp),
                    )
                }
            }
        }
        }
    }
}
