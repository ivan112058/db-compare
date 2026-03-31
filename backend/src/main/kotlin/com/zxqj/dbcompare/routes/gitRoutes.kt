package com.zxqj.dbcompare.routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

data class GitStatus(
    val branch: String = "",
    val isDirty: Boolean = false,
    val error: String? = null
)

fun Route.gitRoutes() {
    route("/git") {
        get("/status") {
            val path = call.request.queryParameters["path"]
                ?: return@get call.respond(GitStatus(error = "path required"))
            val dir = File(path)

            if (!dir.exists() || !dir.isDirectory) {
                return@get call.respond(GitStatus(error = "Invalid directory"))
            }

            val status = try {
                if (!File(dir, ".git").exists()) {
                    GitStatus(error = "Not a git repository")
                } else {
                    val branchProcess = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
                        .directory(dir).start()
                    val branch = branchProcess.inputStream.bufferedReader().readText().trim()

                    val statusProcess = ProcessBuilder("git", "status", "--porcelain")
                        .directory(dir).start()
                    val statusOutput = statusProcess.inputStream.bufferedReader().readText()

                    GitStatus(branch = branch, isDirty = statusOutput.isNotBlank())
                }
            } catch (e: Exception) {
                GitStatus(error = e.message)
            }
            call.respond(status)
        }

        get("/root") {
            val path = call.request.queryParameters["path"]
                ?: return@get call.respond("")
            var dir = File(path)
            if (dir.exists() && dir.isFile) dir = dir.parentFile

            val home = File(System.getProperty("user.home"))
            var current: File? = dir
            while (current != null && current.exists()) {
                if (File(current, ".git").exists()) {
                    return@get call.respond(current.absolutePath)
                }
                if (current == home || current.parentFile == null) break
                current = current.parentFile
            }
            call.respond(dir.absolutePath)
        }
    }
}
