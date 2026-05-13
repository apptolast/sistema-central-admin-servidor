package com.apptolast.ragingestor

import com.apptolast.ragingestor.config.RagIngestorProperties
import com.apptolast.ragingestor.embed.MarkdownDocIngester
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import java.nio.file.Files
import java.nio.file.Path

class VectorStoreIngestTest {

    private lateinit var workdir: Path
    private val captured = mutableListOf<List<Document>>()
    private val capturingStore = object : VectorStore {
        override fun add(documents: List<Document>) {
            captured += documents
        }
        override fun delete(idList: List<String>) = Unit
        override fun delete(filterExpression: org.springframework.ai.vectorstore.filter.Filter.Expression) = Unit
        override fun similaritySearch(request: SearchRequest): List<Document> = emptyList()
        override fun getName(): String = "capturing"
    }

    @BeforeEach
    fun setUp() {
        workdir = Files.createTempDirectory("rag-test-")
    }

    @AfterEach
    fun tearDown() {
        workdir.toFile().deleteRecursively()
    }

    @Test
    fun `ingest with VectorStore adds documents with citation metadata`() {
        val rb = workdir.resolve("docs/runbooks/RB-01.md")
        Files.createDirectories(rb.parent)
        Files.writeString(
            rb,
            """
                # RB-01

                ## 1 Síntomas

                Disco al 73%.

                ## 2 Remediación

                Mover containerd al HC volume.
            """.trimIndent(),
        )

        val ingester = MarkdownDocIngester(
            properties = RagIngestorProperties(workdir = workdir.toString()),
            vectorStore = capturingStore,
        )

        ingester.ingest("docs/runbooks/RB-01.md", "a3f1b2c")

        captured shouldHaveSize 1
        val docs = captured.single()
        docs shouldHaveSize 3
        docs[0].metadata shouldContain ("path" to "docs/runbooks/RB-01.md")
        docs[0].metadata shouldContain ("sha" to "a3f1b2c")
        docs[0].metadata["section"] shouldBe "intro"
        docs[1].metadata["section"] shouldBe "1-sintomas"
        docs[2].metadata["section"] shouldBe "2-remediacion"
    }

    @Test
    fun `ingest without VectorStore is a no-op upsert but still chunks`() {
        val rb = workdir.resolve("a.md")
        Files.writeString(rb, "# t\n\nbody")
        val ingester = MarkdownDocIngester(
            properties = RagIngestorProperties(workdir = workdir.toString()),
            vectorStore = null,
        )
        ingester.ingest("a.md", "deadbee")
        captured shouldHaveSize 0
    }

    @Test
    fun `missing file is skipped without error`() {
        val ingester = MarkdownDocIngester(
            properties = RagIngestorProperties(workdir = workdir.toString()),
            vectorStore = capturingStore,
        )
        ingester.ingest("does/not/exist.md", "deadbee")
        captured shouldHaveSize 0
    }
}
