package com.hcqdrive.share

import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class ShareDto(
    val token: String,
    val path: String,
    val createdAt: Long,
    val expiresAt: Long?,
    val hasPassword: Boolean,
    val downloadCount: Int,
    val isExpired: Boolean,
    val maxDownloads: Int? = null,
)

/**
 * In-memory share-link store. Persistence is intentionally out of scope for M2 —
 * all links are lost on service restart, matching the documented behaviour.
 */
object ShareService {

    private data class ShareLink(
        val token: String,
        val path: String,
        val createdAt: Long,
        val expiresAt: Long?,
        val maxDownloads: Int?,
        val passwordHash: String?,
        val downloadCount: Int,
        val isRevoked: Boolean,
    )

    private val links = ConcurrentHashMap<String, ShareLink>()

    fun create(path: String, ttlSeconds: Long?, maxDownloads: Int?, password: String?): ShareDto {
        val token = generateToken()
        val now = System.currentTimeMillis()
        val expiresAt = if (ttlSeconds != null && ttlSeconds > 0) now + ttlSeconds * 1000L else null
        val normalizedPassword = password?.takeIf { it.isNotEmpty() }
        val link = ShareLink(
            token = token,
            path = path,
            createdAt = now,
            expiresAt = expiresAt,
            maxDownloads = maxDownloads?.takeIf { it > 0 },
            passwordHash = normalizedPassword?.let { hash(it) },
            downloadCount = 0,
            isRevoked = false,
        )
        links[token] = link
        cleanup()
        return toDto(link)
    }

    /**
     * Order of checks is intentional: terminal states (revoked / expired / over-quota)
     * are reported as their respective results, and password-protected links only
     * surface [ResolveResult.PasswordRequired] when they would otherwise succeed.
     */
    fun resolve(token: String, password: String?): ResolveResult {
        val link = links[token] ?: return ResolveResult.NotFound
        if (link.isRevoked) return ResolveResult.NotFound
        if (link.expiresAt != null && System.currentTimeMillis() > link.expiresAt) return ResolveResult.Expired
        if (link.maxDownloads != null && link.downloadCount >= link.maxDownloads) return ResolveResult.MaxDownloadsReached
        if (link.passwordHash != null) {
            if (password == null) return ResolveResult.PasswordRequired
            if (hash(password) != link.passwordHash) return ResolveResult.InvalidPassword
        }
        return ResolveResult.Success(link.token, link.path)
    }

    fun recordDownload(token: String) {
        // Use compute() so concurrent downloads on the same token cannot lose a tick.
        links.computeIfPresent(token) { _, existing ->
            existing.copy(downloadCount = existing.downloadCount + 1)
        }
    }

    fun revoke(token: String): Boolean = links.remove(token) != null

    fun list(): List<ShareDto> = links.values.map { toDto(it) }

    fun cleanup() {
        val now = System.currentTimeMillis()
        links.entries.removeAll { (_, v) ->
            v.isRevoked || (v.expiresAt != null && now > v.expiresAt)
        }
    }

    private fun toDto(link: ShareLink) = ShareDto(
        token = link.token,
        path = link.path,
        createdAt = link.createdAt,
        expiresAt = link.expiresAt,
        hasPassword = link.passwordHash != null,
        downloadCount = link.downloadCount,
        isExpired = link.expiresAt != null && System.currentTimeMillis() > link.expiresAt,
        maxDownloads = link.maxDownloads,
    )

    private fun generateToken(): String =
        UUID.randomUUID().toString().replace("-", "").substring(0, 16)

    private fun hash(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(password.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    sealed class ResolveResult {
        data class Success(val token: String, val path: String) : ResolveResult()
        data object NotFound : ResolveResult()
        data object Expired : ResolveResult()
        data object MaxDownloadsReached : ResolveResult()
        data object PasswordRequired : ResolveResult()
        data object InvalidPassword : ResolveResult()
    }
}
