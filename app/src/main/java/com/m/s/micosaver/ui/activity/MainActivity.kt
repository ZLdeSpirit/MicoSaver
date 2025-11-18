package com.m.s.micosaver.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.text.format.DateUtils
import android.util.Base64
import android.view.View
import android.widget.FrameLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.view.isGone
import com.m.s.micosaver.R
import com.m.s.micosaver.ad.AdHelper
import com.m.s.micosaver.broadcast.BroadcastHelper
import com.m.s.micosaver.channel.AppChannelHelper
import com.m.s.micosaver.databinding.MsActivityMainBinding
import com.m.s.micosaver.db.MsDataBase
import com.m.s.micosaver.ex.scope
import com.m.s.micosaver.ex.toastCustom
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.helper.ParamsHelper
import com.m.s.micosaver.helper.VideoHelper
import com.m.s.micosaver.ms
import com.m.s.micosaver.ui.base.BaseActivity
import com.m.s.micosaver.ui.dialog.AnalysisDialog
import com.m.s.micosaver.ui.dialog.DownloadFinishDialog
import com.m.s.micosaver.ui.dialog.PermissionDialog
import com.m.s.micosaver.ui.dialog.SettingsDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Random
import kotlin.math.abs

class MainActivity : BaseActivity() {
    private val mBinding by lazy { MsActivityMainBinding.inflate(layoutInflater) }
    private val showSaved by lazy { ShowSaved() }

    override fun onBroadcastActionList(): List<String> {
        return listOf(
            BroadcastHelper.ACTION_PLAY_VIDEO_CHANGE, BroadcastHelper.ACTION_SAVING_VIDEO_CHANGE
        )
    }

    override fun onBroadcastActionReceived(intent: Intent?) {
        super.onBroadcastActionReceived(intent)
        when (intent?.action) {
            BroadcastHelper.ACTION_PLAY_VIDEO_CHANGE -> {
                playVideoChange()
            }

            BroadcastHelper.ACTION_SAVING_VIDEO_CHANGE -> {
                savingVideoChange()
            }
        }
    }

    override fun onRootView(): View {
        return mBinding.root
    }

    override fun onSetPageEdge(): Boolean {
        return true
    }

    override fun onInitView() {
        listener()
    }

    private fun listener() {
        mBinding.apply {
            settingsLl.setOnClickListener {
                showFullScreen(true){
                    SettingsDialog(this@MainActivity).show()
                }
            }

            downloadLl.setOnClickListener {
                showFullScreen(true){
                    startActivity(Intent(this@MainActivity, MsDownloadingActivity::class.java))
                }
            }

            localVideosLl.setOnClickListener {
                showFullScreen(true){
                    startActivity(Intent(this@MainActivity, MsLocalVideosActivity::class.java))
                }
            }

            favoriteLl.setOnClickListener {
                showFullScreen(true){
                    startActivity(Intent(this@MainActivity, MsFavoriteActivity::class.java))
                }
            }

            startCl.setOnClickListener {
                val parseUrl = pasteContent()
                val isValidUrl = VideoHelper.isValidVideoUrl(parseUrl)
                if (isValidUrl || AppChannelHelper.isPro) {
                    showFullScreen(true) {
                        openParse(parseUrl, isValidUrl, ParamsHelper.FromParse.PASTE)
                    }
                } else {
                    toastCustom(getString(R.string.ms_parse_error_desc1))
                }
            }
        }

        playVideoChange()
        savingVideoChange()
        openMsg {
            checkEnterType()
        }
    }

    private fun openParse(
        parseUrl: String?,
        isValidUrl: Boolean,
        fromParse: ParamsHelper.FromParse
    ) {
        AnalysisDialog(this, isValidUrl, fromParse, parseUrl).show()
    }

