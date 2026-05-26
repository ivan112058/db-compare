package com.zxqj.dbcompare.routes

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.zxqj.dbcompare.model.CompareRequest
import com.zxqj.dbcompare.model.DbConfig
import com.zxqj.dbcompare.model.EnvConfig
import com.zxqj.dbcompare.model.EnvDbInfo
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.io.path.createTempDirectory

class EnvRoutesTest {
    @Test
    fun `fill env form emits scripts for all env inputs`() = testApplication {
        val envConfig = EnvConfig(
            projectName = "demo",
            separateCodePath = true,
            sameDBConfig = false,
            target = EnvDbInfo(
                composePath = "D:\\compose\\target.yml",
                codePath = "D:\\code\\target",
                gitref = "dev",
                prefix = "target-prefix",
                port = 3310,
                service = "mysql-target",
                excludeInitSql = listOf("target-init.sql", "seed.sql"),
                dbConfig = DbConfig(username = "root", password = "secret", database = "target_db")
            ),
            source = EnvDbInfo(
                composePath = "D:\\compose\\source.yml",
                codePath = "D:\\code\\source",
                gitref = "main",
                prefix = "source-prefix",
                port = 3320,
                service = "mysql-source",
                excludeInitSql = listOf("source-init.sql"),
                dbConfig = DbConfig(username = "reader", password = "pwd", database = "source_db")
            )
        )

        application {
            routing {
                get("/env-form") {
                    call.respondDataStar {
                        fillEnvForm(envConfig)
                    }
                }
            }
        }

        val response = client.get("/env-form")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(body, """document.querySelector('input[name="projectName"]').value = 'demo'""")
        assertContains(body, """document.querySelector('input[name="separateCodePath"]').checked = true""")
        assertContains(body, """document.querySelector('input[name="sameDBConfig"]').checked = false""")
        assertContains(body, """document.querySelector('input[name="target.composePath"]').value = 'D:\\compose\\target.yml'""")
        assertContains(body, """document.querySelector('input[name="target.codePath"]').value = 'D:\\code\\target'""")
        assertContains(body, """document.querySelector('input[name="target.gitref"]').value = 'dev'""")
        assertContains(body, """document.querySelector('input[name="target.prefix"]').value = 'target-prefix'""")
        assertContains(body, """document.querySelector('input[name="target.port"]').value = '3310'""")
        assertContains(body, """document.querySelector('input[name="target.service"]').value = 'mysql-target'""")
        assertContains(body, """document.querySelector('input[name="target.database"]').value = 'target_db'""")
        assertContains(body, """document.querySelector('input[name="target.username"]').value = 'root'""")
        assertContains(body, """document.querySelector('input[name="target.password"]').value = 'secret'""")
        assertContains(body, """document.querySelector('oat-chip-input[name="target.excludeInitSql"]').value = ["target-init.sql","seed.sql"]""")
        assertContains(body, """document.querySelector('input[name="source.composePath"]').value = 'D:\\compose\\source.yml'""")
        assertContains(body, """document.querySelector('input[name="source.codePath"]').value = 'D:\\code\\source'""")
        assertContains(body, """document.querySelector('input[name="source.gitref"]').value = 'main'""")
        assertContains(body, """document.querySelector('input[name="source.prefix"]').value = 'source-prefix'""")
        assertContains(body, """document.querySelector('input[name="source.port"]').value = '3320'""")
        assertContains(body, """document.querySelector('input[name="source.service"]').value = 'mysql-source'""")
        assertContains(body, """document.querySelector('input[name="source.database"]').value = 'source_db'""")
        assertContains(body, """document.querySelector('input[name="source.username"]').value = 'reader'""")
        assertContains(body, """document.querySelector('input[name="source.password"]').value = 'pwd'""")
        assertContains(body, """document.querySelector('oat-chip-input[name="source.excludeInitSql"]').value = ["source-init.sql"]""")
    }

    @Test
    fun `to save env request reports missing gitref instead of compose path`() {
        val parameters = parametersOf(
            "projectName" to listOf("yian"),
            "sameDBConfig" to listOf("on"),
            "target.composePath" to listOf("""D:\code\shanghai-yian-system\yian-system-backend\script\docker\docker-compose.yml"""),
            "target.codePath" to listOf("""D:\code\shanghai-yian-system"""),
            "target.gitref" to listOf(""),
            "target.prefix" to listOf("target"),
            "target.port" to listOf("3308"),
            "target.service" to listOf("mysql"),
            "target.database" to listOf("yian-sys-base-db"),
            "target.username" to listOf("root"),
            "target.password" to listOf("Huawei@123"),
            "source.composePath" to listOf("""D:\code\shanghai-yian-system\yian-system-backend\script\docker\docker-compose.yml"""),
            "source.codePath" to listOf("""D:\code\shanghai-yian-system"""),
            "source.gitref" to listOf("main"),
            "source.prefix" to listOf("source"),
            "source.port" to listOf("3307"),
            "source.service" to listOf("mysql"),
            "source.database" to listOf("yian-sys-base-db"),
            "source.username" to listOf("root"),
            "source.password" to listOf("Huawei@123")
        )

        val error = assertFailsWith<BadRequestException> {
            parameters.toSaveEnvRequest()
        }

        assertEquals("target.gitref cannot be empty", error.message)
    }

