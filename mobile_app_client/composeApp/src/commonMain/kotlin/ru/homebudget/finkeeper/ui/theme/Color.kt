package ru.homebudget.finkeeper.ui.theme

import androidx.compose.ui.graphics.Color

// ── Light Theme Colors (from web client index.css) ──
val PrimaryLight = Color(0xFF1B905B)         // --color-primary: #1b905b
val OnPrimaryLight = Color.White
val PrimaryContainerLight = Color(0xFFB8F0D5)
val OnPrimaryContainerLight = Color(0xFF00391A)

val SecondaryLight = Color(0xFF6B8E23)        // --color-brand: #6b8e23
val OnSecondaryLight = Color.White
val SecondaryContainerLight = Color(0xFFD4E8A0)
val OnSecondaryContainerLight = Color(0xFF1A2600)

val TertiaryLight = Color(0xFF558B2F)         // --color-success: #558b2f
val OnTertiaryLight = Color.White

val ErrorLight = Color(0xFFC62828)            // --color-danger: #c62828
val OnErrorLight = Color.White
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

// Background: soft emerald tint matching frontend gradient (from-green-50/20 via-emerald-50 to-teal-100)
val BackgroundLight = Color(0xFFECFDF5)       // emerald-50 — main content bg like frontend
val OnBackgroundLight = Color(0xFF1A1C1E)     // --color-text-main: #1e293b
val SurfaceLight = Color(0xFFFFFFFF)          // --color-surface: #ffffff
val OnSurfaceLight = Color(0xFF1E293B)
val SurfaceVariantLight = Color(0xFFF8FAFC)
val OnSurfaceVariantLight = Color(0xFF64748B) // --color-text-muted: #64748b
val OutlineLight = Color(0xFFE2E8F0)
val OutlineVariantLight = Color(0xFFF1F5F9)

// Navigation bar colors (matching frontend mobile nav: emerald-900/700 gradient)
val NavBarLight = Color(0xFF064E3B)           // emerald-900
val NavBarContentLight = Color(0xFFFFFFFF)
val NavBarContentInactiveLight = Color(0xBBD1FAE5) // emerald-100 with 73% alpha

// Specific semantic colors for light theme
val IncomeColorLight = Color(0xFF047857)      // emerald-700
val ExpenseColorLight = Color(0xFFB91C1C)     // red-700
val SavingsColorLight = Color(0xFF1D4ED8)     // blue-700
val WarningLight = Color(0xFFB45309)          // amber-700
val TealColorLight = Color(0xFF0F766E)        // teal-700 for Накопления
val AvailableColorLight = Color(0xFF4D7C0F)   // lime-700 for Доступно

// Card backgrounds for light
val IncomeCardBgLight = Color(0xFFD1FAE5)     // emerald-100 (darker than bg for contrast)
val ExpenseCardBgLight = Color(0xFFFEE2E2)    // red-100 (more visible than red-50)
val SavingsCardBgLight = Color(0xFFEFF6FF)    // blue-50 (for % в копилку)
val WarningCardBgLight = Color(0xFFFFFBEB)    // amber-50
val TealCardBgLight = Color(0xFFCCFBF1)       // teal-100 (for Накопления)
val AvailableCardBgLight = Color(0xFFFEFCE8)  // yellow-50 (for Доступно)

// ── Dark Theme Colors (modern soft dark, eye-friendly) ──
val PrimaryDark = Color(0xFFDCEB57)
val OnPrimaryDark = Color(0xFF1A2006)
val PrimaryContainerDark = Color(0xFF2B3316)
val OnPrimaryContainerDark = Color(0xFFF1F8B5)

val SecondaryDark = Color(0xFF82966A)
val OnSecondaryDark = Color(0xFF11170B)
val SecondaryContainerDark = Color(0xFF243022)
val OnSecondaryContainerDark = Color(0xFFC6D8B1)

val TertiaryDark = Color(0xFF8CA8C3)
val OnTertiaryDark = Color(0xFF0F1A24)

val ErrorDark = Color(0xFFFFA4A0)
val OnErrorDark = Color(0xFF601410)
val ErrorContainerDark = Color(0xFF7E2A26)
val OnErrorContainerDark = Color(0xFFFFDAD7)

val BackgroundDark = Color(0xFF080B0A)
val OnBackgroundDark = Color(0xFFE7ECE8)
val SurfaceDark = Color(0xFF121715)
val OnSurfaceDark = Color(0xFFE7ECE8)
val SurfaceVariantDark = Color(0xFF1D2420)
val OnSurfaceVariantDark = Color(0xFFA6B3AA)
val OutlineDark = Color(0xFF323D37)
val OutlineVariantDark = Color(0xFF252E29)

// Navigation bar for dark theme
val NavBarDark = Color(0xFF0D110F)
val NavBarContentDark = Color(0xFFDCEB57)
val NavBarContentInactiveDark = Color(0xFF6B786F)

