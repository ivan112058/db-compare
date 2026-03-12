package com.zxqj.dbcompare.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.zxqj.dbcompare.model.CompareRequest
import com.zxqj.dbcompare.model.EnvConfig
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/env")
@CrossOrigin(origins = ["*"])
class EnvController {

    private val configDir = File(System.getProperty("user.dir"), "config")
    private val envDir = File(configDir, "env")
    private val mapper = ObjectMapper(YAMLFactory()).apply {
        findAndRegisterModules()
    }

    init {
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
        if (!envDir.exists()) {
            envDir.mkdirs()
        }
    }

    @GetMapping("/list")
    fun listEnvs(): List<String> {
        return envDir.listFiles { file ->
            file.isFile && (file.name.endsWith(".yml"))
        }?.map { it.name } ?: emptyList()
    }

    @GetMapping("/load")
    fun loadEnv(@RequestParam filename: String): EnvConfig {
        val file = File(envDir, filename)
        if (!file.exists()) {
            throw RuntimeException("Env config file not found: $filename")
        }
        return mapper.readValue(file, EnvConfig::class.java)
    }

    @PostMapping("/save")
    fun saveEnv(@RequestBody config: EnvConfig): Map<String, Any> {
        val filename = "${config.name}.yml"
        val file = File(envDir, filename)
        mapper.writeValue(file, config)
        return mapOf("success" to true, "filename" to filename)
    }

    @PostMapping("/generate")
    fun generateConfig(@RequestBody config: EnvConfig): Map<String, Any> {
        val compareRequest = CompareRequest(
            source = config.source.dbConfig.copy(host = "localhost", port = config.source.port),
            target = config.target.dbConfig.copy(host = "localhost", port = config.target.port),
            ignoreFields = config.ignoreFields,
            excludeTables = config.excludeTables,
            ignoreDataTables = config.ignoreDataTables,
            specifiedPrimaryKeys = config.specifiedPrimaryKeys,
            excludeDataRows = config.excludeDataRows,
        )
        
        val filename = "${config.name}_local.yml"
        val file = File(configDir, filename)
        mapper.writeValue(file, compareRequest)
        
        return mapOf("success" to true, "filename" to filename)
    }

    @PostMapping("/docker/start")
    fun startDocker(@RequestBody params: DockerParams): Map<String, Any> {
        var effectiveParams = params
        if (!params.gitRef.isNullOrEmpty()) {
            try {
                effectiveParams = prepareIsolatedEnvironment(params)
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to original params or rethrow? 
                // If user requested a specific ref and we failed, we should probably fail.
                throw RuntimeException("Failed to prepare isolated environment for ref ${params.gitRef}: ${e.message}")
            }
        }
        
        // Use --force-recreate to handle container name conflicts or configuration changes
        // Use --remove-orphans to clean up old containers
        return runDockerCompose(effectiveParams, "up", "-d", "--force-recreate", "--remove-orphans")
    }

