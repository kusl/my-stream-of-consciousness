package com.github.kusl.msoc

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
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

class Container(private val context: Context) {
    val settings = Settings(context.getSharedPreferences("settings", Context.MODE_PRIVATE), TokenCipher())
    val diary = diaryAt(File(context.filesDir, "repo"), mirror = false)
    val mirror = diaryAt(File(context.filesDir, "mirror"), mirror = true)
    val sync = SyncScheduler(context)

    fun active() = if (settings.readOnly()) mirror else diary

    fun shareTarget(enabled: Boolean) {
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val component = ComponentName(context.packageName, SHARE_TARGET)
        if (context.packageManager.getComponentEnabledSetting(component) != state) {
            context.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }

    private fun diaryAt(dir: File, mirror: Boolean) = Diary(dir, GitRepo(dir, EntryNaming.DIR, EntryNaming::renamed), settings, mirror)

    private companion object {
        const val SHARE_TARGET = "com.github.kusl.msoc.ui.ShareText"
    }
}
