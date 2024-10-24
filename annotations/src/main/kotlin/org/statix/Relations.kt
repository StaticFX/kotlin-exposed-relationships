package org.statix

// Annotation for HasMany relationship
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class HasMany()

// Annotation for BelongsTo relationship
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class BelongsTo()

// Annotation for HasOne relationship
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class HasOne()

// Annotation for BelongsToMany relationship
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class BelongsToMany()
