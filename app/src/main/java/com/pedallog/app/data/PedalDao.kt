package com.pedallog.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Data Access Object unificado para [PedalSession] e [PedalPoint].
 *
 * Todas as operações são [suspend] para execução segura em Dispatchers.IO,
 * garantindo que nenhuma operação de banco bloqueie a main thread do Wear OS.
 */
@Dao
interface PedalDao {

    // ── PedalSession ──────────────────────────────────────────────────────────

    /**
     * Insere uma nova sessão e retorna o ID gerado.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: PedalSession): Long

    /**
     * Atualiza uma sessão existente (isPaused, totalDistance, endTime).
     */
    @Update
    suspend fun updateSession(session: PedalSession)

    /**
     * Retorna a sessão ativa (sem endTime), ou null se não houver nenhuma.
     * Usada para detectar se há uma jornada em andamento ao abrir o app.
     */
    @Query("SELECT * FROM pedal_sessions WHERE endTime IS NULL LIMIT 1")
    suspend fun getActiveSession(): PedalSession?

    /**
     * Retorna uma sessão pelo ID.
     */
    @Query("SELECT * FROM pedal_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): PedalSession?

    /**
     * Retorna todas as sessões finalizadas, da mais recente para a mais antiga.
     */
    @Query("SELECT * FROM pedal_sessions WHERE endTime IS NOT NULL ORDER BY startTime DESC")
    suspend fun getAllCompletedSessions(): List<PedalSession>

    /**
     * Retorna todas as sessões registradas no banco.
     */
    @Query("SELECT * FROM pedal_sessions")
    suspend fun getAllSessions(): List<PedalSession>

    /**
     * Retorna sessões finalizadas que ainda não foram sincronizadas.
     */
    @Query("SELECT * FROM pedal_sessions WHERE endTime IS NOT NULL AND isSynced = 0 ORDER BY startTime ASC")
    suspend fun getUnsyncedSessions(): List<PedalSession>

    // ── PedalPoint ────────────────────────────────────────────────────────────

    /**
     * Insere um ponto GPS e retorna o rowId gerado.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: PedalPoint): Long

    /**
     * Retorna todos os pontos de uma sessão, ordenados cronologicamente.
     * Útil para reconstruir a rota e exibir o histórico do percurso.
     */
    @Query("SELECT * FROM pedal_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getPointsForSession(sessionId: Long): List<PedalPoint>

    /**
     * Remove todos os pontos de uma sessão específica.
     */
    @Query("DELETE FROM pedal_points WHERE sessionId = :sessionId")
    suspend fun clearPointsForSession(sessionId: Long)

    /**
     * Marca o último ponto gravado de uma sessão com segmentBreak = 1 (indicando pausa).
     */
    @Query("UPDATE pedal_points SET segmentBreak = 1 WHERE sessionId = :sessionId AND id = (SELECT id FROM pedal_points WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT 1)")
    suspend fun markLastPointAsBreak(sessionId: Long)
}
