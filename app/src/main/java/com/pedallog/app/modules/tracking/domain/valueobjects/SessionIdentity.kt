package com.pedallog.app.modules.tracking.domain.valueobjects

import java.util.Date
import java.util.UUID

/**
 * Encapsula a identidade e os metadados temporais de uma sessão de pedalada.
 * Auxilia a manter a coesão de classe e o limite de atributos do Object Calisthenics.
 */
data class SessionIdentity(
    val id: Long,
    val syncUuid: String,
    val startTime: Date,
    val endTime: Date? = null
) {

    fun finish(endTime: Date): SessionIdentity {
        return SessionIdentity(
            id = this.id,
            syncUuid = this.syncUuid,
            startTime = this.startTime,
            endTime = endTime
        )
    }

    companion object {
        fun createNew(id: Long = 0L): SessionIdentity {
            return SessionIdentity(
                id = id,
                syncUuid = UUID.randomUUID().toString(),
                startTime = Date()
            )
        }
    }
}
