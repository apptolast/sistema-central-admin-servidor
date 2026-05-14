package com.apptolast.platform.automation.infrastructure

import com.apptolast.platform.automation.application.port.outbound.ExecutionOutcome
import com.apptolast.platform.automation.domain.model.SafeCommand
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * Ejecuta Helm sin pasar por shell: binario absoluto + lista de argumentos.
 */
class HelmCliSandbox(
    private val candidatePaths: List<Path> = listOf(
        Path.of("/usr/local/bin/helm"),
        Path.of("/usr/bin/helm"),
    ),
    private val maxBufferBytes: Int = DEFAULT_MAX_BUFFER_BYTES,
) {
    fun execute(
        command: SafeCommand,
        args: List<String>,
        timeout: Duration,
    ): ExecutionOutcome {
        val helm = resolveHelm()
            ?: return ExecutionOutcome(command, 127, "", "helm binary not found in allowed paths", 0)

        var stdout = ""
        var stderr = ""
        var exitCode = 0
        val durationMs = measureTimeMillis {
            try {
                val process = ProcessBuilder(listOf(helm.toString()) + args).start()
                val out = LimitedBuffer(maxBufferBytes)
                val err = LimitedBuffer(maxBufferBytes)
                val outThread = process.inputStream.drainInto(out)
                val errThread = process.errorStream.drainInto(err)

                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                    process.waitFor(2, TimeUnit.SECONDS)
                    exitCode = 124
                    stderr = "helm command timed out after ${timeout.seconds}s"
                } else {
                    exitCode = process.exitValue()
                }

                outThread.join(1_000)
                errThread.join(1_000)
                stdout = out.asString()
                val processStderr = err.asString()
                stderr = listOf(stderr, processStderr)
                    .filter { it.isNotBlank() }
                    .joinToString(separator = "\n")
            } catch (e: Exception) {
                exitCode = 127
                stderr = "${e::class.java.simpleName}: ${e.message}"
            }
        }

        return ExecutionOutcome(command, exitCode, stdout, stderr, durationMs)
    }

    private fun resolveHelm(): Path? =
        candidatePaths
            .filter { it.isAbsolute }
            .firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }

    private fun InputStream.drainInto(buffer: LimitedBuffer): Thread =
        Thread {
            use { input ->
                val chunk = ByteArray(DEFAULT_CHUNK_BYTES)
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    buffer.write(chunk, read)
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

    private class LimitedBuffer(private val maxBytes: Int) {
        private val out = ByteArrayOutputStream(maxBytes.coerceAtLeast(0))
        private var truncated = false

        @Synchronized
        fun write(bytes: ByteArray, length: Int) {
            if (length <= 0 || out.size() >= maxBytes) {
                truncated = truncated || length > 0
                return
            }

            val remaining = maxBytes - out.size()
            val accepted = minOf(length, remaining)
            out.write(bytes, 0, accepted)
            truncated = truncated || accepted < length
        }

        @Synchronized
        fun asString(): String {
            val text = out.toByteArray().toString(StandardCharsets.UTF_8)
            return if (truncated) "$text\n[truncated]" else text
        }
    }

    companion object {
        private const val DEFAULT_MAX_BUFFER_BYTES = 1_048_576
        private const val DEFAULT_CHUNK_BYTES = 8_192
    }
}
