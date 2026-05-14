package com.apptolast.ragingestor.git

import com.apptolast.ragingestor.config.RagIngestorProperties
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.slf4j.LoggerFactory
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference

/**
 * Polls el repo cada `pollInterval` y detecta docs nuevos/cambiados.
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

        openOrCloneRepository().use { git ->
            fetchBranch(git)

            val currentSha = remoteBranchSha(git)
            val previousSha = loadLastIngestedSha() ?: lastIngestedSha.get()

            if (currentSha == previousSha) {
                log.debug("no new commits, skipping")
                return
            }

            checkout(git, currentSha)
            val changed = if (previousSha == null) {
                allFilesMatching(properties.includePaths, properties.excludePaths)
            } else {
                changedFiles(git, previousSha, currentSha)
            }

            log.info("changed files: {}", changed.size)
            changed.forEach { docIngester.ingest(it, currentSha) }

            persistLastIngestedSha(currentSha)
            lastIngestedSha.set(currentSha)
            log.info("ingest cycle complete sha={}", currentSha)
        }
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

    private fun openOrCloneRepository(): Git {
        val dir = File(properties.workdir)
        if (dir.exists() && File(dir, ".git").exists()) {
            return Git.open(dir)
        }

        log.info("cloning {} into {}", properties.repoUrl, dir)
        dir.parentFile?.mkdirs()
        return Git.cloneRepository()
            .setURI(properties.repoUrl)
            .setBranch(properties.branch)
            .setDirectory(dir)
            .withCredentials()
            .call()
    }

    private fun fetchBranch(git: Git) {
        git.fetch()
            .setRemote("origin")
            .setRefSpecs(RefSpec("+refs/heads/${properties.branch}:refs/remotes/origin/${properties.branch}"))
            .withCredentials()
            .call()
    }

    private fun remoteBranchSha(git: Git): String {
        val remoteRef = "refs/remotes/origin/${properties.branch}"
        val ref = git.repository.exactRef(remoteRef)
            ?: error("remote branch not found: origin/${properties.branch}")
        return ref.objectId.name
    }

    private fun checkout(git: Git, sha: String) {
        git.checkout()
            .setName(sha)
            .call()
    }

    private fun changedFiles(git: Git, previousSha: String, currentSha: String): List<String> {
        val repository = git.repository
        repository.newObjectReader().use { reader ->
            val oldTree = CanonicalTreeParser().apply {
                reset(reader, treeId(repository, previousSha))
            }
            val newTree = CanonicalTreeParser().apply {
                reset(reader, treeId(repository, currentSha))
            }

            return git.diff()
                .setOldTree(oldTree)
                .setNewTree(newTree)
                .call()
                .mapNotNull { it.changedPath() }
                .filter { matches(it, properties.includePaths, properties.excludePaths) }
        }
    }

    private fun treeId(repository: Repository, sha: String): ObjectId {
        val commitId = repository.resolve("$sha^{commit}")
            ?: error("commit not found: $sha")
        RevWalk(repository).use { walk ->
            return walk.parseCommit(commitId).tree.id
        }
    }

    private fun allFilesMatching(include: List<String>, exclude: List<String>): List<String> {
        val root = Paths.get(properties.workdir)
        if (!Files.exists(root)) return emptyList()
        Files.walk(root).use { paths ->
            return paths
                .filter(Files::isRegularFile)
                .map { root.relativize(it).toString().replace(File.separatorChar, '/') }
                .filter { matches(it, include, exclude) }
                .toList()
        }
    }

    private fun matches(path: String, include: List<String>, exclude: List<String>): Boolean {
        fun globMatches(globs: List<String>, p: String): Boolean = globs.any { glob ->
            val regex = glob.replace(".", "\\.").replace("**", ".+").replace("*", "[^/]*")
            p.matches(Regex(regex))
        }
        return globMatches(include, path) && !globMatches(exclude, path)
    }

    private fun credentialsProvider(): CredentialsProvider? {
        val token = properties.repoToken?.takeIf { it.isNotBlank() } ?: return null
        return UsernamePasswordCredentialsProvider("x-access-token", token)
    }

    private fun <T : org.eclipse.jgit.api.TransportCommand<T, *>> T.withCredentials(): T {
        credentialsProvider()?.let { setCredentialsProvider(it) }
        return this
    }

    private fun DiffEntry.changedPath(): String? = when (changeType) {
        DiffEntry.ChangeType.DELETE -> oldPath
        DiffEntry.ChangeType.ADD,
        DiffEntry.ChangeType.COPY,
        DiffEntry.ChangeType.MODIFY,
        DiffEntry.ChangeType.RENAME,
        -> newPath
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
