package com.nightread.app.ui

import android.view.View
import androidx.viewpager2.widget.ViewPager2

/**
 * A custom PageTransformer that completely removes the page transition animation
 * by instantly hiding pages that are not fully selected.
 */
class NoAnimationTransformer : ViewPager2.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        // Counteract slide completely to keep pages stacked on top of each other
        page.translationX = -position * page.width
        page.translationY = 0f
        page.translationZ = 0f
        page.scaleX = 1f
        page.scaleY = 1f

        // Only show the page if it is the closest one to the screen center
        if (position > -0.5f && position <= 0.5f) {
            page.alpha = 1f
            page.visibility = View.VISIBLE
        } else {
            page.alpha = 0f
            page.visibility = View.INVISIBLE
        }
    }
}
