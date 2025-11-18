package com.m.s.micosaver.ui.dialog

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.m.s.micosaver.Constant
import com.m.s.micosaver.R
import com.m.s.micosaver.ad.AdHelper
import com.m.s.micosaver.ad.MsAd
import com.m.s.micosaver.channel.AppChannelHelper
import com.m.s.micosaver.databinding.MsDialogAnalysisBinding
import com.m.s.micosaver.db.info.SavingVideoInfo
import com.m.s.micosaver.ex.scope
import com.m.s.micosaver.ex.toast
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.helper.ParamsHelper
import com.m.s.micosaver.ms
import com.m.s.micosaver.ui.activity.MainActivity
import com.m.s.micosaver.ui.base.BaseActivity
import com.m.s.micosaver.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class AnalysisDialog (
    private val activity: BaseActivity,
    private val isValidUrl: Boolean,
    private val fromParse: ParamsHelper.FromParse,
    private val parseUrl: String?
) : BaseDialog1(activity, false) {

    private val mBinding by lazy { MsDialogAnalysisBinding.inflate(layoutInflater) }

    private var parseVideo: ParseVideo? = null
    private var loadAdState = 0
    private var parseState = 0
    private val handler = Handler(Looper.getMainLooper())
    private val runnable = Runnable {
        if (parseState == 1) {
            showParseAd()
        } else {
            loadAdState = 1
        }
    }

    private var savingVideoInfo: SavingVideoInfo? = null
    private var nativeHelper: ShowNativeHelper? = null
    private var isVisibleDialog = true

    override fun onRootView(): View {
        return mBinding.root
    }

    override fun onInitView() {
        parseVideo = ParseVideo(parseUrl)
        mBinding.run {
            errorDescTv.setText(
                if (AppChannelHelper.isPro)
                    R.string.ms_parse_error_desc
                else
                    R.string.ms_parse_error_desc1
            )

            dialogCloseIv.setOnClickListener {
                dismiss()
            }

            retryBtn.setOnClickListener {
                start()
            }
            saveBtn.setOnClickListener {
                if (mBinding.analysisLoadingLl.isVisible || savingVideoInfo == null) {
                    activity.toast(activity.getString(R.string.ms_parsing_tip))
                    return@setOnClickListener
                }
                showFullScreen(AdHelper.Position.DOWNLOAD_INTERS) {
                    dismiss()
                    savingVideoInfo!!.startDownload()
                    context.startActivity(Intent(context, MainActivity::class.java).apply {
                        putExtra(ParamsHelper.KEY_ENTER_TYPE, ParamsHelper.EnterType.SAVING.type)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    })
                }
            }

        }
        start()
    }

    private fun loadNativeAd() {
        if (nativeHelper == null) {
            nativeHelper = ShowNativeHelper()
        }
        nativeHelper?.load()
    }

    fun showFullScreen(position: String, close: () -> Unit) {
        FirebaseHelper.logEvent("ms_scene_${position}")
        AdHelper.show(MsAd.ShowConfig(activity, position).setCloseCallback(close))
    }

    private fun start() {
        resetParseState()
        handler.removeCallbacks(runnable, 20000)
        AdHelper.load(AdHelper.Position.PARSE_INTERS) {
            if (loadAdState == 1) return@load
            handler.removeCallbacks(runnable)
            if (parseState == 1) {
                showParseAd()
            } else {
                loadAdState = 1
            }
        }
        parseVideo?.start()
        FirebaseHelper.logEvent("ms_parse_start", Bundle().apply {
            putString("from", fromParse.from)
        })
        loadNativeAd()
    }

    private fun resetParseState() {
        parseState = 0
        loadAdState = 0
        savingVideoInfo = null
        handler.removeCallbacks(runnable)
        mBinding.run {
            errorLl.isVisible = false
            analysisLoadingLl.isVisible = true
            contentNsv.isVisible = true
        }
    }

    private fun showParseAd() {
        showFullScreen(AdHelper.Position.PARSE_INTERS) {
            loadNativeAd()
            if (savingVideoInfo == null) {
                mBinding.run {
                    errorLl.isVisible = true
                    analysisLoadingLl.isVisible = false
                    contentNsv.isVisible = false
                }
                return@showFullScreen
            }
            mBinding.run {
                errorLl.isVisible = false
                analysisLoadingLl.isVisible = false
                contentNsv.isVisible = true
            }
        }
        AdHelper.preload(AdHelper.Position.DOWNLOAD_INTERS)
    }

    private fun parseSuccess(info: SavingVideoInfo) {
        savingVideoInfo = info
        if (loadAdState == 1) {
            showParseAd()
        } else {
            parseState = 1
        }
        Glide.with(context).load(info.showCoverUrl()).into(mBinding.imageIv)
        FirebaseHelper.logEvent("ms_parse_suc", Bundle().apply {
            putString("from", fromParse.from)
        })
    }

    private fun parseError(msg: String?) {
        if (loadAdState == 1) {
            showParseAd()
        } else {
            parseState = 1
        }
        Logger.logDebugI("AnalysisDialog", "parseError:${msg?:"not known"}")
        FirebaseHelper.logEvent("ms_parse_error", Bundle().apply {
            putString("msg", msg)
            putString("from", fromParse.from)
        })
    }

    override fun dismiss() {
        isVisibleDialog = false
        super.dismiss()
        parseVideo?.stop()
        handler.removeCallbacks(runnable)
        nativeHelper?.cancelLoad()
    }

    inner class ShowNativeHelper {
        private var loadJob: Job? = null

        fun load() {
            if (loadJob?.isActive == true) return
            loadJob = scope.launch {
                delay(220)
                FirebaseHelper.logEvent("ms_scene_${AdHelper.Position.PARSE_NATIVE}")
                withContext(Dispatchers.Main) {
                    AdHelper.load(AdHelper.Position.PARSE_NATIVE) {
                        if (!isVisibleDialog) return@load
                        AdHelper.show(
                            MsAd.ShowConfig(activity, AdHelper.Position.PARSE_NATIVE)
                                .setNativeLayout(mBinding.nativeContainer)
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


    inner class ParseVideo(private val parseUrl: String?) {

        private var isParsing = true
        private var parseJob: Job? = null

        fun start() {
            isParsing = true
            parseJob?.cancel()
            parseJob = scope.launch {
                if (isValidUrl) {
                    startParse()
                } else {
                    delay(3420)
                    parseError("url_invalid")
                }
            }
        }

        private suspend fun startParse() {
            try {
                val result = startRequest()
                if (result.isSuccessful) {
                    checkResult(result.body?.string())
                } else {
                    parseError(result.code.toString())
                }
            } catch (e: Exception) {
                e.printStackTrace()
                parseError(e.message)
            }
        }

        private suspend fun checkResult(body: String?) {
            if (body.isNullOrEmpty()) {
                parseError("body_null")
                return
            }
            val json = JSONObject(body)
            val code = json.getInt("code")
            if (code == 0) {
                val info = createSavingVideoInfo(json.getJSONObject("data"))
                parseSuccess(info)
            } else {
                parseError(code.toString())
            }
        }

        private fun createSavingVideoInfo(json: JSONObject): SavingVideoInfo {
            return SavingVideoInfo(
                parseUrl.orEmpty(),
                json.getString("yiu"),
                json.getString("bane"),
                json.getString("leo"),
                json.optString("fang")
            ).apply {
                authorName = json.optString("mme")
                duration = json.optLong("jing")
            }
        }

        private fun startRequest(): Response {
            val request = Request.Builder()
                .url(getRequestParseUrl())
                .addHeader("SQA", ms.packageName)
                .build()
            return createClient().newCall(request).execute()
        }

        private fun createClient(): OkHttpClient {
            val builder = OkHttpClient.Builder()
            try {
                val trustManager = @SuppressLint("CustomX509TrustManager")
                object : X509TrustManager {
                    override fun checkClientTrusted(
                        chain: Array<out X509Certificate>?,
                        authType: String?
                    ) = Unit

                    override fun checkServerTrusted(
                        chain: Array<out X509Certificate>?,
                        authType: String?
                    ) = Unit

                    override fun getAcceptedIssuers(): Array<X509Certificate> {
                        return arrayOf()
                    }
                }
                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, arrayOf(trustManager), SecureRandom())
                builder.sslSocketFactory(sslContext.socketFactory, trustManager)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            builder.hostnameVerifier { _, _ -> true }
            return builder.build()
        }

        private suspend fun parseSuccess(info: SavingVideoInfo) {
            if (!isParsing) {
                return
            }
            isParsing = false
            withContext(Dispatchers.Main) {
                this@AnalysisDialog.parseSuccess(info)
            }
        }

        private suspend fun parseError(msg: String?) {
            if (!isParsing) {
                return
            }
            isParsing = false
            withContext(Dispatchers.Main) {
                this@AnalysisDialog.parseError(msg)
            }
        }

        fun stop() {
            isParsing = false
            parseJob?.cancel()
            parseJob = null
        }

        private fun getRequestParseUrl(): String {
            return "${Constant.ANALYSIS_URL}${parseUrl}"
        }

    }

}