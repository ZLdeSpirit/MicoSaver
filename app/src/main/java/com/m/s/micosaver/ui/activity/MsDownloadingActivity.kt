package com.m.s.micosaver.ui.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.bumptech.glide.Glide
import com.m.s.micosaver.R
import com.m.s.micosaver.broadcast.BroadcastHelper
import com.m.s.micosaver.databinding.MsActivityDownloadingBinding
import com.m.s.micosaver.databinding.MsDownloadingItemBinding
import com.m.s.micosaver.db.info.SavingVideoInfo
import com.m.s.micosaver.ex.toast
import com.m.s.micosaver.helper.VideoHelper
import com.m.s.micosaver.ui.base.BaseActivity
import com.m.s.micosaver.ui.dialog.DeleteTipDialog

class MsDownloadingActivity : BaseActivity() {
    private val mBinding by lazy { MsActivityDownloadingBinding.inflate(layoutInflater) }

    private var adapter: DownloadingAdapter? = null

    override fun onRootView(): View {
        return mBinding.root
    }

    override fun onBroadcastActionList(): List<String>? {
        return listOf(BroadcastHelper.ACTION_SAVING_VIDEO_CHANGE)
    }

    override fun onBroadcastActionReceived(intent: Intent?) {
        super.onBroadcastActionReceived(intent)
        loadData()
    }

    override fun onInitView() {
        mBinding.run {
            backBtn.setOnClickListener {
                onClose()
            }
            adapter = DownloadingAdapter()
            recyclerView.adapter = adapter
        }
        loadData()
    }

    private fun loadData() {
        val list = VideoHelper.savingVideoInfoList
        adapter?.setDataList(list)
        mBinding.run {
            recyclerView.isGone = list.isEmpty()
            emptyLl.isGone = list.isNotEmpty()
        }
    }

//    override fun onShowNativeInfo(): Pair<String, FrameLayout> {
//        return AdHelper.Position.ELSE_NATIVE to binding.dropAd
//    }

    @SuppressLint("DefaultLocale")
    fun formatMillis(duration: Long): String {
        val totalSeconds = duration / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours <= 0L) {
            String.format("%02d:%02d", minutes, seconds)
        } else {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    inner class DownloadingHolder(val binding: MsDownloadingItemBinding) :
        ViewHolder(binding.root), SavingVideoInfo.OnDownloadListener {

        override fun onStateChange(info: SavingVideoInfo) {
            binding.downloadStateIv.alpha = if (info.isSaving) 1f else 0.3f
        }

        @SuppressLint("SetTextI18n")
        override fun onProgressChange(info: SavingVideoInfo) {
            val progress = info.progress
//            binding.dropProgressBar.progress = progress
            binding.progressTv.text = "$progress%"
        }

    }

    inner class DownloadingAdapter : Adapter<DownloadingHolder>() {

        private val dataList = mutableListOf<SavingVideoInfo>()

        @SuppressLint("NotifyDataSetChanged")
        fun setDataList(dataList: List<SavingVideoInfo>) {
            this.dataList.clear()
            this.dataList.addAll(dataList)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadingHolder {
            return DownloadingHolder(
                MsDownloadingItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun getItemCount(): Int {
            return dataList.size
        }

        override fun onBindViewHolder(holder: DownloadingHolder, position: Int) {
            holder.binding.apply {
                val context = root.context
                dataList[position].run {
                    holder.onStateChange(this)
                    holder.onProgressChange(this)
                    Glide.with(context).load(showCoverUrl()).into(coverIv)
                    titleTv.text = videoDesc
                    val desc = "${formatMillis(duration)}  ${Formatter.formatFileSize(context, totalLength)}"
                    descTv.text = desc
                    deleteIv.setOnClickListener {
                        DeleteTipDialog(
                            this@MsDownloadingActivity,
                            context.getString(R.string.ms_downloading_delete_tip_title),
                            context.getString(R.string.ms_downloading_delete_tip_desc)
                        ) {
                            deleteDownload()
                            context.toast()
                        }.show()
                    }
                    downloadStateIv.setOnClickListener {
                        if (isSaving) {
                            pauseDownload()
                        } else {
                            startDownload()
                        }
                    }
                }
            }
        }

        override fun onViewAttachedToWindow(holder: DownloadingHolder) {
            super.onViewAttachedToWindow(holder)
            val pos = holder.absoluteAdapterPosition
            if (pos >= 0 && pos < dataList.size) {
                dataList[pos].registerDownloadListener(holder)
            }
        }

        override fun onViewDetachedFromWindow(holder: DownloadingHolder) {
            super.onViewDetachedFromWindow(holder)
            val pos = holder.absoluteAdapterPosition
            if (pos >= 0 && pos < dataList.size) {
                dataList[pos].unregisterDownloadListener(holder)
            }
        }
    }
}