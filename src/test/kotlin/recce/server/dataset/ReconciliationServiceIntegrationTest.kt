package recce.server.dataset

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.r2dbc.spi.R2dbcBadGrammarException
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import reactor.kotlin.core.util.function.*
import reactor.test.StepVerifier
import reactor.util.function.Tuples
import recce.server.config.DataSourceTest

@MicronautTest(
    environments = arrayOf("test-integration"),
    propertySources = arrayOf("classpath:config/application-test-dataset.yml")
)
class ReconciliationServiceIntegrationTest : DataSourceTest() {
    @Inject
    lateinit var service: ReconciliationService

    @Inject
    lateinit var runRepository: MigrationRunRepository

    @Inject
    lateinit var recordRepository: MigrationRecordRepository

    @AfterEach
    override fun tearDown() {
        super.tearDown()
        runRepository.deleteAll().block()
    }

    @Test
    fun `can run a simple reconciliation`() {
        StepVerifier.create(service.runFor("test-dataset"))
            .assertNext { run ->
                assertThat(run.id).isNotNull
                assertThat(run.dataSetId).isEqualTo("test-dataset")
                assertThat(run.createdTime).isNotNull
                assertThat(run.updatedTime).isAfterOrEqualTo(run.createdTime)
                assertThat(run.completedTime).isAfterOrEqualTo(run.createdTime)
                assertThat(run.results).isEqualTo(DataSetResults(3, 4))
            }
            .verifyComplete()

        StepVerifier.create(
            runRepository.findAll()
                .flatMap { run ->
                    recordRepository.findByIdMigrationId(run.id!!).map { Tuples.of(run, it) }
                }
        )
            .assertNext { (run, record) ->
                assertThat(run.dataSetId).isEqualTo("test-dataset")
                assertThat(record.id.migrationId).isEqualTo(run.id)
                assertThat(record.id.migrationKey).isEqualTo("Test0")
                assertThat(record.sourceData).isEqualTo("4e92a72630647a5bc6fc3909b52387e6dd6e4466fc7bcceb7439fd6df18fe866")
                assertThat(record.targetData).isEqualTo("4e92a72630647a5bc6fc3909b52387e6dd6e4466fc7bcceb7439fd6df18fe866")
            }
            .assertNext { (run, record) ->
                assertThat(run.dataSetId).isEqualTo("test-dataset")
                assertThat(record.id.migrationId).isEqualTo(run.id)
                assertThat(record.id.migrationKey).isEqualTo("Test1")
                assertThat(record.sourceData).isEqualTo("ba4d2f35698204cfda7e42cb31752d878f578822920440b5aa0ed79f1ac79785")
                assertThat(record.targetData).isEqualTo("ba4d2f35698204cfda7e42cb31752d878f578822920440b5aa0ed79f1ac79785")
            }
            .assertNext { (run, record) ->
                assertThat(run.dataSetId).isEqualTo("test-dataset")
                assertThat(record.id.migrationId).isEqualTo(run.id)
                assertThat(record.id.migrationKey).isEqualTo("Test2")
                assertThat(record.sourceData).isEqualTo("eb25fb4ad862a2ba8a753d1d1c42889d18651150070113527bf55d50b663e7ac")
                assertThat(record.targetData).isNull()
            }
            .assertNext { (run, record) ->
                assertThat(run.dataSetId).isEqualTo("test-dataset")
                assertThat(record.id.migrationId).isEqualTo(run.id)
                assertThat(record.id.migrationKey).isEqualTo("Test3")
                assertThat(record.sourceData).isNull()
                assertThat(record.targetData).isEqualTo("168c587d9c765ec2cda598750201d15a2e616641455696df176f51d6433dff37")
            }
            .assertNext { (run, record) ->
                assertThat(run.dataSetId).isEqualTo("test-dataset")
                assertThat(record.id.migrationId).isEqualTo(run.id)
                assertThat(record.id.migrationKey).isEqualTo("Test4")
                assertThat(record.sourceData).isNull()
                assertThat(record.targetData).isEqualTo("8b4cde00f0a0d00546a59e74bc9b183a43d69143944101eeae789163b509038d")
            }
            .verifyComplete()
    }

    @Test
    fun `should emit error on bad query`() {
        transaction(sourceDb) { SchemaUtils.drop(TestData) }

        StepVerifier.create(service.runFor("test-dataset"))
            .expectError(R2dbcBadGrammarException::class.java)
            .verify()
    }

    @Test
    fun `triggering multiple recs ignores failures`() {
        StepVerifier.create(service.runIgnoreFailure(listOf("bad", "test-dataset", "bad2")))
            .assertNext { assertThat(it.dataSetId).isEqualTo("test-dataset") }
            .verifyComplete()
    }
}