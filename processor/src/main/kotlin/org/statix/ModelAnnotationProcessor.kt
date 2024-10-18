package org.statix

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.validate
import statix.org.Model
import kotlin.math.log

class ModelAnnotationProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
): SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(Model::class.qualifiedName!!).filterIsInstance<KSClassDeclaration>()

        logger.info("Found ${symbols.count()} symbols")

        symbols.forEach {
            if (!it.validate()) return@forEach
            val containingClass = it as? KSClassDeclaration ?: return@forEach

            val properties = retrieveProperties(containingClass)
            val modelProperties = properties.filter {
                it.type.resolve().declaration.annotations.any { annotation -> annotation.shortName.asString() == "Model" }
            }

            if (properties.isEmpty()) {
                logger.error("@Model annotation of class ${containingClass.simpleName.asString()} must have at least one not relation attribute to be a valid model.")
                return@forEach
            }

            logger.info("Received ${properties.size} properties")

            val dataClassGenerator = DataClassGenerator(logger)

            val dataClass = dataClassGenerator.generateDataClass(containingClass, modelProperties, properties)
            val packageName = containingClass.packageName.asString()

            val extensionFunction = createToModelExtensionFunction(containingClass, dataClass, modelProperties, properties)

            codeGenerator.createNewFile(Dependencies(false), packageName, "${containingClass.simpleName.asString()}DTO", "kt").use {
                it.write(dataClass.toString().toByteArray())
                it.write(extensionFunction.toString().toByteArray())
            }
        }

        return emptyList()
    }

    private fun retrieveProperties(classDeclaration: KSClassDeclaration): List<KSPropertyDeclaration> {
        return classDeclaration.getDeclaredProperties().filter {
            it.annotations.none { annotation -> AnnotationMapping.entries.any { it.annotationClass.simpleName == annotation.shortName.asString() } }
        }.filter {
            it.annotations.none { annotation -> annotation.shortName.asString() == "ModelIgnore"  }
        }.toList()

    }
}

class ModelAnnotationProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ModelAnnotationProcessor(environment.codeGenerator, environment.logger)
    }
}