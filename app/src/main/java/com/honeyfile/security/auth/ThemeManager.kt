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
import android.view.Window
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.WindowCompat
import androidx.core.view.children
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.materialswitch.MaterialSwitch
import com.honeyfile.security.R

class ThemeManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()
        }

    // Palette Definitions
    val darkBg = Color.parseColor("#0F172A")
    val darkCardBg = Color.parseColor("#1E293B")
    val darkCardStroke = Color.parseColor("#334155")
    val darkSubCardBg = Color.parseColor("#0F172A")
    val darkSubCardStroke = Color.parseColor("#1E293B")
    val darkTextPrimary = Color.parseColor("#F8FAFC")
    val darkTextSecondary = Color.parseColor("#94A3B8")

    val lightBg = Color.parseColor("#F8FAFC")
    val lightCardBg = Color.parseColor("#FFFFFF")
    val lightCardStroke = Color.parseColor("#E2E8F0")
    val lightSubCardBg = Color.parseColor("#F1F5F9")
    val lightSubCardStroke = Color.parseColor("#CBD5E1")
    val lightTextPrimary = Color.parseColor("#0F172A")
    val lightTextSecondary = Color.parseColor("#64748B")

    // Accent colors to preserve
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

        private const val TAG_BG_ROOT = "theme_bg_root"
        private const val TAG_BG_SUB_CARD = "theme_bg_sub_card"
    }

    /**
     * Tags all views in the layout hierarchy ONCE based on initial colors and types.
     */
    fun tagViews(rootView: View) {
        tagViewsInternal(rootView, isDarkMode)
    }

    private fun tagViewsInternal(view: View, currentDarkState: Boolean) {
        val background = view.background
        when {
            // ONLY ViewGroup containers with GradientDrawable are sub-cards! (Never TextView badges!)
            view is ViewGroup && background is GradientDrawable && view !is MaterialCardView && view !is MaterialButton -> {
                view.setTag(R.id.theme_bg_tag, TAG_BG_SUB_CARD)
            }
            // ONLY ViewGroup layout containers (never TextViews) are root backgrounds!
            view is ViewGroup && background is ColorDrawable && view !is MaterialCardView && view !is BottomNavigationView && view !is MaterialButton -> {
                view.setTag(R.id.theme_bg_tag, TAG_BG_ROOT)
            }
        }

        if (view is TextView && view !is MaterialButton && view !is Chip && view !is MaterialSwitch && view !is SwitchCompat) {
            tagSingleTextView(view, currentDarkState)
        }

        if (view is ViewGroup) {
            for (child in view.children) {
                tagViewsInternal(child, currentDarkState)
            }
        }
    }

    private fun tagSingleTextView(view: TextView, currentDarkState: Boolean) {
        val currentColor = view.currentTextColor
        if (currentColor in accentColors) {
            view.setTag(R.id.theme_text_tag, TAG_TEXT_SKIP)
        } else {
            val secColor = if (currentDarkState) darkTextSecondary else lightTextSecondary
            val priColor = if (currentDarkState) darkTextPrimary else lightTextPrimary

            val distSec = colorDistance(currentColor, secColor)
            val distPri = colorDistance(currentColor, priColor)

            if (distSec < distPri) {
                view.setTag(R.id.theme_text_tag, TAG_TEXT_SECONDARY)
            } else {
                view.setTag(R.id.theme_text_tag, TAG_TEXT_PRIMARY)
            }
        }
    }

    private fun colorDistance(c1: Int, c2: Int): Int {
        val dr = Color.red(c1) - Color.red(c2)
        val dg = Color.green(c1) - Color.green(c2)
        val db = Color.blue(c1) - Color.blue(c2)
        return dr * dr + dg * dg + db * db
    }

    fun applyInstant(rootView: View, window: Window? = null, toDark: Boolean = isDarkMode) {
        tagViewsInternal(rootView, toDark)
        window?.let { updateWindowStatusBar(it, toDark) }
        applyColorsToHierarchy(
            rootView,
            bg = if (toDark) darkBg else lightBg,
            card = if (toDark) darkCardBg else lightCardBg,
            stroke = if (toDark) darkCardStroke else lightCardStroke,
            subBg = if (toDark) darkSubCardBg else lightSubCardBg,
            subStroke = if (toDark) darkSubCardStroke else lightSubCardStroke,
            textP = if (toDark) darkTextPrimary else lightTextPrimary,
            textS = if (toDark) darkTextSecondary else lightTextSecondary
        )
    }

    fun animateTransition(rootView: View, window: Window, toDark: Boolean, durationMs: Long = 150L) {
        val fromDark = !toDark
        tagViewsInternal(rootView, fromDark)

        val fromBg = if (fromDark) darkBg else lightBg
        val toBg = if (toDark) darkBg else lightBg

        val fromCard = if (fromDark) darkCardBg else lightCardBg
        val toCard = if (toDark) darkCardBg else lightCardBg

        val fromStroke = if (fromDark) darkCardStroke else lightCardStroke
        val toStroke = if (toDark) darkCardStroke else lightCardStroke

        val fromSubBg = if (fromDark) darkSubCardBg else lightSubCardBg
        val toSubBg = if (toDark) darkSubCardBg else lightSubCardBg

        val fromSubStroke = if (fromDark) darkSubCardStroke else lightSubCardStroke
        val toSubStroke = if (toDark) darkSubCardStroke else lightSubCardStroke

        val fromTextP = if (fromDark) darkTextPrimary else lightTextPrimary
        val toTextP = if (toDark) darkTextPrimary else lightTextPrimary

        val fromTextS = if (fromDark) darkTextSecondary else lightTextSecondary
        val toTextS = if (toDark) darkTextSecondary else lightTextSecondary

        updateWindowStatusBar(window, toDark)

        val evaluator = ArgbEvaluator()
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = durationMs
        animator.addUpdateListener { anim ->
            val f = anim.animatedFraction
            val bg = evaluator.evaluate(f, fromBg, toBg) as Int
            val card = evaluator.evaluate(f, fromCard, toCard) as Int
            val stroke = evaluator.evaluate(f, fromStroke, toStroke) as Int
            val subBg = evaluator.evaluate(f, fromSubBg, toSubBg) as Int
            val subStroke = evaluator.evaluate(f, fromSubStroke, toSubStroke) as Int
            val textP = evaluator.evaluate(f, fromTextP, toTextP) as Int
            val textS = evaluator.evaluate(f, fromTextS, toTextS) as Int

            applyColorsToHierarchy(rootView, bg, card, stroke, subBg, subStroke, textP, textS)
        }
        animator.start()
    }

    private fun updateWindowStatusBar(window: Window, dark: Boolean) {
        val statusBarColor = if (dark) darkBg else lightBg
        window.statusBarColor = statusBarColor
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !dark
    }

    private fun applyColorsToHierarchy(
        view: View,
        bg: Int,
        card: Int,
        stroke: Int,
        subBg: Int,
        subStroke: Int,
        textP: Int,
        textS: Int
    ) {
        when (view) {
            is MaterialButton, is Chip -> return
            is MaterialSwitch, is SwitchCompat -> {
                if (view is TextView) view.setTextColor(textP)
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
                if (view.getTag(R.id.theme_text_tag) == null) {
                    tagSingleTextView(view, isDarkMode)
                }
                when (view.getTag(R.id.theme_text_tag)) {
                    TAG_TEXT_PRIMARY -> view.setTextColor(textP)
                    TAG_TEXT_SECONDARY -> view.setTextColor(textS)
                }
            }
        }

        when (view.getTag(R.id.theme_bg_tag)) {
            TAG_BG_ROOT -> view.setBackgroundColor(bg)
            TAG_BG_SUB_CARD -> {
                val gd = view.background as? GradientDrawable
                if (gd != null) {
                    try {
                        gd.setColor(subBg)
                        gd.setStroke(1, subStroke)
                    } catch (_: Exception) { }
                }
            }
        }

        if (view is ViewGroup) {
            for (child in view.children) {
                applyColorsToHierarchy(child, bg, card, stroke, subBg, subStroke, textP, textS)
            }
        }
    }
}

