package com.apptolast.platform.knowledge.domain.model

/**
 * Citation con formato `[source: path#section@sha]`.
 *
 * Enforced por la regla [[feedback_rag_anti_hallucination]]:
 *   - path: ruta al doc en repo (ej. `docs/runbooks/RB-01.md`)
 *   - section: anchor markdown (`#1-síntomas`)
 *   - sha: short git SHA del commit donde existe la sección (verifiable)
 */
data class Citation(
    val sourcePath: String,
    val section: String,
    val sha: String,
) {
    init {
        require(sourcePath.isNotBlank())
        require(section.isNotBlank())
        require(sha.matches(Regex("[a-f0-9]{7,40}"))) { "sha must be hex 7-40 chars" }
    }

    fun toMarkdown(): String = "[source: $sourcePath#$section@$sha]"

    companion object {
        private val PATTERN = Regex("""\[source:\s*([^#\]]+)#([^@\]]+)@([a-f0-9]{7,40})]""")

        /** Extrae todas las citations del texto. */
        fun extractAll(text: String): List<Citation> =
            PATTERN.findAll(text).map { match ->
                Citation(
                    sourcePath = match.groupValues[1].trim(),
                    section = match.groupValues[2].trim(),
                    sha = match.groupValues[3].trim(),
                )
            }.toList()
    }
}
