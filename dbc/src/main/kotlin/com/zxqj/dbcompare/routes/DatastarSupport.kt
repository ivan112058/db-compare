package com.zxqj.dbcompare.routes

import com.fasterxml.jackson.databind.ObjectMapper
import dev.datastar.kotlin.sdk.PatchElementsOptions
import dev.datastar.kotlin.sdk.Response
import dev.datastar.kotlin.sdk.ServerSentEventGenerator
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import java.io.Writer

private val dataStarObjectMapper = ObjectMapper().findAndRegisterModules()

private class KtorDataStarResponse(
    private val call: ApplicationCall,
    private val writer: Writer
) : Response {
    override fun sendConnectionHeaders(
        status: Int,
        headers: Map<String, List<String>>
    ) {
        call.response.status(HttpStatusCode.fromValue(status))
        headers.forEach { (name, values) ->
            if (name == HttpHeaders.ContentType) return@forEach
            values.forEach { value ->
                call.response.headers.append(name, value, safeOnly = false)
            }
        }
    }

    override fun write(text: String) {
        writer.write(text)
    }

    override fun flush() {
        writer.flush()
    }
}

internal fun datastarJson(value: Any): String = dataStarObjectMapper.writeValueAsString(value)

internal suspend fun ApplicationCall.respondDataStar(block: ServerSentEventGenerator.() -> Unit) {
    respondTextWriter(status = HttpStatusCode.OK, contentType = ContentType.Text.EventStream) {
        val generator = ServerSentEventGenerator(KtorDataStarResponse(this@respondDataStar, this))
        generator.block()
    }
}

internal suspend fun ApplicationCall.patchElements(
    elements: String? = null,
    options: PatchElementsOptions = PatchElementsOptions(),
) {
    respondDataStar { patchElements(elements, options) }
}

internal fun ServerSentEventGenerator.patchSignalsJson(value: Any) {
    patchSignals(datastarJson(value))
}

internal fun ServerSentEventGenerator.toast(message: String, type: String = "info") {
    executeScript("window.toast(${datastarJson(message)}, ${datastarJson(type)})")
}
