package com.pedallog.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Representa um ponto de rastreamento GPS capturado durante uma [PedalSession].
 *
 * Cada registro é inserido no banco a cada atualização de localização válida
 * enquanto a sessão NÃO está pausada. Ao excluir uma sessão, todos os seus
 * pontos são removidos automaticamente via CASCADE.
 *
 * @param id          Chave primária gerada automaticamente.
 * @param sessionId   FK para [PedalSession.id] — agrupa pontos por jornada.
 * @param latitude    Latitude do ponto em graus decimais.
 * @param longitude   Longitude do ponto em graus decimais.
 * @param speed       Velocidade no instante do fix, em km/h.
 * @param distance    Distância total acumulada até este ponto na sessão, em km.
 * @param timestamp   Epoch em milissegundos do fix GPS.
 */
@Entity(
    tableName = "pedal_points",
    foreignKeys = [
        ForeignKey(
            entity = PedalSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE   // remove pontos orfãos ao deletar a sessão
        )
    ],
    indices = [Index("sessionId")]          // índice para acelerar queries por sessão
)
data class PedalPoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,                    // FK obrigatória — sempre associada a uma sessão
    val latitude: Double,
    val longitude: Double,
    val speed: Double,                      // km/h
    val distance: Double,                   // km — distância acumulada na sessão
    val timestamp: Long = System.currentTimeMillis(),
    val segmentBreak: Int = 0,              // 1 significa que este é o último ponto antes de uma pausa
    val elevation: Double = 0.0             // altitude em metros
)
