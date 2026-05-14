package com.apptolast.ragingestor.git

import com.apptolast.ragingestor.config.RagIngestorProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference

/**
 * Polls el repo cada `pollInterval` y detecta docs nuevos/cambiados.
 *
 * Phase 1 implementation: shell out a `git`. Phase 2 podríamos usar jgit.
 * El proceso debe correr como un usuario con `git fetch` permission via deploy
 * key montada en `/home/rag/.ssh/id_ed25519`.
 */
@Component
class GitRepoPoller(
    private val properties: RagIngestorProperties,
    private val docIngester: DocIngester,
    @param:Autowired(required = false) private val jdbcTemplate: JdbcTemplate? = null,
) {

    private val log = LoggerFactory.getLogger(GitRepoPoller::class.java)
    private val lastIngestedSha = AtomicReference<String?>(null)

    fun pollAndIngest() {
        log.info("starting RAG ingest cycle repo={} branch={}", properties.repoUrl, properties.branch)

        ensureRepoCloned()
        runGit("fetch", "origin", properties.branch)

        val currentSha = runGit("rev-parse", "origin/${properties.branch}").trim()
        val previousSha = loadLastIngestedSha() ?: lastIngestedSha.get()

        if (currentSha == previousSha) {
            log.debug("no new commits, skipping")
            return
        }

        runGit("checkout", currentSha)
        val changed = if (previousSha == null) {
            // First run — index everything matching includePaths.
            allFilesMatching(properties.includePaths, properties.excludePaths)
        } else {
            runGit("diff", "--name-only", previousSha, currentSha)
                .lines()
                .filter { it.isNotBlank() }
                .filter { matches(it, properties.includePaths, properties.excludePaths) }
        }

        log.info("changed files: {}", changed.size)
        changed.forEach { docIngester.ingest(it, currentSha) }

        persistLastIngestedSha(currentSha)
        lastIngestedSha.set(currentSha)
        log.info("ingest cycle complete sha={}", currentSha)
    }

    private fun loadLastIngestedSha(): String? {
        val jdbc = jdbcTemplate ?: return null
        return runCatching {
            jdbc.query(
                "SELECT last_sha FROM rag_ingest_state WHERE id = 1",
            ) { rs, _ -> rs.getString("last_sha") }.firstOrNull()
        }.onFailure { ex ->
            log.warn("could not read rag_ingest_state, falling back to in-memory state: {}", ex.message)
        }.getOrNull()
    }

    private fun persistLastIngestedSha(sha: String) {
        val jdbc = jdbcTemplate ?: return
        runCatching {
            jdbc.update(
                """
                INSERT INTO rag_ingest_state (id, last_sha, last_run_at)
                VALUES (1, ?, now())
                ON CONFLICT (id)
                DO UPDATE SET last_sha = excluded.last_sha, last_run_at = excluded.last_run_at
                """.trimIndent(),
                sha,
            )
        }.onFailure { ex ->
            log.warn("could not persist rag_ingest_state for sha={}: {}", sha, ex.message)
        }
    }

    private fun ensureRepoCloned() {
        val dir = File(properties.workdir)
        if (!dir.exists() || !File(dir, ".git").exists()) {
            log.info("cloning {} into {}", properties.repoUrl, dir)
            dir.parentFile?.mkdirs()
            runProcess(listOf("git", "clone", "--branch", properties.branch, properties.repoUrl, dir.absolutePath), null)
        }
    }

    private fun runGit(vararg args: String): String {
        return runProcess(listOf("git") + args, File(properties.workdir))
    }

    private fun runProcess(command: List<String>, directory: File?): String {
        val builder = ProcessBuilder(command)
            .redirectErrorStream(true)
        if (directory != null) {
            builder.directory(directory)
        }
        val process = builder
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        require(exitCode == 0) {
            "command failed exit=$exitCode command=${command.joinToString(" ")} output=${output.take(2_000)}"
        }
        return output
    }

    private fun allFilesMatching(include: List<String>, exclude: List<String>): List<String> {
        val root = Paths.get(properties.workdir)
        if (!Files.exists(root)) return emptyList()
        return Files.walk(root)
            .filter(Files::isRegularFile)
            .map { root.relativize(it).toString() }
            .filter { matches(it, include, exclude) }
            .toList()
    }

    private fun matches(path: String, include: List<String>, exclude: List<String>): Boolean {
        fun globMatches(globs: List<String>, p: String): Boolean = globs.any { glob ->
            val regex = glob.replace(".", "\\.").replace("**", ".+").replace("*", "[^/]*")
            p.matches(Regex(regex))
        }
        return globMatches(include, path) && !globMatches(exclude, path)
    }
}

/** Interfaz inyectable para evitar acoplamiento al embedding store en este archivo. */
interface DocIngester {
    fun ingest(relativePath: String, sha: String)
}

@Component
@ConditionalOnProperty(
    prefix = "rag-ingestor",
    name = ["scheduling-enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class ScheduledGitRepoPoller(
    private val poller: GitRepoPoller,
) {
    @Scheduled(fixedDelayString = "#{@ragIngestorProperties.pollInterval.toMillis()}")
    fun pollAndIngest() {
        poller.pollAndIngest()
    }
}
