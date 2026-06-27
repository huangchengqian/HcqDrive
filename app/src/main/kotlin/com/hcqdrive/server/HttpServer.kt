package com.hcqdrive.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.hcqdrive.server.middleware.HcqAuthPlugin
import com.hcqdrive.server.middleware.HcqCorsPlugin
import com.hcqdrive.server.middleware.HcqLoggingPlugin
import com.hcqdrive.server.routes.configureApiRoutes
import com.hcqdrive.server.routes.configureStaticFallback

@Serializable
data class ApiErrorBody(
    val error: String,
    val code: String,
)

object HttpServer {

    private const val DEFAULT_PORT = 8080
    private const val APP_VERSION = "0.1.0"
    private const val APP_NAME = "HcqDrive"

    private val startTimeMillis: Long = System.currentTimeMillis()
    private var engine: io.ktor.server.engine.EmbeddedServer<*, *>? = null

    fun appName(): String = APP_NAME

    fun version(): String = APP_VERSION

    fun uptimeSeconds(): Long = (System.currentTimeMillis() - startTimeMillis) / 1000L

    fun buildApplication(): Application.() -> Unit = {
        install(DefaultHeaders) {
            header("X-Content-Type-Options", "nosniff")
            header("X-Frame-Options", "DENY")
        }
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = false
                isLenient = true
                ignoreUnknownKeys = true
                explicitNulls = false
            })
        }
        install(PartialContent)
        install(HcqCorsPlugin)
        install(HcqAuthPlugin)
        install(HcqLoggingPlugin)
        install(StatusPages) {
            exception<IllegalArgumentException> { call, cause ->
                call.respond(HttpStatusCode.BadRequest, ApiErrorBody(cause.message ?: "Bad request", "BAD_REQUEST"))
            }
            exception<SecurityException> { call, cause ->
                call.respond(HttpStatusCode.Forbidden, ApiErrorBody(cause.message ?: "Forbidden", "FORBIDDEN"))
            }
            exception<NoSuchFileException> { call, _ ->
                call.respond(HttpStatusCode.NotFound, ApiErrorBody("Not found", "NOT_FOUND"))
            }
            exception<java.io.FileNotFoundException> { call, _ ->
                call.respond(HttpStatusCode.NotFound, ApiErrorBody("Not found", "NOT_FOUND"))
            }
            exception<UnsupportedOperationException> { call, cause ->
                call.respond(HttpStatusCode.NotImplemented, ApiErrorBody(cause.message ?: "Not supported", "NOT_SUPPORTED"))
            }
            exception<io.ktor.server.plugins.BadRequestException> { call, cause ->
                call.respond(HttpStatusCode.BadRequest, ApiErrorBody(cause.message ?: "Bad request", "BAD_REQUEST"))
            }
            exception<io.ktor.server.plugins.NotFoundException> { call, _ ->
                call.respond(HttpStatusCode.NotFound, ApiErrorBody("Not found", "NOT_FOUND"))
            }
            exception<kotlinx.serialization.SerializationException> { call, cause ->
                call.respond(HttpStatusCode.BadRequest, ApiErrorBody(cause.message ?: "Invalid payload", "BAD_REQUEST"))
            }
            exception<Throwable> { call, cause ->
                android.util.Log.e("HcqDrive", "Unhandled error: ${cause.javaClass.simpleName}: ${cause.message}", cause)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiErrorBody(cause.message ?: "Internal error", "INTERNAL_ERROR"),
                )
            }
        }
        routing {
            configureApiRoutes()
            configureStaticFallback()
        }
    }

    fun start(
        port: Int = DEFAULT_PORT,
        host: String = "0.0.0.0",
    ) {
        engine = embeddedServer(CIO, port = port, host = host) {
            buildApplication().invoke(this)
        }.start(wait = false)
    }

    fun stop(gracePeriodMillis: Long = 500, timeoutMillis: Long = 1500) {
        engine?.stop(gracePeriodMillis, timeoutMillis)
        engine = null
    }
}
