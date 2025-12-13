package com.autoglm.helper.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface CommandDao {
    @Query("SELECT * FROM commands ORDER BY updatedAt DESC")
    fun getAll(): List<CommandEntity>

    @Query("SELECT * FROM commands WHERE id = :id LIMIT 1")
    fun getById(id: String): CommandEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(command: CommandEntity)

    @Update
    fun update(command: CommandEntity)

    @Query("DELETE FROM commands WHERE id = :id")
    fun delete(id: String)
}
