package com.nightread.app.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ReaderPagerAdapter(
    activity: FragmentActivity,
    var pages: List<CharSequence>,
    var offsets: List<Int> = emptyList()
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = pages.size

    override fun createFragment(position: Int): Fragment {
        val offset = if (position < offsets.size) offsets[position] else 0
        return PageFragment.newInstance(pages[position], offset)
    }
}
