package com.example.oshootcleaner

import androidx.compose.foundation.pager.PagerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A lightweight bridge that lets any composable trigger a pager
 * navigation without restarting the activity.
 *
 * The MainActivity registers the current PagerState when it's created, and
 * clears it when disposed. Composables elsewhere in the app call
 * [navigate] and, if a pager is registered, the pager animates to the
 * target page.
 *
 * This exists because startActivity() with CLEAR_TOP restarts the
 * activity — which re-triggers the splash screen. That's jarring when the
 * user just wants to switch tabs.
 */
object TabNavigator {

    @Volatile
    private var pagerState: PagerState? = null

    @Volatile
    private var scope: CoroutineScope? = null

    fun register(state: PagerState, coroutineScope: CoroutineScope) {
        this.pagerState = state
        this.scope = coroutineScope
    }

    fun unregister() {
        this.pagerState = null
        this.scope = null
    }

    /**
     * Animates the pager to the tab with the given ordinal.
     * Returns true if the navigation was handled internally.
     * Returns false if no pager is registered (caller should fall back to
     * starting the activity).
     */
    fun navigate(tabOrdinal: Int): Boolean {
        val state = pagerState ?: return false
        val s = scope ?: return false
        s.launch {
            state.animateScrollToPage(tabOrdinal)
        }
        return true
    }
}