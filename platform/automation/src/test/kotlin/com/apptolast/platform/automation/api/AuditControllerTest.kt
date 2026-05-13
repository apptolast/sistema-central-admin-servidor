package com.apptolast.platform.automation.api

import com.apptolast.platform.automation.application.port.inbound.QueryAuditUseCase
import com.apptolast.platform.automation.application.port.outbound.AuditQuery
import com.apptolast.platform.automation.application.service.AuditQueryService
import com.apptolast.platform.automation.application.port.outbound.AuditLogRepository
import com.apptolast.platform.automation.domain.model.AuditEntry
import com.apptolast.platform.automation.domain.model.AuditOutcome
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Tests directos del AuditController — sin MockMvc por el mismo motivo que
 * AutomationControllerTest (spring-modulith ABI skew). Invocación directa
 * cubre el mismo contrato: query params → AuditQuery → repo → DTO.
 */
class AuditControllerTest {

    private val now = Instant.parse("2026-05-13T18:00:00Z")

    private val repo = object : AuditLogRepository {
        var lastQuery: AuditQuery? = null
        val store = mutableListOf<AuditEntry>()
        override fun save(entry: AuditEntry) { store.add(entry) }
        override fun findById(id: UUID): AuditEntry? = store.firstOrNull { it.id == id }
        override fun query(query: AuditQuery): List<AuditEntry> {
            lastQuery = query
            return store
        }
    }

    private val useCase: QueryAuditUseCase = AuditQueryService(repo)
    private val controller = AuditController(useCase)

    @Test
    fun `list with default params returns all entries summarized`() {
        repo.save(entry(commandKind = "kubectl-read", outcome = AuditOutcome.AcceptedOk(0, 10, "stdout-x", "")))
        repo.save(entry(commandKind = "helm-rollback", outcome = AuditOutcome.Rejected("not in whitelist")))

        val page = controller.list(
            from = null, to = null, commandKind = null, outcome = null, userId = null,
            page = 0, size = 50,
        )

        page.items shouldHaveSize 2
        page.page shouldBe 0
        page.size shouldBe 50
        page.items[0].outcome shouldBe "ACCEPTED_OK"
        page.items[0].stdoutExcerpt shouldBe "stdout-x"
        page.items[1].outcome shouldBe "REJECTED"
        page.items[1].rejectionReason shouldBe "not in whitelist"
    }

    @Test
    fun `list forwards filters to the use case`() {
        controller.list(
            from = Instant.parse("2026-05-10T00:00:00Z"),
            to = Instant.parse("2026-05-13T23:59:59Z"),
            commandKind = "kubectl-read",
            outcome = "ACCEPTED_FAIL",
            userId = "pablo",
            page = 1, size = 25,
        )

        val q = repo.lastQuery.shouldNotBeNull()
        q.from shouldBe Instant.parse("2026-05-10T00:00:00Z")
        q.to shouldBe Instant.parse("2026-05-13T23:59:59Z")
        q.commandKind shouldBe "kubectl-read"
        q.outcomeLabel shouldBe "ACCEPTED_FAIL"
        q.userId shouldBe "pablo"
        q.page shouldBe 1
        q.size shouldBe 25
    }

    @Test
    fun `list caps size to MAX_SIZE`() {
        controller.list(
            from = null, to = null, commandKind = null, outcome = null, userId = null,
            page = 0, size = 5000,
        )
        repo.lastQuery.shouldNotBeNull().size shouldBe AuditController.MAX_SIZE
    }

    @Test
    fun `list coerces negative page to 0`() {
        controller.list(
            from = null, to = null, commandKind = null, outcome = null, userId = null,
            page = -3, size = 10,
        )
        repo.lastQuery.shouldNotBeNull().page shouldBe 0
    }

    @Test
    fun `list ignores blank string filters`() {
        controller.list(
            from = null, to = null, commandKind = "", outcome = "  ", userId = "",
            page = 0, size = 10,
        )
        val q = repo.lastQuery.shouldNotBeNull()
        q.commandKind shouldBe null
        q.outcomeLabel shouldBe null
        q.userId shouldBe null
    }

    @Test
    fun `getById returns 200 with full detail when found`() {
        val saved = entry(commandKind = "kubectl-read", outcome = AuditOutcome.AcceptedOk(0, 12, "out", "err"))
        repo.save(saved)

        val response = controller.getById(saved.id)

        response.statusCode.value() shouldBe 200
        response.body!!.id shouldBe saved.id
        response.body!!.commandKind shouldBe "kubectl-read"
        response.body!!.stdoutExcerpt shouldBe "out"
        response.body!!.stderrExcerpt shouldBe "err"
    }

    @Test
    fun `getById returns 404 when missing`() {
        val response = controller.getById(UUID.randomUUID())
        response.statusCode.value() shouldBe 404
    }

    private fun entry(
        commandKind: String,
        outcome: AuditOutcome,
    ) = AuditEntry(
        executedAt = now,
        commandKind = commandKind,
        commandPayload = """{"kind":"$commandKind"}""",
        executorKind = "TestExecutor",
        outcome = outcome,
    )
}
