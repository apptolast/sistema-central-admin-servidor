package com.apptolast.platform.automation.api

import io.fabric8.kubernetes.api.model.batch.v1.CronJob
import io.fabric8.kubernetes.client.KubernetesClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Read-only API para poblar el Cronjob Board con estado real del cluster.
 */
@RestController
@RequestMapping("/api/v1/automation/cronjobs")
@ConditionalOnBean(KubernetesClient::class)
class CronJobController(
    private val client: KubernetesClient,
) {

    @GetMapping
    fun list(@RequestParam(required = false) namespace: String?): List<CronJobDto> {
        val items = if (namespace.isNullOrBlank()) {
            client.batch().v1().cronjobs().inAnyNamespace().list().items
        } else {
            client.batch().v1().cronjobs().inNamespace(namespace).list().items
        }

        return items
            .sortedWith(compareBy({ it.metadata.namespace }, { it.metadata.name }))
            .map(::cronJobToDto)
    }
}

internal fun cronJobToDto(cronJob: CronJob): CronJobDto {
    val status = cronJob.status
    val spec = cronJob.spec
    val suspended = spec?.suspend ?: false
    val activeJobs = status?.active?.size ?: 0
    return CronJobDto(
        namespace = cronJob.metadata.namespace,
        name = cronJob.metadata.name,
        schedule = spec?.schedule ?: "",
        suspended = suspended,
        activeJobs = activeJobs,
        lastScheduleTime = status?.lastScheduleTime,
        lastSuccessfulTime = status?.lastSuccessfulTime,
        status = when {
            suspended -> "SUSPENDED"
            activeJobs > 0 -> "ACTIVE"
            status?.lastSuccessfulTime != null -> "SUCCESS"
            status?.lastScheduleTime != null -> "SCHEDULED"
            else -> "NEVER_RUN"
        },
    )
}

data class CronJobDto(
    val namespace: String,
    val name: String,
    val schedule: String,
    val suspended: Boolean,
    val activeJobs: Int,
    val lastScheduleTime: String?,
    val lastSuccessfulTime: String?,
    val status: String,
)
