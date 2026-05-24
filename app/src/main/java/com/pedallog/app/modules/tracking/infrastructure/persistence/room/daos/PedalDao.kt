package com.pedallog.app.modules.tracking.infrastructure.persistence.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pedallog.app.modules.tracking.infrastructure.persistence.room.models.PedalPointModel
import com.pedallog.app.modules.tracking.infrastructure.persistence.room.models.PedalSessionModel

/**
 * Data Access Object unificado para [PedalSessionModel] e [PedalPointModel].
 */
@Dao
interface PedalDao {

    // ── PedalSessionModel ──────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: PedalSessionModel): Long

    @Update
    suspend fun updateSession(session: PedalSessionModel)

    @Query("SELECT * FROM pedal_sessions WHERE endTime IS NULL LIMIT 1")
    suspend fun getActiveSession(): PedalSessionModel?

    @Query("SELECT * FROM pedal_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): PedalSessionModel?

    @Query("SELECT * FROM pedal_sessions WHERE endTime IS NOT NULL ORDER BY startTime DESC")
    suspend fun getAllCompletedSessions(): List<PedalSessionModel>

    @Query("SELECT * FROM pedal_sessions")
    suspend fun getAllSessions(): List<PedalSessionModel>

    @Query("SELECT * FROM pedal_sessions WHERE endTime IS NOT NULL AND isSynced = 0 ORDER BY startTime ASC")
    suspend fun getUnsyncedSessions(): List<PedalSessionModel>

    // ── PedalPointModel ────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: PedalPointModel): Long

    @Query("SELECT * FROM pedal_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getPointsForSession(sessionId: Long): List<PedalPointModel>

    @Query("DELETE FROM pedal_points WHERE sessionId = :sessionId")
    suspend fun clearPointsForSession(sessionId: Long)

    @Query("UPDATE pedal_points SET segmentBreak = 1 WHERE sessionId = :sessionId AND id = (SELECT id FROM pedal_points WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT 1)")
    suspend fun markLastPointAsBreak(sessionId: Long)
}
