package com.hcqdrive.server.routes

import com.hcqdrive.fs.DeleteRequest
import com.hcqdrive.fs.FileService
import com.hcqdrive.fs.ListResponse
import com.hcqdrive.fs.MkdirRequest
import com.hcqdrive.fs.MoveCopyRequest
import com.hcqdrive.fs.PathRequest
import com.hcqdrive.fs.RenameRequest
import com.hcqdrive.fs.RestoreRequest
import com.hcqdrive.fs.SearchResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.runBlocking

fun Route.configureFsRoutes() {
    route("/api/fs") {
        get("/list") {
            val path = call.request.queryParameters["path"] ?: "/"
            val result: ListResponse = FileService.get().list(path)
            call.respond(result)
        }
        get("/stat") {
            val path = call.request.queryParameters["path"] ?: throw IllegalArgumentException("path required")
            val entry = FileService.get().stat(path)
            if (entry == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Not found", "code" to "NOT_FOUND"))
            } else {
                call.respond(mapOf("entry" to entry))
            }
        }
        get("/search") {
            val q = call.request.queryParameters["q"] ?: ""
            val type = call.request.queryParameters["type"]
            val path = call.request.queryParameters["path"] ?: "/"
            val result: SearchResponse = FileService.get().search(q, type, path)
            call.respond(result)
        }
        post("/mkdir") {
            val req = call.receive<MkdirRequest>()
            runBlocking { FileService.get().mkdir(req.path) }
            call.respond(HttpStatusCode.Created, mapOf("ok" to true))
        }
        post("/rename") {
            val req = call.receive<RenameRequest>()
            runBlocking { FileService.get().rename(req.path, req.newName) }
            call.respond(mapOf("ok" to true))
        }
        post("/move") {
            val req = call.receive<MoveCopyRequest>()
            runBlocking { FileService.get().move(req.src, req.dst) }
            call.respond(mapOf("ok" to true))
        }
        post("/delete") {
            val req = call.receive<DeleteRequest>()
            val message = runBlocking { FileService.get().delete(req.path) }
            call.respond(mapOf("ok" to true, "trashed" to message))
        }
        post("/restore") {
            val req = call.receive<RestoreRequest>()
            val restored = runBlocking { FileService.get().restore(req.trashPath, req.originalPath) }
            call.respond(mapOf("ok" to true, "restored" to restored))
        }
    }
}