// Specific semantic colors for dark theme
val IncomeColorDark = Color(0xFFAAD769)
val ExpenseColorDark = Color(0xFFE57B7B)
val SavingsColorDark = Color(0xFFD7B25A)
val WarningDark = Color(0xFFE7D96A)
val TealColorDark = Color(0xFF79B8A0)
val AvailableColorDark = Color(0xFFA8C96C)

// Card backgrounds for dark
val IncomeCardBgDark = Color(0xFF1B241A)
val ExpenseCardBgDark = Color(0xFF2B1C1D)
val SavingsCardBgDark = Color(0xFF2B271B)
val WarningCardBgDark = Color(0xFF302D1D)
val TealCardBgDark = Color(0xFF1D2A25)
val AvailableCardBgDark = Color(0xFF252C1C)

// ── Cyberpunk Theme Colors (old neon dark theme) ──
val PrimaryCyberpunk = Color(0xFF00E676)
val OnPrimaryCyberpunk = Color(0xFF003919)
val PrimaryContainerCyberpunk = Color(0xFF00522A)
val OnPrimaryContainerCyberpunk = Color(0xFF69FFB0)

val SecondaryCyberpunk = Color(0xFFB2FF59)
val OnSecondaryCyberpunk = Color(0xFF1A3300)
val SecondaryContainerCyberpunk = Color(0xFF2B5200)
val OnSecondaryContainerCyberpunk = Color(0xFFCFFF90)

val TertiaryCyberpunk = Color(0xFF4DB6AC)
val OnTertiaryCyberpunk = Color(0xFF003028)

val ErrorCyberpunk = Color(0xFFFF8A80)
val OnErrorCyberpunk = Color(0xFF690005)
val ErrorContainerCyberpunk = Color(0xFF93000A)
val OnErrorContainerCyberpunk = Color(0xFFFFDAD6)

val BackgroundCyberpunk = Color(0xFF050806)
val OnBackgroundCyberpunk = Color(0xFFE0E6E2)
val SurfaceCyberpunk = Color(0xFF0F1411)
val OnSurfaceCyberpunk = Color(0xFFE0E6E2)
val SurfaceVariantCyberpunk = Color(0xFF1A211D)
val OnSurfaceVariantCyberpunk = Color(0xFF8DA396)
val OutlineCyberpunk = Color(0xFF2D3B33)
val OutlineVariantCyberpunk = Color(0xFF1F2924)

val NavBarCyberpunk = Color(0xFF080C0A)
val NavBarContentCyberpunk = Color(0xFF00E676)
val NavBarContentInactiveCyberpunk = Color(0xFF4F665C)

val IncomeColorCyberpunk = Color(0xFF00E676)
val ExpenseColorCyberpunk = Color(0xFFFF5252)
val SavingsColorCyberpunk = Color(0xFF00E5FF)
val WarningCyberpunk = Color(0xFFFFD740)
val TealColorCyberpunk = Color(0xFF1DE9B6)
val AvailableColorCyberpunk = Color(0xFF76FF03)

val IncomeCardBgCyberpunk = Color(0xFF0A1F14)
val ExpenseCardBgCyberpunk = Color(0xFF2C1014)
val SavingsCardBgCyberpunk = Color(0xFF0A1A22)
val WarningCardBgCyberpunk = Color(0xFF1F1A08)
val TealCardBgCyberpunk = Color(0xFF0A1F1C)
val AvailableCardBgCyberpunk = Color(0xFF141F08)

// ── Chart Colors ──
val ChartColors = listOf(
    Color(0xFF1B905B),
    Color(0xFF2563EB),
    Color(0xFFDC2626),
    Color(0xFFD97706),
    Color(0xFF7C3AED),
    Color(0xFFDB2777),
    Color(0xFF0891B2),
    Color(0xFF65A30D),
    Color(0xFFEA580C),
    Color(0xFF4F46E5),
)

// ── Dark Night Theme Colors (Reference based) ──
val PrimaryDarkNight = Color(0xFF9773FE)         // Purple
val OnPrimaryDarkNight = Color(0xFFFFFFFF)
val PrimaryContainerDarkNight = Color(0xFF2D1F4C) // Darker purple
val OnPrimaryContainerDarkNight = Color(0xFFEADBFF)

val SecondaryDarkNight = Color(0xFF75E8FF)       // Cyan
val OnSecondaryDarkNight = Color(0xFF003544)
val SecondaryContainerDarkNight = Color(0xFF004D61)
val OnSecondaryContainerDarkNight = Color(0xFFBCE9FF)

val TertiaryDarkNight = Color(0xFFECECEC)        // Light Grey / White accent
val OnTertiaryDarkNight = Color(0xFF1A1625)

val ErrorDarkNight = Color(0xFFFFB4AB)
val OnErrorDarkNight = Color(0xFF690005)
val ErrorContainerDarkNight = Color(0xFF93000A)
val OnErrorContainerDarkNight = Color(0xFFFFDAD6)

