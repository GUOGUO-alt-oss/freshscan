package com.example.freshscan.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun get(): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)
}
