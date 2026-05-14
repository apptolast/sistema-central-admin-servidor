package com.apptolast.platform.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apptolast.platform.ui.navigation.AppNavigator
import com.apptolast.platform.ui.navigation.Route

private data class RailItem(
    val route: Route,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val items = listOf(
    RailItem(Route.PodDashboard, "Pods", Icons.Outlined.Dashboard),
    RailItem(Route.RunbookViewer, "Runbooks", Icons.AutoMirrored.Outlined.MenuBook),
    RailItem(Route.RagQuery, "Ask", Icons.Outlined.QuestionAnswer),
    RailItem(Route.CronjobBoard, "Cronjobs", Icons.Outlined.Schedule),
    RailItem(Route.AuditLog, "Audit", Icons.Outlined.History),
    RailItem(Route.Login, "Cuenta", Icons.Outlined.Person),
)

@Composable
fun AppNavigationRail(navigator: AppNavigator, modifier: Modifier = Modifier) {
    NavigationRail(
        modifier = modifier.fillMaxHeight().width(80.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { item ->
                val selected = item.route::class == navigator.current::class
                NavigationRailItem(
                    selected = selected,
                    onClick = { navigator.replaceWith(item.route) },
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = { Text(item.label) },
                )
            }
        }
    }
}
