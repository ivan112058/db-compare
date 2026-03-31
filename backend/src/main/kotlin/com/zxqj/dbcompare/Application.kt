package com.zxqj.dbcompare

import com.fasterxml.jackson.databind.SerializationFeature
import com.zxqj.dbcompare.routes.compareRoutes
import com.zxqj.dbcompare.routes.configRoutes
import com.zxqj.dbcompare.routes.envRoutes
import com.zxqj.dbcompare.routes.fsRoutes
import com.zxqj.dbcompare.routes.gitRoutes
import com.zxqj.dbcompare.service.CompareService
import com.zxqj.dbcompare.service.DatabaseService
import com.zxqj.dbcompare.service.ResultCacheService
import com.zxqj.dbcompare.service.SqlGenerationService
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val dbService = DatabaseService()
    val resultCacheService = ResultCacheService()
    val compareService = CompareService(dbService)
    val sqlGenerationService = SqlGenerationService()

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }

    routing {
        staticResources("/", "static") {
            default("index.html")
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
