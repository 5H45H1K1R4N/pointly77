package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.database.PointlyDatabase
import com.example.data.database.ProfileEntity
import com.example.data.model.UserDocument
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SyncManager(
    private val context: Context,
    private val firestoreRepo: FirestoreRepository,
    private val connectivityObserver: ConnectivityObserver
) {
    private val database = PointlyDatabase.getDatabase(context)
    private val dao = database.pointlyDao()
    private val auth = FirebaseAuth.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    sealed interface SyncState {
        object Idle : SyncState
        object Syncing : SyncState
        object Synced : SyncState
        data class Error(val message: String) : SyncState
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    init {
        // Automatically start observing network status to trigger sync on reconnect
        scope.launch {
            connectivityObserver.observe().collectLatest { status ->
                Log.d("SyncManager", "Network status updated in SyncManager: $status")
                if (status == ConnectivityObserver.Status.Online) {
                    performSync()
                }
            }
        }
    }

    fun triggerSync() {
        scope.launch {
            performSync()
        }
    }

    suspend fun performSync() = withContext(Dispatchers.IO) {
        val currentUser = auth.currentUser ?: run {
            _syncState.value = SyncState.Idle
            return@withContext
        }
        val uid = currentUser.uid

        _syncState.value = SyncState.Syncing
        connectivityObserver.setSyncing(true)

        try {
            Log.d("SyncManager", "Initiating background sync for: $uid")

            // 1. Fetch remote user from Firestore
            val remoteUser = firestoreRepo.getDocument("users", uid, UserDocument::class.java)

            // 2. Fetch local user from Room
            val localProfile = dao.getProfileSync()

            if (remoteUser == null) {
                // Remote does not exist: Upload local profile to server
                if (localProfile != null) {
                    val newUserDoc = UserDocument(
                        uid = uid,
                        name = localProfile.name,
                        email = currentUser.email ?: "",
                        xp = localProfile.xp,
                        level = localProfile.level,
                        streak = localProfile.streak,
                        updatedAt = localProfile.updatedAt
                    )
                    firestoreRepo.saveDocument("users", uid, newUserDoc)
                    Log.d("SyncManager", "Uploaded local profile to initialize remote user.")
                } else {
                    // Seed standard profile locally and upload
                    val initialProfile = ProfileEntity(
                        id = 1,
                        name = currentUser.displayName ?: "Pointly Student",
                        updatedAt = System.currentTimeMillis()
                    )
                    dao.insertProfile(initialProfile)
                    val newUserDoc = UserDocument(
                        uid = uid,
                        name = initialProfile.name,
                        email = currentUser.email ?: "",
                        updatedAt = initialProfile.updatedAt
                    )
                    firestoreRepo.saveDocument("users", uid, newUserDoc)
                    Log.d("SyncManager", "Seeded local profile and uploaded initial data.")
                }
            } else {
                // Conflict resolution: Latest updatedAt wins
                if (localProfile == null) {
                    // Cache is empty: Download remote to local
                    val newLocalProfile = ProfileEntity(
                        id = 1,
                        name = remoteUser.name,
                        level = remoteUser.level,
                        xp = remoteUser.xp,
                        streak = remoteUser.streak,
                        updatedAt = remoteUser.updatedAt
                    )
                    dao.insertProfile(newLocalProfile)
                    Log.d("SyncManager", "No local cache. Seeded Room using Firestore data.")
                } else {
                    Log.d("SyncManager", "Comparing stamps. Local: ${localProfile.updatedAt}, Remote: ${remoteUser.updatedAt}")
                    if (localProfile.updatedAt > remoteUser.updatedAt) {
                        // Local is newer: Upload to Firestore
                        val updatedUserDoc = remoteUser.copy(
                            name = localProfile.name,
                            xp = localProfile.xp,
                            level = localProfile.level,
                            streak = localProfile.streak,
                            updatedAt = localProfile.updatedAt
                        )
                        firestoreRepo.saveDocument("users", uid, updatedUserDoc)
                        Log.d("SyncManager", "Local changes were newer. Uploaded to remote.")
                    } else if (remoteUser.updatedAt > localProfile.updatedAt) {
                        // Remote is newer: Download to local Room
                        val updatedLocalProfile = localProfile.copy(
                            name = remoteUser.name,
                            level = remoteUser.level,
                            xp = remoteUser.xp,
                            streak = remoteUser.streak,
                            updatedAt = remoteUser.updatedAt
                        )
                        dao.insertProfile(updatedLocalProfile)
                        Log.d("SyncManager", "Remote changes were newer. Seeded Room database.")
                    } else {
                        Log.d("SyncManager", "Local database and remote store are in sync.")
                    }
                }
            }

            _syncState.value = SyncState.Synced
            Log.d("SyncManager", "Bi-directional synchronization complete.")
        } catch (e: Exception) {
            Log.e("SyncManager", "Synchronization error occurred: ${e.message}", e)
            _syncState.value = SyncState.Error(e.message ?: "Unknown sync error")
        } finally {
            connectivityObserver.setSyncing(false)
        }
    }
}
