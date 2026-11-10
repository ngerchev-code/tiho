package com.ngerchev.tiho

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import com.ngerchev.tiho.ui.AboutScreen
import com.ngerchev.tiho.ui.ChatScreen
import com.ngerchev.tiho.ui.LanguageScreen
import com.ngerchev.tiho.ui.SettingsScreen
import com.ngerchev.tiho.ui.TihoTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TihoTheme {
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
        Screen.ABOUT -> AboutScreen(vm)
    }
}
