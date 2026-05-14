package com.apptolast.platform.automation.api

import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder
import io.fabric8.kubernetes.api.model.batch.v1.CronJobBuilder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CronJobDtoTest {

    @Test
    fun `maps never-run cronjob to stable dto`() {
        val dto = cronJobToDto(
            CronJobBuilder()
                .withNewMetadata().withNamespace("platform").withName("image-refresh").endMetadata()
                .withNewSpec().withSchedule("0 */5 * * *").endSpec()
                .build(),
        )

        dto.namespace shouldBe "platform"
        dto.name shouldBe "image-refresh"
        dto.schedule shouldBe "0 */5 * * *"
        dto.status shouldBe "NEVER_RUN"
        dto.activeJobs shouldBe 0
    }

    @Test
    fun `active cronjob wins over scheduled status`() {
        val dto = cronJobToDto(
            CronJobBuilder()
                .withNewMetadata().withNamespace("cluster-ops").withName("host-checks").endMetadata()
                .withNewSpec().withSchedule("*/5 * * * *").endSpec()
                .withNewStatus()
                    .withLastScheduleTime("2026-05-14T12:45:00Z")
                    .withActive(
                        ObjectReferenceBuilder()
                            .withNamespace("cluster-ops")
                            .withName("host-checks-29646045")
                            .build(),
                    )
                .endStatus()
                .build(),
        )

        dto.status shouldBe "ACTIVE"
        dto.activeJobs shouldBe 1
        dto.lastScheduleTime shouldBe "2026-05-14T12:45:00Z"
    }
}
