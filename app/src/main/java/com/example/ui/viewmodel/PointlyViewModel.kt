package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AchievementEntity
import com.example.data.database.ProfileEntity
import com.example.data.database.StudyMissionEntity
import com.example.data.database.ActivityEntity
import com.example.data.database.PomodoroStateEntity
import com.example.data.repository.PointlyRepository
import com.example.data.repository.AuthRepository
import com.example.data.api.QuizQuestion
import com.example.data.api.QuizResponse
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val message: String) : AuthUiState()
    data class Error(val error: String) : AuthUiState()
}

class PointlyViewModel(application: Application) : AndroidViewModel(application) {
    val firestoreRepository = com.example.data.repository.FirestoreRepository()
    private val repository = PointlyRepository(application, firestoreRepository)
    private val authRepository = AuthRepository(application, firestoreRepository)
    val connectivityObserver: com.example.data.repository.ConnectivityObserver = com.example.data.repository.NetworkConnectivityObserver(application)
    val syncManager = com.example.data.repository.SyncManager(application, firestoreRepository, connectivityObserver)

    val connectionStatus: StateFlow<com.example.data.repository.ConnectivityObserver.Status> = connectivityObserver.observe()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.example.data.repository.ConnectivityObserver.Status.Offline
        )

    val syncState: StateFlow<com.example.data.repository.SyncManager.SyncState> = syncManager.syncState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.example.data.repository.SyncManager.SyncState.Idle
        )

    val currentUser: StateFlow<FirebaseUser?> = authRepository.currentUserState

    private val _isEmailVerified = MutableStateFlow(false)
    val isEmailVerified: StateFlow<Boolean> = _isEmailVerified.asStateFlow()

    private val _authUiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    // Room Database Flows
    val profileState: StateFlow<ProfileEntity?> = repository.profileFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val missionsState: StateFlow<List<StudyMissionEntity>> = repository.missionsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val achievementsState: StateFlow<List<AchievementEntity>> = repository.achievementsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // UI Tab State (0 = Home/Bento, 1 = Missions, 2 = Social/Leaderboard, 3 = Profile Achievements)
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    // Study Panel & Session States
    private val _isStudySessionActive = MutableStateFlow(false)
    val isStudySessionActive: StateFlow<Boolean> = _isStudySessionActive.asStateFlow()

    private val _studyTimerSeconds = MutableStateFlow(0)
    val studyTimerSeconds: StateFlow<Int> = _studyTimerSeconds.asStateFlow()

    private val _activeStudySubject = MutableStateFlow("Physics")
    val activeStudySubject: StateFlow<String> = _activeStudySubject.asStateFlow()

    private val _activeStudyTopic = MutableStateFlow("Bernoulli's Principle")
    val activeStudyTopic: StateFlow<String> = _activeStudyTopic.asStateFlow()

    // Gemini API Quiz State
    private val _isQuizLoading = MutableStateFlow(false)
    val isQuizLoading: StateFlow<Boolean> = _isQuizLoading.asStateFlow()

    private val _currentQuiz = MutableStateFlow<QuizResponse?>(null)
    val currentQuiz: StateFlow<QuizResponse?> = _currentQuiz.asStateFlow()

    private val _currentQuestionIndex = MutableStateFlow(0)
    val currentQuestionIndex: StateFlow<Int> = _currentQuestionIndex.asStateFlow()

    private val _selectedAnswerIndex = MutableStateFlow<Int?>(null)
    val selectedAnswerIndex: StateFlow<Int?> = _selectedAnswerIndex.asStateFlow()

    private val _isAnswerChecked = MutableStateFlow(false)
    val isAnswerChecked: StateFlow<Boolean> = _isAnswerChecked.asStateFlow()

    private val _isAnswerCorrect = MutableStateFlow(false)
    val isAnswerCorrect: StateFlow<Boolean> = _isAnswerCorrect.asStateFlow()

    // Gamified Rewards Feedback State
    private val _celebrationMessage = MutableStateFlow<String?>(null)
    val celebrationMessage: StateFlow<String?> = _celebrationMessage.asStateFlow()

    private val _totalSessionXpEarned = MutableStateFlow(0)
    val totalSessionXpEarned: StateFlow<Int> = _totalSessionXpEarned.asStateFlow()

    // Editing Profile Dialog
    private val _isEditingProfile = MutableStateFlow(false)
    val isEditingProfile: StateFlow<Boolean> = _isEditingProfile.asStateFlow()

    // Simulated Collaborative Community Study Goal
    private val _communityStudyHours = MutableStateFlow(72.5f)
    val communityStudyHours: StateFlow<Float> = _communityStudyHours.asStateFlow()

    private var timerJob: Job? = null

    // Activities database flows
    val activitiesState: StateFlow<List<ActivityEntity>> = repository.activitiesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val pomodoroState: StateFlow<PomodoroStateEntity?> = repository.pomodoroStateFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val statsState: StateFlow<ActivityStats> = activitiesState.map { list ->
        val calendar = java.util.Calendar.getInstance()
        
        // Today start
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val todayStart = calendar.timeInMillis
        
        // Week start
        calendar.set(java.util.Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        val weekStart = calendar.timeInMillis
        
        // Month start
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        val monthStart = calendar.timeInMillis
        
        val completed = list.filter { it.completed }
        
        val todayTime = completed.filter { it.endTime >= todayStart }.sumOf { it.duration } / 60
        val weeklyTime = completed.filter { it.endTime >= weekStart }.sumOf { it.duration } / 60
        val monthlyTime = completed.filter { it.endTime >= monthStart }.sumOf { it.duration } / 60
        val totalTime = completed.sumOf { it.duration } / 60
        
        val activitiesCompleted = completed.size
        val avgSession = if (completed.isNotEmpty()) (completed.sumOf { it.duration } / completed.size) / 60 else 0
        
        val streak = calculateStreak(completed)
        val focusScore = if (completed.isEmpty()) 0 else (completed.filter { it.duration >= 25 * 60 }.size * 5 + avgSession / 4).coerceIn(0, 100)
        
        ActivityStats(
            todayStudyMinutes = todayTime,
            weeklyStudyMinutes = weeklyTime,
            monthlyStudyMinutes = monthlyTime,
            totalStudyMinutes = totalTime,
            currentStreak = streak,
            longestStreak = streak,
            activitiesCompleted = activitiesCompleted,
            averageSessionMinutes = avgSession,
            focusScore = focusScore
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ActivityStats()
    )

    private fun calculateStreak(activities: List<ActivityEntity>): Int {
        if (activities.isEmpty()) return 0
        val days = activities.map {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = it.endTime
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }.distinct().sortedDescending()
        
        if (days.isEmpty()) return 0
        
        val todayCal = java.util.Calendar.getInstance()
        todayCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        todayCal.set(java.util.Calendar.MINUTE, 0)
        todayCal.set(java.util.Calendar.SECOND, 0)
        todayCal.set(java.util.Calendar.MILLISECOND, 0)
        val today = todayCal.timeInMillis
        val yesterday = today - 24 * 60 * 60 * 1000
        
        if (days[0] < yesterday && days[0] != today) {
            return 0
        }
        
        var currentStreak = 1
        for (i in 0 until days.size - 1) {
            val diff = days[i] - days[i + 1]
            if (diff <= 25 * 60 * 60 * 1000) {
                currentStreak++
            } else {
                break
            }
        }
        return currentStreak
    }

    private var pomodoroJob: Job? = null

    init {
        // Initialize and pre-seed the Room DB if empty
        viewModelScope.launch {
            repository.initializeDatabase()
        }

        // Recovery check for Pomodoro
        checkAndRecoverPomodoro()

        // Listen for user state to trigger bidirectional sync
        viewModelScope.launch {
            authRepository.currentUserState.collect { user ->
                _isEmailVerified.value = user?.isEmailVerified == true
                if (user != null) {
                    try {
                        syncManager.performSync()
                    } catch (e: Exception) {
                        Log.e("PointlyViewModel", "Automatic bidirectional sync failed", e)
                    }
                }
            }
        }
    }

    fun setTab(tab: Int) {
        _currentTab.value = tab
    }

    fun setEditingProfile(editing: Boolean) {
        _isEditingProfile.value = editing
    }

    fun updateProfileName(newName: String, newTitle: String) {
        viewModelScope.launch {
            val current = profileState.value ?: return@launch
            repository.updateProfile(current.copy(name = newName, title = newTitle))
        }
    }

    // Toggle Study Session & Stopwatch
    fun startStudySession(subject: String, topic: String) {
        _activeStudySubject.value = subject
        _activeStudyTopic.value = topic
        _isStudySessionActive.value = true
        _studyTimerSeconds.value = 0
        _totalSessionXpEarned.value = 0
        _currentQuiz.value = null

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _studyTimerSeconds.value += 1
            }
        }
    }

    fun stopStudySession() {
        timerJob?.cancel()
        timerJob = null
        _isStudySessionActive.value = false

        // Update profile with study hours completed (1 sec = 1/3600 hour, let's amplify for UI satisfaction)
        viewModelScope.launch {
            val current = profileState.value ?: return@launch
            val extraHours = _studyTimerSeconds.value / 60.0f // Amplify: 1 min = 1 study hour for rewarding pacing!
            val updatedHours = (current.weeklyStudyHours + extraHours).coerceAtMost(24.0f)
            repository.updateProfile(current.copy(weeklyStudyHours = updatedHours))

            // Unlocks 'first_quiz' and potentially 'mission_master' if study completed
            if (_totalSessionXpEarned.value > 0) {
                repository.unlockAchievement("first_quiz")
            }
        }
    }

    // Dynamic Gemini-powered Quizzes
    fun fetchGeminiQuizForCurrentTopic() {
        viewModelScope.launch {
            _isQuizLoading.value = true
            _currentQuiz.value = null
            _currentQuestionIndex.value = 0
            _selectedAnswerIndex.value = null
            _isAnswerChecked.value = false

            val response = repository.generateQuiz(_activeStudyTopic.value, _activeStudySubject.value)
            _currentQuiz.value = response
            _isQuizLoading.value = false

            // Mark 'gemini_partner' achievement as earned!
            repository.unlockAchievement("gemini_partner")
        }
    }

    // Answer Quiz Question
    fun selectAnswer(index: Int) {
        if (!_isAnswerChecked.value) {
            _selectedAnswerIndex.value = index
        }
    }

    fun checkAnswer() {
        val quiz = _currentQuiz.value ?: return
        val currentQuestion = quiz.questions.getOrNull(_currentQuestionIndex.value) ?: return
        val selected = _selectedAnswerIndex.value ?: return

        _isAnswerChecked.value = true
        val correct = selected == currentQuestion.correctOption
        _isAnswerCorrect.value = correct

        if (correct) {
            _totalSessionXpEarned.value += 50
            _celebrationMessage.value = "+50 XP Earned!"
            viewModelScope.launch {
                repository.earnXp(50)
                // Trigger 'Academic Spark' badge
                repository.unlockAchievement("first_quiz")
            }
        } else {
            _celebrationMessage.value = "Incorrect. Read Explanation!"
        }
    }

    fun nextQuestion() {
        val quiz = _currentQuiz.value ?: return
        if (_currentQuestionIndex.value < quiz.questions.size - 1) {
            _currentQuestionIndex.value += 1
            _selectedAnswerIndex.value = null
            _isAnswerChecked.value = false
            _celebrationMessage.value = null
        } else {
            // Quiz completed! Award completion bonus XP!
            _totalSessionXpEarned.value += 100
            _celebrationMessage.value = "Quiz Completed! +100 XP Bonus!"
            viewModelScope.launch {
                repository.earnXp(100)
                // Complete the active fluid dynamics study mission if completed
                val missions = missionsState.value
                missions.find { it.title.contains(_activeStudySubject.value, ignoreCase = true) || it.subject.contains(_activeStudySubject.value, ignoreCase = true) }?.let { mission ->
                    repository.updateMission(mission.copy(completed = true))
                }
                
                // If all missions completed, unlock mission_master
                val updatedMissions = repository.getMissionsSync()
                if (updatedMissions.all { it.completed }) {
                    repository.unlockAchievement("mission_master")
                }
            }
            // Clear quiz to show summary
            _currentQuiz.value = null
        }
    }

    fun clearCelebrationMessage() {
        _celebrationMessage.value = null
    }

    // Increase community study hours
    fun contributeCommunityStudy() {
        _communityStudyHours.value += 2.5f
    }

    // --- COMPLETE GAMIFIED AUTHENTICATION ENGINE SYSTEM ---

    fun loginWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _authUiState.value = AuthUiState.Loading
            val result = authRepository.loginWithEmail(email, password)
            result.onSuccess { user ->
                _isEmailVerified.value = user.isEmailVerified
                _authUiState.value = AuthUiState.Success("Welcome back, ${user.email}!")
            }.onFailure { error ->
                _authUiState.value = AuthUiState.Error(error.localizedMessage ?: "Login failed. Please check credentials.")
            }
        }
    }

    fun signUpWithEmail(
        email: String,
        password: String,
        name: String,
        username: String,
        className: String,
        section: String
    ) {
        viewModelScope.launch {
            _authUiState.value = AuthUiState.Loading
            val result = authRepository.signUpWithEmail(email, password, name, username, className, section)
            result.onSuccess { user ->
                _isEmailVerified.value = user.isEmailVerified
                _authUiState.value = AuthUiState.Success("Registration successful! Verification email sent.")
            }.onFailure { error ->
                _authUiState.value = AuthUiState.Error(error.localizedMessage ?: "Sign up failed. Please try again.")
            }
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            _authUiState.value = AuthUiState.Loading
            val result = authRepository.resetPassword(email)
            result.onSuccess {
                _authUiState.value = AuthUiState.Success("Password reset instructions have been sent to $email.")
            }.onFailure { error ->
                _authUiState.value = AuthUiState.Error(error.localizedMessage ?: "Failed to send reset email.")
            }
        }
    }

    fun resendVerificationEmail() {
        viewModelScope.launch {
            _authUiState.value = AuthUiState.Loading
            val result = authRepository.sendEmailVerification()
            result.onSuccess {
                _authUiState.value = AuthUiState.Success("A fresh verification email has been sent!")
            }.onFailure { error ->
                _authUiState.value = AuthUiState.Error(error.localizedMessage ?: "Failed to send verification email.")
            }
        }
    }

    fun checkEmailVerificationStatus() {
        viewModelScope.launch {
            _authUiState.value = AuthUiState.Loading
            val result = authRepository.reloadUserAndCheckVerification()
            result.onSuccess { verified ->
                _isEmailVerified.value = verified
                if (verified) {
                    _authUiState.value = AuthUiState.Success("Email verified successfully! Welcome to Pointly!")
                } else {
                    _authUiState.value = AuthUiState.Error("Email is still not verified. Please check your inbox.")
                }
            }.onFailure { error ->
                _authUiState.value = AuthUiState.Error(error.localizedMessage ?: "Verification check failed.")
            }
        }
    }

    fun showLocalNotification(title: String, message: String) {
        val context = getApplication<Application>().applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "pointly_focus",
                "Pointly Focus Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pomodoro and focus timer updates"
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val builder = NotificationCompat.Builder(context, "pointly_focus")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    fun startPomodoro(durationMinutes: Int, isBreak: Boolean = false, activityType: String = "Study") {
        viewModelScope.launch {
            val durationSeconds = durationMinutes * 60
            val newState = PomodoroStateEntity(
                id = 1,
                durationSeconds = durationSeconds,
                remainingSeconds = durationSeconds,
                isRunning = true,
                isBreak = isBreak,
                activityType = activityType,
                lastTickTime = System.currentTimeMillis(),
                originalDurationSeconds = durationSeconds,
                skipBreak = false
            )
            repository.savePomodoroState(newState)
            startPomodoroTimerJob()
            if (isBreak) {
                showLocalNotification("Break Started", "Take a well-deserved break for $durationMinutes minutes!")
            } else {
                showLocalNotification("Focus Started", "Time to focus on $activityType for $durationMinutes minutes!")
            }
        }
    }

    fun pausePomodoro() {
        viewModelScope.launch {
            val currentState = repository.getPomodoroState() ?: return@launch
            val newState = currentState.copy(isRunning = false)
            repository.savePomodoroState(newState)
            pomodoroJob?.cancel()
            showLocalNotification("Timer Paused", "Your focus session is paused.")
        }
    }

    fun resumePomodoro() {
        viewModelScope.launch {
            val currentState = repository.getPomodoroState() ?: return@launch
            val newState = currentState.copy(isRunning = true, lastTickTime = System.currentTimeMillis())
            repository.savePomodoroState(newState)
            startPomodoroTimerJob()
            showLocalNotification("Timer Resumed", "Back in the zone!")
        }
    }

    fun skipBreak() {
        viewModelScope.launch {
            val currentState = repository.getPomodoroState() ?: return@launch
            val newState = currentState.copy(
                isBreak = false,
                remainingSeconds = currentState.originalDurationSeconds,
                durationSeconds = currentState.originalDurationSeconds,
                isRunning = false
            )
            repository.savePomodoroState(newState)
            pomodoroJob?.cancel()
            showLocalNotification("Break Skipped", "Ready to focus again!")
        }
    }

    fun stopPomodoro() {
        viewModelScope.launch {
            val currentState = repository.getPomodoroState() ?: return@launch
            val newState = currentState.copy(
                isRunning = false,
                remainingSeconds = currentState.originalDurationSeconds
            )
            repository.savePomodoroState(newState)
            pomodoroJob?.cancel()
            showLocalNotification("Timer Stopped", "Focus session stopped.")
        }
    }

    fun resetPomodoro() {
        viewModelScope.launch {
            val currentState = repository.getPomodoroState() ?: return@launch
            val newState = currentState.copy(
                isRunning = false,
                remainingSeconds = currentState.originalDurationSeconds
            )
            repository.savePomodoroState(newState)
            pomodoroJob?.cancel()
        }
    }

    fun checkAndRecoverPomodoro() {
        viewModelScope.launch {
            val state = repository.getPomodoroState()
            if (state == null) {
                // Seed initial state
                repository.savePomodoroState(PomodoroStateEntity())
            } else if (state.isRunning) {
                val now = System.currentTimeMillis()
                val elapsedSeconds = ((now - state.lastTickTime) / 1000).toInt()
                if (elapsedSeconds > 0) {
                    val newRemaining = (state.remainingSeconds - elapsedSeconds).coerceAtLeast(0)
                    val newState = state.copy(
                        remainingSeconds = newRemaining,
                        lastTickTime = now
                    )
                    repository.savePomodoroState(newState)
                    if (newRemaining == 0) {
                        handlePomodoroCompletion(newState)
                    } else {
                        startPomodoroTimerJob()
                    }
                } else {
                    startPomodoroTimerJob()
                }
            }
        }
    }

    private fun startPomodoroTimerJob() {
        pomodoroJob?.cancel()
        pomodoroJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val currentState = repository.getPomodoroState() ?: break
                if (!currentState.isRunning) break
                
                val now = System.currentTimeMillis()
                val newRemaining = (currentState.remainingSeconds - 1).coerceAtLeast(0)
                val newState = currentState.copy(
                    remainingSeconds = newRemaining,
                    lastTickTime = now
                )
                repository.savePomodoroState(newState)
                
                if (newRemaining == 0) {
                    handlePomodoroCompletion(newState)
                    break
                }
            }
        }
    }

    private fun handlePomodoroCompletion(state: PomodoroStateEntity) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (!state.isBreak) {
                // Focus complete! Create completed activity
                val durationSec = state.originalDurationSeconds
                val xpAmount = (durationSec / 300) * 10 // 10 XP per 5 min Focus
                val pointsAmount = (durationSec / 300) * 10 // 10 Points per 5 min Focus
                
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val newActivity = ActivityEntity(
                    activityId = UUID.randomUUID().toString(),
                    uid = user?.uid ?: "",
                    title = "Pomodoro ${state.activityType}",
                    type = state.activityType,
                    duration = durationSec,
                    xpEarned = xpAmount,
                    pointsEarned = pointsAmount,
                    startTime = now - durationSec * 1000L,
                    endTime = now,
                    completed = true,
                    createdAt = now,
                    updatedAt = now
                )
                
                repository.insertActivity(newActivity)
                repository.earnRewards(xpAmount, pointsAmount)
                
                showLocalNotification("Focus Session Completed", "Fantastic work! You earned $xpAmount XP & $pointsAmount Points!")
                
                // Unlock first activity achievement
                repository.unlockAchievement("first_quiz")
                
                // Set break state
                val breakMinutes = if (state.originalDurationSeconds >= 50 * 60) 10 else 5
                val breakSeconds = breakMinutes * 60
                val breakState = state.copy(
                    isBreak = true,
                    durationSeconds = breakSeconds,
                    remainingSeconds = breakSeconds,
                    isRunning = true,
                    lastTickTime = System.currentTimeMillis()
                )
                repository.savePomodoroState(breakState)
                startPomodoroTimerJob()
            } else {
                // Break complete!
                showLocalNotification("Break Finished", "Ready to focus again? Let's start a new session!")
                val focusState = state.copy(
                    isBreak = false,
                    durationSeconds = state.originalDurationSeconds,
                    remainingSeconds = state.originalDurationSeconds,
                    isRunning = false,
                    lastTickTime = 0L
                )
                repository.savePomodoroState(focusState)
            }
        }
    }

    // Direct Activities control (e.g. log direct activities / delete / edit)
    fun logActivityDirectly(title: String, type: String, durationMinutes: Int) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val durationSec = durationMinutes * 60
            val xpAmount = durationMinutes * 2
            val pointsAmount = durationMinutes * 2
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            
            val newActivity = ActivityEntity(
                activityId = UUID.randomUUID().toString(),
                uid = user?.uid ?: "",
                title = title,
                type = type,
                duration = durationSec,
                xpEarned = xpAmount,
                pointsEarned = pointsAmount,
                startTime = now - durationSec * 1000L,
                endTime = now,
                completed = true,
                createdAt = now,
                updatedAt = now
            )
            repository.insertActivity(newActivity)
            repository.earnRewards(xpAmount, pointsAmount)
            showLocalNotification("Activity Logged", "You successfully logged $title!")
        }
    }

    fun deleteActivity(activityId: String) {
        viewModelScope.launch {
            repository.deleteActivity(activityId)
        }
    }

    fun updateActivityCustom(activity: ActivityEntity) {
        viewModelScope.launch {
            repository.updateActivity(activity)
        }
    }

    fun logout() {
        viewModelScope.launch {
            _authUiState.value = AuthUiState.Loading
            val result = authRepository.logout()
            result.onSuccess {
                _isEmailVerified.value = false
                _authUiState.value = AuthUiState.Idle
            }.onFailure { error ->
                _authUiState.value = AuthUiState.Error(error.localizedMessage ?: "Failed to log out.")
            }
        }
    }

    fun clearAuthUiState() {
        _authUiState.value = AuthUiState.Idle
    }
}

data class ActivityStats(
    val todayStudyMinutes: Int = 0,
    val weeklyStudyMinutes: Int = 0,
    val monthlyStudyMinutes: Int = 0,
    val totalStudyMinutes: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val activitiesCompleted: Int = 0,
    val averageSessionMinutes: Int = 0,
    val focusScore: Int = 0
)
