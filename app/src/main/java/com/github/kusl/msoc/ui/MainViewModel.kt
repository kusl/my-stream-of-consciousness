package com.github.kusl.msoc.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.kusl.msoc.Container
import com.github.kusl.msoc.core.Entry
import com.github.kusl.msoc.core.Sizes
import com.github.kusl.msoc.data.Config
import com.github.kusl.msoc.sync.SyncStatus
import com.github.kusl.msoc.sync.describe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MainViewModel(private val container: Container) : ViewModel() {
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()
    private val unreachable = MutableStateFlow(false)

    @OptIn(FlowPreview::class)
    val status: StateFlow<SyncStatus> = combine(container.sync.status(), unreachable) { work, down -> if (down) SyncStatus.Waiting else work }
        .debounce(1500)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus.Idle)

    var draft by mutableStateOf("")
    var configured by mutableStateOf(false)
        private set
    var readOnly by mutableStateOf(false)
        private set

    init {
        adopt(container.settings.load())
        container.sync.schedulePeriodic()
        viewModelScope.launch { container.sync.request() }
        viewModelScope.launch { container.sync.finished().collect { reload() } }
    }

    suspend fun watch() {
        var wait = POLL
        while (true) {
            wait = if (poll()) POLL else (wait * 2).coerceAtMost(MAX_WAIT)
            delay(wait)
        }
    }

    fun receive(subject: String?, text: String?) {
        val title = subject.orEmpty().trim()
        val body = text.orEmpty().trim()
        val shared = when {
            title.isEmpty() || body.contains(title) -> body
            body.isEmpty() -> title
            else -> "$title\n$body"
        }
        if (shared.isNotEmpty()) draft = if (draft.isBlank()) shared else "${draft.trimEnd()}\n\n$shared"
    }

    fun send() {
        val text = draft
        if (readOnly || text.isBlank() || Sizes.utf8(text) > Sizes.MAX) return
        draft = ""
        viewModelScope.launch {
            runCatching { container.diary.add(text) }.onFailure { draft = text }
            reload()
            container.sync.request()
        }
    }

    fun config() = container.settings.load()

    fun lastError() = container.settings.lastError

    fun save(config: Config) {
        val old = container.settings.load()
        container.settings.save(config)
        val new = container.settings.load()
        adopt(new)
        viewModelScope.launch {
            if (old.url != new.url || old.branch != new.branch) {
                container.diary.remoteChanged()
                container.mirror.remoteChanged()
            }
            reload()
            container.sync.request()
        }
    }

    suspend fun read(path: String) = container.active().read(path)

    private suspend fun poll(): Boolean = try {
        if (container.active().refresh()) reload()
        unreachable.value = false
        container.settings.lastError = null
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        unreachable.value = configured
        container.settings.lastError = describe(e)
        false
    }

    private fun adopt(config: Config) {
        configured = config.url.isNotEmpty()
        readOnly = config.readOnly
        container.shareTarget(!config.readOnly)
    }

    private suspend fun reload() {
        _entries.value = container.active().entries()
    }

    private companion object {
        val POLL = 30.seconds
        val MAX_WAIT = 5.minutes
    }
}
