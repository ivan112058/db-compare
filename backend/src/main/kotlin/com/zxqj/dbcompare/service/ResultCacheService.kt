package com.zxqj.dbcompare.service

import com.zxqj.dbcompare.model.TableDiff
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

@Service
class ResultCacheService {
    private val cache = ConcurrentHashMap<String, List<TableDiff>>()

    fun saveResult(diffs: List<TableDiff>): String {
        val id = UUID.randomUUID().toString()
        cache[id] = diffs
        return id
    }

    fun getResult(id: String): List<TableDiff>? {
        return cache[id]
    }
    
    fun getTableDiff(id: String, tableName: String): TableDiff? {
        val diffs = cache[id] ?: return null
        return diffs.find { it.tableName == tableName }
    }
}
