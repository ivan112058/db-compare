package com.zxqj.dbcompare.model.structure

data class Column(
    var name: String? = null,
    var typeName: String? = null,
    var columnSize: Int = 0,
    var decimalDigits: Int = 0,
    var isNullable: Boolean = false,
    var isAutoIncrement: Boolean = false,
    var defaultValue: String? = null
) {
    override fun toString(): String {
        return String.format("%s %s(%d,%d) %s %s DEFAULT %s",
            name, typeName, columnSize, decimalDigits,
            if (isNullable) "NULL" else "NOT NULL",
            if (isAutoIncrement) "AUTO_INCREMENT" else "",
            defaultValue
        )
    }
}
