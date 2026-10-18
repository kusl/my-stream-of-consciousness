package com.github.kusl.msoc.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.github.kusl.msoc.R
import com.github.kusl.msoc.data.Config

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(initial: Config, lastError: String?, onSave: (Config) -> Unit, onBack: () -> Unit) {
    var config by remember { mutableStateOf(initial) }
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Field(R.string.remote_url, config.url, KeyboardType.Uri) { config = config.copy(url = it) }
            Field(R.string.username, config.username) { config = config.copy(username = it) }
            Field(R.string.token, config.token, KeyboardType.Password, secret = true) { config = config.copy(token = it) }
            Field(R.string.branch, config.branch) { config = config.copy(branch = it) }
            Field(R.string.author_name, config.authorName) { config = config.copy(authorName = it) }
            Field(R.string.author_email, config.authorEmail, KeyboardType.Email) { config = config.copy(authorEmail = it) }
            Button(onClick = { onSave(config) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
            if (lastError != null) {
                Text(stringResource(R.string.last_error, lastError), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.settings_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Field(
    @StringRes label: Int,
    value: String,
    type: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value,
        onChange,
        Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = type, autoCorrectEnabled = false),
    )
}
