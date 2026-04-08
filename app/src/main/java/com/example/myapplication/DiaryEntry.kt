package com.example.myapplication

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diary_entries")
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String,
    val lat: Double?,
    val lng: Double?,
    val radiusMeters: Double,
    val poiId: Long? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