    private fun pasteContent(): String? {
        try {
            val manager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            if (manager.hasPrimaryClip() && manager.primaryClipDescription?.hasMimeType("text/plain") == true) {
                return manager.primaryClip?.getItemAt(0)?.text?.toString()?.replace("\n", "")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    @SuppressLint("InlinedApi")
    private fun openMsg(complete: () -> Unit) {
        if (ms.isOpenMsg || abs(System.currentTimeMillis() - ms.data.showOpenMsgTime) < DateUtils.DAY_IN_MILLIS) {
            complete()
            return
        }
        var startTime = -1L
        val launcher = registerLauncher(complete) { isGranted, settingsLauncher ->
            if (isGranted || !openSetting(settingsLauncher, startTime)) {
                complete()
            }
        }
        PermissionDialog(this, {
            startTime = SystemClock.elapsedRealtime()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }) {
            complete()
        }.show()
    }

    private fun openSetting(launcher: ActivityResultLauncher<Intent>, startTime: Long): Boolean {
        if (SystemClock.elapsedRealtime() - startTime > 510) return false
        try {
            launcher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:$packageName".toUri()
            })
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun registerLauncher(
        settings: () -> Unit,
        permission: (Boolean, ActivityResultLauncher<Intent>) -> Unit
    ): ActivityResultLauncher<String> {
        val settingsLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                settings()
            }
        val permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                permission(it, settingsLauncher)
            }
        return permissionLauncher
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startSkipPage(intent)
    }

    private fun checkEnterType() {
        if (startSkipPage(intent)) {
            showSaved.allowStart()
            return
        }
        if (showDefaultGuide()) {
            showSaved.allowStart()
            return
        }
        showSaved.allowStart()
        showSaved.start()
    }

    private fun showDefaultGuide(): Boolean {
        if (!AppChannelHelper.isPro || ms.data.isShowedDefaultGuide) return false
        val config = FirebaseHelper.remoteConfig.defaultGuidePostsConfig
        if (config.isEmpty()) return false
        try {
            val array = JSONArray(String(Base64.decode(config, Base64.NO_WRAP)))
            if (array.length() <= 0) return false
            val parseUrl = array.getString(Random().nextInt(array.length()))
            openParse(
                parseUrl,
                VideoHelper.isValidVideoUrl(parseUrl),
                ParamsHelper.FromParse.DEFAULT
            )
            ms.data.isShowedDefaultGuide = true
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun startSkipPage(intent: Intent?): Boolean {
        if (intent == null) return false
        val enterType = intent.getStringExtra(ParamsHelper.KEY_ENTER_TYPE)
        intent.removeExtra(ParamsHelper.KEY_ENTER_TYPE)
        when (enterType) {
            ParamsHelper.EnterType.SAVED.type -> {
                val videoPath = intent.getStringExtra(ParamsHelper.KEY_VIDEO_PATH)
                if (videoPath.isNullOrEmpty()) return false
                VideoHelper.playVideo(this, videoPath)
            }

            ParamsHelper.EnterType.SAVING.type -> {
                startActivity(Intent(this, MsDownloadingActivity::class.java))
            }

            ParamsHelper.EnterType.PARSE.type -> {
                val parseUrl = intent.getStringExtra(ParamsHelper.KEY_PARSE_URL)
                val isValidUrl = VideoHelper.isValidVideoUrl(parseUrl)
                openParse(
                    parseUrl, isValidUrl, ParamsHelper.FromParse.MSG
                )
            }

            ParamsHelper.EnterType.SHARE.type -> {
                val parseUrl = intent.getStringExtra(ParamsHelper.KEY_PARSE_URL)
                val isValidUrl = VideoHelper.isValidVideoUrl(parseUrl)
                if (!isValidUrl && !AppChannelHelper.isPro) {
                    toastCustom(getString(R.string.ms_parse_error_desc1))
                    return false
                }
                openParse(parseUrl, isValidUrl, ParamsHelper.FromParse.SHARE)
            }

            else -> {
                return false
            }
        }
        return true
    }

    override fun onDarkNav(): Boolean {
        return false
    }

    override fun onResume() {
        super.onResume()
        showSaved.start()
    }

    private fun playVideoChange() {
        scope.launch {
            val count = MsDataBase.database.savedVideoDao().noPlayCount()
            withContext(Dispatchers.Main) {
                val isHide = count <= 0
                mBinding.videosNumTv.isGone = isHide

                if (!isHide) {
                    mBinding.videosNumTv.text = "$count"
                }
            }
        }
    }

    private fun savingVideoChange() {
        val size = VideoHelper.savingVideoInfoList.size
        val isHide = size <= 0
        mBinding.downloadNumTv.isGone = isHide
        if (!isHide) {
            mBinding.downloadNumTv.text = "$size"
        }
    }

    override fun onResumePreloadList(): List<String> {
        return listOf(onShowFullScreenPosition())
    }

    override fun onShowFullScreenPosition(): String {
        return AdHelper.Position.MAIN_INTERS
    }

    override fun onShowNativeInfo(): Pair<String, FrameLayout> {
        return AdHelper.Position.MAIN_NATIVE to mBinding.nativeContainer
    }

    override fun onShowCloseAd(): Boolean {
        return false
    }

    inner class ShowSaved {
        private var isCanStart = false
        private var mDialog: DownloadFinishDialog? = null
        private var getJob: Job? = null

        fun allowStart() {
            isCanStart = true
        }

        fun start() {
            if (!isCanStart || getJob?.isActive == true || mDialog != null) return
            getJob = scope.launch {
                val savingVideoInfo =
                    MsDataBase.database.savingVideoDao().getFinishInfo() ?: return@launch
                withContext(Dispatchers.Main) {
                    mDialog = DownloadFinishDialog(this@MainActivity, savingVideoInfo).apply {
                        setOnDismissListener {
                            mDialog = null
                        }
                        show()
                    }
                }
            }
        }
    }

}
