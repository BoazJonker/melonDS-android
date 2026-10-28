package me.magnum.melonds.ui.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import me.magnum.melonds.R

/**
 * Draws each preference as a bevelled, framed parchment panel, in the style of the menus in The Legend of Zelda: Spirit Tracks. Category headers are left without a panel.
 */
class SpiritTracksPanelDecoration(context: Context) : RecyclerView.ItemDecoration() {

    private val density = context.resources.displayMetrics.density
    private val horizontalMargin = (12 * density).toInt()
    private val verticalMargin = (4 * density).toInt()
    private val cornerRadius = 10 * density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.stParchmentLight)
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x33000000
    }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = ContextCompat.getColor(context, R.color.stFrame)
    }
    private val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1 * density
        color = 0xB3E2B54A.toInt()
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1 * density
        color = 0xB3FFFFFF.toInt()
    }
    private val panelRect = RectF()

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        if (isPanel(view, parent)) {
            outRect.set(horizontalMargin, verticalMargin, horizontalMargin, verticalMargin)
        }
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val halfStroke = framePaint.strokeWidth / 2
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (!isPanel(child, parent)) {
                continue
            }

            panelRect.set(
                child.left + halfStroke,
                child.top + child.translationY + halfStroke,
                child.right - halfStroke,
                child.bottom + child.translationY - halfStroke,
            )
            // Drop shadow, panel, wood frame, then a gold line and a light top edge inside it for a bevelled look
            panelRect.offset(0f, 2 * density)
            canvas.drawRoundRect(panelRect, cornerRadius, cornerRadius, shadowPaint)
            panelRect.offset(0f, -2 * density)
            canvas.drawRoundRect(panelRect, cornerRadius, cornerRadius, fillPaint)
            canvas.drawRoundRect(panelRect, cornerRadius, cornerRadius, framePaint)
            panelRect.inset(2.5f * density, 2.5f * density)
            canvas.drawRoundRect(panelRect, cornerRadius - 2.5f * density, cornerRadius - 2.5f * density, goldPaint)
            panelRect.inset(1.2f * density, 1.2f * density)
            canvas.drawLine(panelRect.left + cornerRadius, panelRect.top, panelRect.right - cornerRadius, panelRect.top, highlightPaint)
        }
    }

    private fun isPanel(view: View, parent: RecyclerView): Boolean {
        if (parent.getChildAdapterPosition(view) == RecyclerView.NO_POSITION) {
            return false
        }

        // Regular preferences have a widget frame (for switches, etc.), while category headers don't
        return view.findViewById<View>(android.R.id.widget_frame) != null
    }
}
