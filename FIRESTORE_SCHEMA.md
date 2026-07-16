# Pointly 77 Firestore Database Schema

This document details the centralized Firestore NoSQL database architecture, defining collections, document schemas, model relationships, and query/indexing recommendations designed for offline-first replication.

---

## Centralized Collections & Documents

### 1. `users`
Represents the core student gamified profile.
*   **Path:** `/users/{uid}`
*   **Properties:**
    *   `uid` (String): Unique authentication ID.
    *   `name` (String): User's full display name.
    *   `username` (String): Student's alphanumeric identifier.
    *   `email` (String): Verified student email.
    *   `className` (String): Academic class/grade level.
    *   `section` (String): Specific school section/cohort.
    *   `profileImage` (String): URL linking to avatar or picture.
    *   `points` (Int): Total earned points.
    *   `xp` (Int): Cumulative experience points.
    *   `level` (Int): Calculated level.
    *   `streak` (Int): Active daily study streak count.
    *   `weeklyPoints` (Int): Points accumulated this week.
    *   `monthlyPoints` (Int): Points accumulated this month.
    *   `activitiesCompleted` (Int): Total educational tasks completed.
    *   `quizStats` (Map): Metrics mapping topic ids to numeric scores.
    *   `createdAt` (Long): Timestamp of account creation.
    *   `updatedAt` (Long): Timestamp of last sync/mutation.

### 2. `squads`
Academic study groups supporting peer milestones.
*   **Path:** `/squads/{squadId}`
*   **Properties:**
    *   `squadId` (String): Unique group ID.
    *   `name` (String): Group name.
    *   `description` (String): Purpose statement.
    *   `memberUids` (Array of Strings): List of participant UIDs.
    *   `points` (Int): Combined team score.
    *   `createdAt` (Long): Group creation timestamp.
    *   `updatedAt` (Long): Modified timestamp.

### 3. `messages`
Real-time messaging inside peer squads.
*   **Path:** `/messages/{messageId}`
*   **Properties:**
    *   `messageId` (String): Unique message ID.
    *   `squadId` (String): Target squad ID.
    *   `senderUid` (String): Sender's user ID.
    *   `senderName` (String): Sender's display name.
    *   `text` (String): Chat content.
    *   `timestamp` (Long): Epoch mills sent time.

### 4. `challenges`
Gamified study missions and daily academic goals.
*   **Path:** `/challenges/{challengeId}`
*   **Properties:**
    *   `challengeId` (String): Unique mission ID.
    *   `title` (String): Challenge title.
    *   `description` (String): Completion requirements.
    *   `xpReward` (Int): Experience points rewarded.
    *   `targetValue` (Int): Numeric goal target (e.g., minutes).
    *   `type` (String): Category ("daily", "weekly").
    *   `expiresAt` (Long): Expiry timestamp.
    *   `createdAt` (Long): Creation time.
    *   `updatedAt` (Long): Update time.

### 5. `notifications`
User alerts and system triggers.
*   **Path:** `/notifications/{id}`
*   **Properties:**
    *   `id` (String): Unique alert ID.
    *   `recipientUid` (String): Intended student's UID.
    *   `title` (String): Notification title.
    *   `message` (String): Text payload.
    *   `type` (String): Category ("mission", "squad", "system").
    *   `read` (Boolean): Read status flag.
    *   `timestamp` (Long): Distribution timestamp.

### 6. `quiz_questions`
Gemini-generated curriculum study queries.
*   **Path:** `/quiz_questions/{id}`
*   **Properties:**
    *   `id` (String): Unique question ID.
    *   `question` (String): Prompt text.
    *   `options` (Array of Strings): Answer choices.
    *   `correctAnswerIndex` (Int): 0-indexed correct answer key.
    *   `explanation` (String): Study feedback description.
    *   `category` (String): Subject topic.
    *   `xpValue` (Int): Question XP weight.
    *   `createdAt` (Long): Insertion timestamp.

### 7. `quiz_attempts`
Records of student quiz performance.
*   **Path:** `/quiz_attempts/{attemptId}`
*   **Properties:**
    *   `attemptId` (String): Unique attempt ID.
    *   `userUid` (String): UID of the testing student.
    *   `score` (Int): Total correct answers.
    *   `totalQuestions` (Int): Total question count in attempt.
    *   `xpEarned` (Int): Experience granted.
    *   `timestamp` (Long): Quiz completion timestamp.

### 8. `leaderboard`
Aggregated rankings for dynamic ranking lists.
*   **Path:** `/leaderboard/{uid}`
*   **Properties:**
    *   `uid` (String): Unique student identifier.
    *   `username` (String): Student's handle.
    *   `name` (String): Display name.
    *   `points` (Int): Active points.
    *   `rank` (Int): Cached leader rank.
    *   `updatedAt` (Long): Aggregation timestamp.

---

## Entity Relationships

*   **Many-to-Many via Join arrays:** `squads.memberUids` maps `/squads` directly to `/users`.
*   **One-to-Many (Queries):** `/messages` belongs to `/squads` via `squadId` lookup.
*   **One-to-Many:** `/quiz_attempts` belongs to `/users` via `userUid`.
*   **One-to-Many:** `/notifications` targets `/users` via `recipientUid`.

---

## Recommended Indexes

To support high performance, create these indexes in your Firebase console:

1.  **Messages Order Index:**
    *   Collection: `messages`
    *   Fields: `squadId` (Ascending), `timestamp` (Descending)
2.  **Leaderboard Rankings Index:**
    *   Collection: `leaderboard`
    *   Fields: `points` (Descending), `updatedAt` (Ascending)
3.  **Active Challenges Index:**
    *   Collection: `challenges`
    *   Fields: `type` (Ascending), `expiresAt` (Descending)
