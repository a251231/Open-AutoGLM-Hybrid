package com.autoglm.helper.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CommandHistoryDao {
    @Query("SELECT * FROM command_history WHERE commandId = :commandId ORDER BY timestamp DESC")
    fun historyForCommand(commandId: String): List<CommandHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(history: CommandHistoryEntity)
}
