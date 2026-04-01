package com.zxqj.dbcompare.model.structure

data class PrimaryKey(
    val name: String? = null,
    val columns: List<String>? = null
) {
    override fun toString(): String = "$name (${columns?.joinToString(", ") ?: ""})"
}
