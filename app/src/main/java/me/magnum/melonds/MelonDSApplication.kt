package me.magnum.melonds

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.hilt.work.HiltWorkerFactory
import androidx.preference.PreferenceManager
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import me.magnum.melonds.common.UriFileHandler
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.domain.model.VideoFiltering
import me.magnum.melonds.domain.model.VideoRenderer
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.migrations.Migrator
import me.magnum.melonds.utils.RendererSupport
import javax.inject.Inject

@HiltAndroidApp
class MelonDSApplication : Application(), Configuration.Provider {
    companion object {
        const val NOTIFICATION_CHANNEL_ID_BACKGROUND_TASKS = "channel_cheat_importing"
        private const val KEY_BEST_RENDERER_SELECTED = "best_renderer_selected"

        init {
            System.loadLibrary("melonDS-android-frontend")
        }
    }

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var migrator: Migrator
    @Inject lateinit var uriHandler: UriHandler

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        applyTheme()
        performMigrations()
        forceToonFilter()
        selectBestRenderer()
        MelonDSAndroidInterface.setup(UriFileHandler(this, uriHandler))
    }

    /**
     * The cel-shaded Toon filter is part of this build's look, so it's applied every time the app starts. Other filters can still be picked in the settings,
     * but only last until the app is restarted.
     */
    /**
     * Uses the compute renderer when the device supports it: it supports high resolutions like the OpenGL renderer, but draws more accurately (for example,
     * the shadows of characters in Spirit Tracks, which flicker with the OpenGL renderer). This is done once, so a renderer picked later in the settings is kept.
     */
    private fun selectBestRenderer() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        if (preferences.getBoolean(KEY_BEST_RENDERER_SELECTED, false)) {
            return
        }

        preferences.edit {
            if (RendererSupport.isComputeRendererSupported(this@MelonDSApplication)) {
                putString("video_renderer", VideoRenderer.COMPUTE.name.lowercase())
            }
            putBoolean(KEY_BEST_RENDERER_SELECTED, true)
        }
    }

    private fun forceToonFilter() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val filter = if (preferences.getBoolean("cel_shading", true)) VideoFiltering.TOON else VideoFiltering.LINEAR
        preferences.edit {
            putString("video_filtering", filter.name.lowercase())
        }
    }

    private fun createNotificationChannels() {
        val defaultChannel = NotificationChannelCompat.Builder(NOTIFICATION_CHANNEL_ID_BACKGROUND_TASKS, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(getString(R.string.notification_channel_background_tasks))
            .build()

        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.createNotificationChannel(defaultChannel)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun applyTheme() {
        GlobalScope.launch(Dispatchers.Main) {
            settingsRepository.observeTheme().collect {
                AppCompatDelegate.setDefaultNightMode(it.nightMode)
            }
        }
    }

    private fun performMigrations() {
        migrator.performMigrations()
    }

    override fun onTerminate() {
        super.onTerminate()
        MelonDSAndroidInterface.cleanup()
    }

    override val workManagerConfiguration: Configuration get() {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }
}