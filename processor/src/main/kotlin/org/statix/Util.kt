package org.statix

import com.google.devtools.ksp.symbol.KSAnnotation

fun isAnnotatedWithModel(annotations: Sequence<KSAnnotation>): Boolean {

    return annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() == "org.statix.Model" }
}