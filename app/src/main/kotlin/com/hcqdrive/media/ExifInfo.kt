package com.hcqdrive.media

import kotlinx.serialization.Serializable

@Serializable
data class ExifInfo(
    val make: String? = null,
    val model: String? = null,
    val dateTime: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val iso: Int? = null,
    val aperture: String? = null,
    val shutterSpeed: String? = null,
    val focalLength: String? = null,
)
