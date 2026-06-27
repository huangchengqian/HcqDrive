package com.hcqdrive.server.middleware

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.http.HttpStatusCode

val HcqCorsPlugin = createApplicationPlugin(name = "HcqCorsPlugin") {
    onCall { call ->
        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
        call.response.header(HttpHeaders.AccessControlAllowMethods, "GET, POST, PUT, DELETE, OPTIONS, HEAD")
        call.response.header(HttpHeaders.AccessControlAllowHeaders, "Authorization, Content-Type, X-Requested-With, Range")
        call.response.header(HttpHeaders.AccessControlExposeHeaders, "Content-Range, Content-Length, Content-Disposition")
        call.response.header(HttpHeaders.AccessControlMaxAge, "86400")
        val path = call.request.path()
        val isWebDav = path.startsWith("/dav")
        if (call.request.httpMethod == HttpMethod.Options && !isWebDav) {
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
