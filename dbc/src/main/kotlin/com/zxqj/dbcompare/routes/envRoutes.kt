package com.zxqj.dbcompare.routes

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.zxqj.dbcompare.model.CompareRequest
import com.zxqj.dbcompare.model.DbConfig
import com.zxqj.dbcompare.model.EnvConfig
import com.zxqj.dbcompare.model.EnvDbInfo
import dev.datastar.kotlin.sdk.ElementPatchMode.Inner
import dev.datastar.kotlin.sdk.PatchElementsOptions
import dev.datastar.kotlin.sdk.ServerSentEventGenerator
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.io.File

data class DockerParams(
    val codePath: String,
    val composePath: String,
    val prefix: String,
    val serviceName: String,
    val port: Int,
    val excludeInitSql: List<String>? = null,
    val gitRef: String? = null
)

private data class EnvFilenamePayload(
    val selectedConfig: String = ""
)

private data class DockerCommandPayload(
    val type: String = "",
    val side: EnvDbInfo = EnvDbInfo()
)

internal data class SavedEnvConfig(
    val envConfig: EnvConfig,
    val optionElements: String
)

fun Route.envRoutes() {
    val configDir = File(System.getProperty("user.dir"), "config").apply { mkdirs() }
    val envDir = File(configDir, "env").apply { mkdirs() }
    val yamlMapper = ObjectMapper(YAMLFactory()).apply { findAndRegisterModules() }

    route("/env") {
        get {
            call.respondYamlOptions(envDir, "#env-config-select")
        }

        post {
            val payload = call.receive<EnvFilenamePayload>()
            val file = File(envDir, payload.selectedConfig)

            val text = file.readText(Charsets.UTF_8)

            val envConfig: EnvConfig = yamlMapper.readValue<EnvConfig>(file)
            application.log.info("Loading config from ${file.absolutePath}, text = $text")

            call.respondDataStar {
                fillEnvForm(envConfig)
                otToast("Configuration loaded")
            }
        }

        put {
            val form = call.receiveParameters()
            application.log.info("save env $form")

            val saved = try {
                saveEnvConfig(form, envDir, yamlMapper)
            } catch (e: BadRequestException) {
                application.log.warn(e.message)
                call.respondDataStar {
                    otToast(e.message ?: "env config error", variant = ToastVariant.DANGER)
                }
                return@put
            }

            call.respondDataStar {
                if (saved.optionElements.isNotBlank()) {
                    patchElements(
                        saved.optionElements,
                        PatchElementsOptions(selector = "#env-config-select", mode = Inner)
                    )
                }
                patchSignals("{\"selectedConfig\": \"${saved.envConfig.projectName}.yml\"}")
                otToast("Configuration saved")
            }
        }

        post("/generate") {
            val form = call.receiveParameters()
            application.log.info("generate config $form")

            val saved = try {
                saveAndGenerateConfig(form, envDir, configDir, yamlMapper)
            } catch (e: BadRequestException) {
                application.log.warn(e.message)
                call.respondDataStar {
                    otToast(e.message ?: "env config error", variant = ToastVariant.DANGER)
                }
                return@post
            }

            call.respondDataStar {
                if (saved.optionElements.isNotBlank()) {
                    patchElements(
                        saved.optionElements,
                        PatchElementsOptions(selector = "#env-config-select", mode = Inner)
                    )
                }
                patchSignals("{\"selectedConfig\": \"${saved.envConfig.projectName}.yml\"}")
                otToast("Generated: ${saved.envConfig.projectName}.yml", variant = ToastVariant.SUCCESS)
            }
        }

        post("/docker/start") {
            val form = call.receiveParameters()
            val type = call.request.queryParameters["type"] ?: "target"

            val envConfig = try {
                form.toSaveEnvRequest()
            } catch (e: BadRequestException) {
                call.respondDataStar {
                    otToast(e.message ?: "Invalid form data", variant = ToastVariant.DANGER)
                }
                return@post
            }

            val side = if (type == "source") envConfig.source else envConfig.target
            val params = side.toDockerParams()

            try {
                val effectiveParams = if (!params.gitRef.isNullOrEmpty()) {
                    prepareIsolatedEnvironment(params)
                } else {
                    params
                }
                runDockerCompose(effectiveParams, "up", "-d", "--force-recreate", "--remove-orphans")
                val isRunning = resolveRunning(effectiveParams)
                call.respondDataStar {
                    patchSignalsJson(mapOf(type to mapOf("running" to isRunning)))
                    otToast("$type started", variant = ToastVariant.SUCCESS)
                }
            } catch (e: Exception) {
                call.respondDataStar {
                    otToast(e.message ?: "Failed to start docker", variant = ToastVariant.DANGER)
                }
            }
        }
//
//        post("/docker/stop") {
//            try {
//                val payload = call.receive<DockerCommandPayload>()
//                val params = payload.side.toDockerParams()
//                runDockerCompose(params, "down")
//                val isRunning = resolveRunning(params)
//                call.respondDataStar {
//                    patchSignalsJson(
//                        mapOf(
//                            "${payload.type}Status" to isRunning,
//                            "dockerLoading" to false
//                        )
//                    )
//                    otToast("${payload.type} stopped", variant = ToastVariant.SUCCESS)
//                }
//            } catch (e: Exception) {
//                call.respondDataStar {
//                    patchSignalsJson(mapOf("dockerLoading" to false))
//                    otToast(e.message ?: "Failed to stop docker", variant = ToastVariant.DANGER)
//                }
//            }
//        }

//        post("/docker/status") {
//            val config = call.receive<EnvConfig>()
//            val sourceStatus = resolveRunning(config.source.toDockerParams())
//            val targetStatus = resolveRunning(config.target.toDockerParams())
//            call.respondDataStar {
//                patchSignalsJson(
//                    mapOf(
//                        "sourceStatus" to sourceStatus,
//                        "targetStatus" to targetStatus
//                    )
//                )
//            }
//        }

    }
}

