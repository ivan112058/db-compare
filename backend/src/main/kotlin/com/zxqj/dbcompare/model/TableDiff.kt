package com.zxqj.dbcompare.model

import com.zxqj.dbcompare.model.structure.StructureDiff

data class TableDiff(
    var tableName: String,
    var structDiff: StructureDiff? = null,
    var dataDiff: DataDiff? = null,
    var rowCount: RowCount? = null,
    var primaryKeys: List<String>? = null,
    var treeConfig: TreeConfig? = null,
    var sourceDDL: String? = null,
    var targetDDL: String? = null
) {
    data class DataDiff(
        var added: List<Map<String, Any?>>? = null,
        var removed: List<Map<String, Any?>>? = null,
        var modified: List<Map<String, Any?>>? = null
    )

    data class RowCount(
        var source: Int = 0,
        var target: Int = 0
    )

    data class TreeConfig(
        val idColumn: String,
        val parentIdColumn: String,
        val parentBusinessKeys: List<String>
    )
}
