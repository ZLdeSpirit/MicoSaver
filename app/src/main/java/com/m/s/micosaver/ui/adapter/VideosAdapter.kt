package com.m.s.micosaver.ui.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.m.s.micosaver.R
import com.m.s.micosaver.databinding.MsVideosItemBinding
import com.m.s.micosaver.db.info.SavedVideoInfo
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.helper.VideoHelper

class VideosAdapter(
    private val selectChange: (Int, Int) -> Unit
) : RecyclerView.Adapter<VideosAdapter.VideosHolder>() {

    private val dataList = mutableListOf<SavedVideoInfo>()
    val selectList = mutableListOf<SavedVideoInfo>()
    private var isSelectMode = false

    @SuppressLint("NotifyDataSetChanged")
    fun setDataList(dataList: List<SavedVideoInfo>) {
        this.isSelectMode = false
        this.dataList.clear()
        this.dataList.addAll(dataList)
        this.selectList.clear()
        selectChange.invoke(0, dataList.size)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun switchSelectAll() {
        if (selectList.size == dataList.size) {
            selectList.clear()
        } else {
            selectList.clear()
            selectList.addAll(dataList)
        }
        selectChange.invoke(selectList.size, dataList.size)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun switchSelectMode() {
        isSelectMode = !isSelectMode
        this.selectList.clear()
        selectChange.invoke(0, dataList.size)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideosHolder {
        return VideosHolder(
            MsVideosItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return dataList.size
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: VideosHolder, position: Int) {
        holder.binding.apply {
            val context = root.context
            dataList[position].run {
                checkIv.isGone = !isSelectMode
                likeIv.isGone = isSelectMode
                val isSelect = selectList.contains(this)
                checkIv.setImageResource(if (isSelect) R.drawable.ms_ic_check_1 else R.drawable.ms_ic_check_3)
                likeIv.setImageResource(if (isLiked) R.drawable.ms_ic_like else R.drawable.ms_ic_un_like)
                Glide.with(context).load(videoPath).into(videoImageIv)
                root.setOnClickListener {
                    if (isSelectMode) {
                        if (isSelect) {
                            selectList.remove(this)
                        } else {
                            selectList.add(this)
                        }
                        selectChange.invoke(selectList.size, dataList.size)
                        notifyItemChanged(position)
                    } else {
                        VideoHelper.playVideo(context, videoPath)
                        FirebaseHelper.logEvent("ms_video_click_play")
                    }
                }
                likeIv.setOnClickListener {
                    VideoHelper.likeVideo(this)
                    notifyDataSetChanged()
                }
            }
        }
    }

    inner class VideosHolder(val binding: MsVideosItemBinding) : RecyclerView.ViewHolder(binding.root)
}