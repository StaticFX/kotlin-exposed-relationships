package org.statix

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import java.util.*

class DataClassGenerator(private val logger: KSPLogger, private val resolver: Resolver) {
    /**
     * Builds a data class based on the given Class Declaration and properties provided
     * @param entityClass class annotated with @Model
     * @param modelProperties other classed as property with @Model annotation
     * @param properties every other non ignored property
     */
    fun generateDataClass(
        entityClass: KSClassDeclaration,
        modelProperties: List<KSPropertyDeclaration>,
        properties: List<KSPropertyDeclaration>
    ): TypeSpec {
        val className = "${entityClass.simpleName.asString()}ModelDTO"

        val dataClassBuilder = TypeSpec.classBuilder(className)
            .addModifiers(KModifier.DATA)

        logger.info("Found ${modelProperties.size} model properties in data class $className")

        val constructor = FunSpec.constructorBuilder()
        val innerClass = TypeSpec.classBuilder("Relations").addModifiers(KModifier.INNER)

        modelProperties.forEach { buildModelProperty(it, innerClass, className, dataClassBuilder, constructor) }

        val genericProperties = properties - modelProperties.toSet()

        // Add properties to the data class
        genericProperties.forEach { property ->
            val propertyName = property.simpleName.asString()
            var propertyType = property.type.resolve().toTypeName() // Convert to TypeName for KSP

            logger.info("Found generic property $propertyType")

            if (propertyType.toString().startsWith("org.jetbrains.exposed.dao.id.EntityID")) {
                val genericType = (propertyType as ParameterizedTypeName).typeArguments[0]
                propertyType = genericType
            }

            constructor.addParameter(propertyName, propertyType)
            dataClassBuilder.addProperty(
                PropertySpec.builder(propertyName, propertyType).initializer(propertyName).build()
            )
        }

        val withFunction = FunSpec.builder("with")
            .addParameter("block", LambdaTypeName.get(receiver = ClassName("", "Relations"), returnType = UNIT))
            .returns(ClassName("", className))
            .addCode(CodeBlock.of("relations.block()\n"))
            .addCode(CodeBlock.of("return this"))
            .build()

        dataClassBuilder.addProperty(
            PropertySpec.builder("relations", ClassName("", "Relations"))
                .initializer(CodeBlock.of("Relations()"))
                .addModifiers(KModifier.PRIVATE)
                .build()
        )

        dataClassBuilder.addType(innerClass.build())
        dataClassBuilder.addFunction(withFunction)

        dataClassBuilder.primaryConstructor(constructor.build())
        return dataClassBuilder.build()
    }

    private fun buildModelProperty(
        property: KSPropertyDeclaration,
        relationsClass: TypeSpec.Builder,
        className: String,
        dataClass: TypeSpec.Builder,
        constructor: FunSpec.Builder
    ) {
        var declaredClass = property.type.resolve().declaration as KSClassDeclaration

        if (declaredClass.typeParameters.isNotEmpty()) {
            val arguments = property.type.resolve().arguments
            var resolvedGeneric = false

            for (argument in arguments) {
                val type = argument.type?.resolve()
                    ?: return logger.error("Type from class ${declaredClass.simpleName} is null!")

                logger.info(type.declaration.annotations.joinToString {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() ?: ""
                })

                if (isAnnotatedWithModel(type.declaration.annotations)) {

                    // in this case the model is actually wrapped inside another class for example
                    // SizedIterable<Model> the model can be retrieved through this logic

                    logger.info("Converting ${declaredClass.simpleName.asString()} to ${type.declaration.simpleName.asString()}")
                    declaredClass = type.declaration as KSClassDeclaration
                    declaredClass = getListOfDeclaredClass(declaredClass, resolver)
                    resolvedGeneric = true
                }
            }

            if (!resolvedGeneric) {
                logger.error("Class ${declaredClass.simpleName.asString()} is generic, but wrapped class is not a valid @Model. Please use the @Model annotation in relationships!")
                return
            }
        }

        if (declaredClass.annotations.none { it.shortName.asString() == "Model" }) {
            logger.error("Class ${declaredClass.simpleName.asString()} must be annotated with Model when used for a relationship!")
            return
        }

        val propertyClassName = declaredClass.simpleName.getShortName()

        val type = ClassName("", "${propertyClassName}ModelDTO")
        val propertyName = propertyClassName.replaceFirstChar { char -> char.lowercase(Locale.getDefault()) }

        val relationPropertyName = "${propertyName}Relation"
        val relationPropertyType = property.type.resolve().toTypeName()
        val relationReceiverType = ClassName("", type.simpleName)\

        val lazyRelationProperty = PropertySpec.builder(propertyName, type)
            .addModifiers(KModifier.PRIVATE)
            .delegate(CodeBlock.of("lazy { $relationPropertyName.toModel() }"))
            .build()

        relationsClass.addProperty(lazyRelationProperty)

        relationsClass.addFunction(
            FunSpec.builder(propertyName)
                .addParameter(
                    ParameterSpec
                        .builder(
                            "block",
                            LambdaTypeName.get(receiver = relationReceiverType, returnType = UNIT)
                        )
                        .defaultValue("{}")
                        .build()
                )
                .addCode(CodeBlock.of("this@$className.$propertyName = $propertyName \n"))
                .addCode(CodeBlock.of("$propertyName.apply(block)"))
                .build()
        )

        val relationProperty = ParameterSpec.builder(relationPropertyName, relationPropertyType)
            .addAnnotation(ClassName("kotlinx.serialization", "Transient"))

        constructor.addParameter(relationProperty.build())
        dataClass.addProperty(
            PropertySpec.builder(relationPropertyName, relationPropertyType).initializer(relationPropertyName)
                .addModifiers(KModifier.PRIVATE)
                .build()
        )

        val property = PropertySpec.builder(propertyName, type)
        property.mutable(true)
        property.addModifiers(KModifier.LATEINIT).addModifiers(KModifier.PRIVATE)

        dataClass.addProperty(property.build())
    }

    private fun getListOfDeclaredClass(declaredClass: KSClassDeclaration, resolver: Resolver): KSClassDeclaration {
        val listClassDeclaration = resolver.getClassDeclarationByName(resolver.getKSNameFromString("kotlin.collections.List"))!!

        val declaredClassTypeReference: KSTypeReference = declaredClass.asType()

        val parameterizedListType = listClassDeclaration.asType(listOf(typeArgument))

        return parameterizedListType.declaration as KSClassDeclaration
    }
}

fun createToModelExtensionFunction(entityClass: KSClassDeclaration, typeSpec: TypeSpec, modelProperties: List<KSPropertyDeclaration>, properties: List<KSPropertyDeclaration>): FunSpec {

    val returnTypeName = typeSpec.name!!

    val type = ClassName("", returnTypeName)

    val sortedProperties = modelProperties + (properties - modelProperties.toSet())

    val returnStatement = "return $returnTypeName(${sortedProperties.joinToString(", ") {
        if (it.type.resolve().declaration.qualifiedName?.asString()?.startsWith("org.jetbrains.exposed.dao.id.EntityID") == true) {
            "${it.simpleName.asString()}.value"
        } else {
            it.simpleName.asString()
        }
    }})"

    val function = FunSpec.builder("toModel")
        .receiver(entityClass.asStarProjectedType().toTypeName())
        .returns(type)
        .addStatement(returnStatement)


    return function.build()
}