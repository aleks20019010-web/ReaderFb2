package com.nightread.app.ui

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs
import kotlin.math.max

/**
 * A custom PageTransformer that shrinks the page slightly as it departs
 * and grows it back to full size as it enters the center.
 */
class ZoomOutPageTransformer : ViewPager2.PageTransformer {
    private val MIN_SCALE = 0.85f
    private val MIN_ALPHA = 0.5f

    override fun transformPage(page: View, position: Float) {
        val width = page.width
        val height = page.height

        // Reset default states
        page.translationX = 0f
        page.translationY = 0f
        page.translationZ = 0f
        page.scaleX = 1f
        page.scaleY = 1f
        page.alpha = 1f
        page.visibility = View.VISIBLE

        if (position < -1f) { // [-Infinity,-1)
            // Page is way off-screen to the left
            page.alpha = 0f
            page.visibility = View.INVISIBLE
        } else if (position <= 1f) { // [-1,1]
            // Scale the page down between MIN_SCALE and 1.0
            val scaleFactor = max(MIN_SCALE, 1f - abs(position))
            val vertMargin = height * (1f - scaleFactor) / 2
            val horzMargin = width * (1f - scaleFactor) / 2
            
            // Adjust translation to keep the pages close to each other visually
            if (position < 0) {
                page.translationX = horzMargin - vertMargin / 2
            } else {
                page.translationX = -horzMargin + vertMargin / 2
            }

            page.scaleX = scaleFactor
            page.scaleY = scaleFactor

            // Fade the page relative to its size
            page.alpha = MIN_ALPHA + (scaleFactor - MIN_SCALE) / (1f - MIN_SCALE) * (1f - MIN_ALPHA)
        } else { // (1,+Infinity]
            // Page is way off-screen to the right
            page.alpha = 0f
            page.visibility = View.INVISIBLE
        }

        // Enable hardware acceleration layer for smooth performance
        if (page.layerType != View.LAYER_TYPE_HARDWARE) {
            page.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
    }
}
