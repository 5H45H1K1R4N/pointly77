package com.example.data.model

data class SquadDocument(
    val squadId: String = "",
    val name: String = "",
    val description: String = "",
    val memberUids: List<String> = emptyList(),
    val points: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
