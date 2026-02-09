package com.zxqj.dbcompare.controller

import com.zxqj.dbcompare.model.CompareRequest
import com.zxqj.dbcompare.model.DbConfig
import com.zxqj.dbcompare.service.CompareService
import com.zxqj.dbcompare.service.DatabaseService
import com.zxqj.dbcompare.service.ResultCacheService
import com.zxqj.dbcompare.service.SqlGenerationService
import com.zxqj.dbcompare.model.TableDiff
import org.springframework.web.bind.annotation.*
import jakarta.servlet.http.HttpServletResponse

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = ["*"]) // Allow all for local dev
class CompareController(
    private val compareService: CompareService,
    private val dbService: DatabaseService,
    private val resultCacheService: ResultCacheService,
    private val sqlGenerationService: SqlGenerationService
) {

    @PostMapping("/connect/check")
    fun checkConnection(@RequestBody config: DbConfig): Map<String, Any> {
        return try {
            dbService.connect(config).use { _ ->
                mapOf("success" to true)
            }
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Unknown error"))
        }
    }

    @PostMapping("/compare")
    fun compare(@RequestBody request: CompareRequest): Map<String, Any> {
        return try {
            // Get the raw list of diffs instead of a map wrapper
            val result = compareService.compare(request)
            // Assuming compareService.compare returns mapOf("tables" to List<TableDiff>)
            // We need to verify what compareService.compare returns.
            // Based on previous search, it returns Map<String, Any> where "tables" is List<TableDiff>
            @Suppress("UNCHECKED_CAST")
            val diffs = result["tables"] as List<TableDiff>
            
            if (diffs.isEmpty()) {
                mapOf("success" to true)
            } else {
                val id = resultCacheService.saveResult(diffs)
                mapOf("success" to true, "id" to id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            mapOf("error" to (e.message ?: "Unknown error"))
        }
    }
    
    @GetMapping("/compare/{id}/tables")
    fun getCompareTables(@PathVariable id: String): Map<String, Any> {
        val diffs = resultCacheService.getResult(id) 
            ?: return mapOf("error" to "Result not found or expired")
            
        // Return summary list: name, hasDiff (structure or data or count)
        val summaries = diffs.map { diff ->
            val hasStructDiff = !(diff.structDiff?.isEmpty() ?: true)
            val hasDataDiff = diff.dataDiff?.let {
                !it.added.isNullOrEmpty() || !it.removed.isNullOrEmpty() || !it.modified.isNullOrEmpty()
            } ?: false

            mapOf(
                "tableName" to diff.tableName,
                "hasStructDiff" to hasStructDiff,
                "hasDataDiff" to hasDataDiff
            )
        }
        return mapOf("tables" to summaries)
    }
    
    @GetMapping("/compare/{id}/table/{tableName}")
    fun getTableDetail(@PathVariable id: String, @PathVariable tableName: String): Map<String, Any> {
        val diff = resultCacheService.getTableDiff(id, tableName)
            ?: return mapOf("error" to "Table diff not found")
            
        return mapOf("diff" to diff)
    }

    @GetMapping("/compare/{id}/table/{tableName}/sql")
    fun getTableSql(@PathVariable id: String, @PathVariable tableName: String): Map<String, Any> {
        val diff = resultCacheService.getTableDiff(id, tableName)
            ?: return mapOf("error" to "Table diff not found")
            
        val upgrade = sqlGenerationService.generateUpgradeSql(diff)
        val rollback = sqlGenerationService.generateRollbackSql(diff)
        
        return mapOf(
            "upgradeSql" to upgrade,
            "rollbackSql" to rollback
        )
    }

    @GetMapping("/compare/{id}/sql/download")
    fun downloadSql(
        @PathVariable id: String, 
        @RequestParam type: String,
        response: HttpServletResponse
    ) {
        val diffs = resultCacheService.getResult(id) 
        if (diffs == null) {
            response.sendError(404, "Result not found")
            return
        }
        
        val isUpgrade = type.equals("upgrade", ignoreCase = true)
        val fileName = if (isUpgrade) "upgrade.sql" else "rollback.sql"
        
        response.characterEncoding = "UTF-8"
        response.contentType = "application/sql; charset=UTF-8"
        response.setHeader("Content-Disposition", "attachment; filename=\"$fileName\"")
        
        response.writer.use { writer ->
            val fullSql = if (isUpgrade)
                sqlGenerationService.generateFullUpgradeScript(diffs)
            else
                sqlGenerationService.generateFullRollbackScript(diffs)
                
            if (fullSql.isNotBlank()) {
                writer.write(fullSql)
            }
        }
    }
}
