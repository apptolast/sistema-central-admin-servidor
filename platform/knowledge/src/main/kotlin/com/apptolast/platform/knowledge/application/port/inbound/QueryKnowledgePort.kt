package com.apptolast.platform.knowledge.application.port.inbound

import com.apptolast.platform.knowledge.domain.model.Citation

/**
 * Puerto simple para módulos cliente (ej. inventory) que sólo necesitan
 * recuperar citaciones relevantes para una consulta.
 *
 * Diseño anti-alucinación: el método **NUNCA lanza**. Cualquier fallo (timeout,
 * 5xx, network) → [emptyList]. Mejor sin runbook que un runbook inventado.
 */
interface QueryKnowledgePort {
    /**
     * @param question texto libre.
     * @param topK máximo de citas a devolver (default 5).
     * @return lista posiblemente vacía. Orden = mayor relevancia primero.
     */
    fun query(question: String, topK: Int = 5): List<Citation>
}
