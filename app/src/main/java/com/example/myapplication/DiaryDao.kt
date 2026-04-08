package com.example.myapplication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DiaryEntry): Long

    @Query("SELECT * FROM diary_entries ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<DiaryEntry>>

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun deleteById(id: Long)


    @Query("DELETE FROM diary_entries")
    suspend fun deleteAll()

    @Query("SELECT * FROM diary_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DiaryEntry?

    @Query("SELECT * FROM diary_entries WHERE lat IS NOT NULL AND lng IS NOT NULL")
    suspend fun getAllWithLocationOnce(): List<DiaryEntry>
}


