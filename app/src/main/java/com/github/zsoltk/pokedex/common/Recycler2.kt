package com.github.zsoltk.pokedex.common

import androidx.animation.AnimationEndReason
import androidx.animation.ExponentialDecay
import androidx.compose.*
import androidx.ui.core.*
import androidx.ui.core.gesture.PressGestureDetector
import androidx.ui.foundation.HorizontalScroller
import androidx.ui.foundation.VerticalScroller
import androidx.ui.foundation.animation.AnimatedValueHolder
import androidx.ui.foundation.animation.FlingConfig
import androidx.ui.foundation.gestures.DragDirection
import androidx.ui.foundation.gestures.Draggable
import kotlin.math.max

/**
 * FIXME COPY FROM Scroller.kt
 * This is the state of a [VerticalScroller] and [HorizontalScroller] that
 * allows the developer to change the scroll position by calling methods on this object.
 */
@Model
class ScrollerPosition(initial: Float = 0f) {

    internal val holder = AnimatedValueHolder(initial)

    /**
     * maxPosition this scroller that consume this ScrollerPosition can reach, or [Px.Infinity]
     * if still unknown
     */
    var maxPosition: Px = Px.Infinity
        internal set

    /**
     * current position for scroller
     */
    val value: Px
        get() = -holder.value.px

    /**
     * Fling configuration that specifies fling logic when scrolling ends with velocity.
     *
     * See [FlingConfig] for more info.
     */
    var flingConfig = FlingConfig(
        decayAnimation = ExponentialDecay(
            frictionMultiplier = ScrollerDefaultFriction,
            absVelocityThreshold = ScrollerVelocityThreshold
        )
    )

    /**
     * Smooth scroll to position in pixels
     *
     * @param value target value to smooth scroll to
     */
    // TODO (malkov/tianliu) : think about allowing to scroll with custom animation timings/curves
    fun smoothScrollTo(
        value: Px,
        onEnd: (endReason: AnimationEndReason, finishValue: Float) -> Unit = { _, _ -> }
    ) {
        holder.animatedFloat.animateTo(-value.value, onEnd)
    }

    /**
     * Smooth scroll by some amount of pixels
     *
     * @param value delta to scroll by
     */
    fun smoothScrollBy(
        value: Px,
        onEnd: (endReason: AnimationEndReason, finishValue: Float) -> Unit = { _, _ -> }
    ) {
        smoothScrollTo(this.value + value, onEnd)
    }

    /**
     * Instantly jump to position in pixels
     *
     * @param value target value to jump to
     */
    fun scrollTo(value: Px) {
        holder.animatedFloat.snapTo(-value.value)
    }

    /**
     * Instantly jump by some amount of pixels
     *
     * @param value delta to jump by
     */
    fun scrollBy(value: Px) {
        scrollTo(this.value + value)
    }

    companion object {
        private const val ScrollerDefaultFriction = 0.35f
        private const val ScrollerVelocityThreshold = 1000f
    }
}

@Composable
fun Recycler2(
    scrollerPosition: ScrollerPosition = +memo { ScrollerPosition() },
    direction: DragDirection = DragDirection.Vertical,
    itemsCount: Int,
    items: @Composable() (Int) -> Unit
) {
    // FIXME Copy from Scroller.kt
    PressGestureDetector(onPress = { scrollerPosition.scrollTo(scrollerPosition.value) }) {
        Draggable(
            dragValue = scrollerPosition.holder,
            onDragValueChangeRequested = {
                scrollerPosition.holder.animatedFloat.snapTo(it)
            },
            onDragStopped = {
                scrollerPosition.holder.fling(scrollerPosition.flingConfig, it)
            },
            dragDirection = direction,
            enabled = true
        ) {
            RecyclerLayout(
                scrollerPosition = scrollerPosition,
                direction = direction,
                itemsCount = itemsCount,
                items = items
            )
        }
    }
}

