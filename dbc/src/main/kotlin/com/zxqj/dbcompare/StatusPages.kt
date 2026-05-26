package com.zxqj.dbcompare

import com.zxqj.dbcompare.routes.ToastVariant
import com.zxqj.dbcompare.routes.otToast
import com.zxqj.dbcompare.routes.respondDataStar
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.application.log.warn("bad request: ${cause.message}")
            call.respondThrowable(
                status = HttpStatusCode.BadRequest,
                publicMessage = cause.message ?: "Bad request"
            )
        }

        exception<Throwable> { call, cause ->
            call.application.log.error("unhandled request error", cause)
            call.respondThrowable(
                status = HttpStatusCode.InternalServerError,
                publicMessage = "Internal server error"
            )
        }
    }
}

private suspend fun ApplicationCall.respondThrowable(status: HttpStatusCode, publicMessage: String) {
    if (request.path().startsWith("/api")) {
        respondDataStar(status = status) {
            otToast(publicMessage, variant = ToastVariant.DANGER)
        }
        return
    }

    respondText(
        text = publicMessage,
        status = status,
        contentType = ContentType.Text.Plain.withCharset(Charsets.UTF_8)
    )
}
