package com.flintr.tv.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Category(
    @Json(name = "category_id")
    val categoryId: String,
    @Json(name = "category_name")
    val categoryName: String,
    @Json(name = "parent_id")
    val parentId: Int = 0
)

