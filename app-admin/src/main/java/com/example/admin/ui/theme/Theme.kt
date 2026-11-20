package com.example.admin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AdminDarkColors = darkColorScheme(
    primary = AdminGold,
    onPrimary = AdminInk,
    primaryContainer = AdminNavy,
    onPrimaryContainer = Color(0xFFF3EFE6),
    secondary = AdminTeal,
    onSecondary = Color.White,
    error = Color(0xFFEF8A82),
    background = Color(0xFF111C24),
    onBackground = Color(0xFFF0ECE3),
    surface = Color(0xFF192833),
    onSurface = Color(0xFFF0ECE3),
    surfaceVariant = Color(0xFF233644),
    onSurfaceVariant = Color(0xFFB5C4CC)
)

private val AdminLightColors = lightColorScheme(
    primary = AdminNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4F0),
    onPrimaryContainer = AdminNavyDark,
    secondary = AdminTeal,
    onSecondary = Color.White,
    error = AdminDanger,
    background = AdminCanvasBg,
    onBackground = AdminInk,
    surface = AdminCardSurface,
    onSurface = AdminInk,
    surfaceVariant = AdminSurfaceVariant,
    onSurfaceVariant = AdminMuted
)

@Composable
fun AdminTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) AdminDarkColors else AdminLightColors,
        typography = AdminTypography,
        content = content
    )
}
