package com.github.kusl.msoc.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.kusl.msoc.R
import com.github.kusl.msoc.core.Entry
import com.github.kusl.msoc.core.Sizes
import com.github.kusl.msoc.sync.SyncStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    entries: List<Entry>,
    status: SyncStatus,
    configured: Boolean,
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onSettings: () -> Unit,
    read: suspend (String) -> String,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = { TextButton(onClick = onSettings) { Text(stringResource(R.string.settings)) } },
            )
        },
        bottomBar = { InputBar(draft, onDraft, onSend) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val notice = when {
                !configured -> stringResource(R.string.local_only)
                status == SyncStatus.Waiting -> stringResource(R.string.sync_unavailable)
                else -> null
            }
            if (notice != null) {
                Text(
                    notice,
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (entries.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.empty), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(entries, key = { it.path }) { Bubble(it, read) }
                }
            }
        }
    }
}

@Composable
private fun Bubble(entry: Entry, read: suspend (String) -> String) {
    var expanded by rememberSaveable(entry.path) { mutableStateOf(false) }
    var full by remember(entry.path) { mutableStateOf<String?>(null) }
    if (expanded && full == null) LaunchedEffect(entry.path) { full = read(entry.path) }
    val tint = if (entry.size > Sizes.WARN) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
    val text = if (expanded) full ?: entry.preview else if (entry.truncated) entry.preview + "…" else entry.preview
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            modifier = Modifier.widthIn(max = 340.dp).clickable(enabled = entry.truncated) { expanded = !expanded },
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                SelectionContainer { Text(text, color = tint) }
                Text(
                    meta(entry),
                    Modifier.align(Alignment.End),
                    style = MaterialTheme.typography.labelSmall,
                    color = tint.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun InputBar(draft: String, onDraft: (String) -> Unit, onSend: () -> Unit) {
    val size = remember(draft) { Sizes.utf8(draft) }
    val tooLarge = size > Sizes.MAX
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(8.dp)) {
            if (size > Sizes.SHOW) {
                Text(
                    if (tooLarge) stringResource(R.string.too_large, Sizes.format(size), Sizes.format(Sizes.MAX)) else Sizes.format(size),
                    Modifier.padding(start = 8.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (size > Sizes.WARN) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    draft,
                    onDraft,
                    Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.placeholder)) },
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSend, enabled = draft.isNotBlank() && !tooLarge) { Text(stringResource(R.string.send)) }
            }
        }
    }
}

private val timeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private fun meta(entry: Entry): String {
    val time = timeFormat.format(entry.at.atZone(ZoneId.systemDefault()))
    return if (entry.size > Sizes.SHOW) "$time  ${Sizes.format(entry.size)}" else time
}
