package com.zxqj.dbcompare.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class EnvConfig(
    val projectName: String,
    val separateCodePath: Boolean = false,
    val sameDBConfig: Boolean = false,
    val source: EnvDbInfo = EnvDbInfo(),
    val target: EnvDbInfo = EnvDbInfo()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EnvDbInfo(
    val composePath: String = "",
    val codePath: String = "",
    val gitref: String? = null,
    val prefix: String = "",
    val port: Int = 3306,
    val service: String = "",
    val excludeInitSql: List<String>? = null,
    val dbConfig: DbConfig = DbConfig()
)
