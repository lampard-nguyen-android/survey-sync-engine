# Database Schema Documentation

This document provides comprehensive documentation of SurveySyncEngine's local database schema built
with Room. It covers all entities, their relationships, foreign key constraints, and the overall
data model that supports offline-first survey synchronization.

---

## Table of Contents

1. [QuestionDefinitionEntity - Survey Template](#1-questiondefinitionentity)
2. [Entity Relationships](#2-entity-relationships)
3. [Complete Database Schema (ERD)](#3-complete-database-schema-erd)

---

## 1. QuestionDefinitionEntity

**File Path**: `data/src/main/java/com/survey/sync/engine/data/entity/QuestionDefinitionEntity.kt`

### Overview

QuestionDefinitionEntity serves as the **template/schema** for survey questions. It defines what
questions exist, how to display them in the UI, and how to validate user input. This entity is
shared across all surveys and pre-populated during app initialization.

### Complete Entity Definition

```kotlin
@Entity(tableName = "question_definitions")
data class QuestionDefinitionEntity(
    @PrimaryKey
    val questionKey: String,      // Technical ID (e.g., "crop_type")

    val sectionType: String,      // GENERAL, FARM, LIVESTOCK, etc.

    val isRepeating: Boolean,     // True if belongs to repeating section

    val inputType: String,        // TEXT, NUMBER, GPS, PHOTO

    val labelText: String,        // Display label for UI

    val sortOrder: Int,           // Order within section
)
```

### Field-by-Field Breakdown

| Field           | Type    | Null?    | Description                                                           | Example Values                                            | Purpose                                                                  |
|-----------------|---------|----------|-----------------------------------------------------------------------|-----------------------------------------------------------|--------------------------------------------------------------------------|
| **questionKey** | String  | NOT NULL | Primary key - Unique technical identifier for the question            | `"farmer_name"`, `"crop_type"`, `"farm_photo"`            | Identifies question across all surveys, used as FK in answers            |
| **sectionType** | String  | NOT NULL | Section category this question belongs to                             | `"GENERAL"`, `"FARM"`, `"LIVESTOCK"`                      | Groups related questions for UI organization                             |
| **isRepeating** | Boolean | NOT NULL | Whether this question can have multiple instances (repeating section) | `false` (general info), `true` (multiple farms)           | Determines if user can add multiple answers with different instanceIndex |
| **inputType**   | String  | NOT NULL | Type of input control for UI rendering and validation                 | `"TEXT"`, `"NUMBER"`, `"GPS"`, `"PHOTO"`                  | Drives UI component selection and input validation                       |
| **labelText**   | String  | NOT NULL | Human-readable label displayed to users                               | `"Farmer Name"`, `"Primary Crop"`, `"Take Photo of Farm"` | Shown in survey form as question text                                    |
| **sortOrder**   | Int     | NOT NULL | Display order within the section (lower numbers appear first)         | `1`, `2`, `3`, ...                                        | Controls question sequence in UI                                         |

### Primary Key

**Column**: `questionKey`
**Type**: String (not auto-generated)
**Uniqueness**: Global across all question definitions
**Format**: Lowercase with underscores (e.g., `"farm_size_hectares"`)

**Why String Key?**

- Human-readable in database queries
- Stable across app updates (unlike auto-increment IDs)
- Used directly in API requests/responses
- Easier debugging and testing

### Table Name

```kotlin
tableName = "question_definitions"
```

**Columns Created in SQLite**:

```sql
CREATE TABLE question_definitions (
    questionKey TEXT PRIMARY KEY NOT NULL,
    sectionType TEXT NOT NULL,
    isRepeating INTEGER NOT NULL,  -- Boolean stored as 0/1
    inputType TEXT NOT NULL,
    labelText TEXT NOT NULL,
    sortOrder INTEGER NOT NULL
);
```

### Purpose and Characteristics

**Role**: Question Metadata and UI Blueprint

1. **Template for Dynamic Forms**:
    - UI reads question definitions to build survey forms dynamically
    - No hardcoded questions in code—all driven by database
    - Enables adding new questions without app updates (when fetched from server)

2. **Shared Across Surveys**:
    - NOT survey-specific (one definition, many answers)
    - All surveys use the same question definitions
    - Reduces data duplication

3. **Read-Only at Runtime**:
    - Pre-populated during app initialization
    - Can be updated from server for new question types
    - Never modified by user

4. **Drives Validation**:
    - `inputType` determines validation rules:
        - `TEXT`: String input
        - `NUMBER`: Numeric validation
        - `GPS`: Latitude/longitude format
        - `PHOTO`: File path validation

5. **Enables Repeating Sections**:
    - `isRepeating = true` allows multiple instances
    - Example: One farmer can have multiple farms
    - Each instance tracked by `instanceIndex` in AnswerEntity

### Example Data

```
┌────────────────────┬──────────┬─────────────┬───────────┬──────────────────────────┬───────────┐
│ questionKey        │ section  │ isRepeating │ inputType │ labelText                │ sortOrder │
├────────────────────┼──────────┼─────────────┼───────────┼──────────────────────────┼───────────┤
│ farmer_name        │ GENERAL  │ false       │ TEXT      │ Farmer Name              │ 1         │
│ farmer_age         │ GENERAL  │ false       │ NUMBER    │ Age                      │ 2         │
│ farmer_phone       │ GENERAL  │ false       │ TEXT      │ Phone Number             │ 3         │
├────────────────────┼──────────┼─────────────┼───────────┼──────────────────────────┼───────────┤
│ farm_location      │ FARM     │ true        │ GPS       │ Farm GPS Location        │ 1         │
│ farm_size_hectares │ FARM     │ true        │ NUMBER    │ Farm Size (Hectares)     │ 2         │
│ crop_type          │ FARM     │ true        │ TEXT      │ Primary Crop             │ 3         │
│ farm_photo         │ FARM     │ true        │ PHOTO     │ Take Photo of Farm       │ 4         │
├────────────────────┼──────────┼─────────────┼───────────┼──────────────────────────┼───────────┤
│ livestock_count    │ LIVESTOCK│ true        │ NUMBER    │ Number of Livestock      │ 1         │
│ livestock_photo    │ LIVESTOCK│ true        │ PHOTO     │ Take Photo of Livestock  │ 2         │
└────────────────────┴──────────┴─────────────┴───────────┴──────────────────────────┴───────────┘
```

### Repeating Section Example

**Survey with 2 Farms**:

```
QuestionDefinitionEntity (shared):
  questionKey = "crop_type"
  sectionType = "FARM"
  isRepeating = true
  inputType = "TEXT"
  labelText = "Primary Crop"

AnswerEntity (Survey survey-001, Farm 1):
  answerUuid = "ans-001"
  parentSurveyId = "survey-001"
  questionKey = "crop_type"  ← References same definition
  instanceIndex = 1          ← First farm
  answerValue = "Coffee"

AnswerEntity (Survey survey-001, Farm 2):
  answerUuid = "ans-002"
  parentSurveyId = "survey-001"
  questionKey = "crop_type"  ← References same definition
  instanceIndex = 2          ← Second farm
  answerValue = "Tea"
```

**UI Rendering**:

```
┌─────────────────────────────────────┐
│ Farm 1                              │
├─────────────────────────────────────┤
│ Primary Crop: [Coffee        ]     │  ← instanceIndex = 1
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│ Farm 2                              │
├─────────────────────────────────────┤
│ Primary Crop: [Tea           ]     │  ← instanceIndex = 2
└─────────────────────────────────────┘

[+ Add Another Farm]
```

---

## 2. Entity Relationships

### 2.1 AnswerEntity → QuestionDefinitionEntity

**Relationship Type**: Many-to-One (N:1)
**Nature**: Each answer references one question definition
**Cascade**: RESTRICT (cannot delete question if answers exist)

#### Foreign Key Definition

**File**: `data/src/main/java/com/survey/sync/engine/data/entity/AnswerEntity.kt` (Lines 22-27)

```kotlin
@Entity(
    tableName = "answers",
    foreignKeys = [
        // ... other FKs ...
        ForeignKey(
            entity = QuestionDefinitionEntity::class,
            parentColumns = ["questionKey"],
            childColumns = ["questionKey"],
            onDelete = ForeignKey.RESTRICT  // Prevent deletion
        )
    ],
    indices = [
        Index("questionKey")  // Index for fast lookups
    ]
)
```

#### Relationship Diagram

```
┌─────────────────────────────────────┐
│ QuestionDefinitionEntity (1)        │
│ ─────────────────────────────────── │
│ PK: questionKey = "crop_type"       │
│     labelText = "Primary Crop"      │
│     inputType = "TEXT"              │
└───────────────┬─────────────────────┘
                │
                │ Referenced by (FK: questionKey)
                │ onDelete: RESTRICT
                │
                ▼
┌─────────────────────────────────────┐
│ AnswerEntity (N)                    │
│ ─────────────────────────────────── │
│ PK: answerUuid = "ans-1"            │
│ FK: questionKey = "crop_type" ──────┘
│     answerValue = "Coffee"          │
├─────────────────────────────────────┤
│ PK: answerUuid = "ans-2"            │
│ FK: questionKey = "crop_type" ──────┐
│     answerValue = "Tea"             │
├─────────────────────────────────────┤
│ PK: answerUuid = "ans-3"            │
│ FK: questionKey = "crop_type" ──────┐
│     answerValue = "Wheat"           │
└─────────────────────────────────────┘

Cardinality: 1 QuestionDefinition → Many Answers
```

#### Example Queries

**Get all answers for a specific question across all surveys**:

```sql
SELECT a.*
FROM answers a
JOIN question_definitions qd ON a.questionKey = qd.questionKey
WHERE qd.questionKey = 'crop_type';
```

**Get question metadata for a given answer**:

```sql
SELECT
    qd.labelText,
    qd.inputType,
    qd.sectionType,
    a.answerValue,
    a.answeredAt
FROM answers a
JOIN question_definitions qd ON a.questionKey = qd.questionKey
WHERE a.answerUuid = 'ans-001';
```

**Result**:

```
labelText    | inputType | sectionType | answerValue | answeredAt
─────────────┼───────────┼─────────────┼─────────────┼─────────────────────
Primary Crop | TEXT      | FARM        | Coffee      | 2024-03-01 14:30:00
```

#### RESTRICT Behavior

**What happens if you try to delete a question definition that has answers?**

```sql
-- This will FAIL with foreign key constraint error
DELETE FROM question_definitions WHERE questionKey = 'crop_type';

-- Error: FOREIGN KEY constraint failed
-- Reason: 3 answers reference this question (ans-1, ans-2, ans-3)
```

**To delete a question definition**:

1. First delete all answers referencing it
2. Then delete the question definition

```sql
-- Step 1: Delete answers
DELETE FROM answers WHERE questionKey = 'crop_type';

-- Step 2: Now safe to delete question
DELETE FROM question_definitions WHERE questionKey = 'crop_type';
```

---

### 2.2 MediaAttachmentEntity → QuestionDefinitionEntity

**Relationship Type**: Indirect (through AnswerEntity)
**Nature**: Media → Answer → Question Definition
**Cascade**: CASCADE (Answer) → RESTRICT (Question Definition)

#### Relationship Chain

```
MediaAttachmentEntity
    ↓ (FK: answerUuid, CASCADE)
AnswerEntity
    ↓ (FK: questionKey, RESTRICT)
QuestionDefinitionEntity
```

#### Foreign Key Definitions

**MediaAttachmentEntity → AnswerEntity** (Lines 23-28):

```kotlin
ForeignKey(
    entity = AnswerEntity::class,
    parentColumns = ["answerUuid"],
    childColumns = ["answerUuid"],
    onDelete = ForeignKey.CASCADE  // Delete media when answer deleted
)
```

**AnswerEntity → QuestionDefinitionEntity** (Lines 22-27):

```kotlin
ForeignKey(
    entity = QuestionDefinitionEntity::class,
    parentColumns = ["questionKey"],
    childColumns = ["questionKey"],
    onDelete = ForeignKey.RESTRICT  // Protect question definition
)
```

#### Complete Relationship Diagram

```
┌─────────────────────────────────────────┐
│ QuestionDefinitionEntity (1)            │
│ ─────────────────────────────────────── │
│ PK: questionKey = "farm_photo"          │
│     labelText = "Take Photo of Farm"    │
│     inputType = "PHOTO"                 │
└───────────────┬─────────────────────────┘
                │
                │ Referenced by (FK: questionKey, RESTRICT)
                │
                ▼
┌─────────────────────────────────────────┐
│ AnswerEntity (1)                        │
│ ─────────────────────────────────────── │
│ PK: answerUuid = "ans-photo-1"          │
│ FK: questionKey = "farm_photo" ─────────┘
│     answerValue = "photo_123.jpg"       │
└───────────────┬─────────────────────────┘
                │
                │ Referenced by (FK: answerUuid, CASCADE)
                │
                ▼
┌─────────────────────────────────────────┐
│ MediaAttachmentEntity (N)               │
│ ─────────────────────────────────────── │
│ PK: attachmentId = "media-1"            │
│ FK: answerUuid = "ans-photo-1" ─────────┘
│     localFilePath = "/storage/photo.jpg"│
│     fileSize = 2048576                  │
│     syncStatus = PENDING                │
└─────────────────────────────────────────┘

Relationship Path:
  MediaAttachment → Answer → QuestionDefinition
```

#### Example Queries

**Get all media attachments for photo-type questions**:

```sql
SELECT
    ma.attachmentId,
    ma.localFilePath,
    ma.syncStatus,
    qd.labelText,
    qd.questionKey
FROM media_attachments ma
JOIN answers a ON ma.answerUuid = a.answerUuid
JOIN question_definitions qd ON a.questionKey = qd.questionKey
WHERE qd.inputType = 'PHOTO';
```

**Get question definition for a media attachment**:

```sql
SELECT
    qd.*,
    a.answerValue,
    ma.localFilePath,
    ma.syncStatus
FROM media_attachments ma
JOIN answers a ON ma.answerUuid = a.answerUuid
JOIN question_definitions qd ON a.questionKey = qd.questionKey
WHERE ma.attachmentId = 'media-1';
```

**Result**:

```
questionKey | labelText           | inputType | answerValue    | localFilePath          | syncStatus
────────────┼─────────────────────┼───────────┼────────────────┼────────────────────────┼───────────
farm_photo  | Take Photo of Farm  | PHOTO     | photo_123.jpg  | /storage/photo_123.jpg | PENDING
```

#### Business Logic Constraint

**Important**: Only answers with `questionDefinition.inputType = "PHOTO"` should have media
attachments.

This is a **business logic constraint**, not enforced at database level.

**Enforcement in Code** (`SurveyRepositoryImpl.kt` Lines 156-165):

```kotlin
for (answer in survey.answers) {
    val filePath = answer.answerValue ?: continue

    // Look up question definition to determine input type
    val definition = questionDefinitionDao.getQuestionByKey(answer.questionKey)
    if (definition?.inputType != InputType.PHOTO.name) continue  // ← Enforcement

    // Only create media attachment for PHOTO questions
    val attachment = MediaAttachment(...)
    photoAttachments.add(attachment)
}
```

---

### 2.3 SurveyEntity → QuestionDefinitionEntity

**Relationship Type**: Indirect (through AnswerEntity)
**Nature**: Survey → Answers → Question Definitions
**Cascade**: CASCADE (Survey → Answers) → RESTRICT (Answers → Questions)

#### Relationship Chain

```
SurveyEntity
    ↓ (FK: parentSurveyId, CASCADE)
AnswerEntity
    ↓ (FK: questionKey, RESTRICT)
QuestionDefinitionEntity
```

#### Foreign Key Definitions

**AnswerEntity → SurveyEntity** (Lines 16-21):

```kotlin
ForeignKey(
    entity = SurveyEntity::class,
    parentColumns = ["surveyId"],
    childColumns = ["parentSurveyId"],
    onDelete = ForeignKey.CASCADE  // Delete answers when survey deleted
)
```

**AnswerEntity → QuestionDefinitionEntity** (Lines 22-27):

```kotlin
ForeignKey(
    entity = QuestionDefinitionEntity::class,
    parentColumns = ["questionKey"],
    childColumns = ["questionKey"],
    onDelete = ForeignKey.RESTRICT  // Protect question definition
)
```

#### Complete Relationship Diagram

```
┌───────────────────────────────────────────┐
│ QuestionDefinitionEntity (Template)       │
│ ───────────────────────────────────────── │
│ PK: questionKey = "farmer_name"           │
│     labelText = "Farmer Name"             │
│     inputType = "TEXT"                    │
└────────────────┬──────────────────────────┘
                 │
                 │ Referenced by (FK: questionKey, RESTRICT)
                 │
                 ▼
┌───────────────────────────────────────────┐    ┌───────────────────────────┐
│ SurveyEntity (1)                          │    │ QuestionDefinition:       │
│ ───────────────────────────────────────── │    │ "crop_type"               │
│ PK: surveyId = "survey-001"               │    └───────────┬───────────────┘
│     agentId = "agent-123"                 │                │
│     farmerId = "farmer-456"               │                │ (FK: RESTRICT)
│     syncStatus = PENDING                  │                │
└────────────────┬──────────────────────────┘                │
                 │                                            │
                 │ Referenced by (FK: parentSurveyId, CASCADE)│
                 │                                            │
                 ▼                                            ▼
┌───────────────────────────────────────────┬───────────────────────────────┐
│ AnswerEntity #1                           │ AnswerEntity #2               │
│ ───────────────────────────────────────── │ ───────────────────────────── │
│ PK: answerUuid = "ans-001"                │ PK: answerUuid = "ans-002"    │
│ FK: parentSurveyId = "survey-001" ────────┘ FK: parentSurveyId = ...      │
│ FK: questionKey = "farmer_name" ────────────┐ FK: questionKey = "crop..." │
│     answerValue = "John Doe"              │ │     answerValue = "Coffee"  │
└───────────────────────────────────────────┘ └───────────────────────────────┘

Cardinality: 1 Survey → Many Answers → Many Questions (reused)
```

#### Example Queries

**Get all questions answered in a survey** (with answers):

```sql
SELECT
    qd.questionKey,
    qd.labelText,
    qd.inputType,
    qd.sectionType,
    a.answerValue,
    a.instanceIndex,
    a.answeredAt
FROM surveys s
JOIN answers a ON s.surveyId = a.parentSurveyId
JOIN question_definitions qd ON a.questionKey = qd.questionKey
WHERE s.surveyId = 'survey-001'
ORDER BY qd.sectionType, qd.sortOrder, a.instanceIndex;
```

**Result**:

```
questionKey        | labelText           | inputType | sectionType | answerValue | instanceIndex | answeredAt
───────────────────┼─────────────────────┼───────────┼─────────────┼─────────────┼───────────────┼──────────────────
farmer_name        | Farmer Name         | TEXT      | GENERAL     | John Doe    | 0             | 2024-03-01 14:30
farmer_age         | Age                 | NUMBER    | GENERAL     | 45          | 0             | 2024-03-01 14:31
farm_location      | Farm GPS Location   | GPS       | FARM        | 1.234,5.678 | 1             | 2024-03-01 14:32
farm_size_hectares | Farm Size (Hectares)| NUMBER    | FARM        | 5.5         | 1             | 2024-03-01 14:33
crop_type          | Primary Crop        | TEXT      | FARM        | Coffee      | 1             | 2024-03-01 14:34
farm_location      | Farm GPS Location   | GPS       | FARM        | 2.345,6.789 | 2             | 2024-03-01 14:35
farm_size_hectares | Farm Size (Hectares)| NUMBER    | FARM        | 3.2         | 2             | 2024-03-01 14:36
crop_type          | Primary Crop        | TEXT      | FARM        | Tea         | 2             | 2024-03-01 14:37
```

**Get all surveys that answered a specific question**:

```sql
SELECT
    s.surveyId,
    s.agentId,
    s.farmerId,
    s.syncStatus,
    a.answerValue,
    a.answeredAt
FROM surveys s
JOIN answers a ON s.surveyId = a.parentSurveyId
WHERE a.questionKey = 'crop_type'
ORDER BY a.answeredAt DESC;
```

**Survey completion status** (which questions answered):

```sql
SELECT
    qd.questionKey,
    qd.labelText,
    qd.isRepeating,
    CASE
        WHEN a.answerUuid IS NOT NULL THEN 'Answered'
        ELSE 'Unanswered'
    END as status,
    COUNT(a.answerUuid) as instanceCount
FROM question_definitions qd
LEFT JOIN answers a ON qd.questionKey = a.questionKey
    AND a.parentSurveyId = 'survey-001'
GROUP BY qd.questionKey, qd.labelText, qd.isRepeating
ORDER BY qd.sectionType, qd.sortOrder;
```

#### Cascade Behavior

**What happens when you delete a survey?**

```sql
-- Delete survey
DELETE FROM surveys WHERE surveyId = 'survey-001';

-- CASCADE effect:
-- 1. All answers with parentSurveyId = 'survey-001' are automatically deleted
-- 2. All media_attachments linked to those answers are also deleted (CASCADE)
-- 3. Question definitions remain UNCHANGED (RESTRICT prevents deletion)

Result:
  ✓ Survey deleted
  ✓ Answers deleted (CASCADE from survey)
  ✓ Media attachments deleted (CASCADE from answers)
  ✗ Question definitions preserved (templates for future surveys)
```

**Cascade Chain**:

```
DELETE Survey
    ↓ (CASCADE)
DELETE Answers
    ↓ (CASCADE)
DELETE Media Attachments

Question Definitions ← UNCHANGED (RESTRICT protects them)
```

---

## 3. Complete Database Schema (ERD)

### Entity-Relationship Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         QUESTION_DEFINITIONS                                │
│                           (Survey Template)                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│ PK  questionKey: String              "crop_type", "farmer_name", etc.      │
│     sectionType: String              GENERAL, FARM, LIVESTOCK               │
│     isRepeating: Boolean             true (multiple instances) / false      │
│     inputType: String                TEXT, NUMBER, GPS, PHOTO               │
│     labelText: String                "Primary Crop", "Farmer Name"          │
│     sortOrder: Int                   1, 2, 3... (display order)             │
│                                                                             │
│ Purpose: Template/schema for all survey questions                          │
│ Shared: Used by all surveys (not survey-specific)                          │
│ Lifecycle: Pre-populated, rarely changes                                   │
└───────────────────────────┬─────────────────────────────────────────────────┘
                            │
                            │ (1)
                            │ referenced by (FK, RESTRICT)
                            │ Cannot delete if answers exist
                            │ (N)
                            │
┌───────────────────────────┴─────────────────────────────────────────────────┐
│                              ANSWERS                                        │
│                        (Individual Responses)                               │
├─────────────────────────────────────────────────────────────────────────────┤
│ PK  answerUuid: String               UUID for idempotency                  │
│ FK  parentSurveyId: String ────┐     → surveys.surveyId (CASCADE)          │
│ FK  questionKey: String ────────┼───→ → question_definitions.questionKey   │
│     instanceIndex: Int          │     0=general, 1,2,3...=repeating        │
│     answerValue: String?        │     Actual answer data                   │
│     answeredAt: Date            │     Timestamp when answered              │
│     uploadedAt: Date?           │     null if not uploaded                 │
│     syncStatus: SyncStatusEntity│     PENDING, SYNCED                      │
│     retryCount: Int             │     Upload retry attempts                │
│                                 │                                           │
│ Indexes:                        │                                           │
│   - parentSurveyId             │                                           │
│   - questionKey                 │                                           │
│   - answerUuid (unique)         │                                           │
└──────────────────┬──────────────┴───────────────────────┬───────────────────┘
                   │                                      │
         (N)       │                                      │ (1)
  child of (FK, CASCADE)                       referenced by (FK, CASCADE)
  Delete answers when survey deleted          Delete media when answer deleted
         (1)       │                                      │ (N)
                   │                                      │
┌──────────────────┴──────────────────────────────────────┴───────────────────┐
│                             SURVEYS                                         │
│                         (Survey Sessions)                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│ PK  surveyId: String                 UUID for survey session                │
│     agentId: String                  Who collected it                       │
│     farmerId: String                 Survey subject                         │
│     syncStatus: SyncStatusEntity     PENDING, SYNCING, PENDING_MEDIA,       │
│                                      SYNCED, FAILED                         │
│     createdAt: Date                  Creation timestamp                     │
│     retryCount: Int                  Sync retry attempts (max 3)            │
│     lastAttemptAt: Date?             Last sync attempt timestamp            │
│                                                                             │
│ Purpose: Represents one complete survey collection session                 │
│ Lifecycle: Created offline → Synced to server → Optionally deleted         │
└──────────────────┬──────────────────────────────────────────────────────────┘
                   │
                   │ (1)
                   │ parent of (FK, CASCADE)
                   │ Delete children when survey deleted
                   │ (N)
                   │
┌──────────────────┴──────────────────────────────────────────────────────────┐
│                        MEDIA_ATTACHMENTS                                    │
│                          (Photo Files)                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│ PK  attachmentId: String             UUID for media file                   │
│ FK  parentSurveyId: String ────┐     → surveys.surveyId (CASCADE)          │
│ FK  answerUuid: String ─────────┼───→ → answers.answerUuid (CASCADE)       │
│     localFilePath: String       │     /storage/.../photo.jpg               │
│     fileSize: Long              │     Bytes                                │
│     uploadedAt: Date?           │     null if pending                      │
│     syncStatus: SyncStatusEntity│     PENDING, SYNCED                      │
│     retryCount: Int             │     Upload retry attempts                │
│                                 │                                           │
│ Indexes:                        │                                           │
│   - parentSurveyId             │                                           │
│   - answerUuid                  │                                           │
│   - attachmentId (unique)       │                                           │
│                                 │                                           │
│ Purpose: Tracks local photo files and upload status                        │
│ Lifecycle: Created with PHOTO answer → Uploaded → Cleaned up after 30 days │
└─────────────────────────────────────────────────────────────────────────────┘


FOREIGN KEY RELATIONSHIPS:
══════════════════════════════════════════════════════════════════════════════

1. answers.parentSurveyId → surveys.surveyId
   ├─ Cardinality: Many-to-One (N:1)
   ├─ onDelete: CASCADE
   └─ Behavior: Deleting survey deletes all its answers

2. answers.questionKey → question_definitions.questionKey
   ├─ Cardinality: Many-to-One (N:1)
   ├─ onDelete: RESTRICT
   └─ Behavior: Cannot delete question if answers exist

3. media_attachments.parentSurveyId → surveys.surveyId
   ├─ Cardinality: Many-to-One (N:1)
   ├─ onDelete: CASCADE
   └─ Behavior: Deleting survey deletes all its media

4. media_attachments.answerUuid → answers.answerUuid
   ├─ Cardinality: Many-to-One (N:1, typically 1:1 for PHOTO)
   ├─ onDelete: CASCADE
   └─ Behavior: Deleting answer deletes its media


CARDINALITY SUMMARY:
══════════════════════════════════════════════════════════════════════════════

QuestionDefinitionEntity (1) ←───── (N) AnswerEntity
SurveyEntity (1) ←───────────────── (N) AnswerEntity
SurveyEntity (1) ←───────────────── (N) MediaAttachmentEntity
AnswerEntity (1) ←───────────────── (N) MediaAttachmentEntity


SYNC STATUS VALUES (SyncStatusEntity enum):
══════════════════════════════════════════════════════════════════════════════

PENDING       - Not yet uploaded to server (initial state)
SYNCING       - Currently being uploaded (SurveyEntity only, transient state)
PENDING_MEDIA - Survey data uploaded, media attachments pending (SurveyEntity only, retryable)
SYNCED        - Successfully uploaded to server (final success state)
FAILED        - Upload failed (SurveyEntity only, retryable if retryCount < max)


CASCADE DELETION FLOW:
══════════════════════════════════════════════════════════════════════════════

DELETE SurveyEntity
    ↓ (CASCADE via parentSurveyId)
DELETE all AnswerEntity records
    ↓ (CASCADE via answerUuid)
DELETE all MediaAttachmentEntity records
    ↓
QuestionDefinitionEntity ← UNCHANGED (protected by RESTRICT)

Result:
  ✓ Survey deleted
  ✓ All answers for that survey deleted
  ✓ All media for those answers deleted
  ✗ Question definitions preserved (templates for future surveys)
```

### Data Flow Lifecycle

```
TYPICAL DATA FLOW (from survey creation to cleanup):
══════════════════════════════════════════════════════════════════════════════

[1] APP INITIALIZATION
    ↓
Pre-populate QuestionDefinitionEntity
    • Load question templates from assets or API
    • Insert into question_definitions table
    • Examples: farmer_name, crop_type, farm_photo, etc.

[2] SURVEY CREATION (Offline)
    ↓
Agent creates SurveyEntity
    • surveyId: UUID
    • syncStatus: PENDING
    • createdAt: now()
    • Insert into surveys table

[3] ANSWERING QUESTIONS
    ↓
For each question answered:
    • Look up QuestionDefinitionEntity by questionKey
    • Create AnswerEntity:
        - answerUuid: UUID
        - parentSurveyId: links to survey
        - questionKey: links to question definition
        - answerValue: user input
        - syncStatus: PENDING
    • Insert into answers table

[4] TAKING PHOTOS
    ↓
For PHOTO-type questions:
    • Save photo to local storage
    • Create MediaAttachmentEntity:
        - attachmentId: UUID
        - parentSurveyId: links to survey
        - answerUuid: links to PHOTO answer
        - localFilePath: /storage/path/photo.jpg
        - syncStatus: PENDING
    • Insert into media_attachments table

[5] BACKGROUND SYNC (WorkManager)
    ↓
SurveySyncWorker triggers:
    • Get pending surveys (status = PENDING or FAILED)
    • For each survey:
        a. Update: syncStatus = SYNCING
        b. Upload survey data (answers)
        c. Upload media attachments
        d. Update: syncStatus = SYNCED
        e. Update answer: syncStatus = SYNCED
        f. Update media: syncStatus = SYNCED

[6] MEDIA CLEANUP (30+ days later)
    ↓
StorageCleanupWorker triggers:
    • Find SYNCED media older than 30 days
    • Delete local photo files
    • Delete MediaAttachmentEntity records
    • Note: Answers and Surveys remain (only media deleted)

[7] EVENTUAL SURVEY DELETION (Optional)
    ↓
If survey deleted:
    • DELETE FROM surveys WHERE surveyId = 'xxx'
    • CASCADE deletes:
        - All AnswerEntity records
        - All MediaAttachmentEntity records
    • RESTRICT preserves:
        - QuestionDefinitionEntity (templates)
```

### Example Data Set

**Complete Dataset Showing All Relationships**:

```
═════════════════════════════════════════════════════════════════════════════
QUESTION_DEFINITIONS (Pre-populated Templates)
═════════════════════════════════════════════════════════════════════════════

┌─────────────────┬─────────┬────────────┬──────────┬──────────────────┬──────────┐
│ questionKey     │ section │ isRepeating│ inputType│ labelText        │sortOrder │
├─────────────────┼─────────┼────────────┼──────────┼──────────────────┼──────────┤
│ farmer_name     │ GENERAL │ false      │ TEXT     │ Farmer Name      │ 1        │
│ farmer_age      │ GENERAL │ false      │ NUMBER   │ Age              │ 2        │
│ farm_size       │ FARM    │ true       │ NUMBER   │ Farm Size (ha)   │ 1        │
│ crop_type       │ FARM    │ true       │ TEXT     │ Primary Crop     │ 2        │
│ farm_photo      │ FARM    │ true       │ PHOTO    │ Farm Photo       │ 3        │
└─────────────────┴─────────┴────────────┴──────────┴──────────────────┴──────────┘

═════════════════════════════════════════════════════════════════════════════
SURVEYS (Active Survey Session)
═════════════════════════════════════════════════════════════════════════════

┌───────────┬──────────┬───────────┬────────────┬─────────────────────┬────────────┐
│ surveyId  │ agentId  │ farmerId  │ syncStatus │ createdAt           │ retryCount │
├───────────┼──────────┼───────────┼────────────┼─────────────────────┼────────────┤
│ survey-001│ agent-1  │ farmer-A  │ PENDING    │ 2024-03-01 14:00:00 │ 0          │
└───────────┴──────────┴───────────┴────────────┴─────────────────────┴────────────┘

═════════════════════════════════════════════════════════════════════════════
ANSWERS (Survey Responses)
═════════════════════════════════════════════════════════════════════════════

┌──────────────┬──────────────┬──────────────┬──────────┬─────────────┬────────────┬─────────────────────┐
│ answerUuid   │parentSurveyId│ questionKey  │ instance │ answerValue │ syncStatus │ answeredAt          │
├──────────────┼──────────────┼──────────────┼──────────┼─────────────┼────────────┼─────────────────────┤
│ ans-001      │ survey-001   │ farmer_name  │ 0        │ John Doe    │ PENDING    │ 2024-03-01 14:00:05 │
│ ans-002      │ survey-001   │ farmer_age   │ 0        │ 45          │ PENDING    │ 2024-03-01 14:00:10 │
│ ans-003      │ survey-001   │ farm_size    │ 1        │ 5.5         │ PENDING    │ 2024-03-01 14:01:00 │
│ ans-004      │ survey-001   │ crop_type    │ 1        │ Coffee      │ PENDING    │ 2024-03-01 14:01:15 │
│ ans-005      │ survey-001   │ farm_photo   │ 1        │ photo1.jpg  │ PENDING    │ 2024-03-01 14:01:30 │
│ ans-006      │ survey-001   │ farm_size    │ 2        │ 3.2         │ PENDING    │ 2024-03-01 14:02:00 │
│ ans-007      │ survey-001   │ crop_type    │ 2        │ Tea         │ PENDING    │ 2024-03-01 14:02:15 │
│ ans-008      │ survey-001   │ farm_photo   │ 2        │ photo2.jpg  │ PENDING    │ 2024-03-01 14:02:30 │
└──────────────┴──────────────┴──────────────┴──────────┴─────────────┴────────────┴─────────────────────┘

Explanation:
  • ans-001, ans-002: General section (instanceIndex = 0, non-repeating)
  • ans-003 to ans-005: Farm #1 (instanceIndex = 1)
  • ans-006 to ans-008: Farm #2 (instanceIndex = 2)

═════════════════════════════════════════════════════════════════════════════
MEDIA_ATTACHMENTS (Photo Files)
═════════════════════════════════════════════════════════════════════════════

┌──────────────┬──────────────┬─────────────┬─────────────────────────────┬──────────┬────────────┬────────────┐
│ attachmentId │parentSurveyId│ answerUuid  │ localFilePath               │ fileSize │ uploadedAt │ syncStatus │
├──────────────┼──────────────┼─────────────┼─────────────────────────────┼──────────┼────────────┼────────────┤
│ media-001    │ survey-001   │ ans-005     │ /storage/photo_farm1.jpg    │ 2097152  │ null       │ PENDING    │
│ media-002    │ survey-001   │ ans-008     │ /storage/photo_farm2.jpg    │ 1835008  │ null       │ PENDING    │
└──────────────┴──────────────┴─────────────┴─────────────────────────────┴──────────┴────────────┴────────────┘

Explanation:
  • media-001: Photo for Farm #1 (links to ans-005, which is farm_photo for instance 1)
  • media-002: Photo for Farm #2 (links to ans-008, which is farm_photo for instance 2)

═════════════════════════════════════════════════════════════════════════════

SUMMARY OF THIS DATASET:
  • 1 Survey (survey-001) for farmer-A by agent-1
  • 8 Answers:
      - 2 general answers (farmer name, age)
      - 6 farm-related answers (2 farms × 3 questions each)
  • 2 Media Attachments (1 photo per farm)
  • All data is PENDING (not yet synced)

AFTER SYNC:
  • Survey syncStatus: PENDING → SYNCING → SYNCED
  • Answers syncStatus: PENDING → SYNCED
  • Media syncStatus: PENDING → SYNCED
  • Media uploadedAt: null → 2024-03-01 14:05:00

AFTER 30+ DAYS:
  • StorageCleanupWorker deletes:
      - /storage/photo_farm1.jpg (file)
      - /storage/photo_farm2.jpg (file)
      - media-001 and media-002 (database records)
  • Survey and Answers remain intact (only media cleaned up)
```

---

## Summary

SurveySyncEngine's database schema is designed for offline-first survey collection with the
following key characteristics:

1. **QuestionDefinitionEntity**: Serves as the template/schema for all survey questions, shared
   across all surveys
2. **SurveyEntity**: Represents individual survey collection sessions
3. **AnswerEntity**: Stores individual question responses with support for repeating sections
4. **MediaAttachmentEntity**: Tracks photo attachments linked to PHOTO-type answers

**Foreign Key Relationships**:

- Answers → Survey (CASCADE): Delete answers when survey deleted
- Answers → Questions (RESTRICT): Protect question templates
- Media → Answer (CASCADE): Delete media when answer deleted
- Media → Survey (CASCADE): Delete media when survey deleted

**Data Flow**: Question templates → Survey creation → Answers → Photos → Sync → Cleanup

This architecture enables reliable offline data collection with intelligent sync and storage
management for field operations in challenging connectivity environments.
