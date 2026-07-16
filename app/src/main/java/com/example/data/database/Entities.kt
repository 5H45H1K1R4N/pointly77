package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "John Doe",
    val level: Int = 14,
    val xp: Int = 2100, // XP out of 2500 for level up
    val streak: Int = 77,
    val rank: Int = 4,
    val title: String = "Gold Tier",
    val weeklyStudyHours: Float = 15.0f,
    val weeklyGoalHours: Float = 20.0f,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "missions")
data class StudyMissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val subject: String,
    val description: String,
    val xpReward: Int,
    val completed: Boolean = false,
    val timeRemaining: String = "04:12:45",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val icon: String, // Emoji representation
    val earned: Boolean = false,
    val earnedAt: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey val activityId: String,
    val uid: String = "",
    val title: String,
    val type: String, // Study, Reading, Workout, Meditation, Running, Custom
    val duration: Int, // duration in seconds
    val xpEarned: Int,
    val pointsEarned: Int,
    val startTime: Long,
    val endTime: Long,
    val completed: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "pomodoro_state")
data class PomodoroStateEntity(
    @PrimaryKey val id: Int = 1,
    val durationSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val isRunning: Boolean = false,
    val isBreak: Boolean = false,
    val activityType: String = "Study",
    val lastTickTime: Long = 0L,
    val originalDurationSeconds: Int = 25 * 60,
    val skipBreak: Boolean = false
)
