package com.zxqj.dbcompare.model.structure

data class Index(
    var name: String? = null,
    var isUnique: Boolean = false,
    var columns: List<String>? = null
) {
    override fun toString(): String {
        return String.format("%s (%s) ON (%s)",
            name, if (isUnique) "UNIQUE" else "INDEX", columns?.joinToString(", ") ?: ""
        )
    }
}
