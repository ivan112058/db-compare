package com.zxqj.dbcompare.model.structure

data class Index(
    val name: String? = null,
    val isUnique: Boolean = false,
    val columns: List<String>? = null
) {
    override fun toString(): String =
        "$name (${if (isUnique) "UNIQUE" else "INDEX"}) ON (${columns?.joinToString(", ") ?: ""})"
}