fun Parameters.toSaveEnvRequest(): EnvConfig {
    fun toStringList(name: String): List<String>? {
        val value = this[name] ?: return null
        return ObjectMapper().findAndRegisterModules().readValue(value, List::class.java).map { it.toString() }
    }

    fun toDbConfig(name: String): DbConfig {
        return DbConfig(
            username = this["$name.username"]?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw BadRequestException("$name.username cannot be empty"),
            password = this["$name.password"]?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw BadRequestException("$name.password cannot be empty"),
            database = this["$name.database"]?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw BadRequestException("$name.database cannot be empty")
        )
    }

    fun toEnvDbInfo(name: String, separateCodePath: Boolean, sameDBConfig: Boolean, target: EnvDbInfo?): EnvDbInfo {
        return EnvDbInfo(
            composePath = if (target == null || separateCodePath) {
                this["$name.composePath"]?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw BadRequestException("$name.composePath cannot be empty")
            } else {
                target.composePath
            },
            codePath = if (target == null || separateCodePath) {
                this["$name.codePath"]?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw BadRequestException("$name.codePath cannot be empty")
            } else {
                target.codePath
            },
            gitref = this["$name.gitref"]?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw BadRequestException("$name.gitref cannot be empty"),
            prefix = this["$name.prefix"]?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw BadRequestException("$name.prefix cannot be empty"),
            port = this["$name.port"]?.toIntOrNull() ?: throw BadRequestException("$name.port should be 1~65535"),
            service = if (target == null || !sameDBConfig) {
                this["$name.service"]?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw BadRequestException("$name.service cannot be empty")
            } else {
                target.service
            },
            excludeInitSql = toStringList("$name.excludeInitSql"),
            dbConfig = if (target == null || !sameDBConfig) {
                toDbConfig(name)
            } else {
                target.dbConfig
            }
        )
    }

    val projectName = this["projectName"]?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw BadRequestException("project name cannot be empty")
    val separateCodePath = this["separateCodePath"]?.trim() == "on"
    val sameDBConfig = this["sameDBConfig"]?.trim() == "on"
    val target = toEnvDbInfo("target", separateCodePath, sameDBConfig, null)
    val source = toEnvDbInfo("source", separateCodePath, sameDBConfig, target)
    return EnvConfig(projectName, separateCodePath, sameDBConfig, source, target)
}

