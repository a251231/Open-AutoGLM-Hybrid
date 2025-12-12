package com.autoglm.helper

import java.util.UUID

data class Command(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val updatedAt: Long = System.currentTimeMillis()
)
