package com.github.kusl.msoc

import android.app.Application
import android.content.Context
import com.github.kusl.msoc.core.Diary
import com.github.kusl.msoc.core.EntryNaming
import com.github.kusl.msoc.data.Settings
import com.github.kusl.msoc.data.TokenCipher
import com.github.kusl.msoc.git.GitRepo
import com.github.kusl.msoc.sync.SyncScheduler
import java.io.File

class App : Application() {
    val container by lazy { Container(this) }

    override fun onCreate() {
        super.onCreate()
        if (System.getProperty("user.home").isNullOrEmpty()) System.setProperty("user.home", filesDir.path)
    }
}

class Container(context: Context) {
    val settings = Settings(context.getSharedPreferences("settings", Context.MODE_PRIVATE), TokenCipher())
    val diary = diaryAt(File(context.filesDir, "repo"), mirror = false)
    val mirror = diaryAt(File(context.filesDir, "mirror"), mirror = true)
    val sync = SyncScheduler(context)

    fun active() = if (settings.readOnly()) mirror else diary

    private fun diaryAt(dir: File, mirror: Boolean) = Diary(dir, GitRepo(dir, EntryNaming.DIR, EntryNaming::renamed), settings, mirror)
}
