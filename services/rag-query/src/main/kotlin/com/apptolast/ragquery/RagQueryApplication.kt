package com.apptolast.ragquery

import com.apptolast.ragquery.config.RagQueryProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(RagQueryProperties::class)
class RagQueryApplication

fun main(args: Array<String>) {
    runApplication<RagQueryApplication>(*args)
}
