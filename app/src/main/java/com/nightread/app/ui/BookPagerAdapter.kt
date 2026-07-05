package com.nightread.app.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class BookPagerAdapter(
    activity: FragmentActivity,
    private var pages: List<String>
) : FragmentStateAdapter(activity) {

    fun updatePages(newPages: List<String>) {
        this.pages = newPages
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = pages.size

    override fun createFragment(position: Int): Fragment {
        return PageFragment.newInstance(pages[position])
    }

    override fun getItemId(position: Int): Long {
        val pageText = pages.getOrNull(position) ?: ""
        return (pageText.hashCode().toLong() shl 32) or position.toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        val position = (itemId and 0xFFFFFFFFL).toInt()
        if (position in pages.indices) {
            val pageText = pages[position]
            val expectedId = (pageText.hashCode().toLong() shl 32) or position.toLong()
            return itemId == expectedId
        }
        return false
    }
}
