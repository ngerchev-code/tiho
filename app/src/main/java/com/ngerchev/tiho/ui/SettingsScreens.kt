package com.ngerchev.tiho.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.ngerchev.tiho.data.Accent
import com.ngerchev.tiho.data.Retention
import com.ngerchev.tiho.data.SendTarget
import com.ngerchev.tiho.data.SettingsStore
import com.ngerchev.tiho.data.TextSize
import com.ngerchev.tiho.data.ThemeMode
import com.ngerchev.tiho.update.Updater

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
        RadioRow(
            label = stringResource(R.string.lang_bg),
            selected = settings.languageTag == SettingsStore.LANG_BG,
            onSelect = { vm.setLanguage(SettingsStore.LANG_BG) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        RadioRow(
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
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp),
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
    val dark = LocalIsDark.current
    SubScreen(stringResource(R.string.settings_title), onBack = { vm.navigate(Screen.CHAT) }) {

        // ---- Appearance ----
        SectionTitle(stringResource(R.string.sec_appearance))
        Label(stringResource(R.string.set_theme))
        ChoiceRow(
            options = listOf(
                ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                ThemeMode.LIGHT to stringResource(R.string.theme_light),
                ThemeMode.DARK to stringResource(R.string.theme_dark)
            ),
            selected = settings.themeMode,
            onSelect = { value -> vm.updateSettings { it.copy(themeMode = value) } }
        )
        Label(stringResource(R.string.set_accent))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Accent.values().forEach { accent ->
                val selected = accent == settings.accent
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .border(
                            width = if (selected) 3.dp else 0.dp,
                            color = if (selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.background,
                            shape = CircleShape
                        )
                        .padding(6.dp)
                        .background(accentPreview(accent, dark), CircleShape)
                        .clickable { vm.updateSettings { it.copy(accent = accent) } }
                )
            }
        }
        Label(stringResource(R.string.set_text_size))
        ChoiceRow(
            options = listOf(
                TextSize.SMALL to stringResource(R.string.size_small),
                TextSize.NORMAL to stringResource(R.string.size_normal),
                TextSize.LARGE to stringResource(R.string.size_large)
            ),
            selected = settings.textSize,
            onSelect = { value -> vm.updateSettings { it.copy(textSize = value) } }
        )

        // ---- Dictation ----
        SectionTitle(stringResource(R.string.sec_dictation))
        SwitchRow(
            title = stringResource(R.string.set_on_device_title),
            text = stringResource(R.string.set_on_device_text),
            checked = settings.preferOnDevice,
            onChange = { value -> vm.updateSettings { it.copy(preferOnDevice = value) } }
        )
        SwitchRow(
            title = stringResource(R.string.set_auto_send_title),
            text = stringResource(R.string.set_auto_send_text),
            checked = settings.autoSend,
            onChange = { value -> vm.updateSettings { it.copy(autoSend = value) } }
        )
        SwitchRow(
            title = stringResource(R.string.set_auto_punct_title),
            text = stringResource(R.string.set_auto_punct_text),
            checked = settings.autoPunctuation,
            onChange = { value -> vm.updateSettings { it.copy(autoPunctuation = value) } }
        )
        SwitchRow(
            title = stringResource(R.string.set_punct_title),
            text = stringResource(R.string.set_punct_text),
            checked = settings.spokenPunctuation,
            onChange = { value -> vm.updateSettings { it.copy(spokenPunctuation = value) } }
        )
        SwitchRow(
            title = stringResource(R.string.set_punct_keys_title),
            text = stringResource(R.string.set_punct_keys_text),
            checked = settings.showPunctuationKeys,
            onChange = { value -> vm.updateSettings { it.copy(showPunctuationKeys = value) } }
        )
        SwitchRow(
            title = stringResource(R.string.set_screen_on_title),
            text = stringResource(R.string.set_screen_on_text),
            checked = settings.keepScreenOn,
            onChange = { value -> vm.updateSettings { it.copy(keepScreenOn = value) } }
        )
        SwitchRow(
            title = stringResource(R.string.set_haptics_title),
            text = stringResource(R.string.set_haptics_text),
            checked = settings.haptics,
            onChange = { value -> vm.updateSettings { it.copy(haptics = value) } }
        )

        // ---- Sending ----
        SectionTitle(stringResource(R.string.sec_sending))
        Text(
            text = stringResource(R.string.set_after_send_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        SendTarget.values().forEach { target ->
            RadioRow(
                label = when (target) {
                    SendTarget.NONE -> stringResource(R.string.target_none)
                    SendTarget.SHARE_MENU -> stringResource(R.string.target_menu)
                    else -> target.title ?: target.name
                },
                selected = settings.afterSend == target,
                onSelect = { vm.updateSettings { it.copy(afterSend = target) } }
            )
        }

        // ---- Privacy ----
        SectionTitle(stringResource(R.string.sec_privacy))
        Label(stringResource(R.string.set_retention))
        ChoiceRow(
            options = listOf(
                Retention.SESSION to stringResource(R.string.ret_session),
                Retention.HOUR_1 to stringResource(R.string.ret_1h),
                Retention.HOURS_24 to stringResource(R.string.ret_24h),
                Retention.DAYS_7 to stringResource(R.string.ret_7d)
            ),
            selected = settings.retention,
            onSelect = { value -> vm.updateSettings { it.copy(retention = value) } }
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                vm.clearConversation()
                vm.navigate(Screen.CHAT)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.menu_clear))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.privacy_heading),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.privacy_network) + "\n\n" +
                stringResource(R.string.privacy_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(12.dp))
    Text(text = text, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(6.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 12.dp),
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
        if (Updater.ENABLED) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { vm.checkForUpdate(manual = true) }) {
                Text(stringResource(R.string.about_check_update))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.about_body),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.privacy_network) + "\n\n" +
                stringResource(R.string.privacy_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
