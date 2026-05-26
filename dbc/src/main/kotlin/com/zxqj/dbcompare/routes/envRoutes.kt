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

internal data class SavedEnvConfig(
    val envConfig: EnvConfig,
    val optionElements: String
)

fun Route.envRoutes() {
    val configDir = File(System.getProperty("user.dir"), "config").apply { mkdirs() }
    val envDir = File(configDir, "env").apply { mkdirs() }
    val yamlMapper = ObjectMapper(YAMLFactory()).apply { findAndRegisterModules() }

    suspend fun ApplicationCall.loadEnv(name: String?) {
        val envName = name?.trim().orEmpty()
        if (envName.isBlank()) {
            respondDataStar {
                otToast("Env config name cannot be empty", variant = ToastVariant.DANGER)
            }
            return
        }

        val file = File(envDir, normalizeYamlName(envName))
        if (!file.exists()) {
            respondDataStar {
                otToast("Env config file not found: ${file.name}", variant = ToastVariant.DANGER)
            }
            return
        }

        val envConfig: EnvConfig = yamlMapper.readValue<EnvConfig>(file)
        application.log.info("Loading config from ${file.absolutePath}")

        val targetRunning = resolveRunning(envConfig.target.toDockerParams())
        val sourceRunning = resolveRunning(envConfig.source.toDockerParams())

        respondDataStar {
            fillEnvForm(envConfig)
            patchSignalsJson(
                mapOf(
                    "selectedConfig" to file.name,
                    "target" to mapOf("running" to targetRunning),
                    "source" to mapOf("running" to sourceRunning)
                )
            )
            otToast("Configuration loaded")
        }
    }

    suspend fun ApplicationCall.saveEnv(name: String?, receivedForm: Parameters? = null) {
        val form = receivedForm ?: receiveParameters()
        application.log.info("save env $form")

        val saved = try {
            saveEnvConfig(form, envDir, yamlMapper, name)
        } catch (e: BadRequestException) {
            application.log.warn(e.message)
            respondDataStar {
                otToast(e.message ?: "env config error", variant = ToastVariant.DANGER)
            }
            return
        }

        respondDataStar {
            if (saved.optionElements.isNotBlank()) {
                patchElements(
                    saved.optionElements,
                    PatchElementsOptions(selector = "#env-config-select", mode = Inner)
                )
            }
            patchSignalsJson(mapOf("selectedConfig" to "${saved.envConfig.projectName}.yml"))
            setInputValue("projectName", saved.envConfig.projectName)
            otToast("Configuration saved")
        }
    }

    suspend fun ApplicationCall.generateCompareConfig(name: String?, receivedForm: Parameters? = null) {
        val form = receivedForm ?: receiveParameters()
        application.log.info("generate config $form")

        val saved = try {
            saveAndGenerateConfig(form, envDir, configDir, yamlMapper, name)
        } catch (e: BadRequestException) {
            application.log.warn(e.message)
            respondDataStar {
                otToast(e.message ?: "env config error", variant = ToastVariant.DANGER)
            }
            return
        }

        respondDataStar {
            if (saved.optionElements.isNotBlank()) {
                patchElements(
                    saved.optionElements,
                    PatchElementsOptions(selector = "#env-config-select", mode = Inner)
                )
            }
            patchSignalsJson(mapOf("selectedConfig" to "${saved.envConfig.projectName}.yml"))
            setInputValue("projectName", saved.envConfig.projectName)
            otToast("Generated: ${saved.envConfig.projectName}.yml", variant = ToastVariant.SUCCESS)
        }
    }

    suspend fun ApplicationCall.changeRuntime(sideName: String?, start: Boolean) {
        val type = sideName ?: "target"
        if (type != "target" && type != "source") {
            respondDataStar {
                otToast("Invalid side: $type", variant = ToastVariant.DANGER)
            }
            return
        }

        val form = receiveParameters()
        application.log.info("runtime change: type=$type, start=$start, form=$form")

        val envConfig = try {
            form.toSaveEnvRequest()
        } catch (e: BadRequestException) {
            application.log.warn("runtime change: form parse error: ${e.message}")
            respondDataStar {
                otToast(e.message ?: "Invalid form data", variant = ToastVariant.DANGER)
            }
            return
        }

        val side = if (type == "source") envConfig.source else envConfig.target
        val params = side.toDockerParams()

        try {
            val effectiveParams = if (start && !params.gitRef.isNullOrEmpty()) {
                application.log.info("runtime change: preparing isolated environment for gitRef=${params.gitRef}")
                prepareIsolatedEnvironment(params)
            } else {
                params
            }
            if (start) {
                runDockerCompose(effectiveParams, "up", "-d", "--force-recreate", "--remove-orphans")
            } else {
                runDockerCompose(effectiveParams, "down")
            }
            val isRunning = resolveRunning(effectiveParams)
            respondDataStar {
                patchSignalsJson(mapOf(type to mapOf("running" to isRunning)))
                otToast("$type ${if (start) "started" else "stopped"}", variant = ToastVariant.SUCCESS)
            }
        } catch (e: Exception) {
            application.log.error("runtime change: failed", e)
            respondDataStar {
                otToast(e.message ?: "Failed to change docker runtime", variant = ToastVariant.DANGER)
            }
        }
    }

    route("/envs") {
        get {
            call.respondYamlOptions(envDir, "#env-config-select")
        }

        get("/{name}") {
            call.loadEnv(call.parameters["name"])
        }

        put("/{name}") {
            call.saveEnv(call.parameters["name"])
        }

        post("/{name}/compare-config") {
            call.generateCompareConfig(call.parameters["name"])
        }

        put("/{name}/sides/{side}/runtime") {
            call.changeRuntime(call.parameters["side"], start = true)
        }

        delete("/{name}/sides/{side}/runtime") {
            call.changeRuntime(call.parameters["side"], start = false)
        }
    }

    route("/env") {
        get {
            call.respondYamlOptions(envDir, "#env-config-select")
        }

        post {
            val payload = call.receive<EnvFilenamePayload>()
            call.loadEnv(payload.selectedConfig)
        }

        put {
            val form = call.receiveParameters()
            call.saveEnv(form["projectName"], form)
        }

        post("/generate") {
            val form = call.receiveParameters()
            call.generateCompareConfig(form["projectName"], form)
        }

        post("/docker/start") {
            val type = call.request.queryParameters["type"] ?: "target"
            call.changeRuntime(type, start = true)
        }

        post("/docker/stop") {
            val type = call.request.queryParameters["type"] ?: "target"
            call.changeRuntime(type, start = false)
        }

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
    yamlMapper: ObjectMapper,
    resourceName: String? = null
): SavedEnvConfig {
    val requestedName = resourceName?.trim()?.takeIf { it.isNotEmpty() }?.removeSuffix(".yml")
    val request = form.toSaveEnvRequest().let {
        if (requestedName == null) it else it.copy(projectName = requestedName)
    }
    envDir.mkdirs()  // Ensure directory exists
    val file = File(envDir, normalizeYamlName(request.projectName))
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
    yamlMapper: ObjectMapper,
    resourceName: String? = null
): SavedEnvConfig {
    val saved = saveEnvConfig(form, envDir, yamlMapper, resourceName)
    val compareFile = File(configDir, normalizeYamlName(saved.envConfig.projectName))
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
    println("prepareIsolatedEnvironment: codePath=${params.codePath}, composePath=${params.composePath}, gitRef=${params.gitRef}")

    val codeDir = File(params.codePath)
    val composeFile = File(params.composePath)

    if (!codeDir.exists() || !composeFile.exists()) {
        throw RuntimeException("Code or Compose file not found")
    }

    val relComposePath = composeFile.absolutePath
        .removePrefix(codeDir.absolutePath)
        .removePrefix(File.separator)
        .replace('\\', '/')  // Git always uses forward slashes
    println("prepareIsolatedEnvironment: relComposePath=$relComposePath")

    val gitShowCmd = "${params.gitRef}:$relComposePath"
    println("prepareIsolatedEnvironment: git show $gitShowCmd")
    val composeContent = runGitCommand(codeDir, "show", gitShowCmd)
    println("prepareIsolatedEnvironment: composeContent length=${composeContent.length}")

    val yamlFactory = YAMLFactory()
    val mapper = ObjectMapper(yamlFactory)
    val rootNode = mapper.readTree(composeContent)

    val volumeFiles = mutableListOf<String>()

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
                            .replace('\\', '/')  // Git always uses forward slashes
                        volumeFiles.add(relPath)
                    }
                }
            }
        }
    }
    println("prepareIsolatedEnvironment: volumeFiles=$volumeFiles")

    val tempDir = File(System.getProperty("java.io.tmpdir"), "db-compare/${params.prefix}_${params.gitRef}")
    if (!tempDir.exists()) tempDir.mkdirs()
    println("prepareIsolatedEnvironment: tempDir=${tempDir.absolutePath}")

    // Write compose file (already read from git)
    val gitRef = params.gitRef!!
    val composeTargetFile = File(tempDir, relComposePath)
    composeTargetFile.parentFile.mkdirs()
    composeTargetFile.writeText(composeContent, Charsets.UTF_8)
    println("prepareIsolatedEnvironment: wrote compose file to ${composeTargetFile.absolutePath}")

    // Extract each volume file directly
    for (filePath in volumeFiles) {
        val targetFile = File(tempDir, filePath)
        targetFile.parentFile.mkdirs()
        val content = runGitCommand(codeDir, "show", "$gitRef:$filePath")
        targetFile.writeText(content, Charsets.UTF_8)
    }
    println("prepareIsolatedEnvironment: extracted ${volumeFiles.size} volume files")

    val newComposePath = composeTargetFile.absolutePath
    println("prepareIsolatedEnvironment: newComposePath=$newComposePath")
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

private fun runDockerCompose(params: DockerParams, vararg commands: String) {
    val tempComposeFile = createModifiedComposeFile(params)
    try {
        val cmd = mutableListOf("docker-compose")
        cmd.add("-f"); cmd.add(tempComposeFile.absolutePath)
        if (params.prefix.isNotEmpty()) {
            cmd.add("-p"); cmd.add(params.prefix)
        }
        cmd.addAll(commands)
        if (params.serviceName.isNotEmpty()) cmd.add(params.serviceName)

        println("runDockerCompose: cmd=${cmd.joinToString(" ")}")
        println("runDockerCompose: tempComposeFile=${tempComposeFile.absolutePath}")
        println("runDockerCompose: codePath=${params.codePath}")

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
        println("runDockerCompose: exitCode=$exitCode, output=$output")
        if (exitCode != 0) {
            throw RuntimeException("Docker command failed with code $exitCode: $output")
        }
    } finally {
        tempComposeFile.delete()
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
