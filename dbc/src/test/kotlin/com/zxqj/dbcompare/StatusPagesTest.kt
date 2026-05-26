package com.zxqj.dbcompare

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class StatusPagesTest {
    @Test
    fun `bad request returns datastar toast with 400`() = testApplication {
        application {
            configureStatusPages()
            routing {
                get("/api/bad-request") {
                    throw BadRequestException("bad input")
                }
            }
        }

        val response = client.get("/api/bad-request")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ContentType.Text.EventStream.withCharset(Charsets.UTF_8), response.contentType())
        assertContains(response.bodyAsText(), "bad input")
        assertContains(response.bodyAsText(), "danger")
    }

    @Test
    fun `unexpected error returns generic datastar toast with 500`() = testApplication {
        application {
            configureStatusPages()
            routing {
                get("/api/internal-error") {
                    error("boom")
                }
            }
        }

        val response = client.get("/api/internal-error")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(ContentType.Text.EventStream.withCharset(Charsets.UTF_8), response.contentType())
        assertContains(response.bodyAsText(), "Internal server error")
        assertContains(response.bodyAsText(), "danger")
    }
}
