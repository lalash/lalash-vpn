package com.v2ray.ang.handler

import com.v2ray.ang.handler.KillSwitchPolicy.Action
import com.v2ray.ang.handler.KillSwitchPolicy.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KillSwitchPolicyTest {

    private fun fail(times: Int, start: State = State(), autoSwitch: Boolean = true): Pair<State, Action> {
        var state = start
        var action = Action.NONE
        repeat(times) { KillSwitchPolicy.next(state, ok = false, autoSwitch = autoSwitch).let { state = it.first; action = it.second } }
        return state to action
    }

    @Test
    fun healthyConnectionDoesNothing() {
        assertEquals(State() to Action.NONE, KillSwitchPolicy.next(State(), ok = true, autoSwitch = true))
    }

    @Test
    fun shortGlitchesAreIgnored() {
        val (state, action) = fail(KillSwitchPolicy.FAILS_BEFORE_ACTION - 1)
        assertEquals(Action.NONE, action)
        assertEquals(State(), KillSwitchPolicy.next(state, ok = true, autoSwitch = true).first)
    }

    @Test
    fun lostConnectionReloadsThenSwitches() {
        val n = KillSwitchPolicy.FAILS_BEFORE_ACTION
        var (state, action) = fail(n)
        assertEquals(Action.RELOAD, action)
        repeat(KillSwitchPolicy.RELOADS_BEFORE_SWITCH - 1) {
            fail(n, state).let { state = it.first; action = it.second }
            assertEquals(Action.RELOAD, action)
        }
        fail(n, state).let { state = it.first; action = it.second }
        assertEquals(Action.SWITCH_CONFIG, action)
    }

    @Test
    fun withoutAutoSwitchItKeepsReloading() {
        var state = State()
        repeat(5) {
            val (s, a) = fail(KillSwitchPolicy.FAILS_BEFORE_ACTION, state, autoSwitch = false)
            assertEquals(Action.RELOAD, a)
            state = s
        }
    }

    @Test
    fun recoveryIsReportedOnce() {
        val (lost, _) = fail(KillSwitchPolicy.FAILS_BEFORE_ACTION)
        val (state, action) = KillSwitchPolicy.next(lost, ok = true, autoSwitch = true)
        assertEquals(Action.RECOVERED, action)
        assertEquals(Action.NONE, KillSwitchPolicy.next(state, ok = true, autoSwitch = true).second)
    }

    @Test
    fun picksFastestOtherConfig() {
        val list = listOf("a" to 300L, "b" to 120L, "c" to -1L, "d" to 90L)
        assertEquals("b", KillSwitchPolicy.pickNextServer("d", list))
        assertEquals("d", KillSwitchPolicy.pickNextServer("a", list))
        assertEquals("c", KillSwitchPolicy.pickNextServer("a", listOf("a" to 50L, "c" to -1L)))
        assertNull(KillSwitchPolicy.pickNextServer("a", listOf("a" to 50L)))
    }
}
