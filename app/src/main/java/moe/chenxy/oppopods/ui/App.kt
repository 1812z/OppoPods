package moe.chenxy.oppopods.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

@Composable
fun App(
    themeMode: MutableState<Int> = mutableStateOf(0),
    onThemeModeChange: (Int) -> Unit = {},
    accentMode: MutableState<Int> = mutableStateOf(0),
    onAccentModeChange: (Int) -> Unit = {},
    floatingBottomBar: MutableState<Boolean> = mutableStateOf(false),
    onFloatingBottomBarChange: (Boolean) -> Unit = {},
    blurBottomBar: MutableState<Boolean> = mutableStateOf(false),
    onBlurBottomBarChange: (Boolean) -> Unit = {},
    appLanguage: MutableState<Int> = mutableStateOf(AppLocale.SYSTEM),
    onAppLanguageChange: (Int) -> Unit = {},
) {
    val colorSchemeMode = when (themeMode.value) {
        1 -> ColorSchemeMode.Light
        2 -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.System
    }
    val backStack: NavBackStack = remember { mutableStateListOf<NavKey>(Screen.Main) }

    AppLocale.Provider(language = appLanguage.value) {
        AppTheme(colorSchemeMode = colorSchemeMode, accentMode = accentMode.value) {
            MainUI(
                backStack = backStack,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                accentMode = accentMode,
                onAccentModeChange = onAccentModeChange,
                floatingBottomBar = floatingBottomBar,
                onFloatingBottomBarChange = onFloatingBottomBarChange,
                blurBottomBar = blurBottomBar,
                onBlurBottomBarChange = onBlurBottomBarChange,
                appLanguage = appLanguage,
                onAppLanguageChange = onAppLanguageChange,
            )
        }
    }
}
