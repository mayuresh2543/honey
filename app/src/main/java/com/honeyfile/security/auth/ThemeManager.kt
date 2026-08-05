package com.honeyfile.security.auth

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.children
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.materialswitch.MaterialSwitch

class ThemeManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()
        }

    // Color palettes
    val darkBg = Color.parseColor("#0F172A")
    val darkCardBg = Color.parseColor("#1E293B")
    val darkCardStroke = Color.parseColor("#334155")
    val darkTextPrimary = Color.parseColor("#F8FAFC")
    val darkTextSecondary = Color.parseColor("#94A3B8")

    val lightBg = Color.parseColor("#F8FAFC")
    val lightCardBg = Color.parseColor("#FFFFFF")
    val lightCardStroke = Color.parseColor("#E2E8F0")
    val lightTextPrimary = Color.parseColor("#0F172A")
    val lightTextSecondary = Color.parseColor("#64748B")

    fun getBgColor(dark: Boolean) = if (dark) darkBg else lightBg
    fun getCardBgColor(dark: Boolean) = if (dark) darkCardBg else lightCardBg
    fun getCardStrokeColor(dark: Boolean) = if (dark) darkCardStroke else lightCardStroke
    fun getTextPrimaryColor(dark: Boolean) = if (dark) darkTextPrimary else lightTextPrimary
    fun getTextSecondaryColor(dark: Boolean) = if (dark) darkTextSecondary else lightTextSecondary

    // Accent colors that must NOT be changed during theme transitions
    private val accentColors = setOf(
        Color.parseColor("#22C55E"), // success_green
        Color.parseColor("#EAB308"), // warning_yellow
        Color.parseColor("#EF4444"), // alert_red
        Color.parseColor("#38BDF8"), // primary_accent (cyan)
        Color.parseColor("#0284C7"), // primary_accent_light
        Color.WHITE,
        Color.BLACK
    )

    companion object {
        private const val PREF_NAME = "honeyfile_theme_prefs"
        private const val KEY_DARK_MODE = "key_dark_mode"
        private const val TAG_TEXT_PRIMARY = "theme_text_primary"
        private const val TAG_TEXT_SECONDARY = "theme_text_secondary"
        private const val TAG_TEXT_SKIP = "theme_text_skip"
    }

    /**
     * Pre-tag all TextViews in the tree as PRIMARY, SECONDARY, or SKIP
     * before animation so that classification is stable across frames.
     */
    private fun tagTextViews(view: View, fromDark: Boolean) {
        if (view is TextView && view !is MaterialButton && view !is Chip && view !is MaterialSwitch && view !is SwitchCompat) {
            val currentColor = view.currentTextColor
            if (currentColor in accentColors) {
                view.tag = TAG_TEXT_SKIP
            } else {
                // Classify based on which theme color set it matches closest
                val fromPrimary = if (fromDark) darkTextPrimary else lightTextPrimary
                val fromSecondary = if (fromDark) darkTextSecondary else lightTextSecondary

                val distPrimary = colorDistance(currentColor, fromPrimary)
                val distSecondary = colorDistance(currentColor, fromSecondary)

                if (distSecondary < distPrimary) {
                    view.tag = TAG_TEXT_SECONDARY
                } else {
                    view.tag = TAG_TEXT_PRIMARY
                }
            }
        }

        if (view is ViewGroup) {
            for (child in view.children) {
                tagTextViews(child, fromDark)
            }
        }
    }

    private fun colorDistance(c1: Int, c2: Int): Int {
        val dr = Color.red(c1) - Color.red(c2)
        val dg = Color.green(c1) - Color.green(c2)
        val db = Color.blue(c1) - Color.blue(c2)
        return dr * dr + dg * dg + db * db
    }

    fun animateThemeTransition(rootView: View, toDark: Boolean, durationMs: Long = 350L) {
        val fromDark = !toDark

        // Pre-tag text views ONCE before the animation begins
        tagTextViews(rootView, fromDark)

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
        // Tag views so that classification is set
        tagTextViews(rootView, dark)

        val bg = getBgColor(dark)
        val card = getCardBgColor(dark)
        val stroke = getCardStrokeColor(dark)
        val textP = getTextPrimaryColor(dark)
        val textS = getTextSecondaryColor(dark)
        applyColorsRecursive(rootView, bg, card, stroke, textP, textS)
    }

    private fun applyColorsRecursive(view: View, bg: Int, card: Int, stroke: Int, textP: Int, textS: Int) {
        when (view) {
            is MaterialButton -> {
                // Skip MaterialButtons — they have their own accent backgrounds
                return
            }
            is MaterialSwitch, is SwitchCompat -> {
                // Handle switch text color
                if (view is TextView) {
                    view.setTextColor(textP)
                }
                return
            }
            is Chip -> {
                // Skip chips — Material3 handles their styling
                return
            }
            is MaterialCardView -> {
                view.setCardBackgroundColor(card)
                view.strokeColor = stroke
            }
            is BottomNavigationView -> {
                view.setBackgroundColor(card)
                view.itemTextColor = ColorStateList.valueOf(textP)
                view.itemIconTintList = ColorStateList.valueOf(textP)
            }
            is TextView -> {
                when (view.tag) {
                    TAG_TEXT_PRIMARY -> view.setTextColor(textP)
                    TAG_TEXT_SECONDARY -> view.setTextColor(textS)
                    // TAG_TEXT_SKIP or null -> don't touch
                }
            }
        }

        // Handle background drawables (e.g. card_live_feed_bg)
        val background = view.background
        if (background is GradientDrawable) {
            // This is likely a shape drawable like card_live_feed_bg
            // Check if its current color is a dark theme color
            try {
                background.setColor(bg)
                background.setStroke(1, stroke)
            } catch (_: Exception) { }
        } else if (view !is MaterialCardView && view !is BottomNavigationView
            && view !is MaterialButton && background is ColorDrawable) {
            // Plain color background views
            val bgColor = background.color
            if (bgColor == darkBg || bgColor == lightBg) {
                view.setBackgroundColor(bg)
            }
        }

        if (view is ViewGroup) {
            for (child in view.children) {
                applyColorsRecursive(child, bg, card, stroke, textP, textS)
            }
        }
    }
}
