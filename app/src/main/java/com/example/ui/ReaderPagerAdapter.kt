package com.example.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ReaderPagerAdapter(
    activity: FragmentActivity,
    private val pages: List<String>
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = pages.size

    override fun createFragment(position: Int): Fragment {
        return PageFragment.newInstance(pages[position])
    }
}
