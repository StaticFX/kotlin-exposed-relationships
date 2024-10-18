package org.statix

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toTypeName
import java.util.*

class DataClassGenerator(private val logger: KSPLogger) {
    fun generateDataClass(entityClass: KSClassDeclaration, modelProperties: List<KSPropertyDeclaration>, properties: List<KSPropertyDeclaration>): TypeSpec {
        val className = "${entityClass.simpleName.asString()}ModelDTO"

        val dataClassBuilder = TypeSpec.classBuilder(className)
            .addModifiers(KModifier.DATA)

        properties.forEach {
            logger.info("Found annotation for property ${it.simpleName.asString()} > ${it.type.resolve().declaration.annotations.joinToString { it.shortName.asString() }}")
        }

        logger.info("Found ${modelProperties.size} model properties in data class $className")

        val constructor = FunSpec.constructorBuilder()
        val innerClass = TypeSpec.classBuilder("Relations").addModifiers(KModifier.INNER)

        modelProperties.forEach {
            val declaredClass = it.type.resolve().declaration as KSClassDeclaration
            val propertyClassName = declaredClass.simpleName.getShortName()
            val type = ClassName("", "${propertyClassName}ModelDTO")
            val propertyName = propertyClassName.replaceFirstChar { char -> char.lowercase(Locale.getDefault()) }

            val relationPropertyName = "${propertyName}Relation"
            val relationPropertyType = it.type.resolve().toTypeName()
            val relationReceiverType = ClassName("", type.simpleName)

            val lazyRelationProperty = PropertySpec.builder(propertyName, type)
                .addModifiers(KModifier.PRIVATE)
                .delegate(CodeBlock.of("lazy { $relationPropertyName.toModel() }"))
                .build()

            innerClass.addProperty(lazyRelationProperty)

            innerClass.addFunction(FunSpec.builder(propertyName)
                .addParameter(
                    ParameterSpec
                        .builder("block",
                            LambdaTypeName.get(receiver = relationReceiverType, returnType = UNIT))
                        .defaultValue("{}")
                        .build())
                .addCode(CodeBlock.of("this@$className.$propertyName = $propertyName \n"))
                .addCode(CodeBlock.of("$propertyName.apply(block)"))
                .build())

            val relationProperty = ParameterSpec.builder(relationPropertyName, relationPropertyType)
                .addAnnotation(ClassName("kotlinx.serialization", "Transient"))

            constructor.addParameter(relationProperty.build())
            dataClassBuilder.addProperty(PropertySpec.builder(relationPropertyName, relationPropertyType).initializer(relationPropertyName).addModifiers(KModifier.PRIVATE)
                .build())

            val property = PropertySpec.builder(propertyName, type)
            property.mutable(true)
            property.addModifiers(KModifier.LATEINIT).addModifiers(KModifier.PRIVATE)

            dataClassBuilder.addProperty(property.build())
        }


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
            dataClassBuilder.addProperty(PropertySpec.builder(propertyName, propertyType).initializer(propertyName).build())
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
                .build())

        dataClassBuilder.addType(innerClass.build())
        dataClassBuilder.addFunction(withFunction)

        dataClassBuilder.primaryConstructor(constructor.build())
        return dataClassBuilder.build()
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