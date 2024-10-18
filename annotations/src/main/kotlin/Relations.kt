package statix.org

import org.jetbrains.exposed.dao.Entity
import kotlin.reflect.KClass


@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class HasOne(val model: KClass<out Entity<*>>, val localKey: String, val foreignKey: String)

annotation class HasMany()