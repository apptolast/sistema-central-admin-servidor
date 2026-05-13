package com.apptolast.platform.ui.navigation

/**
 * Rutas de la SPA. Mapean a las 6 pantallas spec'd en docs/design/specs/.
 *
 * Implementación de navegación: state holder simple (sin lib externa) hasta
 * Phase 2 cuando decidamos voyager vs decompose vs androidx-navigation-multi.
 */
sealed class Route(val path: String, val title: String) {
    data object PodDashboard : Route("/inventory/pods", "Pods")
    data class PodDetail(val namespace: String, val name: String) :
        Route("/inventory/pods/$namespace/$name", "Pod · $name")
    data object RunbookViewer : Route("/runbooks", "Runbooks")
    data object RagQuery : Route("/ask", "Pregunta a la plataforma")
    data object CronjobBoard : Route("/cronjobs", "Cronjobs")
    data object Login : Route("/login", "Iniciar sesión")
}
