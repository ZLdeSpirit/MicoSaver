package com.m.s.micosaver.ui.fragment

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter

class HowToUseAdapter : FragmentStateAdapter {

    private val exitsItemId: ArrayList<Long> = arrayListOf()

    private var fragmentList: List<Fragment>

    constructor(fm: FragmentManager, lifecycle: Lifecycle, fragments: List<Fragment>) : super(fm, lifecycle) {
        fragmentList = fragments
    }

    override fun getItemCount(): Int = fragmentList.size

    override fun containsItem(itemId: Long): Boolean {
        return exitsItemId.contains(itemId)
    }

    override fun getItemId(position: Int): Long = position.toLong()

    override fun createFragment(position: Int): Fragment {
        val newPos = position.toLong()
        if (!exitsItemId.contains(newPos)) {
            exitsItemId.add(newPos)
        }
        return fragmentList[position]
    }
}