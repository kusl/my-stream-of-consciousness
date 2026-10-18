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
    val diary = File(context.filesDir, "repo").let { Diary(it, GitRepo(it, EntryNaming.DIR, EntryNaming::renamed), settings) }
    val sync = SyncScheduler(context)
}
