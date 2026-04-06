package com.zxqj.dbcompare.routes

import dev.datastar.kotlin.sdk.Response
import dev.datastar.kotlin.sdk.ServerSentEventGenerator
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.Writer

fun Route.sseGet(
    path: String,
    handler: suspend ServerSentEventGenerator.() -> Unit
) {
    get(path) {
        call.respondTextWriter(
            status = HttpStatusCode.OK,
            contentType = ContentType.Text.EventStream
        ) {
            val generator = ServerSentEventGenerator(adaptResponse(this))
            handler(generator)
        }
    }
}

fun Route.ssePost(
    path: String,
    handler: suspend ServerSentEventGenerator.() -> Unit
) {
    post(path) {
        call.respondTextWriter(
            status = HttpStatusCode.OK,
            contentType = ContentType.Text.EventStream
        ) {
            val generator = ServerSentEventGenerator(adaptResponse(this))
            handler(generator)
        }
    }
}

private fun adaptResponse(writer: Writer): Response =
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