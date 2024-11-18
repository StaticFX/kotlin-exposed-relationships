package org.statix

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import java.util.*
import kotlin.math.log

class DataClassGenerator(private val logger: KSPLogger, entityClass: KSClassDeclaration, private val modelPackage: String, private val generatedPackage: String) {
    private val dataClassName = "${entityClass.simpleName.asString()}ModelDTO"
    private val dataClassBuilder = TypeSpec.classBuilder(dataClassName).addModifiers(KModifier.DATA)
        .addAnnotation(AnnotationSpec.builder(ClassName("kotlinx.serialization", "Serializable")).build())
    private val dataClassConstructor = FunSpec.constructorBuilder()

    private val innerRelationsClass = TypeSpec.classBuilder("Relations").addModifiers(KModifier.INNER)

    /**
     * Builds a data class based on the given Class Declaration and properties provided
     * @param properties every other non ignored property
     */
    fun generateDataClass(
        properties: List<KSPropertyDeclaration>
    ): TypeSpec {

        val modelProperties = properties.filter { isModelProperty(it) }
        val genericProperties = properties - modelProperties.toSet()

        // Add properties to the data class
        genericProperties.forEach { buildProperty(it) }
        modelProperties.forEach { buildModelProperty(it) }

        buildWithFunction()
        buildRelationsClassProperty()
        buildAttributeProperty()

        dataClassBuilder.addType(innerRelationsClass.build())
        dataClassBuilder.primaryConstructor(dataClassConstructor.build())

        return dataClassBuilder.build()
    }

    private fun buildRelationsClassProperty() {
        dataClassBuilder.addProperty(
            PropertySpec.builder("relations", ClassName("", "Relations"))
                .initializer(CodeBlock.of("Relations()"))
                .addModifiers(KModifier.PRIVATE)
                .addAnnotation(ClassName("kotlinx.serialization", "Transient"))
                .build()
        )
    }

    private fun buildAttributeProperty() {
        val name = "attributes"

        val anyType = ClassName("kotlinx.serialization.json","JsonElement")

        val type = MUTABLE_MAP
            .parameterizedBy(String::class.asClassName(), anyType)

        val property = PropertySpec.builder(name, type)
            .initializer("mutableMapOf()").build()

        dataClassBuilder.addProperty(property)
    }

    private fun buildWithFunction() {
        val withFunction = FunSpec.builder("with")
            .addModifiers(KModifier.SUSPEND)
            .addParameter("block", LambdaTypeName.get(receiver = ClassName("", "Relations"), returnType = UNIT).copy(suspending = true))
            .returns(ClassName(generatedPackage, dataClassName))
            .addCode(CodeBlock.of("relations.block()\n"))
            .addCode(CodeBlock.of("return this"))
            .build()

        dataClassBuilder.addFunction(withFunction)
    }

    private fun buildProperty(property: KSPropertyDeclaration) {
        val propertyName = property.simpleName.asString()
        var propertyType = property.type.resolve().toTypeName() // Convert to TypeName for KSP

        if (propertyType.toString().startsWith("org.jetbrains.exposed.dao.id.EntityID")) {
            val genericType = (propertyType as ParameterizedTypeName).typeArguments[0]
            propertyType = genericType
        }

        dataClassConstructor.addParameter(propertyName, propertyType)
        dataClassBuilder.addProperty(
            PropertySpec.builder(propertyName, propertyType).initializer(propertyName).build()
        )
    }

    private fun buildModelProperty(
        property: KSPropertyDeclaration,
    ) {
        var declaredClass = property.type.resolve().declaration as KSClassDeclaration

        if (declaredClass.typeParameters.isNotEmpty()) {
            // handle SizedIterable relations
            val arguments = property.type.resolve().arguments

            if (!genericsIncludeModel(arguments)) {
                logger.error("Class ${declaredClass.simpleName.asString()} is generic, but generic types to not have Model annotation")
                return
            }

            declaredClass = receiveModelAnnotatedGenerics(arguments).first()
            buildMultiRelationProperty(property, declaredClass)
            return
        }

        if (declaredClass.annotations.none { it.shortName.asString() == "Model" }) {
            logger.error("Class ${declaredClass.simpleName.asString()} must be annotated with Model when used for a relationship!")
            return
        }

        buildSingleRelationProperty(property)
    }

    private fun buildSingleRelationProperty(property: KSPropertyDeclaration) {
        val propertyClassName = property.simpleName.asString()

        val qualifiedName = property.type.resolve().declaration.qualifiedName?.getShortName() ?: return logger.error("Error while resolving type for property")
        val type = ClassName(modelPackage, qualifiedName)

        val propertyName = propertyClassName.replaceFirstChar { char -> char.lowercase(Locale.getDefault()) }

        buildRelationProperty(propertyName, type, type.simpleName,false)
    }

    private fun buildMultiRelationProperty(property: KSPropertyDeclaration, mappedClass: KSClassDeclaration) {
        val propertyClassName = property.simpleName.getShortName()

        val className = mappedClass.toClassName()

        val type = List::class.asTypeName().parameterizedBy(className)

        val propertyName = propertyClassName.replaceFirstChar { char -> char.lowercase(Locale.getDefault()) }

        buildRelationProperty(propertyName, type, mappedClass.toClassName().simpleName, true)
    }

