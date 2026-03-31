package com.zxqj.dbcompare.service

import com.zxqj.dbcompare.model.TableDiff
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ResultCacheService {
    private val cache = ConcurrentHashMap<String, List<TableDiff>>()

    fun saveResult(diffs: List<TableDiff>): String {
        val id = UUID.randomUUID().toString()
        cache[id] = diffs
        return id
    }

    fun getResult(id: String): List<TableDiff>? = cache[id]

    fun getTableDiff(id: String, tableName: String): TableDiff? =
        cache[id]?.find { it.tableName == tableName }
}
