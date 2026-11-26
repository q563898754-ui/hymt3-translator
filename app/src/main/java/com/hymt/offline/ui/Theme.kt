package com.hymt.offline.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---- 调色板：深色中控屏 --------------------------------------------------
val Bg = Color(0xFF06090C)
val BgGlow = Color(0xFF0B1117)
val Panel = Color(0xFF111820)
val PanelTop = Color(0xFF16202A)
val PanelStroke = Color(0xFF22303C)
val Mint = Color(0xFF5FE3C0)
val MintDeep = Color(0xFF2C8571)
val Amber = Color(0xFFFFB877)
val TextMain = Color(0xFFE9F1F5)
val TextDim = Color(0xFF8698A6)
val TextFaint = Color(0xFF55636F)
val Danger = Color(0xFFFF6B6B)

private val scheme = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF03211A),
    secondary = Amber,
    onSecondary = Color(0xFF2A1700),
    background = Bg,
    onBackground = TextMain,
    surface = Panel,
    onSurface = TextMain,
    surfaceVariant = PanelTop,
    onSurfaceVariant = TextDim,
    outline = PanelStroke,
    error = Danger
)

private val typography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Light,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.2.sp,
        color = TextMain
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.4.sp,
        color = TextMain
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.3.sp,
        color = TextMain
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = TextMain
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        color = TextDim
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.sp,
        color = TextFaint
    )
)

@Composable
fun HyMtTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
