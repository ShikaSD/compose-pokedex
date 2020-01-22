package com.github.zsoltk.pokedex.common

import androidx.compose.*
import androidx.ui.core.*
import androidx.ui.core.gesture.PressGestureDetector
import androidx.ui.foundation.gestures.DragDirection
import androidx.ui.foundation.gestures.Draggable
import kotlin.math.max


@Composable
fun Recycler1(
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
    var recyclerState by +state { RecyclerState() }
    Layout(children = {
        recyclerState.range.forEach {
            Key(it) {
                items(it)
            }
        }
    }) { measurables, constraints ->
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
        (0..measurableRange).forEach {
            val placeable = measurables[it].measure(childConstraints)
            placeables.add(placeable)
            measuredItemsHeight += placeable.height.value
            measuredItemsWidth = max(placeable.width.value.toFloat(), measuredItemsWidth)
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

        val result = layout(measuredItemsWidth.toInt().ipx, measuredItemsHeight.toInt().ipx) {
            var currentHeight = initialHeight
            placeables.forEach {
                it.place(0.px, currentHeight.px - scrollerPosition.value)
                currentHeight += it.height.value
            }
        }

        // Visible range management
        var newRange = visibleRange
        var newHeight = initialHeight

        val visibleTop = scrollerPosition.value.value
        val visibleBottom = if (visibleRange.last == itemsCount - 1) {
            measuredItemsHeight
        } else {
            visibleTop + constraints.maxHeight.value.toFloat()
        }

        // Expand
        // Bottom
        if (visibleBottom > measuredItemsHeight) {
            newRange = newRange.first..(newRange.last + 1)
        }

        // Shrink
        // Top
        var firstVisiblePlaceableIndex = -1
        var firstVisiblePlaceableHeight = initialHeight
        for (i in (0 until placeables.size)) {
            val placeable = placeables[i]
            if (firstVisiblePlaceableHeight + placeable.height.value < visibleTop) {
                firstVisiblePlaceableHeight += placeable.height.value
            } else {
                firstVisiblePlaceableIndex = i
                break
            }
        }
//        println("First visible index $firstVisiblePlaceableIndex, height = $firstVisiblePlaceableHeight")

        if (firstVisiblePlaceableIndex != -1) {
            newRange = (newRange.first + firstVisiblePlaceableIndex)..newRange.last
            newHeight = firstVisiblePlaceableHeight
        }

//        println("Measured visibleTop=$visibleTop, visibleBottom=$visibleBottom, measuredItemsHeight=$measuredItemsHeight, state=$recyclerState")
        if (newRange != recyclerState.range || newHeight != recyclerState.height) {
//            println("Replacing $recyclerState with $newRange and $newHeight")
            recyclerState = RecyclerState(range = newRange, createNodesFrom = 0, height = newHeight)
        }

        result
    }
}
