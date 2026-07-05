package com.nightread.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.nightread.app.R

class HighlightItemAnimator(private val adapter: SeriesGroupAdapter) : DefaultItemAnimator() {

    override fun animateAdd(holder: RecyclerView.ViewHolder?): Boolean {
        if (holder is SeriesGroupAdapter.BookViewHolder) {
            val book = adapter.getBookAt(holder.adapterPosition)
            if (book != null && adapter.newlyAddedSha1s.contains(book.sha1)) {
                // Apply gold border / highlight effect
                playHighlightAnimation(holder.itemView)
                adapter.newlyAddedSha1s.remove(book.sha1)
            }
        }
        return super.animateAdd(holder)
    }

    private fun playHighlightAnimation(view: View) {
        val coverBackground = view.findViewById<View>(R.id.vCoverBackground) ?: return
        
        val originalBackground = coverBackground.background
        
        val highlightColor = Color.parseColor("#FFD700") // Gold color
        val transparentGold = Color.parseColor("#00FFD700")
        
        // We can create a flash effect by animating a foreground drawable or just animating elevation
        view.elevation = 8f * view.resources.displayMetrics.density
        
        val animator = ValueAnimator.ofFloat(1f, 0f)
        animator.duration = 1000
        animator.addUpdateListener { anim ->
            val fraction = anim.animatedValue as Float
            view.elevation = (8f * view.resources.displayMetrics.density) * fraction
            
            // Optionally we can pulse alpha or something, but standard animateAdd already fades in.
            // A simple scale bump is nice.
            view.scaleX = 1f + (0.05f * fraction)
            view.scaleY = 1f + (0.05f * fraction)
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                view.elevation = 0f
                view.scaleX = 1f
                view.scaleY = 1f
            }
        })
        animator.start()
    }
}
