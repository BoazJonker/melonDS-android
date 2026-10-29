package me.magnum.melonds.ui.common

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import me.magnum.melonds.domain.model.layout.LayoutComponent
import me.magnum.melonds.domain.model.layout.PositionedLayoutComponent
import me.magnum.melonds.domain.model.layout.UILayout
import me.magnum.melonds.ui.layouteditor.model.LayoutTarget

open class LayoutView(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {

    protected lateinit var viewBuilderFactory: LayoutComponentViewBuilderFactory
    protected val views = mutableMapOf<LayoutComponent, LayoutComponentView>()

    init {
        layoutDirection = LAYOUT_DIRECTION_LTR
    }

    fun setLayoutComponentViewBuilderFactory(factory: LayoutComponentViewBuilderFactory) {
        viewBuilderFactory = factory
    }

    open fun instantiateLayout(layoutConfiguration: UILayout, layoutTarget: LayoutTarget = LayoutTarget.MAIN_SCREEN) {
        destroyLayout()
        loadLayout(layoutConfiguration, layoutTarget)
    }

    fun destroyLayout() {
        views.clear()
        removeAllViews()
    }

    fun getInstantiatedComponents(): List<LayoutComponent> {
        return views.keys.toList()
    }

    fun getLayoutComponentViews(): List<LayoutComponentView> {
        return views.values.toList()
    }

    fun getLayoutComponentView(layoutComponent: LayoutComponent): LayoutComponentView? {
        return views[layoutComponent]
    }

    private fun loadLayout(layout: UILayout, layoutTarget: LayoutTarget) {
        val components = when(layoutTarget) {
            LayoutTarget.MAIN_SCREEN -> layout.mainScreenLayout.components
            LayoutTarget.SECONDARY_SCREEN -> layout.secondaryScreenLayout.components
        }
        if (components == null) return

        val screens = components.filter { it.isScreen() }.sortedBy { if (it.onTop) 0 else 1 }
        val others = components.filterNot { it.isScreen() }

        (screens + others).forEach {
            views[it.component] = addPositionedLayoutComponent(it)
        }
    }

    protected fun addPositionedLayoutComponent(layoutComponent: PositionedLayoutComponent): LayoutComponentView {
        val viewBuilder = viewBuilderFactory.getLayoutComponentViewBuilder(layoutComponent.component)
        val view = viewBuilder.build(context)

        val viewParams = LayoutParams(layoutComponent.rect.width, layoutComponent.rect.height).apply {
            leftMargin = layoutComponent.rect.x
            topMargin = layoutComponent.rect.y
        }

        val viewLayoutComponent = LayoutComponentView(view, viewBuilder.getAspectRatio(), layoutComponent.component).apply {
            baseAlpha = layoutComponent.alpha
            setHighlighted(false)
            onTop = layoutComponent.onTop
        }

        if (layoutComponent.isScreen()) {
            // Screens should be below other views
            addView(view, 0, viewParams)
        } else {
            addView(view, viewParams)
        }

        onLayoutComponentViewAdded(viewLayoutComponent)
        return viewLayoutComponent
    }

    protected open fun onLayoutComponentViewAdded(layoutComponentView: LayoutComponentView) {
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        keepControlsOnScreen()
    }

    /**
     * Moves any control that would stick out past an edge of this view back inside it, with a small margin. This keeps the controls whole when the view is
     * smaller than the size the layout was made for (for example because of system bars or rounded screen corners).
     */
    private fun keepControlsOnScreen() {
        if (width <= 0 || height <= 0) {
            return
        }
        val margin = (4 * resources.displayMetrics.density).toInt()
        var moved = false
        views.values.forEach { componentView ->
            if (componentView.component.isScreen()) {
                return@forEach
            }
            val view = componentView.view
            val params = view.layoutParams as? LayoutParams ?: return@forEach
            if (view.width <= 0 || view.height <= 0 || view.width > width || view.height > height) {
                return@forEach
            }
            val maxLeft = maxOf(0, width - view.width - margin)
            val maxTop = maxOf(0, height - view.height - margin)
            val newLeft = params.leftMargin.coerceIn(minOf(margin, maxLeft), maxLeft)
            val newTop = params.topMargin.coerceIn(minOf(margin, maxTop), maxTop)
            if (newLeft != params.leftMargin || newTop != params.topMargin) {
                params.leftMargin = newLeft
                params.topMargin = newTop
                moved = true
            }
        }
        if (moved) {
            // Apply the new positions in the next layout pass
            post { requestLayout() }
        }
    }
}