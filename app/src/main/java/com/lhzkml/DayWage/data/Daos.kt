package com.lhzkml.DayWage.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE archived = 0 ORDER BY position, name")
    fun all(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE archived = 0 ORDER BY position, name")
    suspend fun list(): List<Project>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun byId(id: String): Project?

    @Query("SELECT COUNT(*) FROM projects WHERE archived = 0")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(project: Project)

    @Query("DELETE FROM projects WHERE id = :id AND id != 'default'")
    suspend fun delete(id: String)

    @Query("UPDATE projects SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)
}

@Dao
interface SalaryDao {
    @Query("SELECT * FROM salary_versions WHERE projectId = :projectId ORDER BY fromDay DESC, id DESC")
    fun versions(projectId: String): Flow<List<SalaryVersion>>

    @Query("SELECT * FROM salary_versions WHERE projectId = :projectId ORDER BY fromDay DESC, id DESC")
    suspend fun versionsOnce(projectId: String): List<SalaryVersion>

    /** 某天生效的版本：生效日不晚于该天的最新一条。 */
    @Query("SELECT * FROM salary_versions WHERE projectId = :projectId AND fromDay <= :day ORDER BY fromDay DESC, id DESC LIMIT 1")
    suspend fun effective(projectId: String, day: Long): SalaryVersion?

    @Query("SELECT * FROM salary_versions WHERE projectId = :projectId ORDER BY fromDay DESC, id DESC LIMIT 1")
    suspend fun latest(projectId: String): SalaryVersion?

    @Query("SELECT * FROM salary_versions")
    fun allVersions(): Flow<List<SalaryVersion>>

    @Query("SELECT * FROM salary_versions")
    suspend fun allVersionsOnce(): List<SalaryVersion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(version: SalaryVersion): Long

    @Query("DELETE FROM salary_versions WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts ORDER BY position")
    fun all(): Flow<List<Shift>>

    @Query("SELECT * FROM shifts ORDER BY position")
    suspend fun list(): List<Shift>

    @Upsert
    suspend fun upsert(shift: Shift)

    @Query("DELETE FROM shifts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    fun range(from: Long, to: Long): Flow<List<WorkRecord>>

    @Query("SELECT * FROM records WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    suspend fun rangeOnce(from: Long, to: Long): List<WorkRecord>

    @Query("SELECT * FROM records WHERE epochDay = :day")
    fun day(day: Long): Flow<WorkRecord?>

    @Query("SELECT * FROM records WHERE epochDay = :day")
    suspend fun dayOnce(day: Long): WorkRecord?

    @Query("SELECT * FROM records ORDER BY epochDay DESC")
    fun all(): Flow<List<WorkRecord>>

    @Upsert
    suspend fun save(record: WorkRecord)

    @Query("DELETE FROM records WHERE epochDay = :day")
    suspend fun delete(day: Long)

    @Query("SELECT COUNT(*) FROM records")
    suspend fun count(): Int

    @Query("DELETE FROM records")
    suspend fun clearAll()
}

@Dao
interface PayItemDao {
    @Query("SELECT * FROM pay_items ORDER BY kind, position, id")
    fun all(): Flow<List<PayItem>>

    @Query("SELECT * FROM pay_items ORDER BY kind, position, id")
    suspend fun list(): List<PayItem>

    @Upsert
    suspend fun upsert(item: PayItem): Long

    @Query("DELETE FROM pay_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM pay_items")
    suspend fun count(): Int
}

@Dao
interface DayPayDao {
    @Query("SELECT * FROM day_pays WHERE epochDay = :day")
    fun forDay(day: Long): Flow<List<DayPay>>

    @Query("SELECT * FROM day_pays WHERE epochDay = :day")
    suspend fun forDayOnce(day: Long): List<DayPay>

    @Query("SELECT * FROM day_pays WHERE epochDay BETWEEN :from AND :to")
    fun range(from: Long, to: Long): Flow<List<DayPay>>

    @Query("SELECT * FROM day_pays WHERE epochDay BETWEEN :from AND :to")
    suspend fun rangeOnce(from: Long, to: Long): List<DayPay>

    @Upsert
    suspend fun save(pay: DayPay)

    @Delete
    suspend fun delete(pay: DayPay)

    @Query("DELETE FROM day_pays WHERE epochDay = :day AND itemId = :itemId")
    suspend fun deleteFor(day: Long, itemId: Long)

    @Query("DELETE FROM day_pays WHERE epochDay = :day")
    suspend fun clearDay(day: Long)

    @Query("DELETE FROM day_pays")
    suspend fun clearAll()
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY done, endDay, id DESC")
    fun all(): Flow<List<Goal>>

    @Query("SELECT * FROM goals ORDER BY done, endDay, id DESC")
    suspend fun list(): List<Goal>

    @Upsert
    suspend fun upsert(goal: Goal): Long

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE goals SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("DELETE FROM goals")
    suspend fun clearAll()
}
