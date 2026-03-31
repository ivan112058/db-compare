package com.zxqj.dbcompare.model

data class CompareRequest(
    val source: DbConfig? = null,
    val target: DbConfig? = null,
    val ignoreFields: List<String>? = null,
    val excludeTables: List<String>? = null,
    val ignoreDataTables: List<String>? = null,
    val specifiedPrimaryKeys: List<String>? = null,
    val treeTableConfig: List<String>? = null,
    val excludeDataRows: List<String>? = null,
    val includeDataRows: List<String>? = null
)
