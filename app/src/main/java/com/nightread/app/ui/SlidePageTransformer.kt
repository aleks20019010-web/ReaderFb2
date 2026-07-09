package com.nightread.app.ui

import android.view.View
import androidx.viewpager2.widget.ViewPager2

/**
 * Reset all page transformations to the default ViewPager2 slide behavior.
 */
class SlidePageTransformer : ViewPager2.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        page.translationX = 0f
        page.translationY = 0f
        page.translationZ = 0f
        page.scaleX = 1f
        page.scaleY = 1f
        page.alpha = 1f
        page.visibility = View.VISIBLE
    }
}
