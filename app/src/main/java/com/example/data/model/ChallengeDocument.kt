package com.example.data.model

data class ChallengeDocument(
    val challengeId: String = "",
    val title: String = "",
    val description: String = "",
    val xpReward: Int = 0,
    val targetValue: Int = 0,
    val type: String = "", // e.g. "daily", "weekly"
    val expiresAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
