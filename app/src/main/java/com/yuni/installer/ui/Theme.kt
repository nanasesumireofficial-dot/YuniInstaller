package com.yuni.installer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Yuni {
    val Bg = Color(0xFF0A0A0C)
    val Card = Color(0xFF141014)
    val Border = Color(0xFF2B1A1F)
    val Red = Color(0xFFFF1E3C)
    val RedBright = Color(0xFFFF2A45)
    val RedDeep = Color(0xFFD4122A)
    val Text = Color(0xFFF4F4F6)
    val Muted = Color(0xFF8C8C96)
    val Green = Color(0xFF2ED573)
}

@Composable
fun YuniTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Yuni.Red,
            onPrimary = Color.White,
            secondary = Yuni.RedDeep,
            background = Yuni.Bg,
            onBackground = Yuni.Text,
            surface = Yuni.Card,
            onSurface = Yuni.Text,
            surfaceVariant = Yuni.Card,
            onSurfaceVariant = Yuni.Muted,
            outline = Yuni.Border,
            error = Yuni.Red,
        ),
        content = content,
    )
}