internal fun saveEnvConfig(
    form: Parameters,
    envDir: File,
    yamlMapper: ObjectMapper
): SavedEnvConfig {
    val request = form.toSaveEnvRequest()
    val file = File(envDir, "${request.projectName}.yml")
    val optionElements = if (!file.exists()) {
        file.createNewFile()
        loadYamlOptions(envDir)
    } else {
        ""
    }

    yamlMapper.writeValue(file, request)
    return SavedEnvConfig(request, optionElements)
}

internal fun EnvConfig.toGeneratedCompareRequest(): CompareRequest =
    CompareRequest(
        source = source.dbConfig.copy(host = "localhost", port = source.port),
        target = target.dbConfig.copy(host = "localhost", port = target.port)
    )

internal fun saveAndGenerateConfig(
    form: Parameters,
    envDir: File,
    configDir: File,
    yamlMapper: ObjectMapper
): SavedEnvConfig {
    val saved = saveEnvConfig(form, envDir, yamlMapper)
    val compareFile = File(configDir, "${saved.envConfig.projectName}.yml")
    yamlMapper.writeValue(compareFile, saved.envConfig.toGeneratedCompareRequest())
    return saved
}

internal fun ServerSentEventGenerator.fillEnvForm(envConfig: EnvConfig) {
    setInputValue("projectName", envConfig.projectName)
    setCheckboxValue("separateCodePath", envConfig.separateCodePath)
    setCheckboxValue("sameDBConfig", envConfig.sameDBConfig)
    fillEnvDbInfo("target", envConfig.target)
    fillEnvDbInfo("source", envConfig.source)
}

private fun ServerSentEventGenerator.fillEnvDbInfo(prefix: String, envDbInfo: EnvDbInfo) {
    setInputValue("$prefix.composePath", envDbInfo.composePath)
    setInputValue("$prefix.codePath", envDbInfo.codePath)
    setInputValue("$prefix.gitref", envDbInfo.gitref.orEmpty())
    setInputValue("$prefix.prefix", envDbInfo.prefix)
    setInputValue("$prefix.port", envDbInfo.port.toString())
    setInputValue("$prefix.service", envDbInfo.service)
    setChipInputValue("$prefix.excludeInitSql", envDbInfo.excludeInitSql.orEmpty())
    setInputValue("$prefix.database", envDbInfo.dbConfig.database.orEmpty())
    setInputValue("$prefix.username", envDbInfo.dbConfig.username.orEmpty())
    setInputValue("$prefix.password", envDbInfo.dbConfig.password.orEmpty())
}

