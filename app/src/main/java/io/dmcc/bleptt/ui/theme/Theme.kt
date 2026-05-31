package io.dmcc.bleptt.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Tokens reverse-engineered from the VoxDMR Android screenshot so this PoC reads as the
// same product family. Keep them in one place so the developer adopting the code can pull
// them into VoxDMR proper without hunting through files.
object VoxColors {
    val Background = Color(0xFF0E0E10)
    val Surface = Color(0xFF1B1B1E)
    val SurfaceElevated = Color(0xFF26262B)
    val Outline = Color(0xFF2E2E33)
    val OnBackground = Color(0xFFE6E6E8)
    val Muted = Color(0xFF8E8E93)
    val Coral = Color(0xFFEF5A4D) // The hold-to-transmit button
    val CoralPressed = Color(0xFFD64A3E)
    val PillBlue = Color(0xFFB1CEEF)
}

private val VoxDarkScheme = darkColorScheme(
    primary = VoxColors.Coral,
    onPrimary = Color.White,
    secondary = VoxColors.PillBlue,
    onSecondary = Color(0xFF0E1A2A),
    background = VoxColors.Background,
    onBackground = VoxColors.OnBackground,
    surface = VoxColors.Surface,
    onSurface = VoxColors.OnBackground,
    surfaceVariant = VoxColors.SurfaceElevated,
    onSurfaceVariant = VoxColors.Muted,
    outline = VoxColors.Outline,
    error = VoxColors.Coral,
    onError = Color.White,
)

private val VoxTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun VoxTheme(
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicDarkColorScheme(context)
        else -> VoxDarkScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = VoxColors.Background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = VoxTypography,
        content = content,
    )
}
