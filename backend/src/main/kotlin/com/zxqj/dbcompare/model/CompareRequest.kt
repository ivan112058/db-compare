package com.zxqj.dbcompare.model

data class CompareRequest(
    var source: DbConfig? = null,
    var target: DbConfig? = null,
    var ignoreFields: List<String>? = null,
    var excludeTables: List<String>? = null,
    var ignoreDataTables: List<String>? = null,
    var specifiedPrimaryKeys: List<String>? = null,
    var excludeDataRows: List<String>? = null,
    var includeDataRows: List<String>? = null,
    var specifiedDataQueries: List<String>? = null
)
