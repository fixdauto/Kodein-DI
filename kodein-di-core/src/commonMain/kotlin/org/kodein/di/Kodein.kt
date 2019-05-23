package org.kodein.di

import org.kodein.di.bindings.ContextTranslator
import org.kodein.di.bindings.ExternalSource
import org.kodein.di.bindings.KodeinBinding
import org.kodein.di.bindings.Scope
import org.kodein.di.internal.KodeinImpl

/**
 * KOtlin DEpendency INjection.
 *
 * To construct a Kodein instance, simply use [it's block constructor][Kodein.invoke] and define your bindings in it :
 *
 * ```kotlin
 * val kodein = Kodein {
 *     bind<Dice>() with factory { sides: Int -> RandomDice(sides) }
 *     bind<DataSource>() with singleton { SqliteDS.open("path/to/file") }
 *     bind<Random>() with provider { SecureRandom() }
 *     constant("answer") with "forty-two"
 * }
 * ```
 */
interface Kodein : KodeinAware {

    /**
     * Exception thrown when there is a dependency loop.
     *
     * @param message The message of the exception.
     */
    class DependencyLoopException internal constructor(message: String) : RuntimeException(message)

    /**
     * Exception thrown when asked for a dependency that cannot be found.
     *
     * @property key The key that was not found.
     * @param message The message of the exception.
     */
    class NotFoundException(val key: Key<*, *, *>, message: String)
            : RuntimeException(message)

    /**
     * Exception thrown when searching for bindings and none could be found.
     *
     * @property search The specs that lead to no result.
     * @param message The message of the exception.
     */
    class NoResultException(val search: SearchSpecs, message: String)
        : RuntimeException(message)

    /**
     * Exception thrown when there is an overriding error.
     *
     * @param message The message of the exception.
     */
    class OverridingException(message: String) : RuntimeException(message)

    override val kodein: Kodein get() = this

