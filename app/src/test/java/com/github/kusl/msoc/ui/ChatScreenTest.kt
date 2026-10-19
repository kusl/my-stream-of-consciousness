package com.github.kusl.msoc.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.github.kusl.msoc.core.Entry
import com.github.kusl.msoc.sync.SyncStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class ChatScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        entries: List<Entry> = emptyList(),
        configured: Boolean = true,
        readOnly: Boolean = false,
        status: SyncStatus = SyncStatus.Idle,
        draft: String = "",
    ) = compose.setContent { MaterialTheme { ChatScreen(entries, status, configured, readOnly, draft, {}, {}, {}, { "" }) } }

    @Test
    fun showsEntriesAndLargeSizes() {
        show(
            listOf(
                Entry("entries/b.txt", Instant.parse("2026-10-18T14:28:05Z"), 5, "hello", false),
                Entry("entries/a.txt", Instant.parse("2026-10-18T14:27:05Z"), 2L * 1024 * 1024, "huge", true),
            ),
        )
        compose.onNodeWithText("hello").assertExists()
        compose.onNodeWithText("huge…").assertExists()
        compose.onNodeWithText("2.0 MiB", substring = true).assertExists()
    }

    @Test
    fun blankDraftCannotBeSent() {
        show(draft = "   ")
        compose.onNodeWithText("Send").assertIsNotEnabled()
    }

    @Test
    fun showsDiscreetNotices() {
        show(configured = false)
        compose.onNodeWithText("Add a git remote", substring = true).assertExists()
    }

    @Test
    fun showsSyncUnavailable() {
        show(status = SyncStatus.Waiting)
        compose.onNodeWithText("Sync not currently possible").assertExists()
    }

    @Test
    fun readOnlyHidesInput() {
        show(readOnly = true)
        compose.onNodeWithText("Send").assertDoesNotExist()
        compose.onNodeWithText("Read only", substring = true).assertExists()
    }
}