@Composable
private inline fun RecyclerLayout(
    scrollerPosition: ScrollerPosition,
    direction: DragDirection,
    itemsCount: Int,
    crossinline items: @Composable() (Int) -> Unit
) {
    WithCustomData(
        initialValue = RecyclerState(),
        children = { state ->
            println(state)
            state.range.forEach { index ->
                Key(index) {
                    composer.call(
                        index,
                        ctor = { state },
                        invalid = { it.createNodesFrom > index }
                    ) {
                        Observe {
                            items(index)
                        }
                    }
                }
            }
        }
    ) { constraints: Constraints,
        getChildren: () -> List<Measurable>,
        setData: (RecyclerState) -> Unit,
        getData: () -> RecyclerState,
        recompose: () -> Unit ->

        val recyclerState = getData()
        val measurables = getChildren().toMutableList()

        val visibleRange = recyclerState.range
        val initialHeight = recyclerState.height

        val childConstraints = constraints.copy(
            maxHeight = IntPx.Infinity,
            maxWidth = constraints.maxWidth
        )
        val visibleCount = max(visibleRange.last + 1 - visibleRange.first, 0)
        val placeables = ArrayList<Placeable>(visibleCount)

        var measuredItemsWidth = 0f
        var measuredItemsHeight = initialHeight

        val measurableRange = visibleRange.last - visibleRange.first
        (0 until measurableRange).forEach {
            val placeable = measurables[it].measure(childConstraints)
            placeables.add(placeable)
            measuredItemsHeight += placeable.height.value
            measuredItemsWidth = max(placeable.width.value.toFloat(), measuredItemsWidth)
        }

        // Expand bottom
        val visibleTop = scrollerPosition.value.value
        while (getData().range.last < itemsCount && measuredItemsHeight - visibleTop < constraints.maxHeight.value) {
            val data = getData()
            setData(data.copy(range = data.range.first..data.range.last + 1, createNodesFrom = data.range.last))
            recompose()

            val newMeasurables = getChildren() - measurables
            val newPlaceables = newMeasurables.map { it.measure(childConstraints) }
            newPlaceables.forEach { placeable ->
                placeables.add(placeable)
                measuredItemsHeight += placeable.height.value
                measuredItemsWidth = max(placeable.width.value.toFloat(), measuredItemsWidth)
            }
            measurables.addAll(newMeasurables)
        }

        // Shrink top
        var firstVisiblePlaceableIndex = -1
        var firstVisiblePlaceableHeight = getData().height
        for (i in (0 until placeables.size)) {
            val placeable = placeables[i]
            if (firstVisiblePlaceableHeight + placeable.height.value < visibleTop) {
                firstVisiblePlaceableHeight += placeable.height.value
            } else {
                firstVisiblePlaceableIndex = i
                break
            }
        }
        if (firstVisiblePlaceableIndex != -1) {
            val data = getData()
            setData(
                RecyclerState(
                    range = (data.range.first + firstVisiblePlaceableIndex)..data.range.last,
                    height = firstVisiblePlaceableHeight,
                    createNodesFrom = data.range.first
                )
            )
        }

        val scroll = if (visibleRange.last + 1 == itemsCount) {
            max(measuredItemsHeight - constraints.maxHeight.value, 0f)
        } else {
            Float.POSITIVE_INFINITY
        }
        if (scroll != scrollerPosition.maxPosition.value) {
            scrollerPosition.holder.setBounds(-scroll, 0f)
            scrollerPosition.maxPosition = scroll.px
        }

        recompose()

        layout(measuredItemsWidth.toInt().ipx, measuredItemsHeight.toInt().ipx) {
            var currentHeight = getData().height

            placeables.forEachIndexed { i, placeable ->
                if (i < firstVisiblePlaceableIndex) {
                    return@forEachIndexed
                }

                placeable.place(0.px, currentHeight.px - scrollerPosition.value)
                currentHeight += placeable.height.value
            }
        }
    }
}

data class RecyclerState(
    val range: IntRange = 0..0,
    val createNodesFrom: Int = -1,
    val height: Float = 0f
)
