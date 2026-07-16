package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.ProfileEntity
import com.example.data.database.StudyMissionEntity
import com.example.data.database.AchievementEntity
import com.example.ui.viewmodel.PointlyViewModel
import com.example.ui.viewmodel.AuthUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointlyBentoScreen(
    viewModel: PointlyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isEmailVerified by viewModel.isEmailVerified.collectAsStateWithLifecycle()
    val authUiState by viewModel.authUiState.collectAsStateWithLifecycle()

    LaunchedEffect(authUiState) {
        when (val state = authUiState) {
            is com.example.ui.viewmodel.AuthUiState.Success -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.clearAuthUiState()
            }
            is com.example.ui.viewmodel.AuthUiState.Error -> {
                Toast.makeText(context, state.error, Toast.LENGTH_LONG).show()
                viewModel.clearAuthUiState()
            }
            else -> {}
        }
    }

    if (currentUser == null) {
        PointlyAuthScreen(viewModel = viewModel, modifier = modifier)
        return
    }

    if (!isEmailVerified) {
        PointlyVerificationScreen(viewModel = viewModel, modifier = modifier)
        return
    }

    val profile by viewModel.profileState.collectAsStateWithLifecycle()
    val missions by viewModel.missionsState.collectAsStateWithLifecycle()
    val achievements by viewModel.achievementsState.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()

    val isStudyActive by viewModel.isStudySessionActive.collectAsStateWithLifecycle()
    val isEditingProfile by viewModel.isEditingProfile.collectAsStateWithLifecycle()

    // Dialog state for Classmate invitations
    var showInviteDialog by remember { mutableStateOf(false) }

    // Floating celebration effect triggers on achievement message
    val celebrationMessage by viewModel.celebrationMessage.collectAsStateWithLifecycle()
    LaunchedEffect(celebrationMessage) {
        celebrationMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearCelebrationMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFFFEF7FF), // Bento theme soft canvas
        bottomBar = {
            PointlyBottomNavigation(
                currentTab = currentTab,
                onTabSelected = { viewModel.setTab(it) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main views based on Tab state
            when (currentTab) {
                0 -> BentoHomeTab(
                    profile = profile,
                    missions = missions,
                    viewModel = viewModel,
                    onInviteClick = { showInviteDialog = true }
                )
                1 -> MissionsTab(
                    missions = missions,
                    viewModel = viewModel
                )
                2 -> SocialLeaderboardTab(
                    profile = profile,
                    viewModel = viewModel,
                    onInviteClick = { showInviteDialog = true }
                )
                3 -> ProfileAchievementsTab(
                    profile = profile,
                    achievements = achievements,
                    viewModel = viewModel
                )
            }

            // Animated Study Session & Gemini Quiz overlay
            AnimatedVisibility(
                visible = isStudyActive,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                StudySessionOverlay(
                    viewModel = viewModel,
                    onClose = { viewModel.stopStudySession() }
                )
            }

            // Edit Profile Dialog
            if (isEditingProfile && profile != null) {
                var editName by remember { mutableStateOf(profile!!.name) }
                var editTitle by remember { mutableStateOf(profile!!.title) }

                AlertDialog(
                    onDismissRequest = { viewModel.setEditingProfile(false) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.updateProfileName(editName, editTitle)
                                viewModel.setEditingProfile(false)
                            },
                            modifier = Modifier.testTag("save_profile_button")
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.setEditingProfile(false) }) {
                            Text("Cancel")
                        }
                    },
                    title = { Text("Update Student ID", fontWeight = FontWeight.SemiBold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text("Student Name") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_name_input")
                            )
                            OutlinedTextField(
                                value = editTitle,
                                onValueChange = { editTitle = it },
                                label = { Text("Academic Title / Tier") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    containerColor = Color.White
                )
            }

            // Invite Classmates Dialog
            if (showInviteDialog) {
                AlertDialog(
                    onDismissRequest = { showInviteDialog = false },
                    confirmButton = {
                        Button(
                            onClick = {
                                showInviteDialog = false
                                Toast.makeText(context, "Invite link copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("copy_invite_link_button")
                        ) {
                            Text("Copy Invite Link")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showInviteDialog = false }) {
                            Text("Dismiss")
                        }
                    },
                    title = { Text("Invite Study Squad", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Invite your classmates to earn dynamic team multipliers! Earn +500 XP when they complete their first quiz.", fontSize = 14.sp, color = Color(0xFF49454F))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Study Squad Code:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF6750A4))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
                                border = BorderStroke(1.dp, Color(0xFFE8DEF8))
                            ) {
                                Text(
                                    text = "POINTLY-77-SQUAD-JOIN",
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = Color(0xFF21005D)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    containerColor = Color.White
                )
            }
        }
    }
}

@Composable
fun PointlyBottomNavigation(
    currentTab: Int,
    onTabSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = Color(0xFFF3EDF7),
        tonalElevation = 8.dp
    ) {
        val items = listOf(
            Triple(0, "Home", Icons.Rounded.Home),
            Triple(1, "Missions", Icons.Rounded.Assignment),
            Triple(2, "Social", Icons.Rounded.People),
            Triple(3, "Profile", Icons.Rounded.Person)
        )

        items.forEach { (index, label, icon) ->
            NavigationBarItem(
                selected = currentTab == index,
                onClick = { onTabSelected(index) },
                icon = { Icon(imageVector = icon, contentDescription = label) },
                label = { Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF1D192B),
                    selectedTextColor = Color(0xFF6750A4),
                    indicatorColor = Color(0xFFE8DEF8),
                    unselectedIconColor = Color(0xFF49454F),
                    unselectedTextColor = Color(0xFF49454F)
                ),
                modifier = Modifier.testTag("nav_item_$label")
            )
        }
    }
}

// ==========================================
// HOME TAB - BENTO GRID VIEW
// ==========================================
@Composable
fun BentoHomeTab(
    profile: ProfileEntity?,
    missions: List<StudyMissionEntity>,
    viewModel: PointlyViewModel,
    onInviteClick: () -> Unit
) {
    val activeMission = missions.find { !it.completed } ?: missions.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- BENTO HEADER ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "CURRENT STATUS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6750A4),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "Pointly 77",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D1B20)
                )
            }

            // Interactive Profile Badge
            profile?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .clickable { viewModel.setEditingProfile(true) }
                        .testTag("profile_badge_clickable")
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "LVL ${it.level}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF21005D),
                            modifier = Modifier
                                .background(Color(0xFFEADDFF), RoundedCornerShape(100.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                        Text(
                            text = it.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF49454F).copy(alpha = 0.8f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE8DEF8))
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (it.name.length >= 2) it.name.substring(0, 2).uppercase() else "JD",
                            color = Color(0xFF6750A4),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        // --- BENTO GRID BLOCK 1: ACTIVE MISSION (FULL WIDTH) ---
        activeMission?.let { mission ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("active_mission_card"),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF6750A4)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            // Circular subtle ambient glow behind the card
                            drawCircle(
                                color = Color.White.copy(alpha = 0.08f),
                                radius = 220.dp.toPx(),
                                center = Offset(size.width * 0.95f, size.height * 0.1f)
                            )
                        }
                        .padding(20.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFD0BCFF))
                                )
                                Text(
                                    text = "ACTIVE MISSION",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    color = Color(0xFFD0BCFF)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = mission.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Earn +${mission.xpReward} XP & Custom Badges",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Button(
                                onClick = { viewModel.startStudySession(mission.subject, mission.title) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF6750A4)
                                ),
                                shape = RoundedCornerShape(100.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                                modifier = Modifier.testTag("resume_study_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = "AI Partner", modifier = Modifier.size(16.dp))
                                    Text("Resume Study", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Due in",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = mission.timeRemaining,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- BENTO GRID ROW 2: STREAK (SPLIT 2 COL) & MINI STATS STACK ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // LEFT COLUMN: STREAK CARD (COL SPAN 2)
            profile?.let {
                Card(
                    modifier = Modifier
                        .weight(1.1f)
                        .height(136.dp)
                        .clickable {
                            // Boost streak function for engagement fun
                            viewModel.startStudySession("Physics", "Bernoulli's Principle")
                        },
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEADDFF)),
                    border = BorderStroke(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🔥",
                            fontSize = 32.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${it.streak}",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF21005D),
                            lineHeight = 32.sp
                        )
                        Text(
                            text = "DAY STREAK",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF21005D),
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }

            // RIGHT COLUMN: RANK CARD & PROGRESS CARD (STACKED)
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .height(136.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Stack A: Rank card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable { viewModel.setTab(2) }, // Goes to leaderboards
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFF79747E).copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEF7FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🏆", fontSize = 16.sp)
                        }
                        Column {
                            Text(
                                text = "RANK",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF49454F)
                            )
                            Text(
                                text = "#${profile?.rank ?: 4}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D1B20)
                            )
                        }
                    }
                }

                // Stack B: Level progress metric card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable { viewModel.setTab(3) }, // Goes to profile/stats
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
                    border = BorderStroke(1.dp, Color(0xFFE8DEF8))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8DEF8)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⚡", fontSize = 16.sp)
                        }
                        Column {
                            Text(
                                text = "XP PROG",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF49454F)
                            )
                            profile?.let {
                                val progressPercent = ((it.xp.toFloat() / 2500f) * 100).toInt()
                                Text(
                                    text = "$progressPercent%",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1D1B20)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- BENTO GRID BLOCK 3: SOCIAL ACTIVITY & INVITE (COL SPAN 4) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFF79747E).copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Overlapping Avatar Stack
                    Box(modifier = Modifier.width(72.dp)) {
                        val colors = listOf(Color(0xFF6750A4), Color(0xFF7D5260), Color(0xFF21005D))
                        val initials = listOf("S", "M", "A")
                        for (i in 0 until 3) {
                            Box(
                                modifier = Modifier
                                    .padding(start = (i * 18).dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(colors[i])
                                    .border(1.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    initials[i],
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "Sarah & 2 others studying now",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1D1B20),
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }

                IconButton(
                    onClick = onInviteClick,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFFF3EDF7), CircleShape)
                        .testTag("invite_squad_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Invite Squad",
                        tint = Color(0xFF6750A4),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // --- BENTO GRID BLOCK 4: WEEKLY GOAL METRICS (COL SPAN 4) ---
        profile?.let { prof ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.contributeCommunityStudy() }, // Contribution interactive click
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF49454F)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "WEEKLY STUDY GOAL",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.6f),
                            letterSpacing = 1.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val ratio = (prof.weeklyStudyHours / prof.weeklyGoalHours).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFFD0BCFF),
                                trackColor = Color.White.copy(alpha = 0.2f)
                            )
                            Text(
                                text = "%.1fh/%dh".format(prof.weeklyStudyHours, prof.weeklyGoalHours.toInt()),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📊", fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// ACTIVE STUDY OVERLAY & GEMINI COMPANION PANEL
// ==========================================
@Composable
fun StudySessionOverlay(
    viewModel: PointlyViewModel,
    onClose: () -> Unit
) {
    val subject by viewModel.activeStudySubject.collectAsStateWithLifecycle()
    val topic by viewModel.activeStudyTopic.collectAsStateWithLifecycle()
    val timerSeconds by viewModel.studyTimerSeconds.collectAsStateWithLifecycle()

    val isQuizLoading by viewModel.isQuizLoading.collectAsStateWithLifecycle()
    val currentQuiz by viewModel.currentQuiz.collectAsStateWithLifecycle()
    val activeQuestionIndex by viewModel.currentQuestionIndex.collectAsStateWithLifecycle()
    val selectedAnswer by viewModel.selectedAnswerIndex.collectAsStateWithLifecycle()
    val isAnswerChecked by viewModel.isAnswerChecked.collectAsStateWithLifecycle()
    val isAnswerCorrect by viewModel.isAnswerCorrect.collectAsStateWithLifecycle()
    val sessionXp by viewModel.totalSessionXpEarned.collectAsStateWithLifecycle()

    val formattedTime = remember(timerSeconds) {
        val mins = timerSeconds / 60
        val secs = timerSeconds % 60
        "%02d:%02d".format(mins, secs)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFEF7FF))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Overlay Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("close_study_overlay_button")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close Study Overlay")
                }
                Column {
                    Text("ACTIVE STUDY SESSION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6750A4), letterSpacing = 1.sp)
                    Text("$subject - $topic", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D1B20))
                }
            }

            // Live Timer pill
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEADDFF)),
                shape = RoundedCornerShape(100.dp)
            ) {
                Text(
                    text = formattedTime,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF21005D),
                    fontSize = 15.sp
                )
            }
        }

        // Live stats in study panel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFF79747E).copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Session XP", fontSize = 11.sp, color = Color(0xFF49454F))
                    Text("+$sessionXp XP", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6750A4))
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFF79747E).copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("AI Partner Status", fontSize = 11.sp, color = Color(0xFF49454F))
                    Text("Synced", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF21005D))
                }
            }
        }

        // STUDY BODY: Toggle between start prompt and Gemini interactive quiz
        if (currentQuiz == null && !isQuizLoading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFFE8DEF8))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🧠", fontSize = 48.sp)
                    Text(
                        text = "Gemini AI Study Assistant",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF21005D)
                    )
                    Text(
                        text = "Ready to test your comprehension? Let Gemini synthesize a dynamic, gamified quiz for '$topic' based on real academic parameters.",
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        color = Color(0xFF49454F),
                        lineHeight = 18.sp
                    )

                    Button(
                        onClick = { viewModel.fetchGeminiQuizForCurrentTopic() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("generate_quiz_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = "Spark", modifier = Modifier.size(18.dp))
                            Text("Generate Gemini Quiz", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (isQuizLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = Color(0xFF6750A4))
                    Text(
                        text = "Gemini is building your challenge...",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = Color(0xFF49454F)
                    )
                }
            }
        } else {
            // RENDER ACTIVE GEMINI QUIZ
            val quiz = currentQuiz!!
            val currentQuestion = quiz.questions.getOrNull(activeQuestionIndex)

            if (currentQuestion != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Quiz Progress Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Question ${activeQuestionIndex + 1} of ${quiz.questions.size}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF6750A4)
                        )

                        // Linear progress indicator for questions
                        val progress = (activeQuestionIndex + 1).toFloat() / quiz.questions.size.toFloat()
                        LinearProgressIndicator(
                            progress = { progress },
                            color = Color(0xFF6750A4),
                            trackColor = Color(0xFFE8DEF8),
                            modifier = Modifier
                                .width(100.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }

                    // Question Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFF79747E).copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = currentQuestion.question,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D1B20)
                            )
                        }
                    }

                    // Options list
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        currentQuestion.options.forEachIndexed { optIndex, optionText ->
                            val isSelected = selectedAnswer == optIndex
                            val optionBorderColor = when {
                                isAnswerChecked && optIndex == currentQuestion.correctOption -> Color(0xFF4CAF50) // Green
                                isAnswerChecked && isSelected && !isAnswerCorrect -> Color(0xFFF44336) // Red
                                isSelected -> Color(0xFF6750A4)
                                else -> Color(0xFF79747E).copy(alpha = 0.2f)
                            }
                            val optionBgColor = when {
                                isAnswerChecked && optIndex == currentQuestion.correctOption -> Color(0xFFE8F5E9)
                                isAnswerChecked && isSelected && !isAnswerCorrect -> Color(0xFFFFEBEE)
                                isSelected -> Color(0xFFF3EDF7)
                                else -> Color.White
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isAnswerChecked) { viewModel.selectAnswer(optIndex) }
                                    .testTag("quiz_option_$optIndex"),
                                colors = CardDefaults.cardColors(containerColor = optionBgColor),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.5.dp, optionBorderColor)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val bubbleBg = if (isSelected) Color(0xFF6750A4) else Color(0xFFF3EDF7)
                                    val bubbleText = if (isSelected) Color.White else Color(0xFF49454F)
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(bubbleBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = ('A' + optIndex).toString(),
                                            color = bubbleText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Text(
                                        text = optionText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF1D1B20)
                                    )
                                }
                            }
                        }
                    }

                    // Checked state explanation box
                    if (isAnswerChecked) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isAnswerCorrect) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(if (isAnswerCorrect) "✅" else "❌", fontSize = 18.sp)
                                Column {
                                    Text(
                                        text = if (isAnswerCorrect) "Correct answer!" else "Incorrect",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isAnswerCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)
                                    )
                                    Text(
                                        text = currentQuestion.explanation,
                                        fontSize = 13.sp,
                                        color = Color(0xFF49454F),
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }

                    // Navigation Action CTA button
                    Button(
                        onClick = {
                            if (!isAnswerChecked) {
                                viewModel.checkAnswer()
                            } else {
                                viewModel.nextQuestion()
                            }
                        },
                        enabled = selectedAnswer != null,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("quiz_action_button")
                    ) {
                        val ctaText = when {
                            !isAnswerChecked -> "Check Answer"
                            activeQuestionIndex < quiz.questions.size - 1 -> "Next Question"
                            else -> "Complete & Claim Rewards"
                        }
                        Text(ctaText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// MISSIONS TAB
// ==========================================
@Composable
fun MissionsTab(
    missions: List<StudyMissionEntity>,
    viewModel: PointlyViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Weekly Challenges", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1D1B20))
        Text("Complete active missions in the sandbox to boost your GPA streak multipliers and unlock cosmetic profile tiers.", fontSize = 13.sp, color = Color(0xFF49454F))

        missions.forEach { mission ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (mission.completed) Color(0xFFE8F5E9) else Color.White
                ),
                border = BorderStroke(
                    1.dp,
                    if (mission.completed) Color(0xFF81C784).copy(alpha = 0.4f) else Color(0xFF79747E).copy(alpha = 0.15f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mission.subject.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6750A4),
                            letterSpacing = 1.sp
                        )
                        if (mission.completed) {
                            Text("COMPLETED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        } else {
                            Text(mission.timeRemaining, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF49454F))
                        }
                    }

                    Column {
                        Text(mission.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1D1B20))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(mission.description, fontSize = 13.sp, color = Color(0xFF49454F), lineHeight = 18.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("+${mission.xpReward} XP Reward", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF21005D))
                        if (!mission.completed) {
                            Button(
                                onClick = { viewModel.startStudySession(mission.subject, mission.title) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Study", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SOCIAL TAB (LEADERBOARDS & COMMUNITY GOAL)
// ==========================================
@Composable
fun SocialLeaderboardTab(
    profile: ProfileEntity?,
    viewModel: PointlyViewModel,
    onInviteClick: () -> Unit
) {
    val communityHours by viewModel.communityStudyHours.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Social Arena", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1D1B20))

        // Collaborative Class Challenge
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF21005D))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("COMMUNITY SQUAD CHALLENGE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF), letterSpacing = 1.sp)
                        Text("Active Goal: 100h Study Time", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    }
                    Text("🚀", fontSize = 24.sp)
                }

                val ratio = (communityHours / 100f).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { ratio },
                    color = Color(0xFFD0BCFF),
                    trackColor = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Current: %.1fh / 100h".format(communityHours),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Button(
                        onClick = onInviteClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF21005D)),
                        shape = RoundedCornerShape(100.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Text("Invite Friends", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text("Weekly Student Rankings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1D1B20))

        val rankers = listOf(
            Triple("1st", "Alex Vance", "4,820 XP"),
            Triple("2nd", "Sonia Patel", "4,110 XP"),
            Triple("3rd", "Michael Chang", "3,250 XP"),
            Triple("4th", profile?.name ?: "John Doe", "${(profile?.level ?: 14) * 2500 + (profile?.xp ?: 2100)} XP"),
            Triple("5th", "Alice Carter", "2,980 XP"),
            Triple("6th", "David Miller", "2,640 XP")
        )

        rankers.forEach { (pos, name, xp) ->
            val isUser = name == (profile?.name ?: "John Doe")
            val cardBg = if (isUser) Color(0xFFF3EDF7) else Color.White
            val borderCol = if (isUser) Color(0xFFD0BCFF) else Color(0xFF79747E).copy(alpha = 0.1f)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.5.dp, borderCol)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val posColor = when (pos) {
                            "1st" -> Color(0xFFFFD700) // Gold
                            "2nd" -> Color(0xFFC0C0C0) // Silver
                            "3rd" -> Color(0xFFCD7F32) // Bronze
                            else -> Color(0xFF49454F).copy(alpha = 0.6f)
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(posColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(pos, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (pos.contains("st") || pos.contains("nd") || pos.contains("rd")) posColor else Color(0xFF1D1B20))
                        }

                        Text(name, fontWeight = if (isUser) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
                    }

                    Text(xp, fontWeight = FontWeight.Bold, color = Color(0xFF6750A4), fontSize = 13.sp)
                }
            }
        }
    }
}

// ==========================================
// PROFILE & ACHIEVEMENTS TAB
// ==========================================
@Composable
fun ProfileAchievementsTab(
    profile: ProfileEntity?,
    achievements: List<AchievementEntity>,
    viewModel: PointlyViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Core Profile Display
        profile?.let { prof ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
                border = BorderStroke(1.dp, Color(0xFFE8DEF8))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF6750A4)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (prof.name.length >= 2) prof.name.substring(0, 2).uppercase() else "JD",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(prof.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1D1B20))
                        Text(prof.title, fontSize = 13.sp, color = Color(0xFF6750A4), fontWeight = FontWeight.Medium)
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.setEditingProfile(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF6750A4)),
                            shape = RoundedCornerShape(100.dp),
                            border = BorderStroke(1.dp, Color(0xFFD0BCFF))
                        ) {
                            Text("Edit Profile", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.logout() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFECEF), contentColor = Color(0xFFBA1A1A)),
                            shape = RoundedCornerShape(100.dp),
                            border = BorderStroke(1.dp, Color(0xFFFFDAD6))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ExitToApp,
                                contentDescription = "Sign Out",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sign Out", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Text("Earned Badges", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1D1B20))

        // Grid-like layout for achievements
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            achievements.forEach { achievement ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (achievement.earned) Color.White else Color(0xFF79747E).copy(alpha = 0.04f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (achievement.earned) Color(0xFF79747E).copy(alpha = 0.15f) else Color.Transparent
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (achievement.earned) Color(0xFFEADDFF) else Color(0xFF79747E).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(achievement.icon, fontSize = 20.sp)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                achievement.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (achievement.earned) Color(0xFF1D1B20) else Color(0xFF1D1B20).copy(alpha = 0.5f)
                            )
                            Text(
                                achievement.description,
                                fontSize = 12.sp,
                                color = if (achievement.earned) Color(0xFF49454F) else Color(0xFF49454F).copy(alpha = 0.5f),
                                lineHeight = 16.sp
                            )
                        }

                        if (achievement.earned) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "Unlocked",
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Lock,
                                contentDescription = "Locked",
                                tint = Color(0xFF49454F).copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// POINTLY 77 - SEAMLESS GAMIFIED AUTHENTICATION INTERFACES
