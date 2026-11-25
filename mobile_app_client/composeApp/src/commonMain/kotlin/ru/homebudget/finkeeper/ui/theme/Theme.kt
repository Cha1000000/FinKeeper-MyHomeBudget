package ru.homebudget.finkeeper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import finkeeper24.composeapp.generated.resources.Res
import finkeeper24.composeapp.generated.resources.inter_bold
import finkeeper24.composeapp.generated.resources.inter_medium
import finkeeper24.composeapp.generated.resources.inter_regular
import finkeeper24.composeapp.generated.resources.inter_semibold
import finkeeper24.composeapp.generated.resources.manrope_bold
import finkeeper24.composeapp.generated.resources.manrope_semibold
import org.jetbrains.compose.resources.Font
import ru.homebudget.finkeeper.util.ConfigureSystemBars

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
)

private val CyberpunkColorScheme = darkColorScheme(
    primary = PrimaryCyberpunk,
    onPrimary = OnPrimaryCyberpunk,
    primaryContainer = PrimaryContainerCyberpunk,
    onPrimaryContainer = OnPrimaryContainerCyberpunk,
    secondary = SecondaryCyberpunk,
    onSecondary = OnSecondaryCyberpunk,
    secondaryContainer = SecondaryContainerCyberpunk,
    onSecondaryContainer = OnSecondaryContainerCyberpunk,
    tertiary = TertiaryCyberpunk,
    onTertiary = OnTertiaryCyberpunk,
    error = ErrorCyberpunk,
    onError = OnErrorCyberpunk,
    errorContainer = ErrorContainerCyberpunk,
    onErrorContainer = OnErrorContainerCyberpunk,
    background = BackgroundCyberpunk,
    onBackground = OnBackgroundCyberpunk,
    surface = SurfaceCyberpunk,
    onSurface = OnSurfaceCyberpunk,
    surfaceVariant = SurfaceVariantCyberpunk,
    onSurfaceVariant = OnSurfaceVariantCyberpunk,
    outline = OutlineCyberpunk,
    outlineVariant = OutlineVariantCyberpunk,
)

private val DarkNightColorScheme = darkColorScheme(
    primary = PrimaryDarkNight,
    onPrimary = OnPrimaryDarkNight,
    primaryContainer = PrimaryContainerDarkNight,
    onPrimaryContainer = OnPrimaryContainerDarkNight,
    secondary = SecondaryDarkNight,
    onSecondary = OnSecondaryDarkNight,
    secondaryContainer = SecondaryContainerDarkNight,
    onSecondaryContainer = OnSecondaryContainerDarkNight,
    tertiary = TertiaryDarkNight,
    onTertiary = OnTertiaryDarkNight,
    error = ErrorDarkNight,
    onError = OnErrorDarkNight,
    errorContainer = ErrorContainerDarkNight,
    onErrorContainer = OnErrorContainerDarkNight,
    background = BackgroundDarkNight,
    onBackground = OnBackgroundDarkNight,
    surface = SurfaceDarkNight,
    onSurface = OnSurfaceDarkNight,
    surfaceVariant = SurfaceVariantDarkNight,
    onSurfaceVariant = OnSurfaceVariantDarkNight,
    outline = OutlineDarkNight,
    outlineVariant = OutlineVariantDarkNight,
)

private val BlueOceanColorScheme = darkColorScheme(
    primary = PrimaryBlueOcean,
    onPrimary = OnPrimaryBlueOcean,
    primaryContainer = PrimaryContainerBlueOcean,
    onPrimaryContainer = OnPrimaryContainerBlueOcean,
    secondary = SecondaryBlueOcean,
    onSecondary = OnSecondaryBlueOcean,
    secondaryContainer = SecondaryContainerBlueOcean,
    onSecondaryContainer = OnSecondaryContainerBlueOcean,
    tertiary = TertiaryBlueOcean,
    onTertiary = OnTertiaryBlueOcean,
    error = ErrorBlueOcean,
    onError = OnErrorBlueOcean,
    errorContainer = ErrorContainerBlueOcean,
    onErrorContainer = OnErrorContainerBlueOcean,
    background = BackgroundBlueOcean,
    onBackground = OnBackgroundBlueOcean,
    surface = SurfaceBlueOcean,
    onSurface = OnSurfaceBlueOcean,
    surfaceVariant = SurfaceVariantBlueOcean,
    onSurfaceVariant = OnSurfaceVariantBlueOcean,
    outline = OutlineBlueOcean,
    outlineVariant = OutlineVariantBlueOcean,
)

enum class ThemePalette {
    Light,
    Dark,
    Cyberpunk,
    DarkNight,
    BlueOcean,
}

