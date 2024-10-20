package org.statix

import statix.org.org.statix.HasOne
import kotlin.reflect.KClass

enum class AnnotationMapping(val annotationClass: KClass<out Annotation>) {


    HAS_ONE(HasOne::class)

}