package com.apptolast.ragingestor.git

import com.apptolast.ragingestor.config.RagIngestorProperties
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
) {

    private val log = LoggerFactory.getLogger(GitRepoPoller::class.java)
    private val lastIngestedSha = AtomicReference<String?>(null)

    @Scheduled(fixedDelayString = "#{@ragIngestorProperties.pollInterval.toMillis()}")
    fun pollAndIngest() {
        log.info("starting RAG ingest cycle repo={} branch={}", properties.repoUrl, properties.branch)

        ensureRepoCloned()
        runGit("fetch", "origin", properties.branch)

        val currentSha = runGit("rev-parse", "origin/${properties.branch}").trim()
        val previousSha = lastIngestedSha.get()

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

        lastIngestedSha.set(currentSha)
        log.info("ingest cycle complete sha={}", currentSha)
    }

    private fun ensureRepoCloned() {
        val dir = File(properties.workdir)
        if (!dir.exists() || !File(dir, ".git").exists()) {
            log.info("cloning {} into {}", properties.repoUrl, dir)
            dir.parentFile?.mkdirs()
            ProcessBuilder("git", "clone", "--branch", properties.branch, properties.repoUrl, dir.absolutePath)
                .redirectErrorStream(true)
                .start()
                .waitFor()
        }
    }

    private fun runGit(vararg args: String): String {
        val process = ProcessBuilder(listOf("git") + args)
            .directory(File(properties.workdir))
            .redirectErrorStream(true)
            .start()
        process.waitFor()
        return process.inputStream.bufferedReader().readText()
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
