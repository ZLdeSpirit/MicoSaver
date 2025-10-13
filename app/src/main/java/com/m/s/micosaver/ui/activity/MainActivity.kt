package com.m.s.micosaver.ui.activity

import android.view.View
import com.m.s.micosaver.databinding.MsActivityMainBinding
import com.m.s.micosaver.ui.base.BaseActivity

class MainActivity : BaseActivity() {
    private val mBinding by lazy { MsActivityMainBinding.inflate(layoutInflater) }
    override fun onRootView(): View {
        return mBinding.root
    }

    override fun onInitView() {
    }

}
