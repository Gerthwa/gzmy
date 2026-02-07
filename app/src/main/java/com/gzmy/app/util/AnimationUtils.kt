package com.gzmy.app.util

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * Reusable animation helpers for consistent UI polish.
 */
object AnimationUtils {

    /**
     * Staggered slide-up + fade-in for a list of views.
     * Each view starts [staggerDelay] ms after the previous one.
     */
    fun staggeredEntrance(views: List<View>, staggerDelay: Long = 80L) {
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 60f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setStartDelay(index * staggerDelay)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
        }
    }

    /**
     * Scale-bounce when button is pressed, then a callback on release.
     */
    fun pressScale(view: View, onRelease: (() -> Unit)? = null) {
        view.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(80)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .setInterpolator(OvershootInterpolator(2f))
                    .withEndAction { onRelease?.invoke() }
                    .start()
            }
            .start()
    }

    /**
     * Heartbeat pulse: alternating scale animation.
     */
    fun pulseForever(view: View, minScale: Float = 0.95f, maxScale: Float = 1.08f, duration: Long = 1200L) {
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, minScale, maxScale, minScale)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, minScale, maxScale, minScale)
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            this.duration = duration
            this.interpolator = AccelerateDecelerateInterpolator()
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    /**
     * Fade in a single view.
     */
    fun fadeIn(view: View, duration: Long = 300L, startDelay: Long = 0L) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(startDelay)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /**
     * Slide up from bottom with fade.
     */
    fun slideUp(view: View, duration: Long = 350L, startDelay: Long = 0L) {
        view.alpha = 0f
        view.translationY = 100f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(duration)
            .setStartDelay(startDelay)
            .setInterpolator(DecelerateInterpolator(1.2f))
            .start()
    }

    /**
     * Pop-in scale animation (e.g. for emoji/notification badges).
     */
    fun popIn(view: View, startDelay: Long = 0L) {
        view.scaleX = 0f
        view.scaleY = 0f
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(350)
            .setStartDelay(startDelay)
            .setInterpolator(OvershootInterpolator(1.5f))
            .start()
    }

    /**
     * Shimmer highlight: brief brightness flash on a view.
     */
    fun shimmerFlash(view: View) {
        view.animate()
            .alpha(0.6f)
            .setDuration(150)
            .withEndAction {
                view.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            }
            .start()
    }
}
