package com.m.s.micosaver.helper

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Intent
import android.os.Bundle
import com.m.s.micosaver.channel.AppChannelHelper
import com.m.s.micosaver.db.info.SavingVideoInfo
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.ms
import com.m.s.micosaver.ui.activity.MsSplashActivity
import com.m.s.micosaver.ui.base.BaseActivity
import com.m.s.micosaver.ui.dialog.DownloadFinishDialog

object LifecycleHelper {

    val isForeground: Boolean
        get() = visibilityCount > 0

    private val pageList = mutableListOf<Activity>()
    private var visibilityCount = 0

    fun startHotOpen(activity: Activity) {
        if (!isHotOpenEnable(activity)) return
        activity.startActivity(Intent(activity, MsSplashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(ParamsHelper.KEY_ENTER_TYPE, ParamsHelper.EnterType.HOT_OPEN.type)
        })
    }

    private fun isHotOpenEnable(activity: Activity): Boolean {
        if (activity is MsSplashActivity) return false
        if (activity !is BaseActivity) return false
        for (page in pageList) {
            if (page !is BaseActivity) return false
        }
        return when (FirebaseHelper.remoteConfig.hotOpenEnable) {
            0 -> true
            1 -> AppChannelHelper.isPro
            2 -> !AppChannelHelper.isPro
            else -> false
        }
    }

    fun addLifecycleCallback() {
        ms.registerActivityLifecycleCallbacks(LifecycleCallback())
    }

    fun showSavedDialog(info: SavingVideoInfo) {
        if (pageList.isEmpty()) return
        val page = pageList.last()
        if (page !is BaseActivity || !page.isVisiblePage) return
        DownloadFinishDialog(page, info).show()
    }

    internal class LifecycleCallback : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            pageList.add(activity)
        }

        override fun onActivityStarted(activity: Activity) {
            if (visibilityCount++ == 0) {
                startHotOpen(activity)
            }
        }

        override fun onActivityResumed(activity: Activity) {

        }

        override fun onActivityPaused(activity: Activity) {

        }

        override fun onActivityStopped(activity: Activity) {
            if (--visibilityCount == 0) {
                //TODO
//                AdHelper.preload(AdHelper.Position.WELCOME)
            }
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {

        }

        override fun onActivityDestroyed(activity: Activity) {
            pageList.remove(activity)
        }

    }

}