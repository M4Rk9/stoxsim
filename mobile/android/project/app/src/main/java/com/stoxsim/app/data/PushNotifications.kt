package com.stoxsim.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.stoxsim.app.MainActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Tokens remain in Firebase's private installation store, never in app logs or exported data. */
internal object PushNotifications {
    const val ACTION = "com.stoxsim.app.OPEN_NOTIFICATIONS"
    const val OWNER = "push_owner"
    private const val CHANNEL = "learning_updates"
    val tokenChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private fun preferences(context: Context) = context.getSharedPreferences("push-consent", Context.MODE_PRIVATE)
    fun owner(context: Context): String? = preferences(context).getString("owner", null)
    fun configured(context: Context): Boolean = FirebaseApp.getApps(context).any { it.name == FirebaseApp.DEFAULT_APP_NAME }
    fun permitted(context: Context): Boolean = (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        && context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    @Synchronized fun clear(context: Context) {
        preferences(context).edit().remove("owner").commit()
        context.getSystemService(NotificationManager::class.java).cancelAll()
        if(configured(context)) FirebaseMessaging.getInstance().isAutoInitEnabled = false
    }
    @Synchronized fun allow(context: Context, owner: String) {
        require(configured(context) && permitted(context))
        java.util.UUID.fromString(owner)
        preferences(context).edit().putString("owner", owner).commit()
        FirebaseMessaging.getInstance().isAutoInitEnabled = true
    }
    suspend fun sync(context: Context, api: StoxSimApi, user: String) {
        if(owner(context) != user || !configured(context))return
        if(!permitted(context)) { unregister(context,api,user);return }
        val enabled = api.objectData("notifications/preferences").optBoolean("pushEnabled")
        if(!enabled){unregister(context,api,user);return}
        val token=token()
        if(owner(context)!=user)return
        api.mutate("notifications/devices",body=JSONObject().put("token",token).put("platform","ANDROID"),expectedUserId=user)
    }
    suspend fun unregister(context: Context, api: StoxSimApi, user: String) {
        val registered = owner(context)==user
        clear(context) // Stop local display before any network operation can fail.
        if(registered && configured(context)) try {
            api.mutate("notifications/devices","DELETE",JSONObject().put("token",token()),expectedUserId=user)
        } catch(cancelled: kotlinx.coroutines.CancellationException){throw cancelled}
        catch(_: Exception){ /* Session revocation also removes server device registrations. */ }
    }
    private suspend fun token(): String = try { withTimeout(10000) {
        suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token.addOnSuccessListener { if(continuation.isActive)continuation.resume(it) }
                .addOnFailureListener { if(continuation.isActive)continuation.resumeWithException(java.io.IOException("Push registration unavailable")) }
        }
    } } catch(_: kotlinx.coroutines.TimeoutCancellationException) { throw java.io.IOException("Push registration unavailable") }
    fun accepts(localOwner: String?, remoteOwner: String?, notificationId: String?): Boolean = try {
        localOwner != null && localOwner == remoteOwner && java.util.UUID.fromString(notificationId).toString() == notificationId
    } catch(_: Exception){false}
    @Synchronized fun display(context: Context, data: Map<String,String>) {
        val owner = owner(context)
        if(!permitted(context) || !accepts(owner,data["owner"],data["notificationId"]))return
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"StoxSim updates",NotificationManager.IMPORTANCE_DEFAULT))
        val intent=Intent(context,MainActivity::class.java).setAction(ACTION).putExtra(OWNER,owner)
        val pending=PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // Generic content only; detailed inbox data is fetched under the current authenticated account.
        val notification=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("StoxSim update").setContentText("Open StoxSim to review your notifications.")
            .setContentIntent(pending).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        manager.notify(data.getValue("notificationId").hashCode(),notification)
    }
}

class StoxSimMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { PushNotifications.tokenChanges.tryEmit(Unit) }
    override fun onRegistered(token: String) { PushNotifications.tokenChanges.tryEmit(Unit) }
    override fun onMessageReceived(message: RemoteMessage) { PushNotifications.display(this,message.data) }
}
