package com.github.kusl.msoc.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.github.kusl.msoc.App
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.util.concurrent.TimeUnit

enum class SyncStatus { Idle, Waiting }

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as App).container
        return try {
            var rounds = 0
            do {
                container.diary.sync()
            } while (container.diary.hasUnpushed() && ++rounds < MAX_ROUNDS)
            container.settings.lastError = null
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            container.settings.lastError = "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
            Result.retry()
        }
    }

    private companion object {
        const val MAX_ROUNDS = 3
    }
}

class SyncScheduler(context: Context) {
    private val work = WorkManager.getInstance(context)
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic() {
        work.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).setConstraints(network).build(),
        )
    }

    suspend fun request() {
        val running = work.getWorkInfosForUniqueWorkFlow(NOW).first().any { it.state == WorkInfo.State.RUNNING }
        work.enqueueUniqueWork(
            NOW,
            if (running) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build(),
        )
    }

    fun status(): Flow<SyncStatus> = work.getWorkInfosForUniqueWorkFlow(NOW).map { infos ->
        if (infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }) SyncStatus.Waiting
        else SyncStatus.Idle
    }

    fun finished(): Flow<Unit> = merge(states(NOW), states(PERIODIC)).filter { it != WorkInfo.State.RUNNING }.map { }

    private fun states(name: String) = work.getWorkInfosForUniqueWorkFlow(name).map { it.firstOrNull()?.state }.distinctUntilChanged()

    private companion object {
        const val NOW = "sync"
        const val PERIODIC = "sync-periodic"
    }
}
