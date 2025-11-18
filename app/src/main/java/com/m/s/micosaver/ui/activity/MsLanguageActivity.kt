package com.m.s.micosaver.ui.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.m.s.micosaver.R
import com.m.s.micosaver.ad.AdHelper
import com.m.s.micosaver.channel.AppChannelHelper
import com.m.s.micosaver.databinding.MsActivityLanguageBinding
import com.m.s.micosaver.databinding.MsLanguageItemBinding
import com.m.s.micosaver.ms
import com.m.s.micosaver.ui.base.BaseActivity

class MsLanguageActivity : BaseActivity() {
    private val mBinding by lazy { MsActivityLanguageBinding.inflate(layoutInflater) }

    private var isFirstOpen = ms.data.isFirstOpen
    private var selectCode: String? = null

    override fun onRootView(): View {
        return mBinding.root
    }

    override fun onInitView() {

        mBinding.run {
            backBtn.isGone = isFirstOpen
            backBtn.setOnClickListener {
                onClose()
            }
            okBtn.isGone = !isFirstOpen || !AppChannelHelper.isPro
            val adapter = LanguageAdapter {
                selectCode = it
                okBtn.isGone = it.isNullOrEmpty()
            }
            recyclerView.adapter = adapter
            okBtn.setOnClickListener {
                showFullScreen(true) {
                    ms.data.isFirstOpen = false
                    ms.setProLanguage(selectCode)
                    if (isFirstOpen) {
                        startActivity(Intent(this@MsLanguageActivity, MainActivity::class.java).apply {
                            intent.extras?.let {
                                putExtras(it)
                            }
                        })
                    }
                    finish()
                }
            }
        }
    }

    override fun onRecreatePage() {

    }

    override fun onClose() {
        if (isFirstOpen) return
        super.onClose()
    }

    override fun onShowCloseAd(): Boolean {
        return !isFirstOpen
    }

    override fun onShowFullScreenPosition(): String {
        return AdHelper.Position.LAN_INTERS
    }

    override fun onShowNativeInfo(): Pair<String, FrameLayout> {
        return AdHelper.Position.LAN_NATIVE to mBinding.nativeContainer
    }

    override fun onCreatePreloadList(): List<String> {
        return if (isFirstOpen) {
            listOf(
                AdHelper.Position.MAIN_NATIVE,
                AdHelper.Position.MAIN_INTERS,
                AdHelper.Position.PARSE_INTERS,
                AdHelper.Position.PARSE_NATIVE,
                onShowFullScreenPosition()
            )
        } else {
            listOf(onShowFullScreenPosition())
        }
    }

    inner class LanguageHolder(val binding: MsLanguageItemBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class LanguageAdapter(private val select: (String?) -> Unit) :
        RecyclerView.Adapter<LanguageHolder>() {

        private var selectCode = if (isFirstOpen && AppChannelHelper.isPro) {
            ""
        } else {
            ms.data.languageCode
        }

        init {
            select.invoke(selectCode)
        }

        private val languageList = ms.languageList

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageHolder {
            return LanguageHolder(
                MsLanguageItemBinding.inflate(
                    LayoutInflater.from(this@MsLanguageActivity),
                    parent,
                    false
                )
            )
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onBindViewHolder(holder: LanguageHolder, position: Int) {
            holder.binding.apply {
                languageList[position].run {
                    languageTv.text = second
                    val isSelect = first == selectCode
                    if (isSelect) {
                        itemRoot.setBackgroundResource(R.drawable.ms_rect_storke_46c6fb_c_16)
                    } else {
                        itemRoot.setBackgroundResource(R.drawable.ms_rect_1f334d_c_16)
                    }
                    languageSelectedIv.isVisible = isSelect
                    root.setOnClickListener {
                        if (isSelect) return@setOnClickListener
                        selectCode = first
                        select.invoke(first)
                        notifyDataSetChanged()
                    }
                }
            }
        }

        override fun getItemCount(): Int {
            return languageList.size
        }
    }
}