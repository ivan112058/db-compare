package com.zxqj.dbcompare

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.zxqj.dbcompare.html.configPage
import com.zxqj.dbcompare.routes.*
import com.zxqj.dbcompare.service.CompareService
import com.zxqj.dbcompare.service.DatabaseService
import com.zxqj.dbcompare.service.ResultCacheService
import com.zxqj.dbcompare.service.SqlGenerationService
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val dbService = DatabaseService()
    val resultCacheService = ResultCacheService()
    val compareService = CompareService(dbService)
    val sqlGenerationService = SqlGenerationService()

    val configDir = File(System.getProperty("user.dir"), "config").apply { mkdirs() }

    configureStatusPages()

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }

    routing {
        get("/config") {
            call.respondHtml(HttpStatusCode.OK) {
                configPage(configDir)
            }
//            call.respondStaticHtml("config.html")
        }
        get("/env") {
            call.respondStaticHtml("env.html")
        }
        get("/diff/{id}") {
            call.respondStaticHtml("diff.html")
        }

        staticResources("/", "static") {
            default("config.html")
        }

        route("/api") {
            compareRoutes(compareService, dbService, resultCacheService, sqlGenerationService)
            configRoutes()
            envRoutes()
            fsRoutes()
            gitRoutes()
        }
    }
}

private suspend fun ApplicationCall.respondStaticHtml(fileName: String) {
    val resource = object {}.javaClass.classLoader.getResource("static/$fileName")
    if (resource == null) {
        respond(HttpStatusCode.NotFound)
    } else {
        respondText(resource.readText(), ContentType.Text.Html.withCharset(Charsets.UTF_8))
    }
}

fun listYamlFiles(dir: File): List<String> =
    dir.listFiles { file ->
        file.isFile && file.name.endsWith(".yml")
    }?.map { it.name.removeSuffix(".yml") }?.sorted() ?: emptyList()