val BackgroundDarkNight = Color(0xFF0F111A)      // Midnight Blue background
val OnBackgroundDarkNight = Color(0xFFECECEC)
val SurfaceDarkNight = Color(0xFF1B1E2E)         // Slightly lighter Midnight Blue
val OnSurfaceDarkNight = Color(0xFFECECEC)
val SurfaceVariantDarkNight = Color(0xFF25293D)
val OnSurfaceVariantDarkNight = Color(0xFFD3D3D3) // Grey text
val OutlineDarkNight = Color(0xFF3F445E)
val OutlineVariantDarkNight = Color(0xFF25293D)

val NavBarDarkNight = Color(0xFF0F111A)
val NavBarContentDarkNight = Color(0xFF9773FE)
val NavBarContentInactiveDarkNight = Color(0xFF49454F)

val IncomeColorDarkNight = Color(0xFF75E8FF)     // Cyan for Income
val ExpenseColorDarkNight = Color(0xFFFF5252)    // Bright Red for Expense
val SavingsColorDarkNight = Color(0xFF9773FE)    // Purple for Savings
val WarningDarkNight = Color(0xFFFFD740)
val TealColorDarkNight = Color(0xFF64FFDA)
val AvailableColorDarkNight = Color(0xFFB2FF59)

val IncomeCardBgDarkNight = Color(0xFF0A181A)
val ExpenseCardBgDarkNight = Color(0xFF1A0A0A)
val SavingsCardBgDarkNight = Color(0xFF140F1F)
val WarningCardBgDarkNight = Color(0xFF1A160A)
val TealCardBgDarkNight = Color(0xFF0A1A16)
val AvailableCardBgDarkNight = Color(0xFF121A0A)

// ── Settings Screen Specific Colors (Dark Night) ──
val BackupBlueDarkNight = Color(0xFF1E88E5)       // Deep Blue
val RestorePinkDarkNight = Color(0xFFAD1457)      // Dark Pink
val LogoutRedDarkNight = Color(0xFFB71C1C)       // Deep Red

// ── Blue Ocean Theme Colors (refined for stronger semantic accents) ──
val PrimaryBlueOcean = Color(0xFFB0C4FF)            // Bright ocean mist highlight
val OnPrimaryBlueOcean = Color(0xFF12225E)
val PrimaryContainerBlueOcean = Color(0xFF4D5FD2)   // Indigo CTA / active state
val OnPrimaryContainerBlueOcean = Color(0xFFF2F5FF)

val SecondaryBlueOcean = Color(0xFF9AA8FF)          // Soft periwinkle accent
val OnSecondaryBlueOcean = Color(0xFF141D57)
val SecondaryContainerBlueOcean = Color(0xFF2B376F)
val OnSecondaryContainerBlueOcean = Color(0xFFE8ECFF)

val TertiaryBlueOcean = Color(0xFF75DEFF)           // Cyan chart / support accent
val OnTertiaryBlueOcean = Color(0xFF002A3E)

val ErrorBlueOcean = Color(0xFFFFA0B5)
val OnErrorBlueOcean = Color(0xFF5D1123)
val ErrorContainerBlueOcean = Color(0xFF7A2841)
val OnErrorContainerBlueOcean = Color(0xFFFFD9E0)

val BackgroundBlueOcean = Color(0xFF11162C)         // Deep night ocean base
val OnBackgroundBlueOcean = Color(0xFFF2F4FF)
val SurfaceBlueOcean = Color(0xFF1C2342)            // Neutral dark card surface
val OnSurfaceBlueOcean = Color(0xFFF2F4FF)
val SurfaceVariantBlueOcean = Color(0xFF242C52)
val OnSurfaceVariantBlueOcean = Color(0xFFC5CCF3)
val OutlineBlueOcean = Color(0xFF515D93)
val OutlineVariantBlueOcean = Color(0xFF343D6E)

val NavBarBlueOcean = Color(0xFF18204A)
val NavBarContentBlueOcean = Color(0xFFAAC0FF)
val NavBarContentInactiveBlueOcean = Color(0xFF7380B7)

val IncomeColorBlueOcean = Color(0xFF71DEFF)
val ExpenseColorBlueOcean = Color(0xFFFF98A9)
val SavingsColorBlueOcean = Color(0xFFA183FF)
val WarningBlueOcean = Color(0xFFB8CEFF)
val TealColorBlueOcean = Color(0xFF62F0D2)
val AvailableColorBlueOcean = Color(0xFFB3F66D)

val IncomeCardBgBlueOcean = Color(0xFF10232F)
val ExpenseCardBgBlueOcean = Color(0xFF311927)
val SavingsCardBgBlueOcean = Color(0xFF21183D)
val WarningCardBgBlueOcean = Color(0xFF2A345E)
val TealCardBgBlueOcean = Color(0xFF102B2C)
val AvailableCardBgBlueOcean = Color(0xFF1E2E1D)

val BackupBlueBlueOcean = Color(0xFF4D92FF)
val RestorePinkBlueOcean = Color(0xFF8E71FF)
val LogoutRedBlueOcean = Color(0xFF7A2E47)
