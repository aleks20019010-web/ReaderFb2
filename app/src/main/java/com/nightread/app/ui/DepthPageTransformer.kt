package com.nightread.app.ui

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

/**
 * A custom PageTransformer for ViewPager2 that creates a smooth depth transition.
 * The departing page recedes into the background (scaling down to 0.85 and fading to 0.5 alpha),
 * while the incoming page emerges to the foreground, scaling up from 0.85 to 1.0.
 */
class DepthPageTransformer : ViewPager2.PageTransformer {

    override fun transformPage(page: View, position: Float) {
        val width = page.width.toFloat()

        // Reset properties to avoid visual artifacts on recycled views
        page.translationX = 0f
        page.translationY = 0f
        page.translationZ = 0f
        page.scaleX = 1f
        page.scaleY = 1f
        page.alpha = 1f

        if (position < -1f) { // [-Infinity, -1)
            // Page is way off-screen to the left
            page.alpha = 0f
        } else if (position <= 0f) { // [-1, 0]
            // Current page is moving to the left (departing)
            val progress = abs(position)
            
            // Fade out to 0.5 alpha
            page.alpha = 1f - (progress * 0.5f)
            
            // Scale down to 0.85
            val scaleFactor = 1f - (progress * 0.15f)
            page.scaleX = scaleFactor
            page.scaleY = scaleFactor
            
            // Counteract default slide to keep it centered
            page.translationX = -position * width
            // Push backward in Z-index
            page.translationZ = -1f
            
        } else if (position <= 1f) { // (0, 1]
            // Next page is coming in from the right
            val progress = position
            
            // Keep full alpha or slight fade
            page.alpha = 1f - (progress * 0.2f)
            
            // Scale up from 0.85 to 1.0
            val scaleFactor = 0.85f + ((1f - progress) * 0.15f)
            page.scaleX = scaleFactor
            page.scaleY = scaleFactor
            
            // Stack page by counteracting slide
            page.translationX = -position * width
            // Bring forward in Z-index
            page.translationZ = 1f - position
        } else { // (1, +Infinity]
            // Page is way off-screen to the right
            page.alpha = 0f
        }

        // Enable hardware acceleration layer for butter-smooth animation
        if (page.layerType != View.LAYER_TYPE_HARDWARE) {
            page.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
    }
}
