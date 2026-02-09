package com.zxqj.dbcompare

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.runApplication

@SpringBootApplication(exclude = [DataSourceAutoConfiguration::class])
class DbCompareApplication

fun main(args: Array<String>) {
    runApplication<DbCompareApplication>(*args)
}
