package com.zxqj.dbcompare.routes

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.zxqj.dbcompare.model.CompareRequest
import com.zxqj.dbcompare.model.DbConfig
import com.zxqj.dbcompare.model.EnvConfig
import com.zxqj.dbcompare.model.EnvDbInfo
import com.zxqj.dbcompare.model.TableDiff
import com.zxqj.dbcompare.service.CompareService
import com.zxqj.dbcompare.service.DatabaseService
import com.zxqj.dbcompare.service.ResultCacheService
import com.zxqj.dbcompare.service.SqlGenerationService
import com.zxqj.dbcompare.module
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class DatastarResourceRoutesTest {
    private val yamlMapper = ObjectMapper(YAMLFactory()).apply { findAndRegisterModules() }

    @Test
    fun `page resource routes return application pages`() = testApplication {
        application {
            module()
        }

        val config = client.get("/config")
        val env = client.get("/env")
        val diff = client.get("/diff/result-123")

        assertEquals(HttpStatusCode.OK, config.status)
        assertEquals(HttpStatusCode.OK, env.status)
        assertEquals(HttpStatusCode.OK, diff.status)
        assertContains(config.bodyAsText(), "Configuration")
        assertContains(env.bodyAsText(), "Environment Management")
        assertContains(diff.bodyAsText(), "Comparison Result")
    }

    @Test
    fun `compare resource routes read tables and table details by path`() = testApplication {
        val resultCache = ResultCacheService()
        val resultId = resultCache.saveResult(
            listOf(
                TableDiff(
                    tableName = "users",
                    dataDiff = TableDiff.DataDiff(added = listOf(mapOf("id" to 1, "name" to "Ada"))),
                    primaryKeys = listOf("id")
                )
            )
        )

        application {
            routing {
                route("/api") {
                    compareRoutes(
                        CompareService(DatabaseService()),
                        DatabaseService(),
                        resultCache,
                        SqlGenerationService()
                    )
                }
            }
        }

        val tables = client.get("/api/compare/$resultId/tables")
        val detail = client.get("/api/compare/$resultId/tables/users")

        assertEquals(HttpStatusCode.OK, tables.status)
        assertContains(tables.bodyAsText(), "users")
        assertContains(tables.bodyAsText(), "_tables")
        assertEquals(HttpStatusCode.OK, detail.status)
        assertContains(detail.bodyAsText(), "_selectedTable")
        assertContains(detail.bodyAsText(), "users")
        assertContains(detail.bodyAsText(), "INSERT INTO `users`")
    }

    @Test
    fun `post compare redirects to diff resource when differences exist`() = testApplication {
        application {
            routing {
                route("/api") {
                    compareRoutes(
                        CompareService(DatabaseService()),
                        DatabaseService(),
                        ResultCacheService(),
                        SqlGenerationService(),
                        compare = {
                            listOf(
                                TableDiff(
                                    tableName = "users",
                                    dataDiff = TableDiff.DataDiff(added = listOf(mapOf("id" to 1))),
                                    primaryKeys = listOf("id")
                                )
                            )
                        }
                    )
                }
            }
        }

        val response = client.post("/api/compare") {
            setBody(FormDataContent(compareForm()))
        }

        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(body, "window.location.href = '/diff/")
    }

    @Test
    fun `config resource routes load and save named configs`() = withTemporaryUserDir { tempDir ->
        val configDir = tempDir.resolve("config").toFile().apply { mkdirs() }
        yamlMapper.writeValue(
            configDir.resolve("demo.yml"),
            CompareRequest(
                source = DbConfig(host = "source-host", port = 3310, username = "source-user", password = "source-pass", database = "source_db"),
                target = DbConfig(host = "target-host", port = 3320, username = "target-user", password = "target-pass", database = "target_db"),
                ignoreFields = listOf("updated_at")
            )
        )

        testApplication {
            application {
                routing {
                    route("/api") {
                        configRoutes()
                    }
                }
            }

            val loaded = client.get("/api/configs/demo.yml")
            assertEquals(HttpStatusCode.OK, loaded.status)
            assertContains(loaded.bodyAsText(), "source-host")
            assertContains(loaded.bodyAsText(), "target-host")
            assertContains(loaded.bodyAsText(), "updated_at")

            val saved = client.put("/api/configs/saved") {
                setBody(FormDataContent(compareForm()))
            }
            assertEquals(HttpStatusCode.OK, saved.status)
            assertContains(saved.bodyAsText(), "Configuration saved")

            val savedConfig: CompareRequest = yamlMapper.readValue(configDir.resolve("saved.yml"))
            assertEquals("saved-source", savedConfig.source?.host)
            assertEquals("saved-target", savedConfig.target?.host)
        }
    }

    @Test
    fun `env resource routes load and save named envs`() = withTemporaryUserDir { tempDir ->
        val envDir = tempDir.resolve("config").resolve("env").toFile().apply { mkdirs() }
        yamlMapper.writeValue(
            envDir.resolve("demo.yml"),
            EnvConfig(
                projectName = "demo",
                separateCodePath = true,
                sameDBConfig = false,
                source = EnvDbInfo(
                    composePath = "D:\\source\\docker-compose.yml",
                    codePath = "D:\\source",
                    gitref = "main",
                    prefix = "source",
                    port = 3320,
                    service = "mysql",
                    dbConfig = DbConfig(username = "root", password = "pwd", database = "source_db")
                ),
                target = EnvDbInfo(
                    composePath = "D:\\target\\docker-compose.yml",
                    codePath = "D:\\target",
                    gitref = "dev",
                    prefix = "target",
                    port = 3310,
                    service = "mysql",
                    dbConfig = DbConfig(username = "root", password = "pwd", database = "target_db")
                )
            )
        )

        testApplication {
            application {
                routing {
                    route("/api") {
                        envRoutes()
                    }
                }
            }

            val loaded = client.get("/api/envs/demo.yml")
            assertEquals(HttpStatusCode.OK, loaded.status)
            assertContains(loaded.bodyAsText(), "D:\\\\source")
            assertContains(loaded.bodyAsText(), "D:\\\\target")

            val saved = client.put("/api/envs/saved") {
                setBody(FormDataContent(envForm("saved")))
            }
            assertEquals(HttpStatusCode.OK, saved.status)
            assertContains(saved.bodyAsText(), "Configuration saved")

            val savedEnv: EnvConfig = yamlMapper.readValue(envDir.resolve("saved.yml"))
            assertEquals("saved", savedEnv.projectName)
            assertEquals("saved-target", savedEnv.target.prefix)
            assertEquals("saved-source", savedEnv.source.prefix)
        }
    }

    private fun compareForm(): Parameters = parametersOf(
        "source.host" to listOf("saved-source"),
        "source.port" to listOf("3310"),
        "source.username" to listOf("source-user"),
        "source.password" to listOf("source-pass"),
        "source.database" to listOf("source_db"),
        "target.host" to listOf("saved-target"),
        "target.port" to listOf("3320"),
        "target.username" to listOf("target-user"),
        "target.password" to listOf("target-pass"),
        "target.database" to listOf("target_db")
    )

    private fun envForm(projectName: String): Parameters = parametersOf(
        "projectName" to listOf(projectName),
        "separateCodePath" to listOf("on"),
        "target.composePath" to listOf("""D:\target\docker-compose.yml"""),
        "target.codePath" to listOf("""D:\target"""),
        "target.gitref" to listOf("dev"),
        "target.prefix" to listOf("saved-target"),
        "target.port" to listOf("3310"),
        "target.service" to listOf("mysql"),
        "target.database" to listOf("target_db"),
        "target.username" to listOf("root"),
        "target.password" to listOf("target-pass"),
        "source.composePath" to listOf("""D:\source\docker-compose.yml"""),
        "source.codePath" to listOf("""D:\source"""),
        "source.gitref" to listOf("main"),
        "source.prefix" to listOf("saved-source"),
        "source.port" to listOf("3320"),
        "source.service" to listOf("mysql"),
        "source.database" to listOf("source_db"),
        "source.username" to listOf("root"),
        "source.password" to listOf("source-pass")
    )

    private fun withTemporaryUserDir(block: (java.nio.file.Path) -> Unit) {
        val original = System.getProperty("user.dir")
        val tempDir = createTempDirectory("dbc-resource-routes")
        try {
            System.setProperty("user.dir", tempDir.toString())
            block(tempDir)
        } finally {
            System.setProperty("user.dir", original)
        }
    }
}
