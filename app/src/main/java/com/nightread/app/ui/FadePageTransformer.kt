package com.nightread.app.ui

import android.view.View
import androidx.viewpager2.widget.ViewPager2

/**
 * A custom PageTransformer that fades pages out as they are swiped away
 * and fades incoming pages in smoothly.
 */
class FadePageTransformer : ViewPager2.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        // Counteract the default slide transition to make pages stack
        page.translationX = -position * page.width
        page.translationY = 0f
        page.translationZ = 0f
        page.scaleX = 1f
        page.scaleY = 1f

        if (position < -1f || position > 1f) {
            page.alpha = 0f
            page.visibility = View.INVISIBLE
        } else {
            page.visibility = View.VISIBLE
            // Calculate alpha: 1.0 at center (0.0), down to 0.0 at edges (-1.0 and 1.0)
            page.alpha = 1f - kotlin.math.abs(position)
        }
        
        // Enable hardware acceleration layer for smooth performance
        if (page.layerType != View.LAYER_TYPE_HARDWARE) {
            page.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
    }
}
