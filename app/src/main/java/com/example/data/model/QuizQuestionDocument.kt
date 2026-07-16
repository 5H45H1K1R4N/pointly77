package com.example.data.model

data class QuizQuestionDocument(
    val id: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    val correctAnswerIndex: Int = 0,
    val explanation: String = "",
    val category: String = "",
    val xpValue: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
