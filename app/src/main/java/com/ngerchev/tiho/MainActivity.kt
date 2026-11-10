package com.ngerchev.tiho

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngerchev.tiho.data.ThemeMode
import com.ngerchev.tiho.ui.AboutScreen
import com.ngerchev.tiho.ui.ChatScreen
import com.ngerchev.tiho.ui.LanguageScreen
import com.ngerchev.tiho.ui.SettingsScreen
import com.ngerchev.tiho.ui.TemplatesScreen
import com.ngerchev.tiho.ui.TihoTheme
import com.ngerchev.tiho.ui.UpdateDialog

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Keep the status and navigation bar icons readable for the chosen theme.
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            TihoTheme(dark = dark, accent = settings.accent, textSize = settings.textSize) {
                TihoApp(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app (home button, incoming call screen, share sheet) switches the mic off.
        if (!isChangingConfigurations) viewModel.onBackground()
    }
}

@Composable
private fun TihoApp(vm: ChatViewModel) {
    BackHandler(enabled = vm.screen != Screen.CHAT) { vm.navigate(Screen.CHAT) }
    when (vm.screen) {
        Screen.CHAT -> ChatScreen(vm)
        Screen.LANGUAGE -> LanguageScreen(vm)
        Screen.SETTINGS -> SettingsScreen(vm)
        Screen.TEMPLATES -> TemplatesScreen(vm)
        Screen.ABOUT -> AboutScreen(vm)
    }
    UpdateDialog(vm)
}
