package com.zxqj.dbcompare.routes

import com.fasterxml.jackson.databind.ObjectMapper
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

internal suspend fun ApplicationCall.respondDataStar(
    status: HttpStatusCode = HttpStatusCode.OK,
    block: ServerSentEventGenerator.() -> Unit
) {
    respondTextWriter(status = status, contentType = ContentType.Text.EventStream) {
        val generator = KtorGenerator(this)
        generator.block()
    }
}

internal fun datastarJson(value: Any): String = dataStarObjectMapper.writeValueAsString(value)

internal fun ServerSentEventGenerator.patchSignalsJson(value: Any) {
    patchSignals(datastarJson(value))
}

internal fun ServerSentEventGenerator.setInputValue(name: String, value: String) {
    executeScript(formInputValueScript(name, value))
}

internal fun ServerSentEventGenerator.setCheckboxValue(name: String, value: Boolean) {
    executeScript(formCheckboxValueScript(name, value))
}

internal fun ServerSentEventGenerator.setChipInputValue(name: String, value: List<String>) {
    executeScript(formChipInputValueScript(name, value))
}

internal fun ServerSentEventGenerator.toast(message: String, type: String = "info") {
    executeScript("window.toast(${datastarJson(message)}, ${datastarJson(type)})")
}

enum class ToastVariant(val value: String) {
    SUCCESS("success"),
    DANGER("danger"),
    WARNING("warning")
}

internal fun ServerSentEventGenerator.otToast(message: String, title: String = "", variant: ToastVariant = ToastVariant.SUCCESS) {
    executeScript("ot.toast('$message', '$title', { variant: '${variant.value}' })")
}

internal fun formInputValueScript(name: String, value: String): String =
    """document.querySelector('input[name="${escapeJsSelector(name)}"]').value = '${escapeJsString(value)}'"""

internal fun formCheckboxValueScript(name: String, value: Boolean): String =
    """document.querySelector('input[name="${escapeJsSelector(name)}"]').checked = $value"""

internal fun formChipInputValueScript(name: String, value: List<String>): String =
    """document.querySelector('oat-chip-input[name="${escapeJsSelector(name)}"]').value = ${datastarJson(value)}"""

private fun escapeJsSelector(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

private fun escapeJsString(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\r", "\\r")
        .replace("\n", "\\n")

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
