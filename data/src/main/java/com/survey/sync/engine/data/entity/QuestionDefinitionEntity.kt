package com.survey.sync.engine.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Question metadata/blueprint for UI rendering and validation.
 * Defines structure and behavior of survey questions.
 */
@Entity(tableName = "question_definitions")
data class QuestionDefinitionEntity(
    @PrimaryKey
    val questionKey: String, // Technical ID (e.g., crop_type)
    val sectionType: String, // GENERAL, FARM, LIVESTOCK, etc.
    val isRepeating: Boolean, // True if question belongs to a repeating section
    val inputType: String, // TEXT, NUMBER, GPS, PHOTO
    val labelText: String, // Display label for UI
    val sortOrder: Int, // Order within section
    val validationRules: String? // JSON string containing validation rules
)
