package com.example.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class AppThemeOption(
    val id: String,
    val displayName: String,
    val isDark: Boolean,
    val previewBackground: Color,
    val previewAccent: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val heroOnColor: Color
) {
    SAMUDRA(
        id = "samudra",
        displayName = "Samudra",
        isDark = true,
        previewBackground = Color(0xFF0A1018),
        previewAccent = Color(0xFF4FC3F7),
        heroStart = Color(0xFF0F253E),
        heroEnd = Color(0xFF154B70),
        heroOnColor = Color(0xFFF3F9FF)
    ),
    PINK_MANIS(
        id = "pink_manis",
        displayName = "Pink Manis",
        isDark = false,
        previewBackground = Color(0xFFFFF0F5),
        previewAccent = Color(0xFFEC4899),
        heroStart = Color(0xFFDB2777),
        heroEnd = Color(0xFFEC4899),
        heroOnColor = Color(0xFFFFFFFF)
    ),
    HITAM_ELEGAN(
        id = "hitam_elegan",
        displayName = "Hitam Elegan",
        isDark = true,
        previewBackground = Color(0xFF000000),
        previewAccent = Color(0xFFD4AF37),
        heroStart = Color(0xFF181409),
        heroEnd = Color(0xFF3A2E0E),
        heroOnColor = Color(0xFFFAF6E8)
    ),
    HIJAU_HUTAN(
        id = "hijau_hutan",
        displayName = "Hijau Hutan",
        isDark = true,
        previewBackground = Color(0xFF0F1F17),
        previewAccent = Color(0xFF4ADE80),
        heroStart = Color(0xFF123222),
        heroEnd = Color(0xFF1A5236),
        heroOnColor = Color(0xFFECFDF3)
    ),
    UNGU_SENJA(
        id = "ungu_senja",
        displayName = "Ungu Senja",
        isDark = true,
        previewBackground = Color(0xFF1A1033),
        previewAccent = Color(0xFFA78BFA),
        heroStart = Color(0xFF281852),
        heroEnd = Color(0xFF452882),
        heroOnColor = Color(0xFFF5F3FF)
    ),
    ORANYE_PANTAI(
        id = "oranye_pantai",
        displayName = "Oranye Pantai",
        isDark = false,
        previewBackground = Color(0xFFFFF7ED),
        previewAccent = Color(0xFFF97316),
        heroStart = Color(0xFFEA580C),
        heroEnd = Color(0xFFF97316),
        heroOnColor = Color(0xFFFFFFFF)
    );

    companion object {
        fun fromId(id: String?): AppThemeOption {
            return entries.firstOrNull { it.id == id } ?: SAMUDRA
        }
    }
}

object ThemePreferences {
    private const val PREFS_NAME = "tripbuddy_prefs"
    private const val KEY_APP_THEME = "selected_app_theme"

    fun loadTheme(context: Context): AppThemeOption {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppThemeOption.fromId(prefs.getString(KEY_APP_THEME, AppThemeOption.SAMUDRA.id))
    }

    fun saveTheme(context: Context, theme: AppThemeOption) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_APP_THEME, theme.id).apply()
    }
}

val LocalAppTheme = staticCompositionLocalOf { AppThemeOption.SAMUDRA }

