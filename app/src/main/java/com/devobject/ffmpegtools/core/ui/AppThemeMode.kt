package com.devobject.ffmpegtools.core.ui

import top.yukonga.miuix.kmp.theme.ColorSchemeMode

enum class AppThemeMode(val value: Int, val label: String) {
    SYSTEM(0, "跟随系统"),
    LIGHT(1, "浅色"),
    DARK(2, "深色"),
    MONET_SYSTEM(3, "Monet 跟随系统"),
    MONET_LIGHT(4, "Monet 浅色"),
    MONET_DARK(5, "Monet 深色"),
    DARK_AMOLED(6, "AMOLED 深色");

    companion object {
        fun fromValue(value: Int) = entries.find { it.value == value } ?: SYSTEM
    }

    val isSystem: Boolean get() = value == 0 || value == 3
    val isDark: Boolean get() = value == 2 || value == 5 || value == 6
    val isAmoled: Boolean get() = value == 6
    val isMonet: Boolean get() = value >= 3

    fun toColorSchemeMode(): ColorSchemeMode = when (this) {
        SYSTEM -> ColorSchemeMode.System
        LIGHT -> ColorSchemeMode.Light
        DARK -> ColorSchemeMode.Dark
        MONET_SYSTEM -> ColorSchemeMode.MonetSystem
        MONET_LIGHT -> ColorSchemeMode.MonetLight
        MONET_DARK -> ColorSchemeMode.MonetDark
        DARK_AMOLED -> ColorSchemeMode.Dark
    }
}
