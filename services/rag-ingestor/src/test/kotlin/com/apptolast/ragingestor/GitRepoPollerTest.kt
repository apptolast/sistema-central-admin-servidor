package com.apptolast.ragingestor

import com.apptolast.ragingestor.config.RagIngestorProperties
import com.apptolast.ragingestor.git.DocIngester
import com.apptolast.ragingestor.git.GitRepoPoller
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class GitRepoPollerTest {

    private lateinit var originDir: Path
    private lateinit var workdir: Path
    private lateinit var origin: Git

    @BeforeEach
    fun setUp() {
        originDir = Files.createTempDirectory("rag-origin-")
        workdir = Files.createTempDirectory("rag-workdir-")
        origin = Git.init()
            .setDirectory(originDir.toFile())
            .setInitialBranch("main")
            .call()

        write("README.md", "# Inicio\n\ntexto")
        write("docs/runbooks/RB-01.md", "# RB-01\n\ncontenido")
        origin.add().addFilepattern(".").call()
        origin.commit()
            .setMessage("initial docs")
            .setAuthor("test", "test@example.com")
            .setCommitter("test", "test@example.com")
            .call()
    }

    @AfterEach
    fun tearDown() {
        origin.close()
        originDir.toFile().deleteRecursively()
        workdir.toFile().deleteRecursively()
    }

    @Test
    fun `poll clones with JGit and ingests only changed markdown files`() {
        val ingested = mutableListOf<String>()
        val poller = GitRepoPoller(
            properties = RagIngestorProperties(
                repoUrl = originDir.toUri().toString(),
                branch = "main",
                workdir = workdir.toString(),
                includePaths = listOf("*.md", "docs/**/*.md"),
                excludePaths = emptyList(),
            ),
            docIngester = object : DocIngester {
                override fun ingest(relativePath: String, sha: String) {
                    ingested += relativePath
                }
            },
        )

        poller.pollAndIngest()
        ingested shouldContainExactlyInAnyOrder listOf("README.md", "docs/runbooks/RB-01.md")

        ingested.clear()
        write("docs/runbooks/RB-01.md", "# RB-01\n\ncontenido actualizado")
        write("docs/runbooks/RB-02.md", "# RB-02\n\ncontenido")
        origin.add()
            .addFilepattern("docs/runbooks/RB-01.md")
            .addFilepattern("docs/runbooks/RB-02.md")
            .call()
        origin.commit()
            .setMessage("update docs")
            .setAuthor("test", "test@example.com")
            .setCommitter("test", "test@example.com")
            .call()

        poller.pollAndIngest()
        ingested shouldContainExactlyInAnyOrder listOf("docs/runbooks/RB-01.md", "docs/runbooks/RB-02.md")
    }

    private fun write(relativePath: String, content: String) {
        val file = originDir.resolve(relativePath)
        Files.createDirectories(file.parent ?: originDir)
        Files.writeString(file, content)
    }
}