private fun prepareIsolatedEnvironment(params: DockerParams): DockerParams {
    val codeDir = File(params.codePath)
    val composeFile = File(params.composePath)

    if (!codeDir.exists() || !composeFile.exists()) {
        throw RuntimeException("Code or Compose file not found")
    }

    val relComposePath = composeFile.absolutePath.removePrefix(codeDir.absolutePath).removePrefix(File.separator)
    val composeContent = runGitCommand(codeDir, "show", "${params.gitRef}:$relComposePath")

    val yamlFactory = YAMLFactory()
    val mapper = ObjectMapper(yamlFactory)
    val rootNode = mapper.readTree(composeContent)

    val referencedFiles = mutableListOf(relComposePath)

    val servicesNode = rootNode.path("services")
    val serviceNode = servicesNode.path(params.serviceName)
    if (!serviceNode.isMissingNode) {
        val volumesNode = serviceNode.path("volumes")
        if (volumesNode.isArray) {
            val volumesArray = volumesNode as com.fasterxml.jackson.databind.node.ArrayNode
            for (i in 0 until volumesArray.size()) {
                val volume = volumesArray.get(i).asText()
                val parts = volume.split(":", limit = 2)
                if (parts.isNotEmpty()) {
                    val hostPath = parts[0]
                    if (hostPath.startsWith("./") || hostPath.startsWith("../")) {
                        val composeDir = File(relComposePath).parent ?: ""
                        val resolvedPath = File(File(codeDir, composeDir), hostPath).canonicalPath
                        val relPath = resolvedPath.removePrefix(codeDir.canonicalPath).removePrefix(File.separator)
                        referencedFiles.add(relPath)
                    }
                }
            }
        }
    }

    val commonRoot = findCommonRoot(referencedFiles)

    val tempDir = File(System.getProperty("java.io.tmpdir"), "db-compare/${params.prefix}_${params.gitRef}")
    if (!tempDir.exists()) tempDir.mkdirs()

    val archiveCmd = listOf("git", "archive", "--format=tar", params.gitRef!!, commonRoot)
    val tarCmd = listOf("tar", "-x", "-C", tempDir.absolutePath)

    val archivePb = ProcessBuilder(archiveCmd).directory(codeDir)
    val tarPb = ProcessBuilder(tarCmd)
    val archiveProcess = archivePb.start()
    val tarProcess = tarPb.start()

    archiveProcess.inputStream.transferTo(tarProcess.outputStream)
    tarProcess.outputStream.close()

    val archiveExit = archiveProcess.waitFor()
    val tarExit = tarProcess.waitFor()

    if (archiveExit != 0 || tarExit != 0) {
        throw RuntimeException("Failed to archive/extract code. Archive: $archiveExit, Tar: $tarExit")
    }

    val newComposePath = File(tempDir, relComposePath).absolutePath
    return params.copy(codePath = tempDir.absolutePath, composePath = newComposePath)
}

private fun EnvDbInfo.toDockerParams(): DockerParams =
    DockerParams(
        codePath = codePath,
        composePath = composePath,
        prefix = prefix,
        serviceName = service,
        port = port,
        excludeInitSql = excludeInitSql,
        gitRef = gitref
    )

private fun resolveRunning(params: DockerParams): Boolean =
    if (params.composePath.isBlank() || params.serviceName.isBlank()) {
        false
    } else {
        runCatching {
            checkContainerStatus(params.codePath, params.composePath, params.prefix, params.serviceName)
        }.getOrDefault(false)
    }

private fun runGitCommand(dir: File, vararg args: String): String {
    val pb = ProcessBuilder("git", *args).directory(dir)
    val process = pb.start()
    val output = process.inputStream.bufferedReader().readText()
    val error = process.errorStream.bufferedReader().readText()
    if (process.waitFor() != 0) {
        throw RuntimeException("Git command failed: ${args.joinToString(" ")}. Error: $error")
    }
    return output
}

private fun findCommonRoot(paths: List<String>): String {
    if (paths.isEmpty()) return ""
    var common = File(paths[0]).parent ?: ""
    if (common == "/") common = ""
    for (path in paths) {
        var p = File(path).parent ?: ""
        while (!p.startsWith(common) && common.isNotEmpty()) {
            common = File(common).parent ?: ""
        }
        if (common.isEmpty()) break
    }
    return common
}

private fun checkContainerStatus(codePath: String, composePath: String, prefix: String, serviceName: String): Boolean {
    if (!File(composePath).exists()) return false

    val cmd = mutableListOf("docker-compose")
    if (composePath.isNotEmpty()) {
        cmd.add("-f"); cmd.add(composePath)
    }
    if (prefix.isNotEmpty()) {
        cmd.add("-p"); cmd.add(prefix)
    }
    cmd.add("ps"); cmd.add("-q"); cmd.add(serviceName)

    val pb = ProcessBuilder(cmd)
    if (codePath.isNotEmpty()) pb.directory(File(codePath))
    val process = pb.start()
    val output = process.inputStream.bufferedReader().readText()
    return output.isNotBlank()
}

