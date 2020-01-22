package com.github.zsoltk.pokedex.common

import androidx.compose.*
import androidx.ui.core.*

typealias MeasureWithCustomData<T> = MeasureScope.(
    constraints: Constraints,
    getChildren: () -> List<Measurable>,
    setData: (T) -> Unit,
    getData: () -> T,
    subcompose: () -> Unit
) -> MeasureScope.LayoutResult

@Composable
fun <T : Any> WithCustomData(
    initialValue: T,
    children: @Composable() (T) -> Unit,
    measureBlock: MeasureWithCustomData<T>
) {
    val state = +memo { WithCustomDataState<T>(initialValue) }
    state.children = children
    state.context = +ambient(ContextAmbient)
    state.compositionRef = +compositionReference()
    state.measureBlock = measureBlock
    // if this code was executed subcomposition must be triggered as well
    state.forceRecompose = true

    // Studio mark it as not compilable, but it is
    LayoutNode(ref = state.nodeRef, measureBlocks = state.measureBlocks)

    // if LayoutNode scheduled the remeasuring no further steps are needed - subcomposition
    // will happen later on the measuring stage. otherwise we can assume the LayoutNode
    // already holds the final Constraints and we should subcompose straight away.
    // if owner is null this means we are not yet attached. once attached the remeasuring
    // will be scheduled which would cause subcomposition
    val layoutNode = state.nodeRef.value!!
    if (!layoutNode.needsRemeasure && layoutNode.owner != null) {
        state.subcompose()
    }
}

private class WithCustomDataState<T : Any>(initialValue: T) {
    var compositionRef: CompositionReference? = null
    var context: Context? = null
    val nodeRef = Ref<LayoutNode>()

    var forceRecompose = false

    var children: @Composable() (T) -> Unit = {}
    var customData: T = initialValue
    var measureBlock: MeasureWithCustomData<T>? = null
    val measureBlocks = object : LayoutNode.NoIntristicsMeasureBlocks(
        error = "Intrinsic measurements are not supported by WithConstraints"
    ) {
        override fun measure(
            measureScope: MeasureScope,
            measurables: List<Measurable>,
            constraints: Constraints
        ): MeasureScope.LayoutResult =
            if (measureBlock == null) {
                measureScope.layout(0.ipx, 0.ipx) { }
            } else {
                val root = nodeRef.value!!
                measureBlock!!.invoke(
                    measureScope,
                    constraints,
                    { root.layoutChildren.also { println("Attach status ${it.map { it.isAttached() }}") } },
                    { customData = it },
                    { customData },
                    { subcompose() }
                )
            }
    }

    fun subcompose() {
        val node = nodeRef.value!!
        Compose.subcomposeInto(node, context!!, compositionRef) {
            children(customData)
        }
        forceRecompose = false
    }
}
