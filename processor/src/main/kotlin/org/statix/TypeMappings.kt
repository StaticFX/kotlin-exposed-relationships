package org.statix

enum class TypeMappings(val type: String, val toPackage: String, val toClass: String, val transform: () -> String) {

    DATE_TIME("java.time.LocalDateTime", "kotlin", "String", { ".toString()" }),
    DATE("java.time.LocalDate", "kotlin", "String", { ".toString()" })

}