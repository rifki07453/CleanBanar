package com.example.cleanbanar.core.utils

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

object AnimationUtils {

    /**
     * Memberikan efek tombol/kartu tertekan (mengecil sedikit) secara natural saat disentuh.
     * Tidak kaku seperti animasi default, melainkan agak membal (bouncy).
     */
    fun applyBouncyTouchEffect(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Skala mengecil (95%)
                    val scaleDownX = ObjectAnimator.ofFloat(v, "scaleX", 0.95f)
                    val scaleDownY = ObjectAnimator.ofFloat(v, "scaleY", 0.95f)
                    scaleDownX.duration = 100
                    scaleDownY.duration = 100
                    scaleDownX.interpolator = DecelerateInterpolator()
                    scaleDownY.interpolator = DecelerateInterpolator()

                    val set = AnimatorSet()
                    set.playTogether(scaleDownX, scaleDownY)
                    set.start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Kembali ke ukuran normal dengan efek membal (Overshoot)
                    val scaleUpX = ObjectAnimator.ofFloat(v, "scaleX", 1f)
                    val scaleUpY = ObjectAnimator.ofFloat(v, "scaleY", 1f)
                    scaleUpX.duration = 300
                    scaleUpY.duration = 300
                    scaleUpX.interpolator = OvershootInterpolator(2f)
                    scaleUpY.interpolator = OvershootInterpolator(2f)

                    val set = AnimatorSet()
                    set.playTogether(scaleUpX, scaleUpY)
                    set.start()
                }
            }
            // Kembalikan false agar onClickListener asli tetap berjalan
            false
        }
    }

    /**
     * Menganimasikan bar persentase bundar dari nilai sebelumnya ke nilai baru dengan halus.
     */
    fun animateCircularProgress(view: com.google.android.material.progressindicator.CircularProgressIndicator, targetProgress: Int) {
        val animator = ValueAnimator.ofInt(view.progress, targetProgress)
        animator.duration = 800 // 800ms cukup halus, tidak terlalu lama
        animator.interpolator = DecelerateInterpolator(1.5f)
        animator.addUpdateListener { animation ->
            view.progress = animation.animatedValue as Int
        }
        animator.start()
    }
    
    /**
     * Menganimasikan ProgressBar standar dari nol ke target dengan halus.
     */
    fun animateProgressBar(view: android.widget.ProgressBar, targetProgress: Int) {
        val animator = ObjectAnimator.ofInt(view, "progress", 0, targetProgress)
        animator.duration = 800 
        animator.interpolator = DecelerateInterpolator(1.5f)
        animator.start()
    }
    
    /**
     * Menerapkan efek tulang (skeleton) berkedip perlahan saat loading data.
     * Sangat natural, tanpa library eksternal (anti-bloat).
     */
    fun applySkeletonPulseEffect(view: View): ObjectAnimator {
        val pulse = ObjectAnimator.ofFloat(view, "alpha", 0.4f, 1f)
        pulse.duration = 800
        pulse.repeatMode = ValueAnimator.REVERSE
        pulse.repeatCount = ValueAnimator.INFINITE
        pulse.start()
        return pulse
    }

    /**
     * Menerapkan efek mengambang (naik-turun perlahan) pada ikon kosong (Empty State).
     * Memberikan kesan hidup tanpa perlu animasi Lottie eksternal.
     */
    fun applyFloatingEffect(view: View): ObjectAnimator {
        val floatAnim = ObjectAnimator.ofFloat(view, "translationY", 0f, -15f)
        floatAnim.duration = 1500
        floatAnim.repeatMode = ValueAnimator.REVERSE
        floatAnim.repeatCount = ValueAnimator.INFINITE
        floatAnim.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        floatAnim.start()
        return floatAnim
    }

    /**
     * Menerapkan efek bernapas (skala membesar perlahan) pada latar belakang header.
     * Memberikan kesan elegan dan modern pada dashboard.
     */
    fun applyHeaderBreathingEffect(view: View) {
        // Atur pivot di tengah atas agar membesarnya ke bawah dan ke samping
        view.pivotX = view.resources.displayMetrics.widthPixels / 2f
        view.pivotY = 0f

        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1.0f, 1.06f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1.0f, 1.06f)

        scaleX.duration = 6000
        scaleY.duration = 6000

        scaleX.repeatMode = ValueAnimator.REVERSE
        scaleY.repeatMode = ValueAnimator.REVERSE

        scaleX.repeatCount = ValueAnimator.INFINITE
        scaleY.repeatCount = ValueAnimator.INFINITE

        scaleX.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        scaleY.interpolator = android.view.animation.AccelerateDecelerateInterpolator()

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY)
        animatorSet.start()
    }
}
