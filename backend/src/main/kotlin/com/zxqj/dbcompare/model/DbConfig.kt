package com.zxqj.dbcompare.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class DbConfig(
    var host: String? = null,
    var port: Int = 0,
    var username: String? = null,
    var password: String? = null,
    var database: String? = null
) {
    @get:JsonIgnore
    val jdbcUrl: String
        get() = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai", host, port, database)
}
