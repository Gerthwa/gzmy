package com.gzmy.app.util

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator

/**
 * Ultra-premium animation system for GZMY.
 *
 * Provides:
 * - Entrance effects (staggered, slide, fade, pop)
 * - Micro-interactions (press, shimmer, ripple cascade)
 * - Ambient animations (pulse, breathe, float, glow, orbit)
 * - Utility animations (count-up, typewriter)
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
            view.translationY = 60f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(550)
                .setStartDelay(index * staggerDelay + 100)
                .setInterpolator(DecelerateInterpolator(2.5f))
                .start()
        }
    }

    /**
     * Slide up from bottom with spring-like ease.
     */
    fun slideUp(view: View, duration: Long = 450L, startDelay: Long = 0L) {
        view.alpha = 0f
        view.translationY = 80f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(duration)
            .setStartDelay(startDelay)
            .setInterpolator(DecelerateInterpolator(2.5f))
            .start()
    }

    /**
     * Fade in with subtle scale.
     */
    fun fadeIn(view: View, duration: Long = 400L, startDelay: Long = 0L) {
        view.alpha = 0f
        view.scaleX = 0.96f
        view.scaleY = 0.96f
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
            .setDuration(500)
            .setStartDelay(startDelay)
            .setInterpolator(OvershootInterpolator(1.6f))
            .start()
    }

    /**
     * Slide in from left with parallax feel.
     */
    fun slideInFromLeft(view: View, distance: Float = 120f, duration: Long = 400L, startDelay: Long = 0L) {
        view.alpha = 0f
        view.translationX = -distance
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(duration)
            .setStartDelay(startDelay)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()
    }

    /**
     * Slide in from right.
     */
    fun slideInFromRight(view: View, distance: Float = 120f, duration: Long = 400L, startDelay: Long = 0L) {
        view.alpha = 0f
        view.translationX = distance
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(duration)
            .setStartDelay(startDelay)
            .setInterpolator(DecelerateInterpolator(2f))
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
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(75)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .setInterpolator(OvershootInterpolator(3.5f))
                    .withEndAction { onRelease?.invoke() }
                    .start()
            }
            .start()
    }

    /**
     * Gentle press for smaller elements (emojis, icons).
     */
    fun gentlePress(view: View, onRelease: (() -> Unit)? = null) {
        view.animate()
            .scaleX(0.88f)
            .scaleY(0.88f)
            .setDuration(60)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .setInterpolator(OvershootInterpolator(4f))
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
            .alpha(0.4f)
            .scaleX(1.03f)
            .scaleY(1.03f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    /**
     * Ripple cascade: animate a list of views in sequence with a scale pop.
     * Good for "sending" feedback across multiple elements.
     */
    fun rippleCascade(views: List<View>, delayBetween: Long = 50L) {
        views.forEachIndexed { index, view ->
            view.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(100)
                .setStartDelay(index * delayBetween)
                .withEndAction {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .setInterpolator(OvershootInterpolator(2f))
                        .start()
                }
                .start()
        }
    }

    /**
     * Jelly bounce effect for important updates.
     */
    fun jellyBounce(view: View) {
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.15f, 0.9f, 1.05f, 1f).apply {
            duration = 500
        }
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 0.9f, 1.15f, 0.95f, 1f).apply {
            duration = 500
        }
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            interpolator = DecelerateInterpolator()
            start()
        }
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

    /**
     * Glow pulse — combines scale + alpha for a "breathing glow" effect.
     * Perfect for hero cards and important elements.
     */
    fun glowPulse(view: View, duration: Long = 2500L) {
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.02f, 1f).apply {
            repeatCount = ObjectAnimator.INFINITE
        }
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.02f, 1f).apply {
            repeatCount = ObjectAnimator.INFINITE
        }
        val alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0.85f, 1f, 0.85f).apply {
            repeatCount = ObjectAnimator.INFINITE
        }
        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            this.duration = duration
            this.interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /**
     * Slow continuous rotation (for compass arrows, spinners).
     */
    fun slowRotate(view: View, duration: Long = 8000L) {
        ObjectAnimator.ofFloat(view, View.ROTATION, 0f, 360f).apply {
            this.duration = duration
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    /**
     * Subtle sway — gentle left-right rotation, great for floating elements.
     */
    fun sway(view: View, angle: Float = 3f, duration: Long = 3500L) {
        ObjectAnimator.ofFloat(view, View.ROTATION, -angle, angle, -angle).apply {
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

    /**
     * Typewriter effect — reveals text character by character.
     */
    fun typewriter(view: android.widget.TextView, text: String, charDelay: Long = 40L) {
        view.text = ""
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        text.forEachIndexed { index, _ ->
            handler.postDelayed({
                view.text = text.substring(0, index + 1)
            }, index * charDelay)
        }
    }
}
