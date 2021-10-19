package recce.server.config

import io.micronaut.context.BeanLocator
import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.bind.annotation.Bindable
import javax.annotation.PostConstruct
import javax.validation.constraints.NotNull

interface PostConstructable {
    fun populate(locator: BeanLocator)
}

@ConfigurationProperties("reconciliation")
class ReconciliationConfiguration
@ConfigurationInject constructor(
    @Bindable(defaultValue = "") val triggerOnStart: List<String> = emptyList(),
    val datasets: Map<String, DataSetConfiguration>
) : PostConstructable {

    @PostConstruct
    override fun populate(locator: BeanLocator) {
        for ((name, config) in datasets) {
            config.name = name
            config.populate(locator)
        }
    }
}

class DataSetConfiguration(@NotNull val source: DataLoadDefinition, @NotNull val target: DataLoadDefinition) :
    PostConstructable {
    lateinit var name: String
    override fun populate(locator: BeanLocator) {
        source.populate(locator)
        target.populate(locator)
    }
}