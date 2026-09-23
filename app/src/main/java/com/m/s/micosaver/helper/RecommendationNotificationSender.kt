package com.m.s.micosaver.helper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import androidx.core.graphics.createBitmap
import com.bumptech.glide.Glide
import com.m.s.micosaver.R
import com.m.s.micosaver.db.info.RecommendBean
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.ms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object RecommendationNotificationSender {
    const val TAG = "RecommendNotice"

    suspend fun send(
        logType: String,
        intervalScene: String,
        finalCheck: () -> Boolean,
    ): Boolean {
        val recommend = getRecommendations().randomOrNull()
        if (recommend == null) {
            Log.i(TAG, "type=$logType sent=false reason=no_recommendation")
            return false
        }
        val image = withContext(Dispatchers.IO) { createCoverBitmap(recommend.cover) }
        return withContext(Dispatchers.Main) {
            if (!finalCheck()) {
                Log.i(TAG, "type=$logType sent=false reason=final_condition")
                return@withContext false
            }
            val sent = sendRecommendation(recommend, image, logType)
            Log.i(TAG, "type=$logType sent=$sent")
            if (sent) {
                NotificationIntervalLimiter.recordSent(intervalScene)
                FirebaseHelper.logEvent("ms_send_msg_suc", Bundle().apply {
                    putString("type", logType)
                })
            }
            sent
        }
    }

    private suspend fun getRecommendations(): ArrayList<RecommendBean> =
        suspendCancellableCoroutine { continuation ->
            RecommendManager.getPurchaseUserFunList { list ->
                if (continuation.isActive) continuation.resume(list)
            }
        }

    private fun sendRecommendation(
        recommend: RecommendBean,
        image: Bitmap?,
        logType: String,
    ): Boolean {
        val msgId = SendMsgHelper.getMsgId()
        val intent = SendMsgHelper.createMsgIntent(msgId).apply {
            putExtra(ParamsHelper.KEY_ENTER_TYPE, ParamsHelper.EnterType.PARSE.type)
            putExtra(ParamsHelper.KEY_PARSE_URL, recommend.url)
        }
        val title = recommend.desc.ifBlank {
            recommend.authorName.ifBlank { ms.getString(R.string.ms_app_name) }
        }
        return SendMsgHelper.sendRecommendMsg(
            msgId,
            image,
            title,
            ms.getString(R.string.ms_view),
            intent,
            logType,
        )
    }

    private fun createCoverBitmap(coverUrl: String): Bitmap? {
        if (coverUrl.isBlank()) return null
        return try {
            val drawable = Glide.with(ms).asDrawable().load(coverUrl).submit().get() ?: return null
            if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                ).also { bitmap ->
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                }
            }
        } catch (e: Exception) {
            Log.i(TAG, "load recommendation cover failed", e)
            null
        }
    }
}
