package com.apptolast.platform.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Navegador minimal con stack mutable. Phase 1: dependency-free.
 *
 * Cambiar a [voyager](https://voyager.adriel.cafe/) o decompose en Phase 2 si
 * se requiere deep-linking, back gestures iOS, o multi-window.
 */
class AppNavigator(initial: Route = Route.PodDashboard) {
    private var _stack = mutableStateOf<List<Route>>(listOf(initial))

    val current: Route get() = _stack.value.last()
    val canGoBack: Boolean get() = _stack.value.size > 1

    fun navigateTo(route: Route) {
        _stack.value = _stack.value + route
    }

    fun replaceWith(route: Route) {
        _stack.value = listOf(route)
    }

    fun goBack() {
        if (canGoBack) {
            _stack.value = _stack.value.dropLast(1)
        }
    }
}

@Composable
fun rememberAppNavigator(initial: Route = Route.PodDashboard): AppNavigator =
    remember { AppNavigator(initial) }
