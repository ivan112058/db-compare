package com.zxqj.dbcompare.routes

import com.zxqj.dbcompare.model.CompareRequest
import com.zxqj.dbcompare.model.DbConfig
import com.zxqj.dbcompare.model.TableDiff
import com.zxqj.dbcompare.service.CompareService
import com.zxqj.dbcompare.service.DatabaseService
import com.zxqj.dbcompare.service.ResultCacheService
import com.zxqj.dbcompare.service.SqlGenerationService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.compareRoutes(
    compareService: CompareService,
    dbService: DatabaseService,
    resultCacheService: ResultCacheService,
    sqlGenerationService: SqlGenerationService
) {
    post("/connect/check") {
        val config = call.receive<DbConfig>()
        val result = try {
            dbService.connect(config).use { mapOf("success" to true) }
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Unknown error"))
        }
        call.respond(result)
    }

    post("/compare") {
        val result = try {
            val request = call.receive<CompareRequest>()
            val compareResult = compareService.compare(request)

            @Suppress("UNCHECKED_CAST")
            val diffs = compareResult["tables"] as List<TableDiff>

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
        call.respond(result)
    }

    get("/compare/{id}/tables") {
        val id = call.parameters["id"]!!
        val diffs = resultCacheService.getResult(id)
            ?: return@get call.respond(mapOf("error" to "Result not found or expired"))

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
        call.respond(mapOf("tables" to summaries))
    }

    get("/compare/{id}/table/{tableName}") {
        val id = call.parameters["id"]!!
        val tableName = call.parameters["tableName"]!!
        val diff = resultCacheService.getTableDiff(id, tableName)
            ?: return@get call.respond(mapOf("error" to "Table diff not found"))
        call.respond(mapOf("diff" to diff))
    }

    get("/compare/{id}/table/{tableName}/sql") {
        val id = call.parameters["id"]!!
        val tableName = call.parameters["tableName"]!!
        val diff = resultCacheService.getTableDiff(id, tableName)
            ?: return@get call.respond(mapOf("error" to "Table diff not found"))

        val upgrade = sqlGenerationService.generateUpgradeSql(diff)
        val rollback = sqlGenerationService.generateRollbackSql(diff)
        call.respond(mapOf("upgradeSql" to upgrade, "rollbackSql" to rollback))
    }

    get("/compare/{id}/sql/download") {
        val id = call.parameters["id"]!!
        val type = call.request.queryParameters["type"] ?: "upgrade"
        val diffs = resultCacheService.getResult(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, "Result not found")

        val isUpgrade = type.equals("upgrade", ignoreCase = true)
        val fileName = if (isUpgrade) "upgrade.sql" else "rollback.sql"
        val fullSql = if (isUpgrade)
            sqlGenerationService.generateFullUpgradeScript(diffs)
        else
            sqlGenerationService.generateFullRollbackScript(diffs)

        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, fileName).toString()
        )
        call.respondText(fullSql, ContentType.Application.OctetStream)
    }
}
