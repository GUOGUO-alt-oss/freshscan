package com.example.freshscan.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════
// M3 Color System — 日系精致风格 (Japanese Minimalist)
// Seed: #4A6741 (Moss Green / 苔藓绿)
// Generated via Material Theme Builder — all 29 roles explicit
// ═══════════════════════════════════════════════════════════════

// ── Light ColorScheme ──

val LightPrimary = Color(0xFF3D5A35)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE8EDE4)
val LightOnPrimaryContainer = Color(0xFF1A2C14)
val LightSecondary = Color(0xFF58694E)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFDDE4D6)
val LightOnSecondaryContainer = Color(0xFF162312)
val LightTertiary = Color(0xFF4A6B5D)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFCCF2E1)
val LightOnTertiaryContainer = Color(0xFF002117)
val LightError = Color(0xFFC5554A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD5)
val LightOnErrorContainer = Color(0xFF410002)
val LightBackground = Color(0xFFF8F7F4)
val LightOnBackground = Color(0xFF1B1C1A)
val LightSurface = Color(0xFFFBFAF8)
val LightOnSurface = Color(0xFF1B1C1A)
val LightSurfaceVariant = Color(0xFFE3E2DC)
val LightOnSurfaceVariant = Color(0xFF44483F)
val LightOutline = Color(0xFFC5C2BB)
val LightOutlineVariant = Color(0xFFDCD9D2)
val LightInverseSurface = Color(0xFF30342C)
val LightInverseOnSurface = Color(0xFFF1F0EB)
val LightInversePrimary = Color(0xFFB2CC9E)
val LightSurfaceTint = Color(0xFF3D5A35)
val LightScrim = Color(0xFF000000)
val LightShadow = Color(0xFF000000)

// ── Dark ColorScheme ──

val DarkPrimary = Color(0xFFB2CC9E)
val DarkOnPrimary = Color(0xFF1A2C14)
val DarkPrimaryContainer = Color(0xFF2A4022)
val DarkOnPrimaryContainer = Color(0xFFE8EDE4)
val DarkSecondary = Color(0xFFC1CAAD)
val DarkOnSecondary = Color(0xFF2A3422)
val DarkSecondaryContainer = Color(0xFF404D34)
val DarkOnSecondaryContainer = Color(0xFFDDE4D6)
val DarkTertiary = Color(0xFFB0D5C6)
val DarkOnTertiary = Color(0xFF1B382C)
val DarkTertiaryContainer = Color(0xFF304F43)
val DarkOnTertiaryContainer = Color(0xFFCCF2E1)
val DarkError = Color(0xFFE09090)
val DarkOnError = Color(0xFF601A18)
val DarkErrorContainer = Color(0xFF8C2A25)
val DarkOnErrorContainer = Color(0xFFFFDAD5)
val DarkBackground = Color(0xFF141613)
val DarkOnBackground = Color(0xFFE3E2DC)
val DarkSurface = Color(0xFF1A1C19)
val DarkOnSurface = Color(0xFFE3E2DC)
val DarkSurfaceVariant = Color(0xFF2A2D26)
val DarkOnSurfaceVariant = Color(0xFFC5C2BB)
val DarkOutline = Color(0xFF8F8C85)
val DarkOutlineVariant = Color(0xFF44483F)
val DarkInverseSurface = Color(0xFFE3E2DC)
val DarkInverseOnSurface = Color(0xFF30342C)
val DarkInversePrimary = Color(0xFF3D5A35)
val DarkSurfaceTint = Color(0xFFB2CC9E)
val DarkScrim = Color(0xFF000000)
val DarkShadow = Color(0xFF000000)

// ═══════════════════════════════════════════════════════════════
// Semantic Color Tokens (domain-specific, via CompositionLocal)
// ═══════════════════════════════════════════════════════════════

/** Light semantic colors for freshness / expiry domain. */
val LightSemanticColors = SemanticColors(
    freshnessHigh = Color(0xFF3D5A35),
    freshnessMedium = Color(0xFFC5862D),
    freshnessLow = Color(0xFFB05045),
    fridgeExpiring = Color(0xFFC5862D),
    fridgeExpired = Color(0xFFB05045)
)

/** Dark semantic colors for freshness / expiry domain. */
val DarkSemanticColors = SemanticColors(
    freshnessHigh = Color(0xFFB2CC9E),
    freshnessMedium = Color(0xFFF0C080),
    freshnessLow = Color(0xFFE09090),
    fridgeExpiring = Color(0xFFF0C080),
    fridgeExpired = Color(0xFFE09090)
)

/**
 * Domain-specific semantic colors for freshness and expiry.
 *
 * Injected via [LocalSemanticColors] CompositionLocal.
 * Follows the Jetsnack "AdditionalColors" pattern.
 */
data class SemanticColors(
    val freshnessHigh: Color,
    val freshnessMedium: Color,
    val freshnessLow: Color,
    val fridgeExpiring: Color,
    val fridgeExpired: Color
)

// ═══════════════════════════════════════════════════════════════
// Deprecated — old color constants (kept for migration reference)
// ═══════════════════════════════════════════════════════════════

@Deprecated("Use MaterialTheme.colorScheme.primary or LightPrimary instead")
val Green800 = Color(0xFF2E7D32)

@Deprecated("Use SemanticColors.freshnessHigh or LightSemanticColors.freshnessHigh")
val FreshGreen = Color(0xFF4CAF50)

@Deprecated("Use SemanticColors.freshnessLow or LightSemanticColors.freshnessLow")
val RottenRed = Color(0xFFF44336)

@Deprecated("Use SemanticColors.freshnessMedium or LightSemanticColors.freshnessMedium")
val UncertainOrange = Color(0xFFFF9800)

@Deprecated("Use LightPrimaryContainer or color scheme surfaceVariant")
val Green50 = Color(0xFFE8F5E9)

@Deprecated("Use LightPrimaryContainer")
val Green100 = Color(0xFFC8E6C9)

@Deprecated("Use LightPrimary or semantic equivalents")
val Green200 = Color(0xFFA5D6A7)

@Deprecated("Use LightSecondary or semantic equivalents")
val Green400 = Color(0xFF66BB6A)

@Deprecated("Use LightPrimary or semantic equivalents")
val Green600 = Color(0xFF43A047)

@Deprecated("Use LightError or semantic equivalents")
val WarningYellow = Color(0xFFFFC107)

@Deprecated("Use SemanticColors instead")
val ConfidenceHigh = FreshGreen

@Deprecated("Use SemanticColors instead")
val ConfidenceMedium = UncertainOrange

@Deprecated("Use SemanticColors instead")
val ConfidenceLow = RottenRed

@Deprecated("Use MaterialTheme.colorScheme.surface instead")
val SurfaceLight = Color(0xFFFAFAFA)

@Deprecated("Use MaterialTheme.colorScheme.surface instead")
val SurfaceDark = Color(0xFF121212)

@Deprecated("Use MaterialTheme.colorScheme.onSurfaceVariant instead")
val OnSurfaceVariantLight = Color(0xFF49454F)

@Deprecated("Use MaterialTheme.colorScheme.onSurfaceVariant instead")
val OnSurfaceVariantDark = Color(0xFFCAC4D0)
