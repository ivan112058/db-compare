package com.zxqj.dbcompare.routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean
)

fun Route.fsRoutes() {
    route("/fs") {
        get("/list") {
            val path = call.request.queryParameters["path"]
            var dir = if (path.isNullOrEmpty()) File(System.getProperty("user.home")) else File(path)

            if (dir.exists() && dir.isFile) dir = dir.parentFile
            if (!dir.exists() || !dir.isDirectory) return@get call.respond(emptyList<FileItem>())

            val files = dir.listFiles() ?: return@get call.respond(emptyList<FileItem>())
            val items = files.map {
                FileItem(name = it.name, path = it.absolutePath, isDirectory = it.isDirectory)
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

            call.respond(items)
        }

        get("/parent") {
            val path = call.request.queryParameters["path"]
                ?: return@get call.respond(mapOf("error" to "path required"))
            val file = File(path)
            val parent = file.parentFile
            if (parent == null) {
                call.respond(mapOf<String, String>())
                return@get
            }
            call.respond(FileItem(parent.name, parent.absolutePath, true))
        }
    }
}
