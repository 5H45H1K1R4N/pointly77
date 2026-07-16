package com.example.data.repository

sealed interface ResultState<out T> {
    object Loading : ResultState<Nothing>
    data class Success<out T>(val data: T) : ResultState<T>
    data class Error(val exception: Throwable) : ResultState<Nothing>
    object Offline : ResultState<Nothing>
}
