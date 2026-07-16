package com.example.data.model

data class LeaderboardDocument(
    val uid: String = "",
    val username: String = "",
    val name: String = "",
    val points: Int = 0,
    val rank: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
