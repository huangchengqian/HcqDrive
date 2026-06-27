package com.hcqdrive.auth

import android.content.Context
import android.util.Log
import com.hcqdrive.HcqDriveApp
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

object AuthService {

    private const val TAG = "HcqDrive"
    private const val CODE_TTL_MS = 5 * 60 * 1000L
    private const val PAIR_GRACE_PERIOD_MS = 1000L
    private const val TOKENS_FILE = "hcqdrive_tokens.json"

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    @Volatile
    private var currentCode: String? = null

    @Volatile
    private var currentCodeExpiresAt: Long = 0L

    private val paired: MutableMap<String, PairInfo> = ConcurrentHashMap()

    init {
        loadTokens()
    }

    private fun tokensFile(): File = File(HcqDriveApp.appContext.filesDir, TOKENS_FILE)

    @Serializable
    private data class TokenRecord(val token: String, val pairedAt: Long, val lastSeenAt: Long, val ip: String, val deviceName: String = "")

    private fun loadTokens() {
        try {
            val file = tokensFile()
            if (!file.exists()) return
            val records = json.decodeFromString<List<TokenRecord>>(file.readText())
            for (r in records) {
                paired[r.token] = PairInfo(token = r.token, pairedAt = r.pairedAt, lastSeenAt = r.lastSeenAt, ip = r.ip)
            }
            Log.i(TAG, "Loaded ${records.size} saved tokens")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load tokens: ${e.message}")
        }
    }

    private fun saveTokens() {
        try {
            val records = paired.map { (_, info) ->
                TokenRecord(token = info.token, pairedAt = info.pairedAt, lastSeenAt = info.lastSeenAt, ip = info.ip)
            }
            tokensFile().writeText(json.encodeToString(records))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save tokens: ${e.message}")
        }
    }

    fun generateCode(): String {
        val now = System.currentTimeMillis()
        if (currentCode != null && currentCodeExpiresAt - now > PAIR_GRACE_PERIOD_MS) {
            return currentCode!!
        }
        val code = Random.nextInt(100000, 1000000).toString()
        currentCode = code
        currentCodeExpiresAt = now + CODE_TTL_MS
        Log.i(TAG, "Pair code refreshed: $code (expiresAt=$currentCodeExpiresAt)")
        return code
    }

    fun currentCodeAndExpiry(): Pair<String, Long>? {
        val code = currentCode ?: return null
        if (System.currentTimeMillis() >= currentCodeExpiresAt) {
            currentCode = null
            return null
        }
        return code to currentCodeExpiresAt
    }

    fun verify(code: String, ip: String): String? {
        val active = currentCodeAndExpiry() ?: return null
        val (expected, expiresAt) = active
        if (code.length != 6 || code != expected) return null
        val token = UUID.randomUUID().toString().replace("-", "").take(32)
        val now = System.currentTimeMillis()
        paired[token] = PairInfo(
            token = token,
            pairedAt = now,
            lastSeenAt = now,
            ip = ip,
        )
        currentCode = null
        currentCodeExpiresAt = 0
        Log.i(TAG, "Pair success from $ip, token=${token.take(8)}…")
        saveTokens()
        return token
    }

    fun validateToken(token: String): Boolean {
        val info = paired[token] ?: return false
        info.lastSeenAt = System.currentTimeMillis()
        return true
    }

    fun revoke(token: String): Boolean {
        val removed = paired.remove(token) != null
        if (removed) {
            Log.i(TAG, "Revoked token=${token.take(8)}…")
            saveTokens()
        }
        return removed
    }

    fun pairedCount(): Int = paired.size

    fun isPaired(): Boolean = paired.isNotEmpty()
}
