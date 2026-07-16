package com.example.data.model

data class MessageDocument(
    val messageId: String = "",
    val squadId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
