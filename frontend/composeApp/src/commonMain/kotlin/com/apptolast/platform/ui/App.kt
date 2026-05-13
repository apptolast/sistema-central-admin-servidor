package com.apptolast.platform.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.apptolast.platform.ui.components.AppNavigationRail
import com.apptolast.platform.ui.navigation.Route
import com.apptolast.platform.ui.navigation.rememberAppNavigator
import com.apptolast.platform.ui.screens.cronjobs.CronjobBoardScreen
import com.apptolast.platform.ui.screens.inventory.PodDashboardScreen
import com.apptolast.platform.ui.screens.inventory.PodDetailScreen
import com.apptolast.platform.ui.screens.login.LoginScreen
import com.apptolast.platform.ui.screens.rag.RagQueryScreen
import com.apptolast.platform.ui.screens.runbook.RunbookViewerScreen
import com.apptolast.platform.ui.theme.AppToLastTheme

@Composable
fun App() {
    AppToLastTheme(darkTheme = true) {
        val navigator = rememberAppNavigator(initial = Route.PodDashboard)

        Row(modifier = Modifier.fillMaxSize()) {
            AppNavigationRail(navigator)
            when (val current = navigator.current) {
                Route.PodDashboard -> PodDashboardScreen(navigator)
                is Route.PodDetail -> PodDetailScreen(current, navigator)
                Route.RunbookViewer -> RunbookViewerScreen()
                Route.RagQuery -> RagQueryScreen()
                Route.CronjobBoard -> CronjobBoardScreen()
                Route.Login -> LoginScreen()
            }
        }
    }
}
