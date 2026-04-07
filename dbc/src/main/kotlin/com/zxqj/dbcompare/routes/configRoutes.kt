package com.zxqj.dbcompare.routes

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.zxqj.dbcompare.model.DbConfig
import dev.datastar.kotlin.sdk.ElementPatchMode.Inner
import dev.datastar.kotlin.sdk.PatchElementsOptions
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.PipelineContext
import java.io.File

private data class ConfigPayload(
    val selectedConfig: String = "",
    val saveConfigName: String = "",
    val source: DbConfig? = null,
    val target: DbConfig? = null,
    val ignoreFields: List<String>? = null,
    val excludeTables: List<String>? = null,
    val ignoreDataTables: List<String>? = null,
    val specifiedPrimaryKeys: List<String>? = null,
    val treeTableConfig: List<String>? = null,
    val excludeDataRows: List<String>? = null,
    val includeDataRows: List<String>? = null
)

fun Route.configRoutes() {
    val configDir = File(System.getProperty("user.dir"), "config").apply { mkdirs() }
    val yamlMapper = ObjectMapper(YAMLFactory()).apply { findAndRegisterModules() }

    route("/config") {
        fun loadConfigFileNameOptions(): String {
            val files = listConfigFiles(configDir)

            val selectElement = buildString {
                append("""<option value="">-- Select a file --</option>""")
                for (file in files) {
                    append("""<option value="$file">$file</option>""")
                }
            }
            return selectElement
        }

        get {
            val optionElements = loadConfigFileNameOptions()

            call.respondDataStar {
                patchElements(optionElements, PatchElementsOptions(selector = "#config-select", mode = Inner))
            }
        }

        post {
            val payload = call.receive<ConfigPayload>()
            val file = File(configDir, payload.selectedConfig)

            val text = file.readText(Charsets.UTF_8)
            application.log.info("Loading config from ${file.absolutePath}, text = $text")

            call.respondDataStar {
                otToast("Configuration loaded")
            }
        }

        put {
            val payload = call.receive<ConfigPayload>()
            val fileName = payload.saveConfigName

            val file = File(configDir, "$fileName.yml")
            val optionElements: String
            if (!file.exists()) {
                file.createNewFile()
                optionElements = loadConfigFileNameOptions()
            } else {
                optionElements = ""
            }

            call.respondDataStar {
                patchElements(optionElements, PatchElementsOptions(selector = "#config-select", mode = Inner))
                patchSignals("{\"selectedConfig\": \"$fileName.yml\"}")
                otToast("Configuration saved")
            }
        }
    }
//    route("/config") {
//        get("/list") {
//            val files = listConfigFiles(configDir)
//
//            val selectElement = buildString {
//                append("""<select id="config-select" data-on-intersect="@get('/api/config/list')">""")
//                append("""<option value="">-- Select a file --</option>""")
//                for (file in files) {
//                    append("""<option value="$file">$file</option>""")
//                }
//                append("</select>")
//            }
//
//            call.patchElements(selectElement)
//        }
//
//        post("/load") {
//            val payload = runCatching { call.receive<FilenamePayload>() }.getOrDefault(FilenamePayload())
//            val filename = normalizeYamlName(payload.filename)
//            val file = File(configDir, filename)
//            if (!file.exists()) {
//                call.respondDataStar {
//                    toast("Config file not found: $filename", "error")
//                }
//                return@post
//            }
//            val config = yamlMapper.readValue(file, CompareRequest::class.java)
//            call.respondDataStar {
//                patchSignalsJson(
//                    mapOf(
//                        "selectedConfig" to filename,
//                        "source" to (config.source ?: DbConfig()),
//                        "target" to (config.target ?: DbConfig()),
//                        "ignoreFields" to joinCsv(config.ignoreFields),
//                        "excludeTables" to joinCsv(config.excludeTables),
//                        "ignoreDataTables" to joinCsv(config.ignoreDataTables),
//                        "specifiedPrimaryKeys" to joinCsv(config.specifiedPrimaryKeys),
//                        "treeTableConfig" to joinCsv(config.treeTableConfig),
//                        "excludeDataRows" to joinCsv(config.excludeDataRows),
//                        "includeDataRows" to joinCsv(config.includeDataRows)
//                    )
//                )
//                toast("Config loaded", "success")
//            }
//        }
//
//        post("/save") {
//            val payload = call.receive<ConfigPayload>()
//            val name = normalizeYamlName(payload.filename)
//            val config = CompareRequest(
//                source = payload.source,
//                target = payload.target,
//                ignoreFields = payload.ignoreFields,
//                excludeTables = payload.excludeTables,
//                ignoreDataTables = payload.ignoreDataTables,
//                specifiedPrimaryKeys = payload.specifiedPrimaryKeys,
//                treeTableConfig = payload.treeTableConfig,
//                excludeDataRows = payload.excludeDataRows,
//                includeDataRows = payload.includeDataRows
//            )
//            val file = File(configDir, name)
//            yamlMapper.writeValue(file, config)
//            call.respondDataStar {
//                patchSignalsJson(
//                    mapOf(
//                        "configFiles" to listConfigFiles(configDir),
//                        "selectedConfig" to name
//                    )
//                )
//                toast("Config saved", "success")
//            }
//        }
//    }
}

private fun listConfigFiles(configDir: File): List<String> =
    configDir.listFiles { file ->
        file.isFile && file.name.endsWith(".yml")
    }?.map { it.name }?.sorted() ?: emptyList()

private fun normalizeYamlName(filename: String): String =
    filename.trim().let { if (it.endsWith(".yml")) it else "$it.yml" }

private fun joinCsv(values: List<String>?): String = values?.joinToString(", ").orEmpty()
