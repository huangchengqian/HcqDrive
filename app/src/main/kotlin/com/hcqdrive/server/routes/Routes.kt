package com.hcqdrive.server.routes

import com.hcqdrive.server.WebUiHandler
import com.hcqdrive.server.WebUiHandler.respondNotFoundJson
import com.hcqdrive.server.WebUiHandler.respondSpaFallback
import io.ktor.server.application.call
import io.ktor.server.request.path
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.configureApiRoutes() {
    configureSystemRoutes()
    configureAuthRoutes()
    configureFsRoutes()
    configureFileRoutes()
    configureMediaRoutes()
    configureShareRoutes()
    configureDavRoutes()
}

fun Route.configureStaticFallback() {
    WebUiHandler.install(this)
    get("/{path...}") {
        val path = call.request.path()
        if (path.startsWith("/api/") || path.startsWith("/dav")) {
            call.respondNotFoundJson()
            return@get
        }
        call.respondSpaFallback()
    }
}
