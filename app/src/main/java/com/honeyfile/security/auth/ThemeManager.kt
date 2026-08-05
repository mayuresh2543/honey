package com.honeyfile.security.auth

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.children
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView

class ThemeManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()
        }

    // Color palettes
    private val darkBg = Color.parseColor("#0F172A")
    private val darkCardBg = Color.parseColor("#1E293B")
    private val darkCardStroke = Color.parseColor("#334155")
    private val darkTextPrimary = Color.parseColor("#F8FAFC")
    private val darkTextSecondary = Color.parseColor("#94A3B8")

    private val lightBg = Color.parseColor("#F8FAFC")
    private val lightCardBg = Color.parseColor("#FFFFFF")
    private val lightCardStroke = Color.parseColor("#E2E8F0")
    private val lightTextPrimary = Color.parseColor("#0F172A")
    private val lightTextSecondary = Color.parseColor("#64748B")

    fun getBgColor(dark: Boolean) = if (dark) darkBg else lightBg
    fun getCardBgColor(dark: Boolean) = if (dark) darkCardBg else lightCardBg
    fun getCardStrokeColor(dark: Boolean) = if (dark) darkCardStroke else lightCardStroke
    fun getTextPrimaryColor(dark: Boolean) = if (dark) darkTextPrimary else lightTextPrimary
    fun getTextSecondaryColor(dark: Boolean) = if (dark) darkTextSecondary else lightTextSecondary

    fun animateThemeTransition(rootView: View, toDark: Boolean, durationMs: Long = 350L) {
        rootViewRef = rootView
        val fromDark = !toDark

        val fromBg = getBgColor(fromDark)
        val toBg = getBgColor(toDark)
        val fromCard = getCardBgColor(fromDark)
        val toCard = getCardBgColor(toDark)
        val fromStroke = getCardStrokeColor(fromDark)
        val toStroke = getCardStrokeColor(toDark)
        val fromTextPrimary = getTextPrimaryColor(fromDark)
        val toTextPrimary = getTextPrimaryColor(toDark)
        val fromTextSecondary = getTextSecondaryColor(fromDark)
        val toTextSecondary = getTextSecondaryColor(toDark)

        val evaluator = ArgbEvaluator()

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = durationMs
        animator.addUpdateListener { anim ->
            val fraction = anim.animatedFraction
            val bg = evaluator.evaluate(fraction, fromBg, toBg) as Int
            val card = evaluator.evaluate(fraction, fromCard, toCard) as Int
            val stroke = evaluator.evaluate(fraction, fromStroke, toStroke) as Int
            val textP = evaluator.evaluate(fraction, fromTextPrimary, toTextPrimary) as Int
            val textS = evaluator.evaluate(fraction, fromTextSecondary, toTextSecondary) as Int

            applyColorsRecursive(rootView, bg, card, stroke, textP, textS)
        }
        animator.start()
    }

    fun applyColorsInstant(rootView: View, dark: Boolean) {
        rootViewRef = rootView
        val bg = getBgColor(dark)
        val card = getCardBgColor(dark)
        val stroke = getCardStrokeColor(dark)
        val textP = getTextPrimaryColor(dark)
        val textS = getTextSecondaryColor(dark)
        applyColorsRecursive(rootView, bg, card, stroke, textP, textS)
    }

    private var rootViewRef: View? = null

    // Accent colors that must NOT be changed during theme transitions
    private val accentColors = setOf(
        Color.parseColor("#22C55E"), // success_green
        Color.parseColor("#EAB308"), // warning_yellow
        Color.parseColor("#EF4444"), // alert_red
        Color.parseColor("#38BDF8"), // primary_accent (cyan)
        Color.parseColor("#0284C7"), // primary_accent_light
        Color.WHITE,                 // white (used on heatmap slot badges)
        Color.BLACK                  // black (used on accent-tinted buttons)
    )

    private fun applyColorsRecursive(view: View, bg: Int, card: Int, stroke: Int, textP: Int, textS: Int) {
        // Set background on the root view
        if (view === rootViewRef) {
            view.setBackgroundColor(bg)
        }

        when (view) {
            is MaterialCardView -> {
                view.setCardBackgroundColor(card)
                view.strokeColor = stroke
            }
            is BottomNavigationView -> {
                view.setBackgroundColor(card)
                view.itemTextColor = ColorStateList.valueOf(textP)
                view.itemIconTintList = ColorStateList.valueOf(textP)
            }
            is android.widget.ScrollView, is androidx.core.widget.NestedScrollView -> {
                view.setBackgroundColor(bg)
            }
            is SwitchCompat -> {
                // Don't touch switch styling
            }
            is TextView -> {
                val currentColor = view.currentTextColor
                // Skip accent-colored text (green, red, yellow, cyan, white)
                if (currentColor !in accentColors) {
                    // Determine primary vs secondary by luminance:
                    // Secondary text is lower contrast (mid-gray tones)
                    val lum = colorLuminance(currentColor)
                    if (lum in 0.15..0.55) {
                        // Mid-luminance = secondary text
                        view.setTextColor(textS)
                    } else {
                        // High or low luminance = primary text
                        view.setTextColor(textP)
                    }
                }
            }
        }

        if (view is ViewGroup) {
            for (child in view.children) {
                applyColorsRecursive(child, bg, card, stroke, textP, textS)
            }
        }
    }

    private fun colorLuminance(color: Int): Double {
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    companion object {
        private const val PREF_NAME = "honeyfile_theme_prefs"
        private const val KEY_DARK_MODE = "key_dark_mode"
    }
}
