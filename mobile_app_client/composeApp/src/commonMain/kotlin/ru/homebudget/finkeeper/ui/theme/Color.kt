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
val IncomeCardBgLight = Color(0xFFD1FAE5)     // emerald-100 (darker than bg for contrast)
val ExpenseCardBgLight = Color(0xFFFEE2E2)    // red-100 (more visible than red-50)
val SavingsCardBgLight = Color(0xFFEFF6FF)    // blue-50 (for % в копилку)
val WarningCardBgLight = Color(0xFFFFFBEB)    // amber-50
val TealCardBgLight = Color(0xFFCCFBF1)       // teal-100 (for Накопления)
val AvailableCardBgLight = Color(0xFFFEFCE8)  // yellow-50 (for Доступно)

// ── Dark Theme Colors (futuristic neon-tech style with emerald branding) ──
val PrimaryDark = Color(0xFF00E676)           // Neon emerald green
val OnPrimaryDark = Color(0xFF003919)
val PrimaryContainerDark = Color(0xFF00522A)
val OnPrimaryContainerDark = Color(0xFF69FFB0)

val SecondaryDark = Color(0xFFB2FF59)         // Light lime
val OnSecondaryDark = Color(0xFF1A3300)
val SecondaryContainerDark = Color(0xFF2B5200)
val OnSecondaryContainerDark = Color(0xFFCFFF90)

val TertiaryDark = Color(0xFF4DB6AC)          // Muted teal
val OnTertiaryDark = Color(0xFF003028)

val ErrorDark = Color(0xFFFF8A80)             // Soft neon red
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

val BackgroundDark = Color(0xFF050806)        // Deepest emerald black
val OnBackgroundDark = Color(0xFFE0E6E2)
val SurfaceDark = Color(0xFF0F1411)           // Dark charcoal with subtle green tint
val OnSurfaceDark = Color(0xFFE0E6E2)
val SurfaceVariantDark = Color(0xFF1A211D)    // Slightly lighter dark green-grey
val OnSurfaceVariantDark = Color(0xFF8DA396)  // Muted sage text
val OutlineDark = Color(0xFF2D3B33)           // Subtle green-grey border
val OutlineVariantDark = Color(0xFF1F2924)

// Navigation bar for dark theme
val NavBarDark = Color(0xFF080C0A)            // Almost black with green hint
val NavBarContentDark = Color(0xFF00E676)     // Neon green active
val NavBarContentInactiveDark = Color(0xFF4F665C) // Muted sage

// Specific semantic colors for dark theme (neon/glow style)
val IncomeColorDark = Color(0xFF00E676)       // Neon green
val ExpenseColorDark = Color(0xFFFF5252)      // Neon red
val SavingsColorDark = Color(0xFF00E5FF)      // Cyan/Electric Blue (more futuristic than standard blue)
val WarningDark = Color(0xFFFFD740)           // Bright amber
val TealColorDark = Color(0xFF1DE9B6)         // Neon Teal
val AvailableColorDark = Color(0xFF76FF03)    // Electric lime

// Card backgrounds for dark (deep with subtle color tints)
val IncomeCardBgDark = Color(0xFF0A1F14)      // Deep emerald-black
val ExpenseCardBgDark = Color(0xFF2C1014)     // Deep red-black (slightly lighter for visibility)
val SavingsCardBgDark = Color(0xFF0A1A22)     // Deep cyan-black
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
