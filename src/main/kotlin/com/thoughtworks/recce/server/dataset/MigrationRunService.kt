package com.thoughtworks.recce.server.dataset

import jakarta.inject.Singleton
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

@Singleton
open class MigrationRunService(private val runRepository: MigrationRunRepository) {
    fun start(dataSetId: String): Mono<MigrationRun> = runRepository
        .save(MigrationRun(dataSetId))
        .doOnNext { logger.info { "Starting reconciliation run for $it}..." } }
        .cache()

    fun complete(run: MigrationRun): Mono<MigrationRun> =
        runRepository.update(run.apply { completedTime = LocalDateTime.now() })
            .doOnNext { logger.info { "Run completed for $it" } }
}