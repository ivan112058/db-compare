package com.zxqj.dbcompare.model.structure

data class Column(
    val name: String? = null,
    val typeName: String? = null,
    val columnSize: Int = 0,
    val decimalDigits: Int = 0,
    val isNullable: Boolean = false,
    val isAutoIncrement: Boolean = false,
    val defaultValue: String? = null
) {
    override fun toString(): String =
        "$name $typeName($columnSize,$decimalDigits) ${if (isNullable) "NULL" else "NOT NULL"} ${if (isAutoIncrement) "AUTO_INCREMENT" else ""} DEFAULT $defaultValue"
}
