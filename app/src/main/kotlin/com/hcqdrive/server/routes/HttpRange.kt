package com.hcqdrive.server.routes

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header

/**
 * Parse a single-range HTTP `Range` header. Returns the inclusive byte range
 * to serve; malformed headers fall back to the full body. Mirrors the parser
 * used by `/api/file/raw`.
 */
internal fun parseShareRange(rangeHeader: String?, totalSize: Long): Pair<Long, Long> {
    if (rangeHeader == null || totalSize <= 0) return 0L to (totalSize - 1).coerceAtLeast(0)
    val match = Regex("bytes=(\\d*)-(\\d*)").matchEntire(rangeHeader.trim())
        ?: return 0L to (totalSize - 1)
    val (s, e) = match.destructured
    val start = s.toLongOrNull() ?: 0L
    val end = e.toLongOrNull() ?: (totalSize - 1)
    if (start > end || start >= totalSize) return 0L to (totalSize - 1)
    return start to end.coerceAtMost(totalSize - 1)
}

internal fun ApplicationCall.shareRangeHeader(): String? = request.header(HttpHeaders.Range)
