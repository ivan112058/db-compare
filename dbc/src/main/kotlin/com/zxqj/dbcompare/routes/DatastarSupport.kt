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

object KtorGenerator {
    operator fun invoke(writer: Writer) = ServerSentEventGenerator(adaptResponse(writer))
}

internal suspend fun ApplicationCall.respondDataStar(block: ServerSentEventGenerator.() -> Unit) {
    respondTextWriter(status = HttpStatusCode.OK, contentType = ContentType.Text.EventStream) {
        val generator = KtorGenerator(this)
        generator.block()
    }
}

internal fun datastarJson(value: Any): String = dataStarObjectMapper.writeValueAsString(value)

internal fun ServerSentEventGenerator.patchSignalsJson(value: Any) {
    patchSignals(datastarJson(value))
}

internal fun ServerSentEventGenerator.toast(message: String, type: String = "info") {
    executeScript("window.toast(${datastarJson(message)}, ${datastarJson(type)})")
}

internal fun ServerSentEventGenerator.otToast(message: String, title: String = "", variant: String = "success") {
    executeScript("ot.toast('$message', '$title', { variant: '$variant' })")
}

fun adaptResponse(writer: Writer): Response =
    object : Response {
        override fun sendConnectionHeaders(
            status: Int,
            headers: Map<String, List<String>>,
        ) {
            // connection is already set up when used
        }

        override fun write(text: String) {
            writer.write(text)
        }

        override fun flush() {
            writer.flush()
        }
    }