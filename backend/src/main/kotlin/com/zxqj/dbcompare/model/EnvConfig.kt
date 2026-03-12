package com.zxqj.dbcompare.model

data class EnvConfig(
    var name: String = "",
    var separateCodePath: Boolean = false,
    var source: EnvDbInfo = EnvDbInfo(),
    var target: EnvDbInfo = EnvDbInfo(),
    var ignoreFields: List<String>? = null,
    var excludeTables: List<String>? = null,
    var ignoreDataTables: List<String>? = null,
    var specifiedPrimaryKeys: List<String>? = null,
    var excludeDataRows: List<String>? = null,
    var includeDataRows: List<String>? = null,
)

data class EnvDbInfo(
    var codePath: String = "",
    var composePath: String = "",
    var serviceName: String = "",
    var excludeInitSql: List<String>? = null,
    var gitRef: String? = null,
    var containerPrefix: String = "",
    var port: Int = 3306,
    var dbConfig: DbConfig = DbConfig()
)
