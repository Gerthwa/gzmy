package com.gzmy.app.util

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * Premium animation system for GZMY.
 * Provides micro-interactions, entrance effects, and continuous ambient animations.
 */
object AnimationUtils {

    // ═══════════════════════════════════════
    //  ENTRANCE ANIMATIONS
    // ═══════════════════════════════════════

    /**
     * Staggered slide-up + fade-in for a list of views.
     * Creates a cascading "waterfall" entrance effect.
     */
    fun staggeredEntrance(views: List<View>, staggerDelay: Long = 80L) {
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 50f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setStartDelay(index * staggerDelay + 100)
                .setInterpolator(DecelerateInterpolator(2f))
                .start()
        }
    }

    /**
     * Slide up from bottom with spring-like ease.
     */
    fun slideUp(view: View, duration: Long = 400L, startDelay: Long = 0L) {
        view.alpha = 0f
        view.translationY = 80f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(duration)
            .setStartDelay(startDelay)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()
    }

    /**
     * Fade in with subtle scale.
     */
    fun fadeIn(view: View, duration: Long = 350L, startDelay: Long = 0L) {
        view.alpha = 0f
        view.scaleX = 0.97f
        view.scaleY = 0.97f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(duration)
            .setStartDelay(startDelay)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /**
     * Pop-in with overshoot (for badges, codes, important elements).
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
            .setDuration(450)
            .setStartDelay(startDelay)
            .setInterpolator(OvershootInterpolator(1.8f))
            .start()
    }

    // ═══════════════════════════════════════
    //  INTERACTION FEEDBACK
    // ═══════════════════════════════════════

    /**
     * Premium button press: scale down then bounce back.
     * Feels "physical" like pressing a real button.
     */
    fun pressScale(view: View, onRelease: (() -> Unit)? = null) {
        view.animate()
            .scaleX(0.93f)
            .scaleY(0.93f)
            .setDuration(80)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .setInterpolator(OvershootInterpolator(3f))
                    .withEndAction { onRelease?.invoke() }
                    .start()
            }
            .start()
    }

    /**
     * Quick shimmer/flash to draw attention to a view update.
     */
    fun shimmerFlash(view: View) {
        view.animate()
            .alpha(0.5f)
            .scaleX(1.02f)
            .scaleY(1.02f)
            .setDuration(120)
            .withEndAction {
                view.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(250)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    // ═══════════════════════════════════════
    //  CONTINUOUS / AMBIENT ANIMATIONS
    // ═══════════════════════════════════════

    /**
     * Infinite gentle pulse (heartbeat feel).
     */
    fun pulseForever(view: View, minScale: Float = 0.96f, maxScale: Float = 1.06f, duration: Long = 1400L) {
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, minScale, maxScale, minScale).apply {
            repeatCount = ObjectAnimator.INFINITE
        }
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, minScale, maxScale, minScale).apply {
            repeatCount = ObjectAnimator.INFINITE
        }
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            this.duration = duration
            this.interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /**
     * Breathing alpha animation (for ambient glow effects).
     */
    fun breathe(view: View, minAlpha: Float = 0.4f, maxAlpha: Float = 1f, duration: Long = 2000L) {
        ObjectAnimator.ofFloat(view, View.ALPHA, minAlpha, maxAlpha, minAlpha).apply {
            this.duration = duration
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /**
     * Gentle floating animation (up-down bob).
     */
    fun floatUpDown(view: View, amplitude: Float = 8f, duration: Long = 3000L) {
        ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -amplitude, amplitude, -amplitude).apply {
            this.duration = duration
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    // ═══════════════════════════════════════
    //  UTILITY
    // ═══════════════════════════════════════

    /**
     * Animate a numeric value change on a view (for counters).
     */
    fun countUp(view: android.widget.TextView, from: Int, to: Int, duration: Long = 600L, format: (Int) -> String) {
        ValueAnimator.ofInt(from, to).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                view.text = format(animation.animatedValue as Int)
            }
            start()
        }
    }
}
