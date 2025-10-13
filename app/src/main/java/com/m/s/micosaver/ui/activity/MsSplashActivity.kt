package com.m.s.micosaver.ui.activity

import android.content.Intent
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.m.s.micosaver.databinding.MsActivitySplashBinding
import com.m.s.micosaver.ui.base.BaseActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MsSplashActivity : BaseActivity(){
    private val mBinding by lazy { MsActivitySplashBinding.inflate(layoutInflater) }
    override fun onRootView(): View {
        return mBinding.root
    }

    override fun onSetPageEdge(): Boolean {
        return true
    }

    override fun onInitView() {

        lifecycleScope.launch {
            delay(1000)
            startActivity(Intent(this@MsSplashActivity,MainActivity::class.java))
            finish()
        }
    }
}