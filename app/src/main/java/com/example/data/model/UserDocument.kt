package com.example.data.model

data class UserDocument(
    val uid: String = "",
    val name: String = "",
    val username: String = "",
    val email: String = "",
    val className: String = "",
    val section: String = "",
    val profileImage: String = "",
    val points: Int = 0,
    val xp: Int = 0,
    val level: Int = 1,
    val streak: Int = 0,
    val weeklyPoints: Int = 0,
    val monthlyPoints: Int = 0,
    val activitiesCompleted: Int = 0,
    val quizStats: Map<String, Int> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
