package com.example.myapplication

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pois")
data class Poi(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val lat: Double,
    val lng: Double,
    val source: String,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)