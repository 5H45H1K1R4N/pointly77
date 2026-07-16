package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.database.PointlyDatabase
import com.example.data.database.ProfileEntity
import com.example.data.model.UserDocument
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AuthRepository(
    private val context: Context,
    private val firestoreRepository: FirestoreRepository
) {
    private val auth = FirebaseAuth.getInstance()
    private val db = PointlyDatabase.getDatabase(context)
    private val dao = db.pointlyDao()

    private val _currentUserState = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUserState: StateFlow<FirebaseUser?> = _currentUserState.asStateFlow()

    init {
        // Auth State Listener for persistent sessions and real-time updates
        auth.addAuthStateListener { firebaseAuth ->
            _currentUserState.value = firebaseAuth.currentUser
            Log.d("AuthRepository", "Auth State Changed: User UID = ${firebaseAuth.currentUser?.uid}")
        }
    }

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        name: String,
        username: String,
        className: String,
        section: String
    ): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val user = authResult.user ?: throw Exception("Registration failed - no user returned.")

            // Send verification email
            try {
                user.sendEmailVerification().await()
            } catch (e: Exception) {
                Log.e("AuthRepository", "Failed to send email verification", e)
            }

            // Create firestore user document
            val userDoc = UserDocument(
                uid = user.uid,
                name = name,
                username = username,
                email = email,
                className = className,
                section = section,
                profileImage = "https://api.dicebear.com/7.x/adventurer/svg?seed=$username",
                points = 100,
                xp = 100,
                level = 1,
                streak = 1,
                weeklyPoints = 100,
                monthlyPoints = 100,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            firestoreRepository.saveDocument("users", user.uid, userDoc)

            // Initialize Room cache
            dao.insertProfile(
                ProfileEntity(
                    id = 1,
                    name = name,
                    level = 1,
                    xp = 100,
                    streak = 1,
                    rank = 10,
                    title = "Bronze Tier",
                    weeklyStudyHours = 0.0f,
                    weeklyGoalHours = 10.0f
                )
            )

            Result.success(user)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Sign up error", e)
            Result.failure(e)
        }
    }

    suspend fun loginWithEmail(email: String, password: String): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val user = authResult.user ?: throw Exception("Login failed - no user returned.")

            // Fetch and sync user document from Firestore (Source of truth)
            try {
                syncProfileFromFirestore(user.uid)
            } catch (e: Exception) {
                Log.e("AuthRepository", "Error syncing profile from Firestore during login", e)
            }

            Result.success(user)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Login error", e)
            Result.failure(e)
        }
    }

    suspend fun syncProfileFromFirestore(uid: String) = withContext(Dispatchers.IO) {
        val userDoc = firestoreRepository.getDocument("users", uid, UserDocument::class.java)
        if (userDoc != null) {
            val currentLocal = dao.getProfileSync()
            val profile = ProfileEntity(
                id = 1,
                name = userDoc.name,
                level = userDoc.level,
                xp = userDoc.xp,
                streak = userDoc.streak,
                rank = userDoc.quizStats.getOrDefault("rank", currentLocal?.rank ?: 10),
                title = if (userDoc.xp > 2000) "Gold Tier" else if (userDoc.xp > 1000) "Silver Tier" else "Bronze Tier",
                weeklyStudyHours = currentLocal?.weeklyStudyHours ?: 0.0f,
                weeklyGoalHours = currentLocal?.weeklyGoalHours ?: 10.0f,
                updatedAt = userDoc.updatedAt
            )
            dao.insertProfile(profile)
        }
    }

    suspend fun updateFirestoreUserXpAndLevel(xpEarned: Int, newLevel: Int) = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext
        try {
            firestoreRepository.runTransaction { transaction ->
                val docRef = firestoreRepository.db.collection("users").document(uid)
                val snapshot = transaction.get(docRef)
                val currentXp = snapshot.getLong("xp") ?: 0L
                val currentPoints = snapshot.getLong("points") ?: 0L

                val updates = mapOf(
                    "xp" to (currentXp + xpEarned),
                    "points" to (currentPoints + xpEarned),
                    "level" to newLevel.toLong(),
                    "updatedAt" to System.currentTimeMillis()
                )
                transaction.update(docRef, updates)
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to update Firestore user XP", e)
        }
    }

    suspend fun updateFirestoreProfileName(newName: String, newTitle: String) = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext
        try {
            val updates = mapOf(
                "name" to newName,
                "className" to newTitle,
                "updatedAt" to System.currentTimeMillis()
            )
            firestoreRepository.saveDocument("users", uid, updates)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to update profile name in Firestore", e)
        }
    }

    suspend fun resetPassword(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Password reset failed", e)
            Result.failure(e)
        }
    }

    suspend fun sendEmailVerification(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            auth.currentUser?.sendEmailVerification()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Email verification send failed", e)
            Result.failure(e)
        }
    }

    suspend fun reloadUserAndCheckVerification(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            auth.currentUser?.reload()?.await()
            val user = auth.currentUser
            _currentUserState.value = user
            Result.success(user?.isEmailVerified == true)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Reload user failed", e)
            Result.failure(e)
        }
    }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            auth.signOut()
            _currentUserState.value = null
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Sign out error", e)
            Result.failure(e)
        }
    }
}
