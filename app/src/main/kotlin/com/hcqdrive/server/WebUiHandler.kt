package com.hcqdrive.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.http.content.staticResources
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

object WebUiHandler {

    fun install(route: Route) {
        with(route) {
            staticResources(remotePath = "/", basePackage = "web", index = "index.html") {
                default("index.html")
            }
        }
    }

    suspend fun io.ktor.server.application.ApplicationCall.respondSpaFallback() {
        respondText(
            text = SPA_PLACEHOLDER,
            contentType = ContentType.Text.Html,
        )
    }

    suspend fun io.ktor.server.application.ApplicationCall.respondNotFoundJson() {
        respond(
            HttpStatusCode.NotFound,
            mapOf("error" to "Not found", "code" to "NOT_FOUND"),
        )
    }

    private const val SPA_PLACEHOLDER =
        "<!doctype html><html><body><p>HcqDrive SPA shell. The full web UI is built " +
                "separately under <code>web/</code> and copied into " +
                "<code>app/src/main/assets/web/index.html</code> at build time.</p></body></html>"
}
