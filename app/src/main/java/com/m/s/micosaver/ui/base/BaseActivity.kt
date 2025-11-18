package com.m.s.micosaver.ui.base

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.m.s.micosaver.ad.AdHelper
import com.m.s.micosaver.ad.MsAd
import com.m.s.micosaver.broadcast.BroadcastHelper
import com.m.s.micosaver.ex.scope
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.ms
import com.m.s.micosaver.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class BaseActivity : AppCompatActivity() {

    private var receiver: BroadcastReceiver? = null
    private var nativeHelper: ShowNativeHelper? = null

    var isVisiblePage: Boolean = true
        private set

    abstract fun onRootView(): View

    abstract fun onInitView()

    protected open fun onBroadcastActionList(): List<String>? {
        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ms.setProLanguage(this)
        super.onCreate(savedInstanceState)
        SetPageHelper().start()
        addBackPressed()
        setContentView(onRootView())
        onInitView()
        registerReceiver()
        onCreatePreloadList()?.forEach {
            AdHelper.preload(it)
        }
        if (onShowCloseAd()) {
            AdHelper.preload(AdHelper.Position.CLOSE_INTERS)
        }
    }

    private fun registerReceiver() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(BroadcastHelper.ACTION_SWITCH_LANGUAGE)
        onBroadcastActionList()?.forEach {
            intentFilter.addAction(it)
        }
        receiver = BroadcastHelper.register(
            this,
            intentFilter
        ) {
            if (it?.action == BroadcastHelper.ACTION_SWITCH_LANGUAGE) {
                onRecreatePage()
            } else {
                onBroadcastActionReceived(it)
            }
        }
    }

    protected open fun onRecreatePage() {
        recreate()
    }

    private fun addBackPressed() {
        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onClose()
            }
        })
    }

    override fun onResume() {
        isVisiblePage = true
        super.onResume()
        onShowNativeInfo()?.let {
            if (nativeHelper == null) {
                nativeHelper = ShowNativeHelper()
            }
            nativeHelper?.load(it)
        }
        onResumePreloadList()?.forEach {
            AdHelper.preload(it)
        }
    }

    fun loadNativeAd(nativeInfo: Pair<String, FrameLayout>){
        nativeHelper?.load(nativeInfo)
    }

    fun cancelLoadNativeAd(){
        nativeHelper?.cancelLoad()
    }

    override fun onPause() {
        isVisiblePage = false
        super.onPause()
        cancelLoadNativeAd()
    }

    override fun onDestroy() {
        super.onDestroy()
        onResetData()
    }

    protected fun preloadAd() {
        onCallPreloadList()?.forEach {
            AdHelper.preload(it)
        }
    }

    fun load(position: String, callbacks: () -> Unit) {
        AdHelper.load(position, callbacks)
    }

    fun showFullScreen(isEventScene: Boolean, close: () -> Unit) {
        val position = onShowFullScreenPosition()
        if (position.isNullOrEmpty()) {
            close.invoke()
            Logger.logDebugI("AdManager", "show: position is empty")
            return
        }
        showFullScreen(position, isEventScene, close)
    }

    fun showFullScreen(position: String, isEventScene: Boolean, close: () -> Unit) {
        if (isEventScene) {
            FirebaseHelper.logEvent("ms_scene_${position}")
        }
        AdHelper.show(MsAd.ShowConfig(this, position).setCloseCallback(close))
    }

    protected open fun onClose() {
        if (onShowCloseAd()) {
            AdHelper.show(MsAd.ShowConfig(this, AdHelper.Position.CLOSE_INTERS).setCloseCallback {
                finish()
            })
            return
        }
        finish()
    }

    protected open fun onResetData() {

    }

    protected open fun onBroadcastActionReceived(intent: Intent?) {

    }

    protected open fun onShowCloseAd(): Boolean {
        return true
    }

    protected open fun onCreatePreloadList(): List<String>? {
        return null
    }

    protected open fun onResumePreloadList(): List<String>? {
        return null
    }

    protected open fun onCallPreloadList(): List<String>? {
        return null
    }

    protected open fun onShowFullScreenPosition(): String? {
        return null
    }

    protected open fun onShowNativeInfo(): Pair<String, FrameLayout>? {
        return null
    }

    protected open fun onTopView(): View? = null

    protected open fun onSetPageEdge(): Boolean = false

    protected open fun onDarkStatus(): Boolean = false

    protected open fun onDarkNav(): Boolean = false

    inner class ShowNativeHelper {
        private var loadJob: Job? = null

        fun load(info: Pair<String, FrameLayout>) {
            if (loadJob?.isActive == true) return
            loadJob = scope.launch {
                delay(220)
                withContext(Dispatchers.Main) {
                    load(info.first) {
                        AdHelper.show(
                            MsAd.ShowConfig(this@BaseActivity, info.first)
                                .setNativeLayout(info.second)
                        )
                    }
                }
            }
        }

        fun cancelLoad() {
            loadJob?.cancel()
            loadJob = null
        }
    }

    inner class SetPageHelper {

        fun start() {
            setPageDensity()
            if (onSetPageEdge()) {
                setPageEdge()
                setApplyWindowInsets()
            }
        }

        private fun setApplyWindowInsets() {
            ViewCompat.setOnApplyWindowInsetsListener(onRootView()) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val topView = onTopView()
                if (topView == view) {
                    view.setPadding(
                        systemBars.left,
                        systemBars.top,
                        systemBars.right,
                        systemBars.bottom
                    )
                } else {
                    topView?.setPadding(0, systemBars.top, 0, 0)
                    view.setPadding(
                        systemBars.left,
                        0,
                        systemBars.right,
                        systemBars.bottom
                    )
                }
                insets
            }
        }

        private fun setPageEdge() {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            enableEdgeToEdge(
                if (onDarkStatus()) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT
                ),
                if (onDarkNav()) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT
                )
            )
        }

        private fun setPageDensity() {
            val display = resources.displayMetrics
            val scale = display.heightPixels / 812f
            display.density = scale
            display.densityDpi = (scale * 160).toInt()
            display.scaledDensity = scale
        }

    }

}