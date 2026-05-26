package com.zxqj.dbcompare.routes

import dev.datastar.kotlin.sdk.ElementPatchMode.Inner
import dev.datastar.kotlin.sdk.PatchElementsOptions
import io.ktor.server.application.*
import java.io.File

fun listYamlFiles(dir: File): List<String> =
    dir.listFiles { file ->
        file.isFile && file.name.endsWith(".yml")
    }?.map { it.name }?.sorted() ?: emptyList()

fun loadYamlOptions(dir: File): String {
    val files = listYamlFiles(dir)
    return buildString {
        append("""<option value="">-- Select a file --</option>""")
        for (file in files) {
            append("""<option value="$file">$file</option>""")
        }
    }
}

suspend fun ApplicationCall.respondYamlOptions(dir: File, selector: String) {
    val optionElements = loadYamlOptions(dir)
    respondDataStar {
        patchElements(optionElements, PatchElementsOptions(selector = selector, mode = Inner))
    }
}

fun normalizeYamlName(filename: String): String =
    filename.trim().let { if (it.endsWith(".yml")) it else "$it.yml" }
