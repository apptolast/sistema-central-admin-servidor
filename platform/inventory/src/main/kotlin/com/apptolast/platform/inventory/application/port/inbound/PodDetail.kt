package com.apptolast.platform.inventory.application.port.inbound

import com.apptolast.platform.inventory.domain.model.Pod
import com.apptolast.platform.knowledge.domain.model.Citation

/**
 * Resultado del use case [QueryInventoryUseCase.getPodDetail] — pod + citas
 * a runbooks que el módulo `knowledge` consideró relevantes para él.
 *
 * Diseño de la dependencia cross-module:
 *  - Importamos [Citation] de `platform.knowledge.domain.model` aunque sea otro
 *    módulo. Es legítimo: Citation es un value type estable, parte del contrato
 *    público del bounded context Knowledge. Documentado en ADR-0007.
 *  - [relatedRunbooks] puede ser empty (knowledge caído o sin evidencia). Es
 *    la regla anti-hallucination [[feedback_rag_anti_hallucination]]: mejor un
 *    pod detail sin runbooks que un pod detail con runbooks inventados.
 */
data class PodDetail(
    val pod: Pod,
    val relatedRunbooks: List<Citation>,
)
