package com.v2ray.ang.handler

/**
 * Decisions of the kill switch watchdog, kept free of Android and native code so they can be
 * unit tested. The watchdog checks the connection every [CHECK_INTERVAL_MS]; after
 * [FAILS_BEFORE_ACTION] failures in a row the connection counts as lost and the core is reloaded
 * with the VPN interface kept open, so apps stay blocked instead of leaking. After
 * [RELOADS_BEFORE_SWITCH] reloads that did not help, the next best config is used if allowed.
 */
object KillSwitchPolicy {
    const val CHECK_INTERVAL_MS = 20_000L
    const val FAILS_BEFORE_ACTION = 3
    const val RELOADS_BEFORE_SWITCH = 2

    data class State(val fails: Int = 0, val reloads: Int = 0, val lost: Boolean = false)

    enum class Action { NONE, RELOAD, SWITCH_CONFIG, RECOVERED }

    /** Next state and action after one check that succeeded ([ok]) or failed. */
    fun next(state: State, ok: Boolean, autoSwitch: Boolean): Pair<State, Action> {
        if (ok) return State() to (if (state.lost) Action.RECOVERED else Action.NONE)
        val fails = state.fails + 1
        if (fails < FAILS_BEFORE_ACTION) return state.copy(fails = fails) to Action.NONE
        return if (autoSwitch && state.reloads >= RELOADS_BEFORE_SWITCH) {
            State(lost = true) to Action.SWITCH_CONFIG
        } else {
            State(reloads = state.reloads + 1, lost = true) to Action.RELOAD
        }
    }

    /**
     * The config to switch to: the lowest positive last-test delay among [candidates]
     * (GUID to delay in ms), excluding [current]. Untested or failed configs (delay <= 0) are only
     * used when nothing tested is left, in list order.
     */
    fun pickNextServer(current: String, candidates: List<Pair<String, Long>>): String? {
        val others = candidates.filter { it.first != current }
        return others.filter { it.second > 0 }.minByOrNull { it.second }?.first
            ?: others.firstOrNull()?.first
    }
}
