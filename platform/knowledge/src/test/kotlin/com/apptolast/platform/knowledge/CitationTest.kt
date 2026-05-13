package com.apptolast.platform.knowledge

import com.apptolast.platform.knowledge.domain.model.Citation
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CitationTest {

    @Test
    fun `format toMarkdown produces canonical citation string`() {
        val c = Citation("docs/runbooks/RB-01.md", "1-sintomas", "a3f1b2c")
        c.toMarkdown() shouldBe "[source: docs/runbooks/RB-01.md#1-sintomas@a3f1b2c]"
    }

    @Test
    fun `extractAll parses multiple citations from a response`() {
        val text = """
            El disco está al 73% por containerd
            [source: docs/runbooks/RB-01.md#1-sintomas@a3f1b2c].
            La política recomienda mover containerd al HC volume
            [source: cluster-ops/audit/BACKUP_POLICY.md#diskmgmt@f9e8d72].
        """.trimIndent()

        val citations = Citation.extractAll(text)
        citations shouldHaveSize 2
        citations[0].sourcePath shouldBe "docs/runbooks/RB-01.md"
        citations[1].section shouldBe "diskmgmt"
    }

    @Test
    fun `extractAll returns empty when no citations`() {
        Citation.extractAll("Texto sin citation alguna.") shouldHaveSize 0
    }

    @Test
    fun `constructor rejects invalid sha`() {
        assertThrows<IllegalArgumentException> {
            Citation("docs/x.md", "section", "not-a-sha")
        }
    }

    @Test
    fun `constructor rejects blank sourcePath`() {
        assertThrows<IllegalArgumentException> {
            Citation("", "section", "a3f1b2c")
        }
    }
}
