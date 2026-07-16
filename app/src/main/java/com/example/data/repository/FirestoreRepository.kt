package com.example.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreRepository {
    val db = FirebaseFirestore.getInstance()

    init {
        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build()
            db.firestoreSettings = settings
            Log.d("FirestoreRepository", "Firestore persistence cache configured successfully.")
        } catch (e: Exception) {
            Log.w("FirestoreRepository", "Firestore settings could not be updated: ${e.message}")
        }
    }

    suspend fun <T : Any> saveDocument(collection: String, documentId: String, data: T) {
        db.collection(collection).document(documentId).set(data, SetOptions.merge()).await()
    }

    suspend fun <T : Any> getDocument(collection: String, documentId: String, clazz: Class<T>): T? {
        val snapshot = db.collection(collection).document(documentId).get().await()
        return if (snapshot.exists()) snapshot.toObject(clazz) else null
    }

    suspend fun updateDocument(collection: String, documentId: String, fields: Map<String, Any>) {
        db.collection(collection).document(documentId).update(fields).await()
    }

    suspend fun deleteDocument(collection: String, documentId: String) {
        db.collection(collection).document(documentId).delete().await()
    }

    fun <T : Any> listenDocument(collection: String, documentId: String, clazz: Class<T>): Flow<T?> = callbackFlow {
        val listener = db.collection(collection).document(documentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(snapshot.toObject(clazz))
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    fun <T : Any> listenCollection(collection: String, clazz: Class<T>): Flow<List<T>> = callbackFlow {
        val listener = db.collection(collection)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(clazz) }
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun executeBatch(action: (batch: WriteBatch) -> Unit) {
        val batch = db.batch()
        action(batch)
        batch.commit().await()
    }

    suspend fun <T> runTransaction(transactionBlock: (transaction: com.google.firebase.firestore.Transaction) -> T): T {
        return db.runTransaction { transaction ->
            transactionBlock(transaction)
        }.await()
    }
}
