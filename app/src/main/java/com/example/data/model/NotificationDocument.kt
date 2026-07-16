package com.example.data.model

data class NotificationDocument(
    val id: String = "",
    val recipientUid: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = "",
    val read: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
