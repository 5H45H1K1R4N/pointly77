package com.example.data.model

data class QuizAttemptDocument(
    val attemptId: String = "",
    val userUid: String = "",
    val score: Int = 0,
    val totalQuestions: Int = 0,
    val xpEarned: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
