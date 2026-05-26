package com.zxqj.dbcompare.routes

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.zxqj.dbcompare.model.CompareRequest
import dev.datastar.kotlin.sdk.ElementPatchMode.Inner
import dev.datastar.kotlin.sdk.PatchElementsOptions
import dev.datastar.kotlin.sdk.ServerSentEventGenerator
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.io.File

private data class ConfigFilenamePayload(
    val selectedConfig: String = ""
)

fun Route.configRoutes() {
    val configDir = File(System.getProperty("user.dir"), "config").apply { mkdirs() }
    val yamlMapper = ObjectMapper(YAMLFactory()).apply { findAndRegisterModules() }

    suspend fun ApplicationCall.loadConfig(name: String?) {
        val configName = name?.trim().orEmpty()
        if (configName.isBlank()) {
            respondDataStar {
                otToast("Config name cannot be empty", variant = ToastVariant.DANGER)
            }
            return
        }

        val file = File(configDir, normalizeYamlName(configName))

        if (!file.exists()) {
            respondDataStar {
                otToast("Config file not found: ${file.name}", variant = ToastVariant.DANGER)
            }
            return
        }

        val config: CompareRequest = yamlMapper.readValue(file)
        application.log.info("Loading config from ${file.absolutePath}")

        respondDataStar {
            setInputValue("saveConfigName", file.name.removeSuffix(".yml"))
            fillConfigForm(config)
            otToast("Configuration loaded")
        }
    }

    suspend fun ApplicationCall.saveConfig(name: String?, receivedForm: Parameters? = null) {
        val configName = name?.trim().orEmpty()
        if (configName.isBlank()) {
            respondDataStar {
                otToast("Config name cannot be empty", variant = ToastVariant.DANGER)
            }
            return
        }

        val form = receivedForm ?: receiveParameters()
        val file = File(configDir, normalizeYamlName(configName))
        val optionElements = if (!file.exists()) {
            file.createNewFile()
            loadYamlOptions(configDir)
        } else {
            ""
        }

        yamlMapper.writeValue(file, form.toCompareRequest())

        respondDataStar {
            if (optionElements.isNotBlank()) {
                patchElements(optionElements, PatchElementsOptions(selector = "#config-select", mode = Inner))
            }
            patchSignalsJson(mapOf("selectedConfig" to file.name))
            setInputValue("saveConfigName", file.name.removeSuffix(".yml"))
            otToast("Configuration saved")
        }
    }

    route("/configs") {
        get("/{name}") {
            call.loadConfig(call.parameters["name"])
        }

        put("/{name}") {
            call.saveConfig(call.parameters["name"])
        }
    }
}

internal fun ServerSentEventGenerator.fillConfigForm(config: CompareRequest) {
    setInputValue("target.host", config.target?.host ?: "localhost")
    setInputValue("target.port", (config.target?.port ?: 3306).toString())
    setInputValue("target.username", config.target?.username ?: "root")
    setInputValue("target.password", config.target?.password ?: "")
    setInputValue("target.database", config.target?.database ?: "")

    setInputValue("source.host", config.source?.host ?: "localhost")
    setInputValue("source.port", (config.source?.port ?: 3306).toString())
    setInputValue("source.username", config.source?.username ?: "root")
    setInputValue("source.password", config.source?.password ?: "")
    setInputValue("source.database", config.source?.database ?: "")

    setChipInputValue("ignoreFields", config.ignoreFields.orEmpty())
    setChipInputValue("excludeTables", config.excludeTables.orEmpty())
    setChipInputValue("ignoreDataTables", config.ignoreDataTables.orEmpty())
    setChipInputValue("specifiedPrimaryKeys", config.specifiedPrimaryKeys.orEmpty())
    setChipInputValue("treeTableConfig", config.treeTableConfig.orEmpty())
    setChipInputValue("excludeDataRows", config.excludeDataRows.orEmpty())
    setChipInputValue("includeDataRows", config.includeDataRows.orEmpty())
}