private fun colorSchemeFor(option: AppThemeOption): ColorScheme {
    return when (option) {
        AppThemeOption.SAMUDRA -> darkColorScheme(
            primary = Color(0xFF4FC3F7),
            onPrimary = Color(0xFF062030),
            primaryContainer = Color(0xFF13364D),
            onPrimaryContainer = Color(0xFFD8F3FF),
            secondary = Color(0xFF38BDF8),
            onSecondary = Color(0xFF062030),
            secondaryContainer = Color(0xFF113549),
            onSecondaryContainer = Color(0xFFE0F2FE),
            tertiary = Color(0xFFFBBF24),
            onTertiary = Color(0xFF281900),
            tertiaryContainer = Color(0xFF473108),
            onTertiaryContainer = Color(0xFFFEF3C7),
            background = Color(0xFF0A1018),
            onBackground = Color(0xFFF1F6FB),
            surface = Color(0xFF121C29),
            onSurface = Color(0xFFF1F6FB),
            surfaceVariant = Color(0xFF192738),
            onSurfaceVariant = Color(0xFFA7BBD0),
            outline = Color(0xFF24384F),
            outlineVariant = Color(0xFF24384F),
            error = Color(0xFFF87171),
            onError = Color(0xFF2D0A0A),
            errorContainer = Color(0xFF4C1414),
            onErrorContainer = Color(0xFFFEE2E2)
        )

        AppThemeOption.PINK_MANIS -> lightColorScheme(
            primary = Color(0xFFEC4899),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFCE7F3),
            onPrimaryContainer = Color(0xFF831843),
            secondary = Color(0xFFDB2777),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFFBCFE8),
            onSecondaryContainer = Color(0xFF701A45),
            tertiary = Color(0xFFF43F5E),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFFE4E6),
            onTertiaryContainer = Color(0xFF881337),
            background = Color(0xFFFFF0F5),
            onBackground = Color(0xFF361024),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF361024),
            surfaceVariant = Color(0xFFFDE2EF),
            onSurfaceVariant = Color(0xFF7A4963),
            outline = Color(0xFFF4C8DE),
            outlineVariant = Color(0xFFF4C8DE),
            error = Color(0xFFDC2626),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFEE2E2),
            onErrorContainer = Color(0xFF7F1D1D)
        )

        AppThemeOption.HITAM_ELEGAN -> darkColorScheme(
            primary = Color(0xFFD4AF37),
            onPrimary = Color(0xFF1A1400),
            primaryContainer = Color(0xFF33290B),
            onPrimaryContainer = Color(0xFFFBE9A6),
            secondary = Color(0xFFE5C158),
            onSecondary = Color(0xFF1A1400),
            secondaryContainer = Color(0xFF2A220A),
            onSecondaryContainer = Color(0xFFF7E7B4),
            tertiary = Color(0xFFF59E0B),
            onTertiary = Color(0xFF1F1200),
            tertiaryContainer = Color(0xFF3D2806),
            onTertiaryContainer = Color(0xFFFEF3C7),
            background = Color(0xFF000000),
            onBackground = Color(0xFFF7F5EE),
            surface = Color(0xFF121212),
            onSurface = Color(0xFFF7F5EE),
            surfaceVariant = Color(0xFF1D1B16),
            onSurfaceVariant = Color(0xFFC2BBA6),
            outline = Color(0xFF2F2A1E),
            outlineVariant = Color(0xFF2F2A1E),
            error = Color(0xFFF87171),
            onError = Color(0xFF2D0A0A),
            errorContainer = Color(0xFF4C1414),
            onErrorContainer = Color(0xFFFEE2E2)
        )

        AppThemeOption.HIJAU_HUTAN -> darkColorScheme(
            primary = Color(0xFF4ADE80),
            onPrimary = Color(0xFF052411),
            primaryContainer = Color(0xFF15432A),
            onPrimaryContainer = Color(0xFFDCFCE7),
            secondary = Color(0xFF34D399),
            onSecondary = Color(0xFF052418),
            secondaryContainer = Color(0xFF133A2C),
            onSecondaryContainer = Color(0xFFD1FAE5),
            tertiary = Color(0xFFA3E635),
            onTertiary = Color(0xFF142402),
            tertiaryContainer = Color(0xFF2A4008),
            onTertiaryContainer = Color(0xFFECFCCB),
            background = Color(0xFF0F1F17),
            onBackground = Color(0xFFECFDF3),
            surface = Color(0xFF162B20),
            onSurface = Color(0xFFECFDF3),
            surfaceVariant = Color(0xFF1E382B),
            onSurfaceVariant = Color(0xFF9EC4AE),
            outline = Color(0xFF284A38),
            outlineVariant = Color(0xFF284A38),
            error = Color(0xFFF87171),
            onError = Color(0xFF2D0A0A),
            errorContainer = Color(0xFF4C1414),
            onErrorContainer = Color(0xFFFEE2E2)
        )

        AppThemeOption.UNGU_SENJA -> darkColorScheme(
            primary = Color(0xFFA78BFA),
            onPrimary = Color(0xFF1B0C3D),
            primaryContainer = Color(0xFF392569),
            onPrimaryContainer = Color(0xFFEDE9FE),
            secondary = Color(0xFFC084FC),
            onSecondary = Color(0xFF220A38),
            secondaryContainer = Color(0xFF3A1E59),
            onSecondaryContainer = Color(0xFFF3E8FF),
            tertiary = Color(0xFFF472B6),
            onTertiary = Color(0xFF330820),
            tertiaryContainer = Color(0xFF4C1634),
            onTertiaryContainer = Color(0xFFFCE7F3),
            background = Color(0xFF1A1033),
            onBackground = Color(0xFFF5F3FF),
            surface = Color(0xFF241744),
            onSurface = Color(0xFFF5F3FF),
            surfaceVariant = Color(0xFF2F2056),
            onSurfaceVariant = Color(0xFFC0B3E6),
            outline = Color(0xFF3E2D6B),
            outlineVariant = Color(0xFF3E2D6B),
            error = Color(0xFFF87171),
            onError = Color(0xFF2D0A0A),
            errorContainer = Color(0xFF4C1414),
            onErrorContainer = Color(0xFFFEE2E2)
        )

        AppThemeOption.ORANYE_PANTAI -> lightColorScheme(
            primary = Color(0xFFF97316),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFEDD5),
            onPrimaryContainer = Color(0xFF7C2D12),
            secondary = Color(0xFFEA580C),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFFED7AA),
            onSecondaryContainer = Color(0xFF7C2D12),
            tertiary = Color(0xFFF59E0B),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFEF3C7),
            onTertiaryContainer = Color(0xFF78350F),
            background = Color(0xFFFFF7ED),
            onBackground = Color(0xFF2C1608),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF2C1608),
            surfaceVariant = Color(0xFFFFE8CE),
            onSurfaceVariant = Color(0xFF7D5A44),
            outline = Color(0xFFF7D4B2),
            outlineVariant = Color(0xFFF7D4B2),
            error = Color(0xFFDC2626),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFEE2E2),
            onErrorContainer = Color(0xFF7F1D1D)
        )
    }
}

val TripBuddyShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun TripBuddyTheme(
    appTheme: AppThemeOption = AppThemeOption.SAMUDRA,
    content: @Composable () -> Unit
) {
    val colorScheme = colorSchemeFor(appTheme)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !appTheme.isDark
                controller.isAppearanceLightNavigationBars = !appTheme.isDark
            }
        }
    }

    CompositionLocalProvider(LocalAppTheme provides appTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TripBuddyTypography,
            shapes = TripBuddyShapes,
            content = content
        )
    }
}
