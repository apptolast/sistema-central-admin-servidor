package com.apptolast.ragingestor

import com.apptolast.ragingestor.config.RagIngestorProperties
import com.apptolast.ragingestor.embed.MarkdownDocIngester
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MarkdownChunkingTest {

    private val ingester = MarkdownDocIngester(RagIngestorProperties(), null)

    @Test
    fun `chunks markdown by H2 sections`() {
        val md = """
            # Doc Title

            Intro paragraph.

            ## 1 Síntomas

            Disco al 73%, eventos `FreeDiskSpaceFailed`.

            ## 2 Diagnóstico

            Ejecutar `df -h /`.

            ### 2.1 Causas comunes

            48G en `/var/lib/containerd`.

            ## 3 Remediación

            Mover containerd al HC volume.
        """.trimIndent()

        val chunks = ingester.chunkMarkdown(md, "docs/runbooks/RB-01.md", "a3f1b2c")

        chunks shouldHaveSize 5
        chunks[0].section shouldBe "intro"
        chunks[1].section shouldBe "1-sintomas"
        chunks[2].section shouldBe "2-diagnostico"
        chunks[3].section shouldBe "2-1-causas-comunes"
        chunks[4].section shouldBe "3-remediacion"
        chunks.forEach { it.sha shouldBe "a3f1b2c" }
        chunks.forEach { it.path shouldBe "docs/runbooks/RB-01.md" }
    }

    @Test
    fun `slugify handles accents and uppercase`() {
        val md = "# Top\n## Configuración Avanzada\nbody"
        val chunks = ingester.chunkMarkdown(md, "x.md", "abc1234")
        chunks[1].section shouldBe "configuracion-avanzada"
    }

    @Test
    fun `empty content produces empty chunk list`() {
        val chunks = ingester.chunkMarkdown("", "x.md", "abc1234")
        chunks shouldHaveSize 0
    }
}
