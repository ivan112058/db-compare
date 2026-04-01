package com.zxqj.dbcompare.routes

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.zxqj.dbcompare.model.CompareRequest
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun Route.configRoutes() {
    val configDir = File(System.getProperty("user.dir"), "config").apply { mkdirs() }
    val yamlMapper = ObjectMapper(YAMLFactory()).apply { findAndRegisterModules() }

    route("/config") {
        get("/list") {
            val files = configDir.listFiles { file ->
                file.isFile && file.name.endsWith(".yml")
            }?.map { it.name } ?: emptyList()
            call.respond(files)
        }

        get("/load") {
            val filename = call.request.queryParameters["filename"]
                ?: return@get call.respond(mapOf("error" to "filename parameter required"))
            val file = File(configDir, filename)
            if (!file.exists()) {
                return@get call.respond(mapOf("error" to "Config file not found: $filename"))
            }
            val config = yamlMapper.readValue(file, CompareRequest::class.java)
            call.respond(config)
        }

        post("/save") {
            val filename = call.request.queryParameters["filename"]
            if (filename == null) {
                call.respond(mapOf("error" to "filename parameter required"))
                return@post
            }
            val name = if (filename.endsWith(".yml")) filename else "$filename.yml"
            val config = call.receive<CompareRequest>()
            val file = File(configDir, name)
            yamlMapper.writeValue(file, config)
            call.respond(mapOf("success" to true, "filename" to name))
        }
    }
}
