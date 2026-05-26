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

private data class TableSummary(
    val tableName: String,
    val hasStructDiff: Boolean,
    val hasDataDiff: Boolean
)

fun Route.compareRoutes(
    compareService: CompareService,
    dbService: DatabaseService,
    resultCacheService: ResultCacheService,
    sqlGenerationService: SqlGenerationService,
    compare: (CompareRequest) -> List<TableDiff> = compareService::compare
) {
    suspend fun ApplicationCall.respondCompareTables(id: String?) {
        if (id.isNullOrBlank()) {
            respondDataStar {
                otToast("Missing result ID", variant = ToastVariant.DANGER)
            }
            return
        }

        val diffs = resultCacheService.getResult(id)
        if (diffs == null) {
            respondDataStar {
                otToast("Result not found", variant = ToastVariant.DANGER)
            }
            return
        }

        val summaries = diffs.toSummaries()
        val structCount = summaries.count { it.hasStructDiff }
        val dataCount = summaries.count { it.hasDataDiff }
        val bothCount = summaries.count { it.hasStructDiff && it.hasDataDiff }

        respondDataStar {
            patchSignalsJson(
                mapOf(
                    "_tables" to summaries,
                    "_structCount" to structCount,
                    "_dataCount" to dataCount,
                    "_bothCount" to bothCount,
                    "_selectedTable" to "",
                    "_detail" to null,
                    "_upgradeSql" to "",
                    "_rollbackSql" to ""
                )
            )
        }
    }

    suspend fun ApplicationCall.respondCompareTable(id: String?, tableName: String?) {
        if (id.isNullOrBlank() || tableName.isNullOrBlank()) {
            respondDataStar {
                otToast("Missing parameters", variant = ToastVariant.DANGER)
            }
            return
        }

        val diff = resultCacheService.getTableDiff(id, tableName)
        if (diff == null) {
            respondDataStar {
                otToast("Table diff not found", variant = ToastVariant.DANGER)
            }
            return
        }

        val upgrade = sqlGenerationService.generateUpgradeSql(diff)
        val rollback = sqlGenerationService.generateRollbackSql(diff)

        respondDataStar {
            patchSignalsJson(
                mapOf(
                    "_loading" to false,
                    "_selectedTable" to tableName,
                    "_detail" to diff,
                    "_upgradeSql" to upgrade,
                    "_rollbackSql" to rollback,
                    "_sqlType" to "upgrade"
                )
            )
        }
    }

    get("/compare/{id}/tables") {
        call.respondCompareTables(call.parameters["id"])
    }

    get("/compare/tables") {
        call.respondCompareTables(call.request.queryParameters["id"])
    }

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
            val diffs = compare(request)

            if (diffs.isEmpty()) {
                call.respondDataStar {
                    otToast("NO DIFF", variant = ToastVariant.SUCCESS)
                }
            } else {
                val id = resultCacheService.saveResult(diffs)
                call.respondDataStar {
                    executeScript("window.location.href = '/diff/$id'")
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

    get("/compare/{id}/tables/{tableName}") {
        call.respondCompareTable(call.parameters["id"], call.parameters["tableName"])
    }

    post("/compare/table") {
        val id = call.request.queryParameters["id"]
        val tableName = call.request.queryParameters["table"]
        call.respondCompareTable(id, tableName)
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
