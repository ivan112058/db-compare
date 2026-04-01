package com.zxqj.dbcompare.model.structure

data class IndexInfo(
    var type: String? = null, // PRIMARY, UNIQUE, INDEX
    var name: String? = null,
    var columns: List<String>? = null
) {
    override fun toString(): String {
        return "$name ($type) ON $columns"
    }
}