data class AppSemanticColors(
    val incomeColor: androidx.compose.ui.graphics.Color,
    val expenseColor: androidx.compose.ui.graphics.Color,
    val savingsColor: androidx.compose.ui.graphics.Color,
    val warningColor: androidx.compose.ui.graphics.Color,
    val tealColor: androidx.compose.ui.graphics.Color,
    val availableColor: androidx.compose.ui.graphics.Color,
    val incomeCardBg: androidx.compose.ui.graphics.Color,
    val expenseCardBg: androidx.compose.ui.graphics.Color,
    val savingsCardBg: androidx.compose.ui.graphics.Color,
    val warningCardBg: androidx.compose.ui.graphics.Color,
    val tealCardBg: androidx.compose.ui.graphics.Color,
    val availableCardBg: androidx.compose.ui.graphics.Color,
    val navBarColor: androidx.compose.ui.graphics.Color,
    val navBarContent: androidx.compose.ui.graphics.Color,
    val navBarContentInactive: androidx.compose.ui.graphics.Color,
    val backupBlue: androidx.compose.ui.graphics.Color,
    val restorePink: androidx.compose.ui.graphics.Color,
    val logoutRed: androidx.compose.ui.graphics.Color,
)

val LightSemanticColors = AppSemanticColors(
    incomeColor = IncomeColorLight,
    expenseColor = ExpenseColorLight,
    savingsColor = SavingsColorLight,
    warningColor = WarningLight,
    tealColor = TealColorLight,
    availableColor = AvailableColorLight,
    incomeCardBg = IncomeCardBgLight,
    expenseCardBg = ExpenseCardBgLight,
    savingsCardBg = SavingsCardBgLight,
    warningCardBg = WarningCardBgLight,
    tealCardBg = TealCardBgLight,
    availableCardBg = AvailableCardBgLight,
    navBarColor = NavBarLight,
    navBarContent = NavBarContentLight,
    navBarContentInactive = NavBarContentInactiveLight,
    backupBlue = BackupBlueDarkNight, // Fallback to DarkNight colors if not defined for Light
    restorePink = RestorePinkDarkNight,
    logoutRed = LogoutRedDarkNight,
)

val DarkSemanticColors = AppSemanticColors(
    incomeColor = IncomeColorDark,
    expenseColor = ExpenseColorDark,
    savingsColor = SavingsColorDark,
    warningColor = WarningDark,
    tealColor = TealColorDark,
    availableColor = AvailableColorDark,
    incomeCardBg = IncomeCardBgDark,
    expenseCardBg = ExpenseCardBgDark,
    savingsCardBg = SavingsCardBgDark,
    warningCardBg = WarningCardBgDark,
    tealCardBg = TealCardBgDark,
    availableCardBg = AvailableCardBgDark,
    navBarColor = NavBarDark,
    navBarContent = NavBarContentDark,
    navBarContentInactive = NavBarContentInactiveDark,
    backupBlue = BackupBlueDarkNight,
    restorePink = RestorePinkDarkNight,
    logoutRed = LogoutRedDarkNight,
)

val DarkNightSemanticColors = AppSemanticColors(
    incomeColor = IncomeColorDarkNight,
    expenseColor = ExpenseColorDarkNight,
    savingsColor = SavingsColorDarkNight,
    warningColor = WarningDarkNight,
    tealColor = TealColorDarkNight,
    availableColor = AvailableColorDarkNight,
    incomeCardBg = IncomeCardBgDarkNight,
    expenseCardBg = ExpenseCardBgDarkNight,
    savingsCardBg = SavingsCardBgDarkNight,
    warningCardBg = WarningCardBgDarkNight,
    tealCardBg = TealCardBgDarkNight,
    availableCardBg = AvailableCardBgDarkNight,
    navBarColor = NavBarDarkNight,
    navBarContent = NavBarContentDarkNight,
    navBarContentInactive = NavBarContentInactiveDarkNight,
    backupBlue = BackupBlueDarkNight,
    restorePink = RestorePinkDarkNight,
    logoutRed = LogoutRedDarkNight,
)

