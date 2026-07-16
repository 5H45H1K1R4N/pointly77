package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PointlyDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun getProfileFlow(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun getProfileSync(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    @Query("SELECT * FROM missions")
    fun getMissionsFlow(): Flow<List<StudyMissionEntity>>

    @Query("SELECT * FROM missions")
    suspend fun getMissionsSync(): List<StudyMissionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMissions(missions: List<StudyMissionEntity>)

    @Update
    suspend fun updateMission(mission: StudyMissionEntity)

    @Query("SELECT * FROM achievements")
    fun getAchievementsFlow(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements")
    suspend fun getAchievementsSync(): List<AchievementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievements(achievements: List<AchievementEntity>)

    @Update
    suspend fun updateAchievement(achievement: AchievementEntity)

    @Query("SELECT * FROM activities ORDER BY startTime DESC")
    fun getActivitiesFlow(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities ORDER BY startTime DESC")
    suspend fun getActivitiesSync(): List<ActivityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: ActivityEntity)

    @Update
    suspend fun updateActivity(activity: ActivityEntity)

    @Query("DELETE FROM activities WHERE activityId = :activityId")
    suspend fun deleteActivityById(activityId: String)

    @Query("SELECT * FROM pomodoro_state WHERE id = 1")
    fun getPomodoroStateFlow(): Flow<PomodoroStateEntity?>

    @Query("SELECT * FROM pomodoro_state WHERE id = 1")
    suspend fun getPomodoroStateSync(): PomodoroStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPomodoroState(state: PomodoroStateEntity)
}
