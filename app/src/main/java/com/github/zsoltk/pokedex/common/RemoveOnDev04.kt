// FIXME this was copied over from Compose sources. Remove this file once dev04 is released.
package com.github.zsoltk.pokedex.common

import androidx.animation.AnimationClockObservable
import androidx.animation.PropKey
import androidx.animation.TransitionAnimation
import androidx.animation.TransitionDefinition
import androidx.animation.TransitionState
import androidx.animation.createAnimation
import androidx.compose.Composable
import androidx.compose.Model
import androidx.compose.ambient
import androidx.compose.memo
import androidx.compose.unaryPlus
import androidx.ui.core.AnimationClockAmbient

/**
 * Composable to use with [TransitionDefinition]-based animations.
 *
 * @param definition Transition definition that defines states and transitions
 * @param toState New state to transition to
 * @param clock Animation clock that pulses animations when time changes. By default the clock is
 *              read from the ambient.
 * @param initState Optional initial state for the transition. When undefined, the initial state
 *                  will be set to the first [toState] seen in the transition.
 * @param onStateChangeFinished An optional listener to get notified when state change animation
 *                              has completed
 * @param children The children composables that will be animated
 *
 * @sample androidx.ui.animation.samples.TransitionSample
 */
// TODO: The list of params is getting a bit long. Consider grouping them.
@Composable
fun <T> Transition(
    definition: TransitionDefinition<T>,
    toState: T,
    clock: AnimationClockObservable = +ambient(AnimationClockAmbient),
    initState: T = toState,
    onStateChangeFinished: ((T) -> Unit)? = null,
    children: @Composable() (state: TransitionState) -> Unit
) {
    if (transitionsEnabled) {
        // TODO: This null is workaround for b/132148894
        val model = +memo(definition, null) { TransitionModel(definition, initState, clock) }
        model.anim.onStateChangeFinished = onStateChangeFinished
        model.anim.toState(toState)
        children(model)
    } else {
        val state = +memo(definition, toState) { definition.getStateFor(toState) }
        children(state)
    }
}

/**
 * Stores the enabled state for [Transition] animations. Useful for tests to disable
 * animations and have reliable screenshot tests.
 */
var transitionsEnabled = true

// TODO(Doris): Use Clock idea instead of TransitionModel with pulse
@Model
private class TransitionModel<T>(
    transitionDef: TransitionDefinition<T>,
    initState: T,
    clock: AnimationClockObservable
) : TransitionState {

    private var animationPulse = 0L
    internal val anim: TransitionAnimation<T> =
        transitionDef.createAnimation(clock, initState).apply {
            onUpdate = {
                animationPulse++
            }
        }

    override fun <T> get(propKey: PropKey<T>): T {
        // we need to access the animationPulse so Compose will record this @Model values usage.
        @Suppress("UNUSED_VARIABLE")
        val pulse = animationPulse
        return anim[propKey]
    }
}