val CyberpunkSemanticColors = AppSemanticColors(
    incomeColor = IncomeColorCyberpunk,
    expenseColor = ExpenseColorCyberpunk,
    savingsColor = SavingsColorCyberpunk,
    warningColor = WarningCyberpunk,
    tealColor = TealColorCyberpunk,
    availableColor = AvailableColorCyberpunk,
    incomeCardBg = IncomeCardBgCyberpunk,
    expenseCardBg = ExpenseCardBgCyberpunk,
    savingsCardBg = SavingsCardBgCyberpunk,
    warningCardBg = WarningCardBgCyberpunk,
    tealCardBg = TealCardBgCyberpunk,
    availableCardBg = AvailableCardBgCyberpunk,
    navBarColor = NavBarCyberpunk,
    navBarContent = NavBarContentCyberpunk,
    navBarContentInactive = NavBarContentInactiveCyberpunk,
    backupBlue = BackupBlueDarkNight,
    restorePink = RestorePinkDarkNight,
    logoutRed = LogoutRedDarkNight,
)

val BlueOceanSemanticColors = AppSemanticColors(
    incomeColor = IncomeColorBlueOcean,
    expenseColor = ExpenseColorBlueOcean,
    savingsColor = SavingsColorBlueOcean,
    warningColor = WarningBlueOcean,
    tealColor = TealColorBlueOcean,
    availableColor = AvailableColorBlueOcean,
    incomeCardBg = IncomeCardBgBlueOcean,
    expenseCardBg = ExpenseCardBgBlueOcean,
    savingsCardBg = SavingsCardBgBlueOcean,
    warningCardBg = WarningCardBgBlueOcean,
    tealCardBg = TealCardBgBlueOcean,
    availableCardBg = AvailableCardBgBlueOcean,
    navBarColor = NavBarBlueOcean,
    navBarContent = NavBarContentBlueOcean,
    navBarContentInactive = NavBarContentInactiveBlueOcean,
    backupBlue = BackupBlueBlueOcean,
    restorePink = RestorePinkBlueOcean,
    logoutRed = LogoutRedBlueOcean,
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }

object AppTheme {
    val semanticColors: AppSemanticColors
        @Composable get() = LocalSemanticColors.current
}

@Composable
private fun appTypography(): Typography {
    val manrope = FontFamily(
        Font(Res.font.manrope_semibold, FontWeight.SemiBold),
        Font(Res.font.manrope_bold, FontWeight.Bold),
    )
    val inter = FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_medium, FontWeight.Medium),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
        Font(Res.font.inter_bold, FontWeight.Bold),
    )
    val tnum = "tnum"
    return Typography(
        displayLarge = TextStyle(fontFamily = manrope, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = tnum),
        displayMedium = TextStyle(fontFamily = manrope, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp, fontFeatureSettings = tnum),
        displaySmall = TextStyle(fontFamily = manrope, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp, fontFeatureSettings = tnum),
        headlineLarge = TextStyle(fontFamily = manrope, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp, fontFeatureSettings = tnum),
        headlineMedium = TextStyle(fontFamily = manrope, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp, fontFeatureSettings = tnum),
        headlineSmall = TextStyle(fontFamily = manrope, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp, fontFeatureSettings = tnum),
        titleLarge = TextStyle(fontFamily = manrope, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp, fontFeatureSettings = tnum),
        titleMedium = TextStyle(fontFamily = manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.1).sp, fontFeatureSettings = tnum),
        titleSmall = TextStyle(fontFamily = inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = tnum),
        bodyLarge = TextStyle(fontFamily = inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, fontFeatureSettings = tnum),
        bodyMedium = TextStyle(fontFamily = inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = tnum),
        bodySmall = TextStyle(fontFamily = inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = tnum),
        labelLarge = TextStyle(fontFamily = inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = tnum),
        labelMedium = TextStyle(fontFamily = inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp, fontFeatureSettings = tnum),
        labelSmall = TextStyle(fontFamily = inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.2.sp, fontFeatureSettings = tnum),
    )
}

@Composable
fun FinKeeperTheme(
    palette: ThemePalette = if (isSystemInDarkTheme()) ThemePalette.Dark else ThemePalette.Light,
    content: @Composable () -> Unit
) {
    val colorScheme =
        when (palette) {
            ThemePalette.Light -> LightColorScheme
            ThemePalette.Dark -> DarkColorScheme
            ThemePalette.Cyberpunk -> CyberpunkColorScheme
            ThemePalette.DarkNight -> DarkNightColorScheme
            ThemePalette.BlueOcean -> BlueOceanColorScheme
        }
    val semanticColors =
        when (palette) {
            ThemePalette.Light -> LightSemanticColors
            ThemePalette.Dark -> DarkSemanticColors
            ThemePalette.Cyberpunk -> CyberpunkSemanticColors
            ThemePalette.DarkNight -> DarkNightSemanticColors
            ThemePalette.BlueOcean -> BlueOceanSemanticColors
        }

    ConfigureSystemBars(isDark = palette != ThemePalette.Light)
    val typography = appTypography()

    CompositionLocalProvider(LocalSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
