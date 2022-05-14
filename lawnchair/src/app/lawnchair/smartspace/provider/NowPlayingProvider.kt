package app.lawnchair.smartspace.provider

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Icon
import android.text.TextUtils
import app.lawnchair.DialogActivity
import app.lawnchair.getAppName
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceScores
import app.lawnchair.smartspace.model.SmartspaceTarget
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.Routes
import app.lawnchair.ui.preferences.components.isNotificationServiceEnabled
import app.lawnchair.ui.preferences.components.notificationDotsEnabled
import com.android.launcher3.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class NowPlayingProvider(context: Context) : SmartspaceDataSource(
    context, R.string.smartspace_now_playing, { smartspaceNowPlaying }
) {

    private val defaultIcon = Icon.createWithResource(context, R.drawable.ic_music_note)

    override val internalTargets = callbackFlow {
        val mediaListener = MediaListener(context) {
            trySend(listOfNotNull(getSmartspaceTarget(it)))
        }
        mediaListener.onResume()
        awaitClose { mediaListener.onPause() }
    }

    private fun getSmartspaceTarget(media: MediaListener): SmartspaceTarget? {
        val tracking = media.tracking ?: return null
        val title = tracking.info.title ?: return null

        val sbn = tracking.sbn
        val icon = sbn.notification.smallIcon ?: defaultIcon

        val mediaInfo = tracking.info
        val artistAndAlbum = listOf(mediaInfo.artist, mediaInfo.album)
            .filter { !TextUtils.isEmpty(it) }
            .joinToString(" – ")
        val subtitle = if (!TextUtils.isEmpty(artistAndAlbum)) {
            artistAndAlbum
        } else sbn?.getAppName(context) ?: context.getAppName(tracking.packageName)
        val intent = sbn?.notification?.contentIntent
        return SmartspaceTarget(
            id = "nowPlaying-${mediaInfo.hashCode()}",
            headerAction = SmartspaceAction(
                id = "nowPlayingAction-${mediaInfo.hashCode()}",
                icon = icon,
                title = title,
                subtitle = subtitle,
                pendingIntent = intent,
                onClick = if (intent == null) Runnable { media.toggle(true) } else null
            ),
            score = SmartspaceScores.SCORE_MEDIA,
            featureType = SmartspaceTarget.FeatureType.FEATURE_MEDIA
        )
    }

    override suspend fun requiresSetup(): Boolean {
        if (!isNotificationServiceEnabled(context)) return true
        if (!notificationDotsEnabled(context).first()) return true
        return false
    }

    override suspend fun startSetup(activity: Activity) {
        suspendCoroutine<Unit> { continuation ->
            val intent = PreferenceActivity.createIntent(activity, "/${Routes.GENERAL}/")
            val message = activity.getString(R.string.event_provider_missing_notification_dots,
                activity.getString(providerName))
            val dialogIntent = DialogActivity.getDialogIntent(
                activity, intent,
                activity.getString(R.string.title_missing_notification_access),
                message,
                context.getString(R.string.title_change_settings)
            ) {
                continuation.resume(Unit)
            }
            activity.startActivity(dialogIntent)
        }
    }
}