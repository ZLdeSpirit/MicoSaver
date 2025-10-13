package com.m.s.micosaver.ui.activity

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import com.m.s.micosaver.R
import com.m.s.micosaver.databinding.MsActivityHowToUseBinding
import com.m.s.micosaver.ui.base.BaseActivity
import com.m.s.micosaver.ui.fragment.HowToUseAdapter
import com.m.s.micosaver.ui.fragment.HowToUseFragment1
import com.m.s.micosaver.ui.fragment.HowToUseFragment2

class MsHowToUseActivity : BaseActivity() {
    private val mBinding by lazy { MsActivityHowToUseBinding.inflate(layoutInflater) }

    private val tabFragment = arrayListOf(
        HowToUseFragment1(),
        HowToUseFragment2(),
    )

    private val onPageChangedListener = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            when(position){
                0 -> {
                    mBinding.apply {
                        dot1View.setBackgroundResource(R.drawable.ms_dot_select)
                        dot2View.setBackgroundResource(R.drawable.ms_dot_un_select)
                    }
                }
                1 -> {
                    mBinding.apply {
                        dot1View.setBackgroundResource(R.drawable.ms_dot_un_select)
                        dot2View.setBackgroundResource(R.drawable.ms_dot_select)
                    }
                }
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
        view()
        listener()
    }

    private fun view(){
        mBinding.apply {
            viewPager.offscreenPageLimit = tabFragment.size
            viewPager.adapter =
                HowToUseAdapter(
                    supportFragmentManager,
                    this@MsHowToUseActivity.lifecycle,
                    tabFragment
                )
            viewPager.registerOnPageChangeCallback(onPageChangedListener)
        }
    }

    private fun listener(){
        mBinding.apply {
            backBtn.setOnClickListener {
                onClose()
            }
        }
    }
}