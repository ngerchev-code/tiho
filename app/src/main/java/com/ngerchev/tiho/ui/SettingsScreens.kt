package com.ngerchev.tiho.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngerchev.tiho.ChatViewModel
import com.ngerchev.tiho.R
import com.ngerchev.tiho.Screen
import com.ngerchev.tiho.data.SettingsStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            content()
        }
    }
}

@Composable
fun LanguageScreen(vm: ChatViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    SubScreen(stringResource(R.string.language_title), onBack = { vm.navigate(Screen.CHAT) }) {
        LanguageRow(
            label = stringResource(R.string.lang_bg),
            selected = settings.languageTag == SettingsStore.LANG_BG,
            onSelect = { vm.setLanguage(SettingsStore.LANG_BG) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LanguageRow(
            label = stringResource(R.string.lang_en),
            selected = settings.languageTag == SettingsStore.LANG_EN,
            onSelect = { vm.setLanguage(SettingsStore.LANG_EN) }
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.language_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SettingsScreen(vm: ChatViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    SubScreen(stringResource(R.string.settings_title), onBack = { vm.navigate(Screen.CHAT) }) {
        SwitchRow(
            title = stringResource(R.string.set_on_device_title),
            text = stringResource(R.string.set_on_device_text),
            checked = settings.preferOnDevice,
            onChange = { vm.setPreferOnDevice(it) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SwitchRow(
            title = stringResource(R.string.set_keep_title),
            text = stringResource(R.string.set_keep_text),
            checked = settings.keepFor24h,
            onChange = { vm.setKeepFor24h(it) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SwitchRow(
            title = stringResource(R.string.set_punct_title),
            text = stringResource(R.string.set_punct_text),
            checked = settings.spokenPunctuation,
            onChange = { vm.setSpokenPunctuation(it) }
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = {
                vm.clearConversation()
                vm.navigate(Screen.CHAT)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.menu_clear))
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.privacy_heading),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.privacy_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SwitchRow(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun AboutScreen(vm: ChatViewModel) {
    val context = LocalContext.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }
    SubScreen(stringResource(R.string.about_title), onBack = { vm.navigate(Screen.CHAT) }) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.about_version, version),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.about_body),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.privacy_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
