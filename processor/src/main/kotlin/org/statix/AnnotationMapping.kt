package org.statix

import kotlin.reflect.KClass

enum class AnnotationMapping(val annotationClass: KClass<out Annotation>) {


    HAS_ONE(statix.org.HasOne::class)

}