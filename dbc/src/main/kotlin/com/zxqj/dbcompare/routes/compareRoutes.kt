package com.zxqj.dbcompare.routes

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.zxqj.dbcompare.model.CompareRequest
import com.zxqj.dbcompare.model.DbConfig
import com.zxqj.dbcompare.model.TableDiff
import com.zxqj.dbcompare.service.CompareService
import com.zxqj.dbcompare.service.DatabaseService
import com.zxqj.dbcompare.service.ResultCacheService
import com.zxqj.dbcompare.service.SqlGenerationService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val objectMapper = ObjectMapper().findAndRegisterModules()

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
    post("/compare") {
        val form = call.receiveParameters()
        application.log.info("compare $form")

        val request = try {
            form.toCompareRequest()
        } catch (e: BadRequestException) {
            application.log.warn(e.message)
            call.respondDataStar {
                otToast(e.message ?: "config error", variant = ToastVariant.DANGER)
            }
            return@post
        }

        try {
            val diffs = compareService.compare(request)

            if (diffs.isEmpty()) {
                call.respondDataStar {
                    otToast("NO DIFF", variant = ToastVariant.SUCCESS)
                }
            } else {
                val id = resultCacheService.saveResult(diffs)
                call.respondDataStar {
                    executeScript("window.location.href = '/diff.html?id=$id'")
                }
            }
        } catch (e: Exception) {
            application.log.error("compare error", e)
            call.respondDataStar {
                otToast(e.message ?: "Compare failed", variant = ToastVariant.DANGER)
            }
        }
//        try {
//            val request = call.receive<CompareRequest>()
//            application.log.info("compare request $request")
//            val diffs = compareService.compare(request)
//
//            if (diffs.isEmpty()) {
//                call.respondDataStar {
//                    patchSignalsJson(
//                        mapOf(
//                            "loading" to false,
//                            "resultId" to "",
//                            "tables" to emptyList<TableSummary>(),
//                            "selectedTable" to null,
//                            "detail" to null,
//                            "upgradeSql" to "",
//                            "rollbackSql" to ""
//                        )
//                    )
//                    toast("No differences found", "info")
//                }
//            } else {
//                val id = resultCacheService.saveResult(diffs)
//                call.respondDataStar {
//                    patchSignalsJson(
//                        mapOf(
//                            "loading" to false,
//                            "resultId" to id,
//                            "tables" to diffs.toSummaries(),
//                            "selectedTable" to null,
//                            "detail" to null,
//                            "upgradeSql" to "",
//                            "rollbackSql" to "",
//                            "sqlType" to "upgrade",
//                            "view" to "result"
//                        )
//                    )
//                    toast("Comparison complete", "success")
//                }
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//            call.respondDataStar {
//                patchSignalsJson(mapOf("loading" to false))
//                toast(e.message ?: "Compare failed", "error")
//            }
//        }
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

fun Parameters.toCompareRequest(): CompareRequest {
    fun toDbConfig(name: String): DbConfig {
        return DbConfig(
            host = this["$name.host"]?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw BadRequestException("$name.host cannot be empty"),
            port = this["$name.port"]?.toIntOrNull() ?: throw BadRequestException("$name.port should be 1~65535"),
            username = this["$name.username"]?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw BadRequestException("$name.username cannot be empty"),
            password = this["$name.password"]?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw BadRequestException("$name.password cannot be empty"),
            database = this["$name.database"]?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw BadRequestException("$name.database cannot be empty")
        )
    }

    fun toStringList(name: String): List<String>? {
        val value = this[name] ?: return null
        return objectMapper.readValue<List<String>>(value)
    }

    return CompareRequest(
        source = toDbConfig("source"),
        target = toDbConfig("target"),
        ignoreFields = toStringList("ignoreFields"),
        excludeTables = toStringList("excludeTables"),
        ignoreDataTables = toStringList("ignoreDataTables"),
        specifiedPrimaryKeys = toStringList("specifiedPrimaryKeys"),
        treeTableConfig = toStringList("treeTableConfig"),
        excludeDataRows = toStringList("excludeDataRows"),
        includeDataRows = toStringList("includeDataRows")
    )
}