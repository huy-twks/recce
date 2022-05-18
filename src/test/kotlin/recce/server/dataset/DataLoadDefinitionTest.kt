package recce.server.dataset

import io.micronaut.context.BeanLocator
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.inject.qualifiers.Qualifiers
import io.r2dbc.spi.Connection
import io.r2dbc.spi.Result
import io.r2dbc.spi.Statement
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.kotlin.*
import reactor.core.publisher.Mono
import reactor.kotlin.test.test
import java.net.URL
import java.nio.file.Paths
import java.util.*

internal class DataLoadDefinitionTest {
    private val testSourceName = "source1"
    private val testQuery = "SELECT * FROM somewhere"
    private val testQueryStatementFromFile = "SELECT * FROM elsewhere\n"
    private val res: URL = javaClass.classLoader.getResource("config/test-query.sql")
    private val testQueryFile = Paths.get(res.toURI()).toFile().absolutePath
    private val testQueryInvalidFile = "test-invalid-query.sql"

    private lateinit var definitionQuery: DataLoadDefinition
    private lateinit var definitionQueryFromFile: DataLoadDefinition
    private lateinit var definitionQueryFromInvalidFile: DataLoadDefinition
    private lateinit var definitionQueryAndQueryFromFile: DataLoadDefinition
    private lateinit var definitionQueryAndQueryFromInvalidFile: DataLoadDefinition
    private lateinit var definitionNoQueryAndNoQueryFromFile: DataLoadDefinition

    private val mockConnection: Connection = mock {
        on { close() } doReturn Mono.empty()
    }

    @BeforeEach
    fun setUp() {
        definitionQuery = DataLoadDefinition(testSourceName, testQuery).apply { role = DataLoadRole.Source }
        definitionQueryFromFile = DataLoadDefinition(testSourceName, "", testQueryFile).apply { role = DataLoadRole.Source }
        definitionQueryFromInvalidFile = DataLoadDefinition(testSourceName, "", testQueryInvalidFile).apply { role = DataLoadRole.Source }
        definitionQueryAndQueryFromFile = DataLoadDefinition(testSourceName, testQuery, testQueryFile).apply { role = DataLoadRole.Source }
        definitionQueryAndQueryFromInvalidFile = DataLoadDefinition(testSourceName, testQuery, testQueryInvalidFile).apply { role = DataLoadRole.Source }
        definitionNoQueryAndNoQueryFromFile = DataLoadDefinition(testSourceName, "", "").apply { role = DataLoadRole.Source }
    }

    @Test
    fun `should load query statement from file if valid query file provided`() {
        val operations = mock<R2dbcOperations>()
        val beanLocator = mock<BeanLocator> {
            on { findBean(any<Class<Any>>(), eq(Qualifiers.byName(testSourceName))) } doReturn Optional.of(operations)
        }

        definitionQueryFromFile.populate(beanLocator)

        assertThat(definitionQueryFromFile.queryStatement).isEqualTo(testQueryStatementFromFile)
    }

    @Test
    fun `should fail to load query statement from file if invalid query file provided`() {
        val operations = mock<R2dbcOperations>()
        val beanLocator = mock<BeanLocator> {
            on { findBean(any<Class<Any>>(), eq(Qualifiers.byName(testSourceName))) } doReturn Optional.of(operations)
        }

        assertThatThrownBy { definitionQueryFromInvalidFile.populate(beanLocator) }
            .isExactlyInstanceOf(ConfigurationException::class.java)
            .hasMessageContaining("Cannot load query statement from queryFile")
    }

    @Test
    fun `should load query statement from query if both query and query file provided`() {
        val operations = mock<R2dbcOperations>()
        val beanLocator = mock<BeanLocator> {
            on { findBean(any<Class<Any>>(), eq(Qualifiers.byName(testSourceName))) } doReturn Optional.of(operations)
        }

        definitionQueryAndQueryFromFile.populate(beanLocator)

        assertThat(definitionQueryAndQueryFromFile.queryStatement).isEqualTo(testQuery)
    }

    @Test
    fun `should load query statement from query if both query and invalid query file provided`() {
        val operations = mock<R2dbcOperations>()
        val beanLocator = mock<BeanLocator> {
            on { findBean(any<Class<Any>>(), eq(Qualifiers.byName(testSourceName))) } doReturn Optional.of(operations)
        }

        definitionQueryAndQueryFromInvalidFile.populate(beanLocator)

        assertThat(definitionQueryAndQueryFromInvalidFile.queryStatement).isEqualTo(testQuery)
    }

    @Test
    fun `should fail to load query statement from query if query not provided and query file not provided`() {
        val operations = mock<R2dbcOperations>()
        val beanLocator = mock<BeanLocator> {
            on { findBean(any<Class<Any>>(), eq(Qualifiers.byName(testSourceName))) } doReturn Optional.of(operations)
        }

        assertThatThrownBy { definitionNoQueryAndNoQueryFromFile.populate(beanLocator) }
            .isExactlyInstanceOf(ConfigurationException::class.java)
            .hasMessageContaining("Either query or queryFile must be provided!")
    }

    @Test
    fun `should populate db operations from context`() {
        val operations = mock<R2dbcOperations>()
        val beanLocator = mock<BeanLocator> {
            on { findBean(any<Class<Any>>(), eq(Qualifiers.byName(testSourceName))) } doReturn Optional.of(operations)
        }

        definitionQuery.populate(beanLocator)

        assertThat(definitionQuery.dbOperations).isEqualTo(operations)
    }

    @Test
    fun `should produce short descriptor with role`() {
        assertThat(definitionQuery.datasourceDescriptor).isEqualTo("Source(ref=source1)")
    }

    @Test
    fun `should throw on failure to find bean`() {

        assertThatThrownBy { definitionQuery.populate(mock()) }
            .isExactlyInstanceOf(ConfigurationException::class.java)
            .hasMessageContaining("source1")
    }

    @Test
    fun `should stream rows from query`() {

        val result = mock<Result>()

        val statement: Statement = mock {
            on { execute() } doReturn Mono.just(result)
        }

        definitionQuery.dbOperations = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS) {
            on { connectionFactory().create() } doReturn Mono.just(mockConnection)
        }

        whenever(mockConnection.createStatement(eq(testQuery))).thenReturn(statement)

        definitionQuery.runQuery()
            .test()
            .expectNext(result)
            .verifyComplete()

        inOrder(mockConnection, statement).apply {
            verify(mockConnection).createStatement(eq(testQuery))
            verify(statement).execute()
            verify(mockConnection).close()
        }
    }

    @Test
    fun `should close connection after failed query`() {
        definitionQuery.dbOperations = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS) {
            on { connectionFactory().create() } doReturn Mono.just(mockConnection)
        }

        definitionQuery.runQuery()
            .test()
            .expectErrorSatisfies {
                assertThat(it)
                    .isInstanceOf(NullPointerException::class.java)
                    .hasMessageContaining("Cannot invoke \"io.r2dbc.spi.Statement.execute()")
            }
            .verify()

        inOrder(mockConnection).apply {
            verify(mockConnection).createStatement(eq(testQuery))
            verify(mockConnection).close()
        }
    }
}
