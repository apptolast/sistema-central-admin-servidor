package com.apptolast.platform.identity.domain

import com.apptolast.platform.identity.api.Permission
import com.apptolast.platform.identity.api.Role
import com.apptolast.platform.identity.domain.model.AuthorizationMatrix
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuthorizationMatrixTest {

    @Test
    fun `ADMIN can do everything destructive`() {
        AuthorizationMatrix.grants(Role.ADMIN) shouldContain Permission.RUNBOOK_EXECUTE_DESTRUCTIVE
        AuthorizationMatrix.grants(Role.ADMIN) shouldContain Permission.SECRET_ROTATE
    }

    @Test
    fun `ONCALL cannot execute destructive runbooks`() {
        AuthorizationMatrix.grants(Role.ONCALL) shouldNotContain Permission.RUNBOOK_EXECUTE_DESTRUCTIVE
    }

    @Test
    fun `ONCALL can ack alerts but not define SLOs`() {
        val perms = AuthorizationMatrix.grants(Role.ONCALL)
        perms shouldContain Permission.SLO_ACK_ALERT
        perms shouldNotContain Permission.SLO_DEFINE
    }

    @Test
    fun `VIEWER can only read`() {
        val perms = AuthorizationMatrix.grants(Role.VIEWER)
        perms shouldContain Permission.INVENTORY_READ
        perms shouldNotContain Permission.INVENTORY_WRITE
        perms shouldNotContain Permission.SECRET_LIST
    }

    @Test
    fun `AGENT cannot list secrets even though it can query RAG`() {
        val perms = AuthorizationMatrix.grants(Role.AGENT)
        perms shouldContain Permission.RAG_QUERY
        perms shouldNotContain Permission.SECRET_LIST
        perms shouldNotContain Permission.RUNBOOK_EXECUTE_SAFE
    }

    @Test
    fun `rolesGranting returns admin and oncall for execute-safe`() {
        AuthorizationMatrix.rolesGranting(Permission.RUNBOOK_EXECUTE_SAFE) shouldBe setOf(Role.ADMIN, Role.ONCALL)
    }

    @Test
    fun `allows multi-role union`() {
        AuthorizationMatrix.allows(setOf(Role.VIEWER, Role.ONCALL), Permission.SLO_ACK_ALERT) shouldBe true
        AuthorizationMatrix.allows(setOf(Role.VIEWER), Permission.SLO_ACK_ALERT) shouldBe false
    }
}
