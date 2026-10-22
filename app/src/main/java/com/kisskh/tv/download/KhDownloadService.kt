package com.kisskh.tv.download

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.kisskh.tv.MainActivity
import com.kisskh.tv.R

private const val CHANNEL_ID = "downloads"
private const val NOTIFICATION_ID = 4101

/**
 * Keeps downloads running while the app is in the background, with a progress notification.
 * The download manager itself lives in [KhDownloads]; this service only hosts it.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class KhDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name,
    0,
) {
    private lateinit var notifications: DownloadNotificationHelper

    override fun onCreate() {
        super.onCreate()
        notifications = DownloadNotificationHelper(this, CHANNEL_ID)
    }

    override fun getDownloadManager(): DownloadManager = KhDownloads.get(this).manager

    // ponytail: no scheduler, so downloads do not restart by themselves after a reboot.
    // They continue the next time the app opens.
    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: List<Download>, notMetRequirements: Int): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return notifications.buildProgressNotification(
            this,
            R.drawable.ic_download_notification,
            open,
            null,
            downloads,
            notMetRequirements,
        )
    }
}
