package org.statix

import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*

fun isAnnotatedWithModel(annotations: Sequence<KSAnnotation>): Boolean {
    return annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() == "org.statix.Model" }
}

fun isModelProperty(property: KSPropertyDeclaration): Boolean {
    val declaration = property.type.resolve().declaration as KSClassDeclaration

    if (isAnnotatedWithModel(declaration.annotations)) return true

    if (declaration.typeParameters.isNotEmpty()) {
        val ksType = property.type.resolve()

        for (argument in ksType.arguments) {
            val resolvedType = argument.type?.resolve()

            if (resolvedType != null) {
                val genericClass = resolvedType.declaration as KSClassDeclaration
                return isAnnotatedWithModel(genericClass.annotations)
            }
        }
    }
    return false
}

fun isEntityProperty(property: KSPropertyDeclaration): Boolean {
    return property.type.resolve().declaration.qualifiedName?.asString()?.startsWith("org.jetbrains.exposed.dao.id.EntityID") ?: false
}

fun genericsIncludeModel(generics: List<KSTypeArgument>): Boolean {
    for (argument in generics) {
        if (isGenericModel(argument))
            return true
    }

    return false
}

private fun isGenericModel(argument: KSTypeArgument): Boolean {
    val type = argument.type?.resolve() ?: return false
    return isAnnotatedWithModel(type.declaration.annotations)
}

fun receiveModelAnnotatedGenerics(generics: List<KSTypeArgument>): List<KSClassDeclaration> {
    return generics.filter { isGenericModel(it) }.map { it.type!!.resolve().declaration as KSClassDeclaration }
}

fun buildSuspendDBQueryFunction(): FunSpec {

    val lambda = LambdaTypeName.get(
        receiver = null,
        returnType = TypeVariableName("T")).copy(suspending = true)

    return FunSpec.builder("dbQuery")
        .addTypeVariable(TypeVariableName("T"))
        .addModifiers(KModifier.SUSPEND)
        .returns(TypeVariableName("T"))
        .addParameter(
            ParameterSpec.builder(
                "block", lambda
            ).build()
        )
        .addCode(                              // Add the function body
            """
            return newSuspendedTransaction(Dispatchers.IO) {
                block()
            }
            """.trimIndent()
        )
        .build()
}