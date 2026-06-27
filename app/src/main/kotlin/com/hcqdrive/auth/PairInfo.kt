package com.hcqdrive.auth

import kotlinx.serialization.Serializable

@Serializable
data class PairInfo(
    val token: String,
    val pairedAt: Long,
    var lastSeenAt: Long,
    val ip: String,
)
