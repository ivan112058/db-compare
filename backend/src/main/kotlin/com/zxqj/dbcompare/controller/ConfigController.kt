package com.zxqj.dbcompare.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.zxqj.dbcompare.model.CompareRequest
import org.springframework.web.bind.annotation.*
import java.io.File

@RestController
@RequestMapping("/api/config")
@CrossOrigin(origins = ["*"])
class ConfigController {

    private val configDir = File(System.getProperty("user.dir"), "config")
    private val mapper = ObjectMapper(YAMLFactory()).apply {
        findAndRegisterModules()
    }

    init {
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
    }

    @GetMapping("/list")
    fun listConfigs(): List<String> {
        return configDir.listFiles { file ->
            file.isFile && (file.name.endsWith(".yaml") || file.name.endsWith(".yml"))
        }?.map { it.name } ?: emptyList()
    }

    @GetMapping("/load")
    fun loadConfig(@RequestParam filename: String): CompareRequest {
        val file = File(configDir, filename)
        if (!file.exists()) {
            throw RuntimeException("Config file not found: $filename")
        }
        return mapper.readValue(file, CompareRequest::class.java)
    }

    @PostMapping("/save")
    fun saveConfig(@RequestParam filename: String, @RequestBody config: CompareRequest): Map<String, Any> {
        val name = if (filename.endsWith(".yaml") || filename.endsWith(".yml")) filename else "$filename.yaml"
        val file = File(configDir, name)
        mapper.writeValue(file, config)
        return mapOf("success" to true, "filename" to name)
    }
}
