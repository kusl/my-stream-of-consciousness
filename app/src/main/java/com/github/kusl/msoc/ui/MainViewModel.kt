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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val container: Container) : ViewModel() {
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    @OptIn(FlowPreview::class)
    val status: StateFlow<SyncStatus> = container.sync.status().debounce(1500)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus.Idle)

    var draft by mutableStateOf("")
    var configured by mutableStateOf(container.settings.load().url.isNotEmpty())
        private set

    init {
        container.sync.schedulePeriodic()
        viewModelScope.launch { container.sync.request() }
        viewModelScope.launch { container.sync.finished().collect { reload() } }
    }

    fun send() {
        val text = draft
        if (text.isBlank() || Sizes.utf8(text) > Sizes.MAX) return
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
        configured = new.url.isNotEmpty()
        viewModelScope.launch {
            if (old.url != new.url || old.branch != new.branch) container.diary.forgetPushed()
            container.sync.request()
        }
    }

    suspend fun read(path: String) = container.diary.read(path)

    private suspend fun reload() {
        _entries.value = container.diary.entries()
    }
}
