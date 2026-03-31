package com.zxqj.dbcompare.model

data class EnvConfig(
    val name: String = "",
    val separateCodePath: Boolean = false,
    val source: EnvDbInfo = EnvDbInfo(),
    val target: EnvDbInfo = EnvDbInfo(),
    val ignoreFields: List<String>? = null,
    val excludeTables: List<String>? = null,
    val ignoreDataTables: List<String>? = null,
    val specifiedPrimaryKeys: List<String>? = null,
    val excludeDataRows: List<String>? = null,
    val includeDataRows: List<String>? = null,
)

data class EnvDbInfo(
    val codePath: String = "",
    val composePath: String = "",
    val serviceName: String = "",
    val excludeInitSql: List<String>? = null,
    val gitRef: String? = null,
    val containerPrefix: String = "",
    val port: Int = 3306,
    val dbConfig: DbConfig = DbConfig()
)
