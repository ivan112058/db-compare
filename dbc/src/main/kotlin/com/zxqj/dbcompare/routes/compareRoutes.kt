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

private data class TableSelectionPayload(
    val resultId: String = "",
    val tableName: String = ""
)

private data class TableSummary(
    val tableName: String,
    val hasStructDiff: Boolean,
    val hasDataDiff: Boolean
)

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
        try {
            val request = call.receive<CompareRequest>()
            val compareResult = compareService.compare(request)

            @Suppress("UNCHECKED_CAST")
            val diffs = compareResult["tables"] as List<TableDiff>

            if (diffs.isEmpty()) {
                call.respondDataStar {
                    patchSignalsJson(
                        mapOf(
                            "loading" to false,
                            "resultId" to "",
                            "tables" to emptyList<TableSummary>(),
                            "selectedTable" to null,
                            "detail" to null,
                            "upgradeSql" to "",
                            "rollbackSql" to ""
                        )
                    )
                    toast("No differences found", "info")
                }
            } else {
                val id = resultCacheService.saveResult(diffs)
                call.respondDataStar {
                    patchSignalsJson(
                        mapOf(
                            "loading" to false,
                            "resultId" to id,
                            "tables" to diffs.toSummaries(),
                            "selectedTable" to null,
                            "detail" to null,
                            "upgradeSql" to "",
                            "rollbackSql" to "",
                            "sqlType" to "upgrade",
                            "view" to "result"
                        )
                    )
                    toast("Comparison complete", "success")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            call.respondDataStar {
                patchSignalsJson(mapOf("loading" to false))
                toast(e.message ?: "Compare failed", "error")
            }
        }
    }

    post("/compare/table") {
        val payload = call.receive<TableSelectionPayload>()
        val diff = resultCacheService.getTableDiff(payload.resultId, payload.tableName)
        if (diff == null) {
            call.respondDataStar {
                patchSignalsJson(mapOf("loading" to false))
                toast("Table diff not found", "error")
            }
            return@post
        }
        val upgrade = sqlGenerationService.generateUpgradeSql(diff)
        val rollback = sqlGenerationService.generateRollbackSql(diff)
        call.respondDataStar {
            patchSignalsJson(
                mapOf(
                    "loading" to false,
                    "selectedTable" to payload.tableName,
                    "detail" to diff,
                    "upgradeSql" to upgrade,
                    "rollbackSql" to rollback,
                    "sqlType" to "upgrade"
                )
            )
        }
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

private fun List<TableDiff>.toSummaries(): List<TableSummary> =
    map { diff ->
        TableSummary(
            tableName = diff.tableName,
            hasStructDiff = !(diff.structDiff?.isEmpty() ?: true),
            hasDataDiff = diff.dataDiff?.let {
                !it.added.isNullOrEmpty() || !it.removed.isNullOrEmpty() || !it.modified.isNullOrEmpty()
            } ?: false
        )
    }