    @Test
    fun `env config converts to compare request using localhost and env ports`() {
        val envConfig = EnvConfig(
            projectName = "demo",
            source = EnvDbInfo(
                port = 3307,
                dbConfig = DbConfig(username = "root", password = "secret", database = "source_db")
            ),
            target = EnvDbInfo(
                port = 3308,
                dbConfig = DbConfig(username = "reader", password = "pwd", database = "target_db")
            )
        )

        val compareRequest = envConfig.toGeneratedCompareRequest()

        assertEquals("localhost", compareRequest.source?.host)
        assertEquals(3307, compareRequest.source?.port)
        assertEquals("source_db", compareRequest.source?.database)
        assertEquals("localhost", compareRequest.target?.host)
        assertEquals(3308, compareRequest.target?.port)
        assertEquals("target_db", compareRequest.target?.database)
    }

    @Test
    fun `to save env request parses exclude init sql as list`() {
        val parameters = parametersOf(
            "projectName" to listOf("yian"),
            "sameDBConfig" to listOf("on"),
            "target.composePath" to listOf("""D:\code\shanghai-yian-system\yian-system-backend\script\docker\docker-compose.yml"""),
            "target.codePath" to listOf("""D:\code\shanghai-yian-system"""),
            "target.gitref" to listOf("dev"),
            "target.prefix" to listOf("target"),
            "target.port" to listOf("3308"),
            "target.service" to listOf("mysql"),
            "target.excludeInitSql" to listOf("""["init.sql","seed.sql"]"""),
            "target.database" to listOf("yian-sys-base-db"),
            "target.username" to listOf("root"),
            "target.password" to listOf("Huawei@123"),
            "source.composePath" to listOf("""D:\code\shanghai-yian-system\yian-system-backend\script\docker\docker-compose.yml"""),
            "source.codePath" to listOf("""D:\code\shanghai-yian-system"""),
            "source.gitref" to listOf("main"),
            "source.prefix" to listOf("source"),
            "source.port" to listOf("3307"),
            "source.service" to listOf("mysql"),
            "source.excludeInitSql" to listOf("""["baseline.sql"]"""),
            "source.database" to listOf("yian-sys-base-db"),
            "source.username" to listOf("root"),
            "source.password" to listOf("Huawei@123")
        )

        val request = parameters.toSaveEnvRequest()

        assertEquals(listOf("init.sql", "seed.sql"), request.target.excludeInitSql)
        assertEquals(listOf("baseline.sql"), request.source.excludeInitSql)
    }

    @Test
    fun `save and generate config writes env and compare files with same project name`() {
        val yamlMapper = ObjectMapper(YAMLFactory()).apply { findAndRegisterModules() }
        val configDir = createTempDirectory("env-generate-test").toFile()
        val envDir = configDir.resolve("env").apply { mkdirs() }
        val parameters = parametersOf(
            "projectName" to listOf("yian"),
            "sameDBConfig" to listOf("on"),
            "target.composePath" to listOf("""D:\code\shanghai-yian-system\yian-system-backend\script\docker\docker-compose.yml"""),
            "target.codePath" to listOf("""D:\code\shanghai-yian-system"""),
            "target.gitref" to listOf("dev"),
            "target.prefix" to listOf("target"),
            "target.port" to listOf("3308"),
            "target.service" to listOf("mysql"),
            "target.excludeInitSql" to listOf("""["init.sql","seed.sql"]"""),
            "target.database" to listOf("yian-sys-base-db"),
            "target.username" to listOf("root"),
            "target.password" to listOf("Huawei@123"),
            "source.composePath" to listOf("""D:\code\shanghai-yian-system\yian-system-backend\script\docker\docker-compose.yml"""),
            "source.codePath" to listOf("""D:\code\shanghai-yian-system"""),
            "source.gitref" to listOf("main"),
            "source.prefix" to listOf("source"),
            "source.port" to listOf("3307"),
            "source.service" to listOf("mysql"),
            "source.excludeInitSql" to listOf("""["baseline.sql"]"""),
            "source.database" to listOf("yian-sys-base-db"),
            "source.username" to listOf("root"),
            "source.password" to listOf("Huawei@123")
        )

        saveAndGenerateConfig(parameters, envDir, configDir, yamlMapper)

        val savedEnvConfig: EnvConfig = yamlMapper.readValue(envDir.resolve("yian.yml"))
        val savedCompareConfig: CompareRequest = yamlMapper.readValue(configDir.resolve("yian.yml"))

        assertEquals("yian", savedEnvConfig.projectName)
        assertEquals("dev", savedEnvConfig.target.gitref)
        assertEquals(listOf("init.sql", "seed.sql"), savedEnvConfig.target.excludeInitSql)
        assertEquals(listOf("baseline.sql"), savedEnvConfig.source.excludeInitSql)
        assertEquals("localhost", savedCompareConfig.source?.host)
        assertEquals(3307, savedCompareConfig.source?.port)
        assertEquals("localhost", savedCompareConfig.target?.host)
        assertEquals(3308, savedCompareConfig.target?.port)
    }

    @Test
    fun `env html contains exclude init sql chip inputs with source gated by separate code path`() {
        val html = Files.readString(Path.of("D:/code/db-compare/dbc/src/main/resources/static/env.html"))

        assertContains(html, """<oat-chip-input name="target.excludeInitSql"""")
        assertContains(html, """<oat-chip-input name="source.excludeInitSql"""")
        assertContains(html, """data-show="${'$'}_separateCodePath"""")
    }
}