private fun runDockerCompose(params: DockerParams, vararg commands: String): Map<String, Any> {
    var tempComposeFile: File? = null
    try {
        tempComposeFile = createModifiedComposeFile(params)

        val cmd = mutableListOf("docker-compose")
        cmd.add("-f"); cmd.add(tempComposeFile.absolutePath)
        if (params.prefix.isNotEmpty()) {
            cmd.add("-p"); cmd.add(params.prefix)
        }
        cmd.addAll(commands)
        if (params.serviceName.isNotEmpty()) cmd.add(params.serviceName)

        val pb = ProcessBuilder(cmd)
        val env = pb.environment()
        env["DB_PORT"] = params.port.toString()
        if (!params.excludeInitSql.isNullOrEmpty()) {
            env["EXCLUDE_INIT_SQL"] = params.excludeInitSql.joinToString(",")
        }
        if (params.codePath.isNotEmpty()) {
            pb.directory(File(params.codePath))
        } else {
            pb.directory(File(params.composePath).parentFile)
        }

        val process = pb.start()
        val output = StringBuilder()
        process.inputStream.bufferedReader().forEachLine { output.appendLine(it) }
        process.errorStream.bufferedReader().forEachLine { output.appendLine(it) }

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw RuntimeException("Docker command failed with code $exitCode: $output")
        }
        return mapOf("success" to true, "output" to output.toString())
    } catch (e: Exception) {
        e.printStackTrace()
        return mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
    } finally {
        tempComposeFile?.delete()
    }
}

private fun createModifiedComposeFile(params: DockerParams): File {
    val originalFile = File(params.composePath)
    if (!originalFile.exists()) {
        throw RuntimeException("Compose file not found: ${params.composePath}")
    }

    val yamlFactory = YAMLFactory()
    val mapper = ObjectMapper(yamlFactory)
    val rootNode = mapper.readTree(originalFile) as ObjectNode

    val servicesNode = rootNode.path("services")
    if (servicesNode.isMissingNode) throw RuntimeException("Invalid compose file: no services defined")

    val serviceNode = servicesNode.path(params.serviceName) as? ObjectNode
        ?: throw RuntimeException("Service '${params.serviceName}' not found in compose file")

    serviceNode.remove("container_name")
    serviceNode.remove("ports")
    val portsArray = serviceNode.putArray("ports")
    portsArray.add("${params.port}:3306")

    val volumesNode = serviceNode.path("volumes")
    if (volumesNode.isArray) {
        val volumesArray = volumesNode as com.fasterxml.jackson.databind.node.ArrayNode
        val newVolumesArray = mapper.createArrayNode()

        for (i in 0 until volumesArray.size()) {
            val volume = volumesArray.get(i).asText()
            val parts = volume.split(":", limit = 2)
            if (parts.isNotEmpty()) {
                val hostPath = parts[0]
                if (hostPath.startsWith("./") || hostPath.startsWith("../") || hostPath.startsWith("/")) {
                    val targetPath = if (parts.size > 1) parts[1] else ""
                    var shouldExclude = false

                    if (targetPath.contains("/docker-entrypoint-initdb.d/") && !params.excludeInitSql.isNullOrEmpty()) {
                        val filename = File(hostPath).name
                        if (params.excludeInitSql.contains(filename)) shouldExclude = true
                    }

                    if (!shouldExclude) {
                        var finalVolume = volume
                        if (hostPath.startsWith("./") || hostPath.startsWith("../")) {
                            val absolutePath = File(originalFile.parentFile, hostPath).canonicalPath
                            finalVolume = if (parts.size > 1) "$absolutePath:${parts[1]}" else absolutePath
                        }
                        newVolumesArray.add(finalVolume)
                    }
                }
            }
        }
        serviceNode.replace("volumes", newVolumesArray)
    }

    val tempFile = File.createTempFile("docker-compose-${params.prefix}-", ".yml")
    mapper.writeValue(tempFile, rootNode)
    return tempFile
}
