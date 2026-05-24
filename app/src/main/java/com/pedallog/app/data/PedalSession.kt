package com.pedallog.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

/**
 * Representa uma jornada completa de ciclismo.
 *
 * Ciclo de vida de uma sessão:
 *  1. Criada com [isPaused] = false e [endTime] = null → sessão ativa
 *  2. Pausada → [isPaused] = true (serviço permanece vivo)
 *  3. Retomada → [isPaused] = false
 *  4. Finalizada → [endTime] preenchido; dados sincronizados via Data Layer
 *
 * @param id            Chave primária gerada automaticamente.
 * @param startTime     Momento do primeiro Start (convertido via [Converters]).
 * @param endTime       Momento do Finish; null = sessão ainda ativa.
 * @param totalDistance Distância acumulada em km — atualizada a cada pausa/finish.
 * @param isPaused      Indica se a sessão está temporariamente pausada.
 * @param syncUuid      UUID único gerado na criação — garante idempotência na
 *                      sincronização com o app mobile (evita duplicatas caso o
 *                      pacote Data Layer seja entregue mais de uma vez).
 */
@Entity(tableName = "pedal_sessions")
data class PedalSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Date = Date(),
    val endTime: Date? = null,
    val totalDistance: Float = 0f,                          // km
    val isPaused: Boolean = false,
    val isSynced: Boolean = false,                          // Indica se já foi sincronizado com o celular
    val activeDurationMs: Long = 0,                         // Duração real cronometrada (ms)
    val syncUuid: String = UUID.randomUUID().toString(),    // identificador único de sincronização
    val totalElevationGain: Float = 0f                      // ganho de elevação acumulado em metros
)
