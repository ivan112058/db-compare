package com.zxqj.dbcompare.model

data class EnvConfig(
    val separateCodePath: Boolean = false,
    val sameDBConfig: Boolean = false,
    val source: EnvDbInfo = EnvDbInfo(),
    val target: EnvDbInfo = EnvDbInfo()
)

data class EnvDbInfo(
    val composePath: String = "",
    val codePath: String = "",
    val gitref: String? = null,
    val prefix: String = "",
    val port: Int = 3306,
    val service: String = "",
    val dbConfig: DbConfig = DbConfig()
)
