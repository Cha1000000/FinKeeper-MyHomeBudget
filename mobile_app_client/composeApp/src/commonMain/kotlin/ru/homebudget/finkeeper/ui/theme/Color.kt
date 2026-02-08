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
val IncomeColorLight = Color(0xFF059669)      // emerald-600
val ExpenseColorLight = Color(0xFFDC2626)     // red-600
val SavingsColorLight = Color(0xFF2563EB)     // blue-600
val WarningLight = Color(0xFFD4A017)          // --color-warning
val TealColorLight = Color(0xFF0D9488)        // teal-600 for Накопления
val AvailableColorLight = Color(0xFF6B8E23)   // brand color for Доступно

// Card backgrounds for light
val IncomeCardBgLight = Color(0xFFECFDF5)     // emerald-50
val ExpenseCardBgLight = Color(0xFFFEF2F2)    // red-50
val SavingsCardBgLight = Color(0xFFEFF6FF)    // blue-50 (for % в копилку)
val WarningCardBgLight = Color(0xFFFFFBEB)    // amber-50
val TealCardBgLight = Color(0xFFCCFBF1)       // teal-100 (for Накопления)
val AvailableCardBgLight = Color(0xFFFEFCE8)  // yellow-50 (for Доступно)

// ── Dark Theme Colors (futuristic neon-tech style) ──
val PrimaryDark = Color(0xFF00E676)           // Neon emerald green
val OnPrimaryDark = Color(0xFF003919)
val PrimaryContainerDark = Color(0xFF003D20)
val OnPrimaryContainerDark = Color(0xFF69FFB0)

val SecondaryDark = Color(0xFF76FF03)         // Electric lime
val OnSecondaryDark = Color(0xFF1A3300)
val SecondaryContainerDark = Color(0xFF1B4400)
val OnSecondaryContainerDark = Color(0xFFA8FF60)

val TertiaryDark = Color(0xFF00BFA5)          // Teal neon
val OnTertiaryDark = Color(0xFF003028)

val ErrorDark = Color(0xFFFF5252)             // Bright neon red
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF5C0011)
val OnErrorContainerDark = Color(0xFFFFB4AB)

val BackgroundDark = Color(0xFF0A0E14)        // Deep space black with blue tint
val OnBackgroundDark = Color(0xFFE0E6ED)
val SurfaceDark = Color(0xFF111923)           // Dark navy card with subtle glow feel
val OnSurfaceDark = Color(0xFFE0E6ED)
val SurfaceVariantDark = Color(0xFF182230)    // Slightly lighter navy
val OnSurfaceVariantDark = Color(0xFF8899AA)
val OutlineDark = Color(0xFF263545)           // Subtle border
val OutlineVariantDark = Color(0xFF152030)

// Navigation bar for dark theme
val NavBarDark = Color(0xFF0D1520)            // Deep navy for nav
val NavBarContentDark = Color(0xFF00E676)     // Neon green active
val NavBarContentInactiveDark = Color(0xFF4A6070) // Muted steel

// Specific semantic colors for dark theme (neon/glow style)
val IncomeColorDark = Color(0xFF00E676)       // Neon green
val ExpenseColorDark = Color(0xFFFF5252)      // Neon red
val SavingsColorDark = Color(0xFF448AFF)      // Electric blue
val WarningDark = Color(0xFFFFD740)           // Bright amber
val TealColorDark = Color(0xFF00BFA5)         // Neon teal
val AvailableColorDark = Color(0xFF76FF03)    // Electric lime

// Card backgrounds for dark (deep with subtle color tints)
val IncomeCardBgDark = Color(0xFF0A1F14)      // Deep emerald-black
val ExpenseCardBgDark = Color(0xFF1F0A0E)     // Deep red-black
val SavingsCardBgDark = Color(0xFF0A1428)     // Deep blue-black
val WarningCardBgDark = Color(0xFF1F1A08)     // Deep amber-black
val TealCardBgDark = Color(0xFF0A1F1C)        // Deep teal-black
val AvailableCardBgDark = Color(0xFF141F08)   // Deep lime-black

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
