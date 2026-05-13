package com.apptolast.platform.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

/**
 * Entry point para wasmJs. Monta el composable [App] en el div #composeApp
 * declarado en src/wasmJsMain/resources/index.html.
 *
 * Build: `./gradlew :composeApp:wasmJsBrowserProductionWebpack`
 * Dev:   `./gradlew :composeApp:wasmJsBrowserDevelopmentRun --continuous`
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        App()
    }
}
