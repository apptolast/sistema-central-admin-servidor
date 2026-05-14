/**
 * Knowledge bounded context (Phase 3).
 *
 * <p>RAG sobre docs internos. Política anti-alucinación enforced en código:
 * <ul>
 *   <li>Toda respuesta lleva 1+ citation {@code [source: path#section@sha]}.</li>
 *   <li>Score de retrieval &lt; 0.55 → "no encuentro evidencia documentada".</li>
 *   <li>La citation se valida contra git: el SHA debe existir y el path debe contener la sección.</li>
 * </ul>
 *
 * <p>Allowed deps: ninguno (el RAG es autónomo).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Knowledge",
    allowedDependencies = {}
)
package com.apptolast.platform.knowledge;
