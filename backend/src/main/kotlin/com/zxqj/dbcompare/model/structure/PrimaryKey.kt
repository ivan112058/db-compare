package com.zxqj.dbcompare.model.structure

data class PrimaryKey(
    var name: String? = null,
    var columns: List<String>? = null
) {
    override fun toString(): String {
        return "$name (${columns?.joinToString(", ") ?: ""})"
    }
}