    private fun buildRelationProperty(name: String, type: TypeName, pureType: String, isMulti: Boolean) {
        val resolvedRelationType = resolveRelationType(pureType, isMulti)
        val relationReceiverType = resolveReceiverRelationType(pureType)

        val relationPropertyName = "${name}Relation"

        val lazyRelationProperty = buildLazyRelationProperty(name, resolvedRelationType, relationPropertyName, isMulti)
        innerRelationsClass.addProperty(lazyRelationProperty)

        val innerRelationFunction = buildInnerRelationFunction(name, relationReceiverType, name, isMulti)
        innerRelationsClass.addFunction(innerRelationFunction)

        val relationProperty = ParameterSpec.builder(relationPropertyName, type.copy(nullable = true))
            .addAnnotation(ClassName("kotlinx.serialization", "Transient"))
            .defaultValue("%L", "null")

        dataClassConstructor.addParameter(relationProperty.build())
        dataClassBuilder.addProperty(
            PropertySpec.builder(relationPropertyName, type.copy(nullable = true)).initializer(relationPropertyName)
                .addModifiers(KModifier.PRIVATE)
                .initializer(relationPropertyName)
                .build()
        )

        val lateInitRelationProperty = PropertySpec.builder(name, resolvedRelationType.copy(nullable = true))
            .mutable(true)
            .initializer("null")
            .build()

        val serializedProperty = buildSerializedRelationProperty(name, resolvedRelationType, isMulti)

        //dataClassBuilder.addProperty(serializedProperty)
        dataClassBuilder.addProperty(lateInitRelationProperty)
    }

    private fun buildSerializedRelationProperty(name: String, resolvedRelationType: TypeName, isMulti: Boolean): PropertySpec {
        return PropertySpec.builder("serialized${name.replaceFirstChar { it.uppercase() }}", resolvedRelationType.copy(nullable = true))
            .mutable(false)
            .getter(FunSpec.getterBuilder().addStatement("return if (::$name.isInitialized) $name else null").build())
            .addAnnotation(AnnotationSpec.builder(SerialName::class).addMember(""""$name"""").build())
            .build()
    }

    private fun resolveRelationType(type: String, isMulti: Boolean): TypeName {
        if (isMulti) {
            return List::class.asTypeName().parameterizedBy(ClassName(generatedPackage, "${type}ModelDTO"))
        }

        return ClassName(generatedPackage, "${type}ModelDTO")
    }

    private fun resolveReceiverRelationType(type: String): TypeName {
        return ClassName(generatedPackage, "${type}ModelDTO.Relations")
    }

    private fun buildLazyRelationProperty(propertyName: String, propertyType: TypeName, relationPropertyName: String, isMulti: Boolean): PropertySpec {
        return PropertySpec.builder(propertyName, propertyType)
            .addModifiers(KModifier.PRIVATE)
            .delegate(CodeBlock.of("lazy { ${if (isMulti) "$relationPropertyName!!.map { it.toModel() }" else "$relationPropertyName!!.toModel()" }}"))
            .build()
    }

    private fun buildInnerRelationFunction(functionName: String, lambdaReceiver: TypeName, relationPropertyName: String, isMulti: Boolean): FunSpec {
        val applyCodeBlock = if (isMulti) "$relationPropertyName.forEach { it.with(block) }" else "$relationPropertyName.with { block() }"
        val codeBlock = """
            dbQuery {
                this@$dataClassName.$relationPropertyName = $relationPropertyName
            }
            $applyCodeBlock
        """.trimIndent()


        return FunSpec.builder(functionName)
            .addModifiers(KModifier.SUSPEND)
            .addParameter(
                ParameterSpec
                    .builder(
                        "block",
                        LambdaTypeName.get(receiver = lambdaReceiver, returnType = UNIT).copy(suspending = true)
                    )
                    .defaultValue("{}")
                    .build()
            )
            .addCode(codeBlock)
            .build()
    }
}

fun createToModelExtensionFunction(entityClass: KSClassDeclaration, returns: TypeSpec, properties: List<KSPropertyDeclaration>, packageName: String): FunSpec {

    val returnTypeName = returns.name!!
    val type = ClassName(packageName, returnTypeName)

    val modelProperties = properties.filter { isModelProperty(it) }
    val genericProperties = properties - modelProperties.toSet()

    val modelsCodeblock = modelProperties.joinToString { if (genericsIncludeModel(it.type.resolve().arguments)) "${it.simpleName.asString()}.toList()" else it.simpleName.asString() }

    val propertiesCodeBlock = genericProperties.joinToString(", ") { if (isEntityProperty(it)) "this@toModel.${it.simpleName.asString()}.value" else it.simpleName.asString() }

    val combinedCodeBlock = "($propertiesCodeBlock${ if (propertiesCodeBlock.isNotEmpty() && modelsCodeblock.isNotEmpty()) ", " else "" }$modelsCodeblock)"

    val returnStatement = "return transaction { $returnTypeName$combinedCodeBlock }"

    val function = FunSpec.builder("toModel")
        .receiver(entityClass.asStarProjectedType().toTypeName())
        .returns(type)
        .addStatement(returnStatement)


    return function.build()
}