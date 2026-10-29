package com.boazjonker.unbound.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Building blocks for the app's screens: violet panels with a gold border, titles, 3D buttons and a message banner.
 */
object Themed {

    private fun Context.dp(value: Float) = value * resources.displayMetrics.density

    fun panelBackground(context: Context) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(0xF2241650.toInt(), 0xF2120B2E.toInt()),
    ).apply {
        cornerRadius = context.dp(22f)
        setStroke(context.dp(2.5f).toInt(), Palette.GOLD)
    }

    fun panel(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = panelBackground(context)
        val padding = context.dp(18f).toInt()
        setPadding(padding, padding, padding, padding)
    }

    fun scroll(context: Context, content: View): ScrollView = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun title(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        typeface = Palette.bold
        setTextColor(Palette.GOLD)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        letterSpacing = 0.08f
        gravity = Gravity.CENTER
        setShadowLayer(context.dp(6f), 0f, 0f, 0xAA5B36B8.toInt())
        setPadding(0, 0, 0, context.dp(10f).toInt())
    }

    fun body(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(0xFFE6E0FA.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.15f)
        setPadding(context.dp(4f).toInt(), 0, context.dp(4f).toInt(), context.dp(12f).toInt())
    }

    fun plank(context: Context, title: String, subtitle: String? = null, primary: Boolean = false, onClick: () -> Unit): PlankButton =
        PlankButton(context).apply {
            this.title = title
            this.subtitle = subtitle
            isPrimary = primary
            setOnClickListener { onClick() }
        }

    fun banner(context: Context): TextView = TextView(context).apply {
        typeface = Palette.bold
        setTextColor(Palette.INK)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        gravity = Gravity.CENTER
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Palette.GOLD_LIGHT, Palette.GOLD),
        ).apply {
            cornerRadius = context.dp(20f)
            setStroke(context.dp(2f).toInt(), Palette.GOLD_DARK)
        }
        val horizontal = context.dp(20f).toInt()
        val vertical = context.dp(9f).toInt()
        setPadding(horizontal, vertical, horizontal, vertical)
        elevation = context.dp(6f)
        visibility = View.GONE
    }

    /**
     * Slides the banner in from the top, shows [text] for a moment and slides it away.
     */
    fun showBanner(banner: TextView, text: String, top: Float) {
        banner.text = text
        banner.animate().cancel()
        banner.visibility = View.VISIBLE
        banner.alpha = 0f
        banner.translationY = top - banner.context.dp(30f)
        banner.scaleX = 0.9f
        banner.scaleY = 0.9f
        banner.animate()
            .alpha(1f).translationY(top).scaleX(1f).scaleY(1f)
            .setInterpolator(android.view.animation.OvershootInterpolator(2f))
            .setDuration(260)
            .withEndAction {
                banner.animate().alpha(0f).translationY(top - banner.context.dp(20f)).setStartDelay(1400).setDuration(220)
                    .withEndAction { banner.visibility = View.GONE; banner.animate().setStartDelay(0) }
                    .start()
            }
            .start()
    }

    /**
     * A message in a themed panel, with a single button.
     */
    fun showMessage(activity: Activity, title: String, message: String, button: String = "OK", onDismiss: () -> Unit = {}) {
        showDialog(activity, title, message, listOf(button to onDismiss), onCancel = onDismiss)
    }

    /**
     * A themed dialog with a title, an optional message and one 3D button per option. The first option is highlighted.
     */
    fun showDialog(
        activity: Activity,
        title: String,
        message: String?,
        options: List<Pair<String, () -> Unit>>,
        onCancel: () -> Unit = {},
    ): Dialog {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel = panel(activity)
        panel.addView(title(activity, title))
        message?.let { panel.addView(body(activity, it)) }
        options.forEachIndexed { index, (label, action) ->
            panel.addView(
                plank(activity, label, primary = index == 0) {
                    dialog.setOnCancelListener(null)
                    dialog.dismiss()
                    action()
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        dialog.setContentView(scroll(activity, panel))
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            minOf(activity.dp(420f).toInt(), activity.resources.displayMetrics.widthPixels - activity.dp(32f).toInt()),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        dialog.setOnCancelListener { onCancel() }
        dialog.show()
        // The panel pops in with a small bounce
        panel.scaleX = 0.85f
        panel.scaleY = 0.85f
        panel.alpha = 0f
        panel.animate().scaleX(1f).scaleY(1f).alpha(1f).setInterpolator(android.view.animation.OvershootInterpolator(2f)).setDuration(260).start()
        return dialog
    }
}
