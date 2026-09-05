package com.example.cargomanifestapp

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Cargo Retro Brutalism Design System
 *
 * Terinspirasi Neo-Brutalism + Retro Web/Y2K:
 * - warna blok kontras
 * - border hitam tebal
 * - hard shadow
 * - sudut hampir kotak
 * - tipografi monospace/terminal
 */

object CargoRetroColors {
    val Ink = Color(0xFF111111)
    val Paper = Color(0xFFFFFDF6)
    val Cream = Color(0xFFFFF1B8)
    val Blue = Color(0xFF4F91D5)
    val BlueLight = Color(0xFFAED5F4)
    val Pink = Color(0xFFE98AC6)
    val Green = Color(0xFF8DDBAA)
    val Orange = Color(0xFFFF6B4A)
    val Purple = Color(0xFF9D86D8)
    val Cyan = Color(0xFF77D9E8)
    val White = Color(0xFFFFFFFF)
}

private val CargoLightScheme = lightColorScheme(
    primary = CargoRetroColors.Blue,
    onPrimary = CargoRetroColors.Ink,
    primaryContainer = CargoRetroColors.BlueLight,
    onPrimaryContainer = CargoRetroColors.Ink,
    secondary = CargoRetroColors.Pink,
    onSecondary = CargoRetroColors.Ink,
    tertiary = CargoRetroColors.Green,
    onTertiary = CargoRetroColors.Ink,
    background = CargoRetroColors.Cream,
    onBackground = CargoRetroColors.Ink,
    surface = CargoRetroColors.Paper,
    onSurface = CargoRetroColors.Ink,
    surfaceVariant = Color(0xFFF4EFD9),
    onSurfaceVariant = CargoRetroColors.Ink,
    outline = CargoRetroColors.Ink,
    error = CargoRetroColors.Orange,
    onError = CargoRetroColors.Ink
)

private val CargoDarkScheme = darkColorScheme(
    primary = CargoRetroColors.BlueLight,
    secondary = CargoRetroColors.Pink,
    tertiary = CargoRetroColors.Green,
    background = Color(0xFF171717),
    surface = Color(0xFF222222),
    onBackground = CargoRetroColors.Paper,
    onSurface = CargoRetroColors.Paper,
    outline = CargoRetroColors.Paper
)

private val CargoRetroTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 0.4.sp
    )
)

private val CargoRetroShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp)
)

@Composable
fun CargoRetroTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) CargoDarkScheme else CargoLightScheme,
        typography = CargoRetroTypography,
        shapes = CargoRetroShapes,
        content = content
    )
}
