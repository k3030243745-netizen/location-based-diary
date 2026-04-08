package com.example.myapplication

data class SuggestedCategory(
    val categoryId: String,
    val confidence: Double,
    val displayName: String
)

data class IntentClassifyResponse(
    val purpose: String,
    val topCategories: List<SuggestedCategory>,
    val recommendedRadiusM: Int,
    val keywords: List<String>,
    val source: String
)