    /**
     * In Kodein, each [KodeinBinding] is bound to a Key. A Key holds all information necessary to retrieve a factory (and therefore an instance).
     *
     * A key contains many types & a tag.
     * It defines the types of the values that will be passed to and retrieved from Kodein, not the values themselves.
     *
     * @param C The in context type.
     * @param A The in argument type (`Unit` for a provider).
     * @param T The out type.
     * @property contextType The in context type.
     * @property argType The in argument type (`Unit` for a provider).
     * @property type The out type.
     * @property tag The associated tag.
     */
    @Suppress("EqualsOrHashCode")
    data class Key<in C, in A, out T: Any>(
            val contextType: TypeToken<in C>,
            val argType: TypeToken<in A>,
            val type: TypeToken<out T>,
            val tag: Any?
    ) {

        /**
         * Because this type is immutable, we can cache its hash to make it faster inside a HashMap.
         */
        private var _hashCode: Int = 0

        override fun hashCode(): Int {
            if (_hashCode == 0) {
                _hashCode = contextType.hashCode()
                _hashCode = 31 * _hashCode + argType.hashCode()
                _hashCode = 29 * _hashCode + type.hashCode()
                _hashCode = 23 * _hashCode + (tag?.hashCode() ?: 0)
            }
            return _hashCode
        }

        override fun equals(other: Any?): Boolean {
            return other is Key<*,*,*> && hashCode() == other.hashCode()
        }

        /**
         * @return The [description]
         */
        override fun toString() = description

        /**
         * Right part of the description string.
         *
         * @param dispString a function that gets the display string for a type.
         */
        private fun StringBuilder.appendDescription(dispString: TypeToken<*>.() -> String) {
            append(" with ")
            if (contextType != AnyToken) {
                append("?<${contextType.dispString()}>().")
            }
            append("? { ")
            if (argType != UnitToken) {
                append(argType.dispString())
                append(" -> ")
            }
            append("? }")
        }


        /**
         * Description using simple type names. The description is as close as possible to the code used to create this bind.
         */
        val bindDescription: String get() = "bind<${type.simpleDispString()}>(${ if (tag != null) "tag = \"$tag\"" else "" })"

        /**
         * Description using full type names. The description is as close as possible to the code used to create this bind.
         */
        val bindFullDescription: String get() = "bind<${type.fullDispString()}>(${ if (tag != null) "tag = \"$tag\"" else "" })"

        /**
         * Description using simple type names. The description is as close as possible to the code used to create this key.
         */
        val description: String get() = buildString {
            append(bindDescription)
            appendDescription(TypeToken<*>::simpleDispString)
        }

        val internalDescription: String get() = "(context: ${contextType.simpleDispString()}, arg: ${argType.simpleDispString()}, type: ${type.simpleDispString()}, tag: $tag)"

        /**
         * Description using full type names. The description is as close as possible to the code used to create this key.
         */
        val fullDescription: String get() = buildString {
            append(bindFullDescription)
            appendDescription(TypeToken<*>::fullDispString)
        }
    }

    /**
     * Defines a kodein DSL function
     */
    @DslMarker
    annotation class KodeinDsl

    /**
     * Base builder DSL interface that allows to define scoped and context bindings.
     *
     * @param C The context type.
     */
    interface BindBuilder<C> {
        /**
         * The context type that will be used by all bindings that are defined in this DSL context.
         */
        val contextType: TypeToken<C>

        /**
         * Used to define bindings with a context.
         *
         * @param C The context type.
         */
        interface WithContext<C> : BindBuilder<C> {
            /** @suppress */
            class Impl<C>(override val contextType: TypeToken<C>) : WithContext<C>
        }

        /**
         * Used to define bindings with a scope.
         *
         * @param C The scope's Context.
         */
        interface WithScope<C> : BindBuilder<C> {

            /**
             * The scope that will be used by all bindings that are defined in this DSL context.
             */
            val scope: Scope<C>

            /** @suppress */
            class Impl<C>(override val contextType: TypeToken<C>, override val scope: Scope<C>) : WithScope<C>
        }
    }

    /**
     * Allows for the DSL inside the block argument of the constructor of `Kodein` and `Kodein.Module`.
     *
     * Methods of this classes are really just proxies to the [KodeinContainer.Builder] methods.
     *
     * @property containerBuilder Every methods eventually ends up to a call to this builder.
     */
    @KodeinDsl
    interface Builder : BindBuilder.WithContext<Any?>, BindBuilder.WithScope<Any?> {

        val containerBuilder: KodeinContainer.Builder

        /**
         * Left part of the type-binding syntax (`bind(type, tag)`).
         *
         * @param T The type to bind.
         */
        interface TypeBinder<T : Any> {
            /**
             * Binds the previously given type and tag to the given binding.
             *
             * @param binding The binding to bind.
             * @throws OverridingException If this bindings overrides an existing binding and is not allowed to.
             */
            infix fun <C, A> with(binding: KodeinBinding<in C, in A, out T>)
        }

        /**
         * Left part of the direct-binding syntax (`bind(tag)`).
         */
        interface DirectBinder {
            /**
             * Binds the previously given tag to the given binding.
             *
             * The bound type will be the [KodeinBinding.createdType].
             *
             * @param binding The binding to bind.
             * @throws OverridingException If this bindings overrides an existing binding and is not allowed to.
             */
            infix fun <C, A, T: Any> from(binding: KodeinBinding<in C, in A, out T>)
        }

        /**
         * Left part of the constant-binding syntax (`constant(tag)`).
         *
         */
        interface ConstantBinder {
            /**
             * Binds the previously given tag to the given instance.
             *
             * @param T The type of value to bind.
             * @param value The instance to bind.
             * @param valueType The type to bind the instance to.
             * @throws OverridingException If this bindings overrides an existing binding and is not allowed to.
             */
            @Suppress("FunctionName")
            fun <T: Any> With(valueType: TypeToken<out T>, value: T)
        }

        /**
         * Starts the binding of a given type with a given tag.
         *
         * @param T The type of value to bind.
         * @param type The type to bind.
         * @param tag The tag to bind.
         * @param overrides Whether this bind **must** or **must not** override an existing binding.
         * @return The binder: call [TypeBinder.with]) on it to finish the binding syntax and register the binding.
         */
        @Suppress("FunctionName")
        fun <T : Any> Bind(type: TypeToken<out T>, tag: Any? = null, overrides: Boolean? = null): TypeBinder<T>

        /**
         * Starts a direct binding with a given tag. A direct bind does not define the type to be bound, the type will be defined according to the bound factory.
         *
         * @param tag The tag to bind.
         * @param overrides Whether this bind **must** or **must not** override an existing binding.
         * @return The binder: call [DirectBinder.from]) on it to finish the binding syntax and register the binding.
         */
        @Suppress("FunctionName")
        fun Bind(tag: Any? = null, overrides: Boolean? = null): DirectBinder

        /**
         * Starts a constant binding.
         *
         * @param tag The tag to bind.
         * @param overrides Whether this bind **must** or **must not** override an existing binding.
         * @return The binder: call `with` on it to finish the binding syntax and register the binding.
         */
        fun constant(tag: Any, overrides: Boolean? = null): ConstantBinder

        /**
         * Imports all bindings defined in the given [Kodein.Module] into this builder's definition.
         *
         * Note that modules are *definitions*, they will re-declare their bindings in each kodein instance you use.
         *
         * @param module The module object to import.
         * @param allowOverride Whether this module is allowed to override existing bindings.
         *                      If it is not, overrides (even explicit) will throw an [OverridingException].
         * @throws OverridingException If this module overrides an existing binding and is not allowed to
         *                             OR [allowOverride] is true while YOU don't have the permission to override.
         */
        fun import(module: Module, allowOverride: Boolean = false)

        /**
         * Like [import] but checks that will only import each module once.
         *
         * If the module has already been imported, nothing happens.
         *
         * Careful: this is checked by name. If two modules share the same name, only one will be imported!
         */
        fun importOnce(module: Module, allowOverride: Boolean = false)

        /**
         * Adds a callback that will be called once the Kodein object is configured and instantiated.
         *
         * @param cb The callback.
         */
        fun onReady(cb: DKodein.() -> Unit)

        fun RegisterContextTranslator(translator: ContextTranslator<*, *>)
    }

    /**
     * Builder to create a [Kodein] object.
     */
    interface MainBuilder : Builder {

        /**
         * The external source is repsonsible for fetching / creating a value when Kodein cannot find a matching binding.
         */
        var externalSource: ExternalSource?

        /**
         * Imports all bindings defined in the given [Kodein] into this builder.
         *
         * Note that this preserves scopes, meaning that a singleton-bound in the kodein argument will continue to exist only once.
         * Both kodein objects will share the same instance.
         *
         * Note that externalSource **will be overeridden** if defined in the extended Kodein.
         *
         * @param kodein The kodein object to import.
         * @param allowOverride Whether this module is allowed to override existing bindings.
         *   If it is not, overrides (even explicit) will throw an [OverridingException].
         * @param copy The copy specifications, that defines which bindings will be copied to the new container.
         *   All bindings from the extended container will be accessible in the new container, but only the copied bindings are able to access overridden bindings in this new container.
         *   By default, all bindings that do not hold references (e.g. not singleton or multiton) are copied.
         * @throws OverridingException If this kodein overrides an existing binding and is not allowed to
         *   OR [allowOverride] is true while YOU don't have the permission to override.
         */
        fun extend(kodein: Kodein, allowOverride: Boolean = false, copy: Copy = Copy.NonCached)

        /**
         * Imports all bindings defined in the given [Kodein] into this builder.
         *
         * Note that this preserves scopes, meaning that a singleton-bound in the kodein argument will continue to exist only once.
         * Both kodein objects will share the same instance.
         *
         * Note that externalSource **will be overeridden** if defined in the extended Kodein.
         *
         * @param dkodein The direct kodein object to import.
         * @param allowOverride Whether this module is allowed to override existing bindings.
         *   If it is not, overrides (even explicit) will throw an [OverridingException].
         * @param copy The copy specifications, that defines which bindings will be copied to the new container.
         *   All bindings from the extended container will be accessible in the new container, but only the copied bindings are able to access overridden bindings in this new container.
         *   By default, all bindings that do not hold references (e.g. not singleton or multiton) are copied.
         * @throws OverridingException If this kodein overrides an existing binding and is not allowed to
         *   OR [allowOverride] is true while YOU don't have the permission to override.
         */
        fun extend(dkodein: DKodein, allowOverride: Boolean = false, copy: Copy = Copy.NonCached)
    }

    /**
     * A module is constructed the same way as in [Kodein] is:
     *
     * ```kotlinprivate
     * val module = Kodein.Module {
     *     bind<DataSource>() with singleton { SqliteDS.open("path/to/file") }
     * }
     * ```
     *
     * @property name The name of this module (for debug).
     * @property allowSilentOverride Whether this module is allowed to non-explicit overrides.
     * @property init The block of configuration for this module.
     */
    data class Module(val name: String, val allowSilentOverride: Boolean = false, val prefix: String = "", val init: Builder.() -> Unit) {
        @Deprecated("You should name your modules, for debug purposes.", replaceWith = ReplaceWith("Module(\"module name\", allowSilentOverride, init)"), level = DeprecationLevel.WARNING)
        constructor(allowSilentOverride: Boolean = false, init: Builder.() -> Unit) : this("", allowSilentOverride, "", init)
    }

    /**
     * Every methods eventually ends up to a call to this container.
     */
    val container: KodeinContainer

    companion object {

        /**
         * Creates a [Kodein] instance.
         *
         * @param allowSilentOverride Whether the configuration block is allowed to non-explicit overrides.
         * @param init The block of configuration.
         * @return The new Kodein object, freshly created, and ready for hard work!
         */
        operator fun invoke(allowSilentOverride: Boolean = false, init: MainBuilder.() -> Unit): Kodein = KodeinImpl(allowSilentOverride, init)

        /**
         * Creates a [Kodein] instance that will be lazily created upon first access.
         *
         * @param allowSilentOverride Whether the configuration block is allowed to non-explicit overrides.
         * @param init The block of configuration.
         * @return A lazy property that will yield, when accessed, the new Kodein object, freshly created, and ready for hard work!
         */
        fun lazy(allowSilentOverride: Boolean = false, init: MainBuilder.() -> Unit): LazyKodein = LazyKodein { KodeinImpl(allowSilentOverride, init) }

        /**
         * Creates a direct [DKodein] instance that will be lazily created upon first access.
         *
         * @param allowSilentOverride Whether the configuration block is allowed to non-explicit overrides.
         * @param init The block of configuration.
         * @return The new DKodein object, freshly created, and ready for hard work!
         */
        fun direct(allowSilentOverride: Boolean = false, init: MainBuilder.() -> Unit): DKodein = KodeinImpl(allowSilentOverride, init).direct

        /**
         * Creates a Kodein object but without directly calling onReady callbacks.
         *
         * Instead, returns both the kodein instance and the callbacks.
         * Note that the returned kodein object should not be used before calling the callbacks.
         *
         * This is an **internal** function that exists primarily to prevent Kodein.global recursion.
         *
         * @param allowSilentOverride Whether the configuration block is allowed to non-explicit overrides.
         * @param init The block of configuration.
         * @return a Pair with the Kodein object, and the callbacks function to call.
         *   Note that you *should not* use the Kodein object before calling the callbacks function.
         */
        fun withDelayedCallbacks(allowSilentOverride: Boolean = false, init: MainBuilder.() -> Unit): Pair<Kodein, () -> Unit> = KodeinImpl.withDelayedCallbacks(allowSilentOverride, init)
    }

}