// =========================================================================

enum class AuthScreenMode {
    LOGIN,
    REGISTER,
    FORGOT_PASSWORD
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointlyAuthScreen(
    viewModel: PointlyViewModel,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(AuthScreenMode.LOGIN) }
    val authUiState by viewModel.authUiState.collectAsStateWithLifecycle()

    // Login Fields
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Register Fields
    var regName by remember { mutableStateOf("") }
    var regUsername by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regClass by remember { mutableStateOf("") }
    var regSection by remember { mutableStateOf("") }

    // Forgot Password Fields
    var forgotEmail by remember { mutableStateOf("") }

    // Local Validation Errors
    var validationError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFEF7FF)) // Bento theme soft canvas
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // High-Polished Gamified Brand Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF6750A4)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "P",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column {
                    Text(
                        "POINTLY 77",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = Color(0xFF21005D),
                        letterSpacing = 1.sp
                    )
                    Text(
                        "GAMIFIED LEARNING ECOSYSTEM",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6750A4)
                    )
                }
            }

            // Central Bento-Inspired Auth Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, Color(0xFFE8DEF8))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // MODE HEADER
                    val titleText = when (mode) {
                        AuthScreenMode.LOGIN -> "Initiate Session"
                        AuthScreenMode.REGISTER -> "Create Student Profile"
                        AuthScreenMode.FORGOT_PASSWORD -> "Recover Magical Key"
                    }
                    val subtitleText = when (mode) {
                        AuthScreenMode.LOGIN -> "Access your learning progression, streak, and missions."
                        AuthScreenMode.REGISTER -> "Begin your journey to claim epic achievements and study wisdom."
                        AuthScreenMode.FORGOT_PASSWORD -> "Enter your email address to receive password recovery spell."
                    }

                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF21005D)
                    )
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF49454F)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Local Error feedback or Firestore errors
                    validationError?.let {
                        Text(
                            text = "⚠ $it",
                            color = Color(0xFFBA1A1A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // INPUT FIELDS BASED ON SELECTED MODE
                    when (mode) {
                        AuthScreenMode.LOGIN -> {
                            OutlinedTextField(
                                value = email,
                                onValueChange = {
                                    email = it
                                    validationError = null
                                },
                                label = { Text("Email Address") },
                                leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null, tint = Color(0xFF6750A4)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_email_input")
                            )

                            OutlinedTextField(
                                value = password,
                                onValueChange = {
                                    password = it
                                    validationError = null
                                },
                                label = { Text("Secret Password") },
                                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color(0xFF6750A4)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_password_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        validationError = null
                                        mode = AuthScreenMode.FORGOT_PASSWORD
                                    },
                                    modifier = Modifier.testTag("switch_to_forgot_password_button")
                                ) {
                                    Text("Forgot Password?", color = Color(0xFF6750A4), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            Button(
                                onClick = {
                                    if (email.isBlank() || !email.contains("@")) {
                                        validationError = "Please enter a valid email address."
                                    } else if (password.length < 6) {
                                        validationError = "Password must be at least 6 characters."
                                    } else {
                                        validationError = null
                                        viewModel.loginWithEmail(email.trim(), password)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("login_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (authUiState is AuthUiState.Loading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                } else {
                                    Text("Login to Sanctuary", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }

                        AuthScreenMode.REGISTER -> {
                            OutlinedTextField(
                                value = regName,
                                onValueChange = { regName = it; validationError = null },
                                label = { Text("Full Name") },
                                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, tint = Color(0xFF6750A4)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_name_input")
                            )

                            OutlinedTextField(
                                value = regUsername,
                                onValueChange = { regUsername = it; validationError = null },
                                label = { Text("Unique Username") },
                                leadingIcon = { Icon(Icons.Rounded.AccountBox, contentDescription = null, tint = Color(0xFF6750A4)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_username_input")
                            )

                            OutlinedTextField(
                                value = regEmail,
                                onValueChange = { regEmail = it; validationError = null },
                                label = { Text("School/Personal Email") },
                                leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null, tint = Color(0xFF6750A4)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_email_input")
                            )

                            OutlinedTextField(
                                value = regPassword,
                                onValueChange = { regPassword = it; validationError = null },
                                label = { Text("Secure Password (Min 6 Chars)") },
                                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color(0xFF6750A4)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_password_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = regClass,
                                    onValueChange = { regClass = it; validationError = null },
                                    label = { Text("Class") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("register_class_input")
                                )

                                OutlinedTextField(
                                    value = regSection,
                                    onValueChange = { regSection = it; validationError = null },
                                    label = { Text("Section") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("register_section_input")
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Button(
                                onClick = {
                                    if (regName.isBlank()) {
                                        validationError = "Name cannot be empty."
                                    } else if (regUsername.length < 3) {
                                        validationError = "Username must be at least 3 characters."
                                    } else if (!regEmail.contains("@")) {
                                        validationError = "Please enter a valid email address."
                                    } else if (regPassword.length < 6) {
                                        validationError = "Password must be at least 6 characters."
                                    } else if (regClass.isBlank() || regSection.isBlank()) {
                                        validationError = "Class and Section fields are required."
                                    } else {
                                        validationError = null
                                        viewModel.signUpWithEmail(
                                            email = regEmail.trim(),
                                            password = regPassword,
                                            name = regName.trim(),
                                            username = regUsername.trim(),
                                            className = regClass.trim(),
                                            section = regSection.trim()
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("register_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (authUiState is AuthUiState.Loading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                } else {
                                    Text("Begin Student Quest", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }

                        AuthScreenMode.FORGOT_PASSWORD -> {
                            OutlinedTextField(
                                value = forgotEmail,
                                onValueChange = { forgotEmail = it; validationError = null },
                                label = { Text("Your Email Address") },
                                leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null, tint = Color(0xFF6750A4)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("forgot_email_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    if (!forgotEmail.contains("@")) {
                                        validationError = "Please enter a valid email address."
                                    } else {
                                        validationError = null
                                        viewModel.resetPassword(forgotEmail.trim())
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("forgot_password_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (authUiState is AuthUiState.Loading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                } else {
                                    Text("Summon Password Spell", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            TextButton(
                                onClick = {
                                    validationError = null
                                    mode = AuthScreenMode.LOGIN
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Back to Login", color = Color(0xFF6750A4), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // MODE SWITCHING FOOTER
            if (mode != AuthScreenMode.FORGOT_PASSWORD) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val promptText = if (mode == AuthScreenMode.LOGIN) "New Student?" else "Already registered?"
                    val actionText = if (mode == AuthScreenMode.LOGIN) "Create profile" else "Login instead"

                    Text(promptText, fontSize = 14.sp, color = Color(0xFF49454F))
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = {
                            validationError = null
                            mode = if (mode == AuthScreenMode.LOGIN) AuthScreenMode.REGISTER else AuthScreenMode.LOGIN
                        },
                        modifier = Modifier.testTag(if (mode == AuthScreenMode.LOGIN) "switch_to_register_button" else "switch_to_login_button")
                    ) {
                        Text(actionText, color = Color(0xFF6750A4), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PointlyVerificationScreen(
    viewModel: PointlyViewModel,
    modifier: Modifier = Modifier
) {
    val authUiState by viewModel.authUiState.collectAsStateWithLifecycle()
    val user by viewModel.currentUser.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFEF7FF))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE8DEF8))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF3EDF7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MarkEmailUnread,
                        contentDescription = "Unverified Email",
                        tint = Color(0xFF6750A4),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    "Email Verification Shield",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF21005D)
                )

                Text(
                    text = "A verification spell was cast to ${user?.email ?: "your email"}. Please activate the verification link in your inbox to unlock the sanctuary dashboard.",
                    fontSize = 14.sp,
                    color = Color(0xFF49454F),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.checkEmailVerificationStatus() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("check_verification_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (authUiState is AuthUiState.Loading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("I Have Verified", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.resendVerificationEmail() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("resend_verification_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFD0BCFF))
                    ) {
                        Text("Resend Spell", fontWeight = FontWeight.Bold, color = Color(0xFF6750A4))
                    }

                    OutlinedButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("verification_logout_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFBA1A1A)),
                        border = BorderStroke(1.dp, Color(0xFFFFDAD6))
                    ) {
                        Icon(Icons.Rounded.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sign Out", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
