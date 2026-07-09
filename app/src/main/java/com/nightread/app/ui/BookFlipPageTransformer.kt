package com.nightread.app.ui

import android.view.View
import androidx.viewpager2.widget.ViewPager2

/**
 * A custom PageTransformer for ViewPager2 that creates a realistic 3D book-flip effect.
 * The active page rotates around its left axis (spine) as it turns to the left,
 * while the next page scales up smoothly from underneath.
 */
class BookFlipPageTransformer : ViewPager2.PageTransformer {

    override fun transformPage(page: View, position: Float) {
        val width = page.width.toFloat()
        
        // Reset properties to avoid leftover states on recycled pages
        page.translationX = 0f
        page.translationY = 0f
        page.translationZ = 0f
        page.scaleX = 1f
        page.scaleY = 1f
        page.rotationX = 0f
        page.rotationY = 0f
        page.rotation = 0f
        page.alpha = 1f

        // Stacking pages on top of each other by cancelling the default slide transition
        page.translationX = -position * width

        if (position < -1f) { // [-Infinity, -1)
            // Page is way off-screen to the left
            page.alpha = 0f
        } else if (position <= 0f) { // [-1, 0]
            // Current page is flipping to the left (going from 0 to -1)
            page.alpha = 1f + position // Fade out near the end of rotation
            page.pivotX = 0f // Rotate around the left edge (book spine)
            page.rotationY = 120f * position // Rotate from 0 to -120 degrees
            
            // Light scaling for depth
            val scale = 1f + 0.08f * position
            page.scaleX = scale
            page.scaleY = scale
            
            // Keep the turning page on top of the next page
            page.translationZ = 1f + position
        } else if (position <= 1f) { // (0, 1]
            // Next page lying flat underneath (going from 1 to 0 as it is revealed)
            page.alpha = 1f
            page.pivotX = 0f
            page.rotationY = 0f
            
            // Scaling up slightly as it is revealed to simulate depth
            val scale = 0.95f + 0.05f * (1f - position)
            page.scaleX = scale
            page.scaleY = scale
            
            // Stay behind the active flipping page
            page.translationZ = -position
        } else { // (1, +Infinity]
            // Page is way off-screen to the right
            page.alpha = 0f
        }

        // Enable hardware acceleration layer for smooth 60 FPS animation performance
        if (page.layerType != View.LAYER_TYPE_HARDWARE) {
            page.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
    }
}
