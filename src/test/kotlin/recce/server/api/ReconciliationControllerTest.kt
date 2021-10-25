package recce.server.api

import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.restassured.builder.RequestSpecBuilder
import io.restassured.filter.log.ResponseLoggingFilter
import io.restassured.http.ContentType
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import io.restassured.specification.RequestSpecification
import jakarta.inject.Inject
import org.apache.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import recce.server.dataset.MigrationRun
import recce.server.dataset.ReconciliationRunner
import recce.server.dataset.ReconciliationService

internal class ReconciliationControllerTest {

    private val testDataset = "testDataset"

    private val service = mock<ReconciliationService> {
        on { runFor(eq(testDataset)) } doReturn Mono.just(MigrationRun(testDataset))
    }

    private val controller = ReconciliationController(service)

    @Test
    fun `controller should delegate to service`() {
        StepVerifier.create(controller.create(ReconciliationController.RunCreationParams(eq(testDataset))))
            .expectNext(MigrationRun(testDataset))
            .verifyComplete()
    }
}

@MicronautTest
internal class ReconciliationControllerApiTest {
    private val testDataset = "testDataset"

    @Inject
    lateinit var server: EmbeddedServer
    lateinit var spec: RequestSpecification

    @BeforeEach
    fun setUp() {
        spec = RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setBaseUri(server.uri)
            .addFilter(ResponseLoggingFilter())
            .build()
    }

    @Test
    fun `controller should delegate to service`() {
        Given {
            spec(spec)
        } When {
            body(mapOf("datasetId" to testDataset))
            post("/runs")
        } Then {
            statusCode(HttpStatus.SC_OK)
            body(
                "datasetId", equalTo(testDataset)
            )
        }
    }

    @Test
    fun `controller should validate params`() {
        val errors: List<Map<String, String>> =
            Given {
                spec(spec)
            } When {
                body(emptyMap<String, String>())
                post("/runs")
            } Then {
                statusCode(HttpStatus.SC_BAD_REQUEST)
                body("message", equalTo("Bad Request"))
            } Extract {
                path("_embedded.errors")
            }

        assertThat(errors)
            .singleElement()
            .satisfies {
                assertThat(it).hasEntrySatisfying("message") { message ->
                    assertThat(message).contains("Missing required creator property 'datasetId'")
                }
            }
    }

    @MockBean(ReconciliationRunner::class)
    fun reconciliationService(): ReconciliationRunner {
        return mock {
            on { runFor(eq(testDataset)) } doReturn Mono.just(MigrationRun(testDataset))
        }
    }
}
