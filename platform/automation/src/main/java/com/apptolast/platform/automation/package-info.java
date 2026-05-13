/**
 * Automation bounded context (Phase 5).
 *
 * <p>Expone runbooks ejecutables y orquesta los cronjobs de cluster-ops
 * existentes en el cluster. NO reemplaza los cronjobs Bash; los envuelve
 * con un control plane consultable y triggerable desde la UI.
 *
 * <p>Allowed deps: {@code inventory}, {@code identity}, {@code knowledge}.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Automation",
    allowedDependencies = {"inventory", "identity", "knowledge"}
)
package com.apptolast.platform.automation;
