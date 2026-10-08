package org.connecttag.lib.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kotlin.reflect.KClass

fun interface InjectedViewModelFactory<VM : ViewModel> {
    fun create(): VM
}

class InjectedViewModelFactoryBuilder {
    private val creators = linkedMapOf<KClass<out ViewModel>, (CreationExtras) -> ViewModel>()

    fun <VM : ViewModel> bind(
        modelClass: KClass<VM>,
        factory: InjectedViewModelFactory<VM>,
    ) {
        creators[modelClass] = { factory.create() }
    }

    fun <VM : ViewModel> bind(
        modelClass: KClass<VM>,
        creator: () -> VM,
    ) {
        creators[modelClass] = { creator() }
    }

    fun <VM : ViewModel> bindWithExtras(
        modelClass: KClass<VM>,
        creator: (CreationExtras) -> VM,
    ) {
        creators[modelClass] = creator
    }

    internal fun build(): ViewModelProvider.Factory {
        val registeredCreators = creators.toMap()
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
                val creator = registeredCreators.entries.firstOrNull { (registeredClass) ->
                    modelClass == registeredClass
                }?.value
                if (creator == null) {
                    val registeredCreatorNames = registeredCreators.keys.joinToString { it.toString() }
                    error(
                        "No injected ViewModel creator registered for $modelClass. " +
                            "Registered creators: $registeredCreatorNames",
                    )
                }
                return creator(extras) as T
            }
        }
    }
}

fun injectedViewModelProviderFactory(
    build: InjectedViewModelFactoryBuilder.() -> Unit,
): ViewModelProvider.Factory {
    return InjectedViewModelFactoryBuilder()
        .apply(build)
        .build()
}

fun <VM : ViewModel> injectedViewModelProviderFactory(
    modelClass: KClass<VM>,
    factory: InjectedViewModelFactory<VM>,
): ViewModelProvider.Factory {
    return injectedViewModelProviderFactory {
        bind(modelClass = modelClass, factory = factory)
    }
}

fun <VM : ViewModel> injectedViewModelProviderFactory(
    modelClass: KClass<VM>,
    creator: (CreationExtras) -> VM,
): ViewModelProvider.Factory {
    return injectedViewModelProviderFactory {
        bindWithExtras(modelClass = modelClass, creator = creator)
    }
}
