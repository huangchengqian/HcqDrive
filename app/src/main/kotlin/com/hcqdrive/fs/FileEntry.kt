package com.hcqdrive.fs

import kotlinx.serialization.Serializable

@Serializable
data class FileEntry(
    val id: String = "",
    val name: String,
    val path: String,
    val kind: String,          // "file" or "directory"
    val size: Long,
    val modifiedAt: Long,
    val mime: String? = null,
    val createdAt: Long? = null,
    val type: String? = null,  // "image"/"video"/"audio"/"document"/"archive"/"folder"/"unknown"
    val hidden: Boolean = false,
    val thumbnailUrl: String? = null,
    val childCount: Int? = null,
)