    private fun prepareIsolatedEnvironment(params: DockerParams): DockerParams {
        val codeDir = File(params.codePath)
        val composeFile = File(params.composePath)
        
        if (!codeDir.exists() || !composeFile.exists()) {
             throw RuntimeException("Code or Compose file not found")
        }
        
        // 1. Calculate relative path of compose file
        val relComposePath = composeFile.absolutePath.removePrefix(codeDir.absolutePath).removePrefix(File.separator)
        
        // 2. Read content from git (we need to find the files referenced in volumes to calculate common root)
        // But first, let's get the content of the compose file from the target ref
        val composeContent = runGitCommand(codeDir, "show", "${params.gitRef}:$relComposePath")
        
        val yamlFactory = YAMLFactory()
        val mapper = ObjectMapper(yamlFactory)
        val rootNode = mapper.readTree(composeContent)
        
        // 3. Find all referenced files
        val referencedFiles = mutableListOf<String>()
        referencedFiles.add(relComposePath)
        
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
                        // Check if relative path
                        if (hostPath.startsWith("./") || hostPath.startsWith("../")) {
                            // Resolve relative to compose file location
                            val composeDir = File(relComposePath).parent ?: ""
                            val resolvedPath = File(File(codeDir, composeDir), hostPath).canonicalPath
                            // Make it relative to repo root again
                            val relPath = resolvedPath.removePrefix(codeDir.canonicalPath).removePrefix(File.separator)
                            referencedFiles.add(relPath)
                        }
                    }
                }
            }
        }
        
        // 4. Calculate common root
        val commonRoot = findCommonRoot(referencedFiles)
        println("Common root for isolation: $commonRoot")
        
        // 5. Archive to temp dir
        val tempDir = File(System.getProperty("java.io.tmpdir"), "db-compare/${params.prefix}_${params.gitRef}")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        
        // git archive --format=tar <ref> <commonRoot> | tar -x -C <tempDir>
        // Note: git archive outputs paths relative to repo root. 
        // If we archive a subdir, it still outputs paths starting with that subdir? 
        // Yes, 'git archive HEAD foo' outputs 'foo/bar'.
        
        val archiveCmd = listOf("git", "archive", "--format=tar", params.gitRef!!, commonRoot)
        val tarCmd = listOf("tar", "-x", "-C", tempDir.absolutePath)
        
        val archivePb = ProcessBuilder(archiveCmd).directory(codeDir)
        val tarPb = ProcessBuilder(tarCmd)
        
        val archiveProcess = archivePb.start()
        val tarProcess = tarPb.start()
        
        // Pipe archive output to tar input
        archiveProcess.inputStream.transferTo(tarProcess.outputStream)
        tarProcess.outputStream.close()
        
        val archiveExit = archiveProcess.waitFor()
        val tarExit = tarProcess.waitFor()
        
        if (archiveExit != 0 || tarExit != 0) {
             throw RuntimeException("Failed to archive/extract code. Archive: $archiveExit, Tar: $tarExit")
        }
        
        // 6. Return new params
        // New code path is tempDir (but effectively it's the root of extracted files, which preserves structure)
        // Wait, if we archive 'yian-system-backend', the tempDir will contain 'yian-system-backend/...'
        // So the new compose file is at tempDir + relComposePath
        
        val newComposePath = File(tempDir, relComposePath).absolutePath
        
        return params.copy(
            codePath = tempDir.absolutePath,
            composePath = newComposePath
        )
    }

    private fun runGitCommand(dir: File, vararg args: String): String {
        val pb = ProcessBuilder("git", *args)
        pb.directory(dir)
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
        // Normalize
        if (common == "/") common = ""
        
        for (path in paths) {
            var p = File(path).parent ?: ""
            while (!p.startsWith(common) && common.isNotEmpty()) {
                common = File(common).parent ?: ""
            }
            if (common.isEmpty()) break
        }
        // Ensure it ends with / if not empty, for git archive? 
        // git archive accepts directory paths with or without slash usually, but safer without for 'src/main'
        return common
    }


    @PostMapping("/docker/stop")
    fun stopDocker(@RequestBody params: DockerParams): Map<String, Any> {
        // Use "down" to stop and remove containers
        return runDockerCompose(params, "down")
    }

    @GetMapping("/docker/status/stream", produces = ["text/event-stream"])
    fun streamDockerStatus(
        @RequestParam codePath: String,
        @RequestParam composePath: String,
        @RequestParam prefix: String,
        @RequestParam serviceName: String,
        @RequestParam port: Int
    ): SseEmitter {
        val emitter = SseEmitter(Long.MAX_VALUE)
        val executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        
        executor.scheduleAtFixedRate({
            try {
                // Use docker-compose ps to check status
                val isRunning = checkContainerStatus(codePath, composePath, prefix, serviceName)
                emitter.send(SseEmitter.event().name("status").data(mapOf("running" to isRunning)))
            } catch (e: Exception) {
                emitter.send(SseEmitter.event().name("error").data(e.message ?: "Unknown error"))
            }
        }, 0, 3, TimeUnit.SECONDS)

        emitter.onCompletion { executor.shutdown() }
        emitter.onTimeout { executor.shutdown() }
        emitter.onError { executor.shutdown() }

        return emitter
    }

    private fun checkContainerStatus(codePath: String, composePath: String, prefix: String, serviceName: String): Boolean {
        if (!File(composePath).exists()) return false
        
        val cmd = mutableListOf("docker-compose")
        if (composePath.isNotEmpty()) {
            cmd.add("-f")
            cmd.add(composePath)
        }
        if (prefix.isNotEmpty()) {
            cmd.add("-p")
            cmd.add(prefix)
        }
        cmd.add("ps")
        cmd.add("-q")
        // No need to append service name for ps -q with project name, 
        // as we want to check if any container in the project is running?
        // Actually, ps -q [SERVICE...] returns IDs. 
        // If we specified prefix (project name), docker-compose should look for containers in that project.
        // However, if the user didn't start it with that project name before, it won't find it.
        // But here we assume the user started it via our tool which uses the prefix.
        // Let's keep service name to be specific.
        cmd.add(serviceName)

        val pb = ProcessBuilder(cmd)
        if (codePath.isNotEmpty()) {
            pb.directory(File(codePath))
        }
        val process = pb.start()
        val output = process.inputStream.bufferedReader().readText()
        return output.isNotBlank()
    }

    @PostMapping("/docker/status")
    fun getDockerStatus(@RequestBody params: DockerParams): Map<String, Any> {
        try {
            val isRunning = checkContainerStatus(params.codePath, params.composePath, params.prefix, params.serviceName)
            return mapOf("success" to true, "running" to isRunning)
        } catch (e: Exception) {
            return mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
        }
    }

    private fun runDockerCompose(params: DockerParams, vararg commands: String): Map<String, Any> {
        var tempComposeFile: File? = null
        try {
            // Generate a temporary modified compose file
            tempComposeFile = createModifiedComposeFile(params)
            
            val cmd = mutableListOf("docker-compose")
            cmd.add("-f")
            cmd.add(tempComposeFile.absolutePath)
            
            if (params.prefix.isNotEmpty()) {
                cmd.add("-p")
                cmd.add(params.prefix)
            }
            cmd.addAll(commands)
            if (params.serviceName.isNotEmpty()) {
                cmd.add(params.serviceName)
            }

            println("Executing Docker command: ${cmd.joinToString(" ")}")

            val pb = ProcessBuilder(cmd)
            val env = pb.environment()
            env["DB_PORT"] = params.port.toString()
            if (!params.excludeInitSql.isNullOrEmpty()) {
                env["EXCLUDE_INIT_SQL"] = params.excludeInitSql.joinToString(",")
            }
            
            // Set working directory to compose file location if possible, or code path
            if (params.codePath.isNotEmpty()) {
                pb.directory(File(params.codePath))
            } else {
                // If code path not set, use the directory of the original compose file
                pb.directory(File(params.composePath).parentFile)
            }

            val process = pb.start()
            val output = StringBuilder()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            while (errorReader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw RuntimeException("Docker command failed with code $exitCode: $output")
            }

            return mapOf("success" to true, "output" to output.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            return mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
        } finally {
            // Cleanup temp file
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
        
        // Read original content
        val rootNode = mapper.readTree(originalFile) as com.fasterxml.jackson.databind.node.ObjectNode
        
        // Modify services
        val servicesNode = rootNode.path("services")
        if (servicesNode.isMissingNode) {
             throw RuntimeException("Invalid compose file: no services defined")
        }

        val serviceNode = servicesNode.path(params.serviceName) as? com.fasterxml.jackson.databind.node.ObjectNode
            ?: throw RuntimeException("Service '${params.serviceName}' not found in compose file")

        // 1. Remove container_name to avoid conflicts
        serviceNode.remove("container_name")

        // 2. Update ports to use the specified port
        // Remove existing ports and add the new one
        serviceNode.remove("ports")
        val portsArray = serviceNode.putArray("ports")
        portsArray.add("${params.port}:3306")

        // 3. Resolve relative paths in volumes to absolute paths
        // Update: User requested to NOT use volumes (for data persistence probably), but init SQL scripts are mounted as volumes.
        // If we remove ALL volumes, init scripts won't run.
        // Assuming user means "don't use persistent data volumes" (like mysql:/var/lib/mysql), but keep bind mounts (for config/init).
        // Let's filter volumes: keep bind mounts (containing / or .), remove named volumes.
        
        val volumesNode = serviceNode.path("volumes")
        if (volumesNode.isArray) {
            val volumesArray = volumesNode as com.fasterxml.jackson.databind.node.ArrayNode
            val newVolumesArray = mapper.createArrayNode()
            
            for (i in 0 until volumesArray.size()) {
                val volume = volumesArray.get(i).asText()
                val parts = volume.split(":", limit = 2)
                if (parts.isNotEmpty()) {
                    val hostPath = parts[0]
                    // Check if it is a bind mount (starts with . or /)
                    if (hostPath.startsWith("./") || hostPath.startsWith("../") || hostPath.startsWith("/")) {
                        // Check if this volume mounts to /docker-entrypoint-initdb.d/ and is in the exclude list
                        val targetPath = if (parts.size > 1) parts[1] else ""
                        var shouldExclude = false
                        
                        if (targetPath.contains("/docker-entrypoint-initdb.d/") && !params.excludeInitSql.isNullOrEmpty()) {
                            // Get the filename from the host path
                            val filename = File(hostPath).name
                            if (params.excludeInitSql.contains(filename)) {
                                shouldExclude = true
                                println("Excluding init SQL: $filename")
                            }
                        }

                        if (!shouldExclude) {
                            // It's a bind mount, keep it (resolving relative paths)
                            var finalVolume = volume
                            if (hostPath.startsWith("./") || hostPath.startsWith("../")) {
                                val absolutePath = File(originalFile.parentFile, hostPath).canonicalPath
                                finalVolume = if (parts.size > 1) "$absolutePath:${parts[1]}" else absolutePath
                            }
                            newVolumesArray.add(finalVolume)
                        }
                    } else {
                        // It's likely a named volume (e.g. mysql:/var/lib/mysql), skip it to avoid persistence
                        println("Skipping named volume: $volume")
                    }
                }
            }
            // Replace volumes with filtered list
            serviceNode.set<com.fasterxml.jackson.databind.node.ArrayNode>("volumes", newVolumesArray)
        }

        // Create temp file
        val tempFile = File.createTempFile("docker-compose-${params.prefix}-", ".yml")
        mapper.writeValue(tempFile, rootNode)
        return tempFile
    }

    data class DockerParams(
        val codePath: String,
        val composePath: String,
        val prefix: String,
        val serviceName: String,
        val port: Int,
        val excludeInitSql: List<String>? = null,
        val gitRef: String? = null
    )
}
