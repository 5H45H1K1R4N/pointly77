package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.api.*
import com.example.data.database.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PointlyRepository(
    private val context: Context,
    private val firestoreRepository: FirestoreRepository
) {
    private val database = PointlyDatabase.getDatabase(context)
    private val dao = database.pointlyDao()

    val profileFlow: Flow<ProfileEntity?> = dao.getProfileFlow()
    val missionsFlow: Flow<List<StudyMissionEntity>> = dao.getMissionsFlow()
    val achievementsFlow: Flow<List<AchievementEntity>> = dao.getAchievementsFlow()

    suspend fun initializeDatabase() = withContext(Dispatchers.IO) {
        val currentProfile = dao.getProfileSync()
        if (currentProfile == null) {
            // Seed Database on first launch
            Log.d("PointlyRepository", "Database empty. Seeding initial gamified data...")
            dao.insertProfile(
                ProfileEntity(
                    id = 1,
                    name = "John Doe",
                    level = 14,
                    xp = 2100, // 84% of 2500 needed to level up
                    streak = 77,
                    rank = 4,
                    title = "Gold Tier",
                    weeklyStudyHours = 15.0f,
                    weeklyGoalHours = 20.0f
                )
            )

            dao.insertMissions(
                listOf(
                    StudyMissionEntity(
                        title = "Mastery Challenge: Fluid Dynamics",
                        subject = "Physics",
                        description = "Master Bernoulli's principle, turbulent flow, and hydrodynamic pressure.",
                        xpReward = 250,
                        completed = false,
                        timeRemaining = "04:12:45"
                    ),
                    StudyMissionEntity(
                        title = "Stellar Evolution",
                        subject = "Astrophysics",
                        description = "Learn the lifecycles of main sequence stars, red giants, and supernovas.",
                        xpReward = 180,
                        completed = false,
                        timeRemaining = "18:45:10"
                    ),
                    StudyMissionEntity(
                        title = "Recursive Logic",
                        subject = "Computer Science",
                        description = "Complete 3 algorithmic structures using recursion and dynamic programming.",
                        xpReward = 300,
                        completed = true,
                        timeRemaining = "Completed"
                    )
                )
            )

            dao.insertAchievements(
                listOf(
                    AchievementEntity("streak_7", "Flame Apprentice", "Maintained a 7-day study streak.", "🔥", true, System.currentTimeMillis()),
                    AchievementEntity("streak_77", "Streak Champion", "Achieved the legendary 77-day streak!", "☄️", true, System.currentTimeMillis()),
                    AchievementEntity("first_quiz", "Academic Spark", "Completed your first Gemini study quiz.", "💡", false, 0L),
                    AchievementEntity("level_10", "Decathlon Scholar", "Reached Student Level 10.", "👑", true, System.currentTimeMillis()),
                    AchievementEntity("gemini_partner", "AI Synthesis", "Generated a custom smart study topic with Gemini.", "🧠", false, 0L),
                    AchievementEntity("mission_master", "Mission Accomplished", "Completed all active study challenges.", "🏆", false, 0L)
                )
            )
        }
    }

    suspend fun updateProfile(profile: ProfileEntity) = withContext(Dispatchers.IO) {
        dao.updateProfile(profile)
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user != null) {
            try {
                val updates = mapOf(
                    "name" to profile.name,
                    "level" to profile.level,
                    "xp" to profile.xp,
                    "streak" to profile.streak,
                    "updatedAt" to System.currentTimeMillis()
                )
                firestoreRepository.saveDocument("users", user.uid, updates)
            } catch (e: Exception) {
                Log.e("PointlyRepository", "Error updating Firestore profile", e)
            }
        }
    }

    suspend fun updateMission(mission: StudyMissionEntity) = withContext(Dispatchers.IO) {
        dao.updateMission(mission)
    }

    suspend fun getMissionsSync(): List<StudyMissionEntity> = withContext(Dispatchers.IO) {
        dao.getMissionsSync()
    }

    suspend fun updateAchievement(achievement: AchievementEntity) = withContext(Dispatchers.IO) {
        dao.updateAchievement(achievement)
    }

    suspend fun earnXp(amount: Int): ProfileEntity? = withContext(Dispatchers.IO) {
        val profile = dao.getProfileSync() ?: return@withContext null
        var newXp = profile.xp + amount
        var newLevel = profile.level
        val xpPerLevel = 2500 // XP threshold for leveling up

        while (newXp >= xpPerLevel) {
            newXp -= xpPerLevel
            newLevel++
        }

        val updatedProfile = profile.copy(xp = newXp, level = newLevel)
        dao.updateProfile(updatedProfile)
        
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user != null) {
            try {
                val updates = mapOf(
                    "xp" to newXp,
                    "level" to newLevel,
                    "points" to newXp,
                    "updatedAt" to System.currentTimeMillis()
                )
                firestoreRepository.saveDocument("users", user.uid, updates)
            } catch (e: Exception) {
                Log.e("PointlyRepository", "Error updating Firestore XP", e)
            }
        }
        
        // Trigger level 10 achievement if applicable
        if (newLevel >= 10) {
            unlockAchievement("level_10")
        }

        updatedProfile
    }

    suspend fun unlockAchievement(id: String) = withContext(Dispatchers.IO) {
        val achievements = dao.getAchievementsSync()
        achievements.find { it.id == id && !it.earned }?.let { achievement ->
            dao.updateAchievement(achievement.copy(earned = true, earnedAt = System.currentTimeMillis()))
        }
    }

    val activitiesFlow: Flow<List<ActivityEntity>> = dao.getActivitiesFlow()

    suspend fun insertActivity(activity: ActivityEntity) = withContext(Dispatchers.IO) {
        dao.insertActivity(activity)
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user != null) {
            try {
                firestoreRepository.saveDocument("activities", activity.activityId, activity)
            } catch (e: Exception) {
                Log.e("PointlyRepository", "Failed to upload activity to Firestore, cached locally.", e)
            }
        }
        
        // Accumulate weekly study hours in user profile dynamically
        val hours = activity.duration / 3600f
        val profile = dao.getProfileSync()
        if (profile != null) {
            val updatedHours = (profile.weeklyStudyHours + hours).coerceAtMost(24.0f)
            val updatedProfile = profile.copy(weeklyStudyHours = updatedHours, updatedAt = System.currentTimeMillis())
            dao.updateProfile(updatedProfile)
        }
    }

    suspend fun deleteActivity(activityId: String) = withContext(Dispatchers.IO) {
        dao.deleteActivityById(activityId)
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user != null) {
            try {
                firestoreRepository.deleteDocument("activities", activityId)
            } catch (e: Exception) {
                Log.e("PointlyRepository", "Failed to delete activity from Firestore.", e)
            }
        }
    }

    suspend fun updateActivity(activity: ActivityEntity) = withContext(Dispatchers.IO) {
        dao.updateActivity(activity)
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user != null) {
            try {
                firestoreRepository.saveDocument("activities", activity.activityId, activity)
            } catch (e: Exception) {
                Log.e("PointlyRepository", "Failed to update activity on Firestore.", e)
            }
        }
    }

    val pomodoroStateFlow: Flow<PomodoroStateEntity?> = dao.getPomodoroStateFlow()

    suspend fun getPomodoroState(): PomodoroStateEntity? = withContext(Dispatchers.IO) {
        dao.getPomodoroStateSync()
    }

    suspend fun savePomodoroState(state: PomodoroStateEntity) = withContext(Dispatchers.IO) {
        dao.insertPomodoroState(state)
    }

    suspend fun earnRewards(xpAmount: Int, pointsAmount: Int): ProfileEntity? = withContext(Dispatchers.IO) {
        val profile = dao.getProfileSync() ?: return@withContext null
        var newXp = profile.xp + xpAmount
        var newLevel = profile.level
        val xpPerLevel = 2500

        while (newXp >= xpPerLevel) {
            newXp -= xpPerLevel
            newLevel++
        }

        val updatedProfile = profile.copy(xp = newXp, level = newLevel, updatedAt = System.currentTimeMillis())
        dao.updateProfile(updatedProfile)

        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user != null) {
            try {
                val updates = mapOf(
                    "xp" to newXp,
                    "level" to newLevel,
                    "points" to (newXp + pointsAmount),
                    "updatedAt" to System.currentTimeMillis()
                )
                firestoreRepository.saveDocument("users", user.uid, updates)
                
                val leaderboardUpdate = mapOf(
                    "uid" to user.uid,
                    "name" to (user.displayName ?: "Pointly Student"),
                    "points" to (newXp + newLevel * 100),
                    "updatedAt" to System.currentTimeMillis()
                )
                firestoreRepository.saveDocument("leaderboard", user.uid, leaderboardUpdate)
            } catch (e: Exception) {
                Log.e("PointlyRepository", "Error updating Firestore rewards", e)
            }
        }

        if (newLevel >= 10) {
            unlockAchievement("level_10")
        }

        updatedProfile
    }

    /**
     * Fetch study quiz content from Gemini API or fallback to mock data.
     */
    suspend fun generateQuiz(topic: String, subject: String): QuizResponse = withContext(Dispatchers.IO) {
        val key = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (key.isEmpty() || key == "MY_GEMINI_API_KEY") {
            Log.w("PointlyRepository", "Gemini API key is empty/placeholder. Using local simulation.")
            return@withContext getMockQuiz(topic, subject)
        }

        val prompt = """
            You are Pointly 77's custom gamified AI Learning Companion.
            Create an exciting, high-quality, 3-question educational quiz for the topic: "$topic" in "$subject".
            Each question must have exactly 4 multiple-choice options and a 0-indexed correctOption index.
            Keep explanations encouraging and concise.
            
            Return ONLY a JSON object that adheres strictly to the following schema without any surrounding backticks, markdown markers, or explanatory text:
            {
              "subject": "$subject",
              "topic": "$topic",
              "explanation": "Brief overview of the concept.",
              "questions": [
                {
                  "id": 1,
                  "question": "A concise multiple choice question",
                  "options": ["Option A", "Option B", "Option C", "Option D"],
                  "correctOption": 1,
                  "explanation": "Why Option B is correct."
                }
              ]
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            generationConfig = GeminiGenerationConfig(responseMimeType = "application/json", temperature = 0.7f)
        )

        try {
            val response = RetrofitClient.service.generateContent(key, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw Exception("Empty response from Gemini")
            
            // Clean up backticks or markdowns if returned by model
            val cleanedJson = jsonText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val adapter = RetrofitClient.moshiInstance.adapter(QuizResponse::class.java)
            adapter.fromJson(cleanedJson) ?: throw Exception("Failed to parse JSON")
        } catch (e: Exception) {
            Log.e("PointlyRepository", "Gemini API error: ${e.message}. Falling back to simulation.", e)
            getMockQuiz(topic, subject)
        }
    }

    private fun getMockQuiz(topic: String, subject: String): QuizResponse {
        // Fallback generator with premium hand-crafted educational content
        val normalizedTopic = topic.lowercase().trim()
        if (normalizedTopic.contains("fluid") || normalizedTopic.contains("bernoulli") || normalizedTopic.contains("physics")) {
            return QuizResponse(
                subject = "Physics",
                topic = "Bernoulli's Principle",
                explanation = "Bernoulli's principle states that for an inviscid flow of a nonconducting fluid, an increase in the speed of the fluid occurs simultaneously with a decrease in pressure or a decrease in the fluid's potential energy.",
                questions = listOf(
                    QuizQuestion(
                        id = 1,
                        question = "According to Bernoulli's principle, what happens to fluid pressure when fluid velocity increases?",
                        options = listOf(
                            "Pressure increases proportionally",
                            "Pressure decreases",
                            "Pressure remains completely static",
                            "Pressure drops to zero immediately"
                        ),
                        correctOption = 1,
                        explanation = "An increase in velocity is accompanied by a corresponding decrease in static pressure."
                    ),
                    QuizQuestion(
                        id = 2,
                        question = "Which real-world application directly relies on Bernoulli's principle for functioning?",
                        options = listOf(
                            "Hydroelectric dams",
                            "Aerodynamic wing lift in aircraft",
                            "Friction in automobile brakes",
                            "Convection currents in soup"
                        ),
                        correctOption = 1,
                        explanation = "The curved upper surface of an airplane wing forces air to travel faster over the top, reducing pressure and creating aerodynamic lift."
                    ),
                    QuizQuestion(
                        id = 3,
                        question = "What fluid model must be assumed for Bernoulli's equation to hold strictly true?",
                        options = listOf(
                            "Highly viscous and turbulent fluid",
                            "Incompressible and non-viscous fluid with steady flow",
                            "Supersonic gaseous plasma",
                            "Perfect vacuum without molecules"
                        ),
                        correctOption = 1,
                        explanation = "Bernoulli's equation assumes steady, incompressible, non-viscous (frictionless) laminar flow."
                    )
                )
            )
        } else {
            // General high-quality placeholder quiz
            return QuizResponse(
                subject = subject,
                topic = topic,
                explanation = "Exploring $topic helps build fundamental comprehension in $subject. This quiz tests core conceptual pillars.",
                questions = listOf(
                    QuizQuestion(
                        id = 1,
                        question = "What is the primary foundation when analyzing $topic?",
                        options = listOf(
                            "Observing repeatable empirical patterns",
                            "Ignoring mathematical formulas",
                            "Relying entirely on random intuition",
                            "None of the above"
                        ),
                        correctOption = 0,
                        explanation = "Scientific analysis begins with direct observation and repeatable empirical testing."
                    ),
                    QuizQuestion(
                        id = 2,
                        question = "Why is studying $topic essential to the broader domain of $subject?",
                        options = listOf(
                            "It simplifies memorization for grading",
                            "It unlocks deep conceptual connections and predictive frameworks",
                            "It is a filler topic without application",
                            "It replaces traditional research labs"
                        ),
                        correctOption = 1,
                        explanation = "Mastering core principles unlocks intuitive connections across multiple disciplines."
                    ),
                    QuizQuestion(
                        id = 3,
                        question = "Which action maximizes understanding of $topic?",
                        options = listOf(
                            "Passive reading without active review",
                            "Active study, interactive quiz retrieval, and AI synthesis",
                            "Rote memorization overnight",
                            "Postponing study sessions indefinitely"
                        ),
                        correctOption = 1,
                        explanation = "Active retrieval, testing, and concept explanation (Feynman Technique) are proven to solidify long-term memory."
                    )
                )
            )
        }
    }
}
