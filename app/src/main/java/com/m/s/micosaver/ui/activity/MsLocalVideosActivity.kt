package com.m.s.micosaver.ui.activity

import android.content.Intent
import android.view.View
import androidx.core.view.isGone
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.m.s.micosaver.R
import com.m.s.micosaver.broadcast.BroadcastHelper
import com.m.s.micosaver.databinding.MsActivityLocalVideosBinding
import com.m.s.micosaver.db.MsDataBase
import com.m.s.micosaver.ex.scope
import com.m.s.micosaver.ex.toast
import com.m.s.micosaver.helper.VideoHelper
import com.m.s.micosaver.ms
import com.m.s.micosaver.ui.adapter.VideosAdapter
import com.m.s.micosaver.ui.base.BaseActivity
import com.m.s.micosaver.ui.dialog.DeleteTipDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MsLocalVideosActivity : BaseActivity() {
    private val mBinding by lazy { MsActivityLocalVideosBinding.inflate(layoutInflater) }

    private lateinit var mAdapter: VideosAdapter

    private var isSelectMode = false

    override fun onRootView(): View {
        return mBinding.root
    }

    override fun onBroadcastActionList(): List<String> {
        return listOf(BroadcastHelper.ACTION_SAVED_VIDEO_CHANGE)
    }

    override fun onBroadcastActionReceived(intent: Intent?) {
        super.onBroadcastActionReceived(intent)
        loadData()
    }

    override fun onInitView() {
        mAdapter = VideosAdapter({ selectCount, totalCount ->
            mBinding.editIv.setImageResource(if (selectCount == totalCount) R.drawable.ms_ic_check_1 else R.drawable.ms_ic_check_3)
        })
        mBinding.apply {
            backBtn.setOnClickListener {
                onClose()
            }

            editIv.setOnClickListener {
                if (isSelectMode) {
                    mAdapter.switchSelectAll()
                } else {
                    isSelectMode = true
                    mAdapter.switchSelectMode()
                    backBtn.setImageResource(R.drawable.ms_ic_close_1)
                    mBinding.editIv.setImageResource(R.drawable.ms_ic_check_3)
                }
            }

            localBottomShareLl.setOnClickListener {
                val list = mAdapter.selectList
                if (list.isEmpty()) {
                    toast(getString(R.string.ms_select_videos))
                    return@setOnClickListener
                }
                ms.shareVideo(list.map { it.videoPath })
            }
            localBottomDeleteLl.setOnClickListener {
                val list = mAdapter.selectList
                if (list.isEmpty()) {
                    toast(getString(R.string.ms_select_videos))
                    return@setOnClickListener
                }
                DeleteTipDialog(
                    this@MsLocalVideosActivity,
                    getString(R.string.ms_delete_video),
                    getString(R.string.ms_delete_video_desc)
                ) {
                    VideoHelper.deleteSavedVideo(list)
                }.show()
            }

            recyclerView.layoutManager = GridLayoutManager(this@MsLocalVideosActivity, 3)
            recyclerView.setHasFixedSize(true)
            (recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
            recyclerView.adapter = mAdapter
        }
        loadData()
    }

    override fun onClose() {
        if (isSelectMode) {
            // X
            isSelectMode = false
            mAdapter.switchSelectMode()
            mBinding.backBtn.setImageResource(R.drawable.ms_ic_back)
            mBinding.editIv.setImageResource(R.drawable.ms_ic_check_2)
        } else {
            super.onClose()
        }
    }

    private fun loadData() {
        scope.launch {
            val dataList = MsDataBase.database.savedVideoDao().getAllVideos()
            withContext(Dispatchers.Main) {
                isSelectMode = false
                mAdapter.setDataList(dataList)
                mBinding.run {
                    recyclerView.isGone = dataList.isEmpty()
                    emptyLl.isGone = dataList.isNotEmpty()
                    editIv.isGone = dataList.isEmpty()
                }
            }
        }
    }

//    override fun onShowNativeInfo(): Pair<String, FrameLayout> {
//        return AdHelper.Position.ELSE_NATIVE to binding.dropAd
//    }
}