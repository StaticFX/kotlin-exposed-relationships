package org.statix

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.writeTo

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

            logger.info(containingClass.getAllProperties().joinToString { it.simpleName.asString() })

            val properties = retrieveProperties(containingClass).toMutableList()
            val idProperty = findIDProperty(containingClass) ?: return@forEach logger.error("ID key not found in model, but is required!")

            properties += idProperty

            if (properties.isEmpty()) {
                logger.error("@Model annotation of class ${containingClass.simpleName.asString()} must have at least one not relation attribute to be a valid model.")
                return@forEach
            }

            logger.info("Received ${properties.size} properties")

            val packageName = containingClass.packageName.asString()
            val generatedPackageName = "${containingClass.packageName.asString()}.generated"

            val dataClassGenerator = DataClassGenerator(logger, containingClass, packageName, generatedPackageName)

            val dataClass = dataClassGenerator.generateDataClass(properties)

            val extensionFunction = createToModelExtensionFunction(containingClass, dataClass, properties, generatedPackageName)

            val dataClassSpec = FileSpec.builder(generatedPackageName, "${containingClass.simpleName.asString()}DTO")
                .addType(dataClass)
                .addImport("statix.org.generated", "dbQuery")
                .build()

            val extensionFileSpec = FileSpec.builder(generatedPackageName, "${containingClass.simpleName.asString()}Extensions")
                .addFunction(extensionFunction)
                .addImport("org.jetbrains.exposed.sql.transactions", "transaction")

                .build()

            dataClassSpec.writeTo(codeGenerator, Dependencies(false))
            extensionFileSpec.writeTo(codeGenerator, Dependencies(false))
        }

        return emptyList()
    }

    override fun finish() {
        val utilityClass = FileSpec.builder("statix.org.generated", "Utilities")
            .addImport("kotlinx.coroutines", "Dispatchers")
            .addImport("org.jetbrains.exposed.sql.transactions.experimental", "newSuspendedTransaction")
            .addFunction(buildSuspendDBQueryFunction())
            .build()

        utilityClass.writeTo(codeGenerator, Dependencies(true))
    }

    private fun retrieveProperties(classDeclaration: KSClassDeclaration): List<KSPropertyDeclaration> {
        return classDeclaration.getDeclaredProperties().filter {
            it.annotations.none { annotation -> annotation.shortName.asString() == "ModelIgnore"  }
        }.toList()
    }

    private fun findIDProperty(classDeclaration: KSClassDeclaration): KSPropertyDeclaration? {
        return classDeclaration.getAllProperties().find { it.simpleName.asString() == "id" }
    }
}

class ModelAnnotationProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ModelAnnotationProcessor(environment.codeGenerator, environment.logger)
    }
}
