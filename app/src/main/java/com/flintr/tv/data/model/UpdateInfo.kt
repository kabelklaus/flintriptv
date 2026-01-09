package com.flintr.tv.data.model

data class UpdateInfo(
    val hasUpdate: Boolean,
    val stableVersion: String? = null,
    val betaVersion: String? = null,
    val downloadUrl: String? = null
)
