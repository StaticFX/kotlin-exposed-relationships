package org.statix

import com.google.devtools.ksp.isConstructor
import statix.org.HasOne
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import kotlin.math.log

class RelationAnnotationProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(HasOne::class.qualifiedName!!).filterIsInstance<KSPropertyDeclaration>()

        symbols.forEach { property ->
            if (!property.validate()) return@forEach
            val containingClass = property.parentDeclaration as? KSClassDeclaration
            val annotation = property.annotations
                .firstOrNull { it.shortName.asString() == "HasOne" }

            annotation?.let { annotation ->
                val modelClass = annotation.arguments
                    .firstOrNull { it.name?.asString() == "model" }?.value as? KSType

                if (containingClass != null && modelClass != null) {
                    validateRelation(containingClass, modelClass, property)
                }
            }
        }

        return emptyList()
    }


    private fun validateRelation(
        containingClass: KSClassDeclaration,
        modelClass: KSType,
        property: KSPropertyDeclaration
    ) {
        if (!isSubclassOfEntity(containingClass)) {
            logger.error("${containingClass.simpleName.asString()} must extend Entity<*> to use @HasOne", property)
            return
        }

        logger.info("Valid relation detected between ${containingClass.simpleName.asString()} and ${modelClass.declaration.simpleName.asString()}")
    }

    private fun isSubclassOfEntity(classDeclaration: KSClassDeclaration): Boolean {
        return isEntityClassRecursive(classDeclaration)
    }

    private fun isEntityClassRecursive(classDeclaration: KSClassDeclaration): Boolean {
        classDeclaration.superTypes.forEach { superType ->
            val resolvedType = superType.resolve()
            val qualifiedName = resolvedType.declaration.qualifiedName?.asString()

            // Check if this class is Entity<*>
            if (qualifiedName == "org.jetbrains.exposed.dao.Entity") {
                return true
            }

            // Recursively check the superclasses of this class
            val superClassDeclaration = resolvedType.declaration as? KSClassDeclaration
            if (superClassDeclaration != null && isEntityClassRecursive(superClassDeclaration)) {
                return true
            }
        }
        return false
    }
}

class RelationAnnotationProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return RelationAnnotationProcessor(environment.codeGenerator, environment.logger)
    }
}