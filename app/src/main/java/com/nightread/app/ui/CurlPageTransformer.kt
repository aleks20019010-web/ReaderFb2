package com.nightread.app.ui

import android.view.View
import androidx.viewpager2.widget.ViewPager2

/**
 * A highly polished 3D Page Curl & Deformation page transformer.
 * Simulates a realistic paper curl, 3D leaf fold, and corner lift.
 */
class CurlPageTransformer : ViewPager2.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        val density = page.resources.displayMetrics.density
        
        // Ensure 3D depth and correct perspective
        page.cameraDistance = 12000f * density

        when {
            position < -1f -> { // [-Infinity, -1]
                // Page is way off-screen to the left
                page.alpha = 0f
                page.visibility = View.INVISIBLE
            }
            position <= 0f -> { // [-1, 0]
                // Current page is being swiped left (turning page)
                page.visibility = View.VISIBLE
                page.alpha = 1f
                
                // Set the pivot to the left edge (book spine)
                page.pivotX = 0f
                page.pivotY = page.height / 2f
                
                // 1. Core 3D fold rotation around the spine
                val rotationYVal = 110f * position
                page.rotationY = rotationYVal
                
                // 2. Organic corner lift / page deformation on Z-axis
                // Reaches maximum curl/tilt at position -0.5
                val curlZ = -12f * position * (1f + position)
                page.rotation = curlZ
                
                // 3. Subtle tilt along X-axis to create a physical paper warp
                val warpX = 8f * position * (1f + position)
                page.rotationX = warpX
                
                // 4. Horizontal scale contraction to simulate visual depth compression as paper folds
                val scaleCompression = 1f + (position * 0.15f)
                page.scaleX = scaleCompression
                page.scaleY = 1f - (Math.abs(position) * 0.05f) // slight vertical squeeze
                
                // 5. Shift translation horizontally to keep the spine anchored correctly
                page.translationX = -position * page.width * 0.12f
                
                // 6. Realistic page shadow fade (darkens inside the fold)
                // We simulate this by lowering alpha slightly to blend with the dark background
                page.alpha = 1f - (Math.abs(position) * 0.45f)
            }
            position <= 1f -> { // (0, 1]
                // Next page sliding in under the curling page
                page.visibility = View.VISIBLE
                page.alpha = 1f
                
                // Keep the next page flat underneath
                page.pivotX = 0f
                page.pivotY = page.height / 2f
                page.rotationX = 0f
                page.rotationY = 0f
                page.rotation = 0f
                page.scaleX = 1f
                page.scaleY = 1f
                
                // Anchor it underneath the active page to reveal itself as the active page peels away
                page.translationX = -position * page.width
            }
            else -> { // (1, +Infinity]
                // Page is off-screen to the right
                page.alpha = 0f
                page.visibility = View.INVISIBLE
            }
        }
    }
}
