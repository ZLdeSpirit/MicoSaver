package com.m.s.micosaver.ui.dialog

import android.view.View
import com.bumptech.glide.Glide
import com.m.s.micosaver.databinding.MsDialogDownloadFinishBinding
import com.m.s.micosaver.db.MsDataBase
import com.m.s.micosaver.db.info.SavingVideoInfo
import com.m.s.micosaver.ex.scope
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.helper.VideoHelper
import com.m.s.micosaver.ui.base.BaseActivity
import kotlinx.coroutines.launch

class DownloadFinishDialog(
    private val activity: BaseActivity,
    private val savingVideoInfo: SavingVideoInfo
) : BaseDialog1(activity, false) {

    private val binding by lazy { MsDialogDownloadFinishBinding.inflate(layoutInflater) }
    private var isVisibleDialog = true

    override fun onRootView(): View {
        return binding.root
    }

    override fun onInitView() {
        binding.run {
            Glide.with(context).load(savingVideoInfo.showCoverUrl()).into(imageIv)

            dialogCloseIv.setOnClickListener {
                dismiss()
            }

            imageIv.setOnClickListener {
                playVideo()
            }
            playBtn.setOnClickListener {
                playVideo()
            }

        }
        deleteSavingVideoInfo()
        loadShowAd()
        FirebaseHelper.logEvent("ms_video_saved")
    }

    private fun loadShowAd() {
        //todo
//        FirebaseHelper.logEvent("ms_scene_${AdHelper.Position.SAVED_NATIVE}")
//        AdHelper.load(AdHelper.Position.SAVED_NATIVE) {
//            if (isVisibleDialog) {
//                AdHelper.show(
//                    DropAd.ShowConfig(page, AdHelper.Position.SAVED_NATIVE)
//                        .setNativeLayout(binding.dropAd)
//                )
//            }
//        }
    }

    private fun playVideo() {
        FirebaseHelper.logEvent("drop_video_saved_play")
        dismiss()
        VideoHelper.playVideo(context, savingVideoInfo.localPath)
    }

    override fun dismiss() {
        super.dismiss()
        isVisibleDialog = false
    }

    private fun deleteSavingVideoInfo() {
        scope.launch {
            MsDataBase.database.savingVideoDao().delete(savingVideoInfo)
        }
    }

}