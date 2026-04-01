package com.zxqj.dbcompare.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class DbConfig(
    val host: String? = null,
    val port: Int = 0,
    val username: String? = null,
    val password: String? = null,
    val database: String? = null
) {
    @get:JsonIgnore
    val jdbcUrl: String
        get() = "jdbc:mysql://$host:$port/$database?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
}
