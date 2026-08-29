package com.wavebeat

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private companion object {
        const val TAG = "SPLASH_T"
        const val PREFS_SETUP = "wavebeat_setup"
        const val KEY_TERMS_ACCEPTED = "terms_accepted"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var launched = false
    private val setupPrefs by lazy { getSharedPreferences(PREFS_SETUP, MODE_PRIVATE) }

    private val navigate = Runnable {
        if (launched) return@Runnable
        launched = true
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val accepted = setupPrefs.getBoolean(KEY_TERMS_ACCEPTED, false)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!launched) {
                    launched = true
                    handler.removeCallbacks(navigate)
                }
                finish()
            }
        })

        val glow = findViewById<View>(R.id.splashGlow)
        val logo = findViewById<View>(R.id.splashLogo)
        val title = findViewById<View>(R.id.splashTitle)
        val tagline = findViewById<View>(R.id.splashTagline)
        val accent = findViewById<View>(R.id.splashAccent)

        fun pulseGlow(v: View) {
            ObjectAnimator.ofFloat(v, View.SCALE_X, 0.82f, 1.06f).apply {
                duration = 900; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start()
            }
            ObjectAnimator.ofFloat(v, View.SCALE_Y, 0.82f, 1.06f).apply {
                duration = 900; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start()
            }
            ObjectAnimator.ofFloat(v, View.ALPHA, 0.55f, 1f).apply {
                duration = 900; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start()
            }
        }
        pulseGlow(glow)

        val badgePulse = ObjectAnimator.ofFloat(accent, View.ALPHA, 0.35f, 1f).apply {
            duration = 700
            startDelay = 900
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }

        logo.translationY = 26f
        logo.alpha = 0f
        val logoIn = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f).setDuration(420),
                ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.72f, 1f).apply {
                    duration = 560
                    interpolator = OvershootInterpolator(1.4f)
                },
                ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.72f, 1f).apply {
                    duration = 560
                    interpolator = OvershootInterpolator(1.4f)
                },
                ObjectAnimator.ofFloat(logo, View.TRANSLATION_Y, 26f, 0f).setDuration(420)
            )
            start()
        }

        title.translationY = 26f
        title.alpha = 0f
        val titleIn = ObjectAnimator.ofFloat(title, View.TRANSLATION_Y, 26f, 0f).apply {
            duration = 520
            startDelay = 280
        }
        titleIn.start()
        ObjectAnimator.ofFloat(title, View.ALPHA, 0f, 1f).apply {
            duration = 520
            startDelay = 280
            start()
        }

        tagline.translationY = 20f
        tagline.alpha = 0f
        ObjectAnimator.ofFloat(tagline, View.TRANSLATION_Y, 20f, 0f).apply {
            duration = 520
            startDelay = 480
            start()
        }
        ObjectAnimator.ofFloat(tagline, View.ALPHA, 0f, 1f).apply {
            duration = 520
            startDelay = 480
            start()
        }

        val getStarted = findViewById<View>(R.id.splashGetStarted)
        val termsCheck = findViewById<android.widget.CheckBox>(R.id.splashTermsCheck)
        val hint = findViewById<View>(R.id.splashHint)

        if (accepted) {
            termsCheck.visibility = View.GONE
            getStarted.visibility = View.GONE
            hint.visibility = View.GONE
            handler.postDelayed(navigate, 1600)
            return
        }

        getStarted.setOnClickListener {
            Log.d(TAG, "CTA click isChecked=${termsCheck.isChecked}")
            if (termsCheck.isChecked) {
                Log.d(TAG, "CTA navigating")
                setupPrefs.edit().putBoolean(KEY_TERMS_ACCEPTED, true).apply()
                navigate.run()
            } else {
                Log.d(TAG, "CTA denied -> hint")
                shake(getStarted)
                hint.visibility = View.VISIBLE
                hint.alpha = 0f
                hint.animate().alpha(1f).setDuration(200).start()
                hint.postDelayed({
                    hint.animate().alpha(0f).setDuration(400).withEndAction {
                        hint.visibility = View.GONE
                    }.start()
                }, 2000)
            }
        }
        getStarted.setOnTouchListener { v, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_UP) v.performClick()
            true
        }

        termsCheck.setOnCheckedChangeListener { _, checked ->
            Log.d(TAG, "terms checked=$checked")
            getStarted.alpha = if (checked) 1f else 0.85f
        }
    }

    private fun shake(v: View) {
        val shake = ValueAnimator.ofFloat(0f, -16f, 14f, -8f, 0f).apply {
            duration = 420
            addUpdateListener { anim ->
                v.translationX = (anim.getAnimatedValue() as Float)
            }
            start()
        }
        shake.start()
    }

    override fun onDestroy() {
        handler.removeCallbacks(navigate)
        super.onDestroy()
    }
}