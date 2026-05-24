package com.pedallog.app.modules.tracking.domain.entities

import com.pedallog.app.modules.tracking.domain.valueobjects.Distance
import com.pedallog.app.modules.tracking.domain.valueobjects.Elevation
import com.pedallog.app.modules.tracking.domain.valueobjects.SessionIdentity
import com.pedallog.app.modules.tracking.domain.valueobjects.SessionState
import java.util.Date

/**
 * Entidade Raiz de Agregado do Domínio para controle de sessão de ciclismo.
 * Encapsula o ciclo de vida e regras de negócio de ativação, pause e conclusão.
 */
data class RideSessionEntity(
    val identity: SessionIdentity,
    val state: SessionState
) {

    fun pause(): RideSessionEntity {
        return RideSessionEntity(
            identity = this.identity,
            state = this.state.pause()
        )
    }

    fun resume(): RideSessionEntity {
        return RideSessionEntity(
            identity = this.identity,
            state = this.state.resume()
        )
    }

    fun updateMetrics(segmentDistance: Distance, currentElevation: Elevation): RideSessionEntity {
        if (isFinished()) {
            return this
        }
        return RideSessionEntity(
            identity = this.identity,
            state = this.state.updateMetrics(segmentDistance, currentElevation)
        )
    }

    fun tickTime(ms: Long): RideSessionEntity {
        if (isFinished()) {
            return this
        }
        return RideSessionEntity(
            identity = this.identity,
            state = this.state.tickTime(ms)
        )
    }

    fun finish(): RideSessionEntity {
        if (isFinished()) {
            return this
        }
        val finishedIdentity = identity.finish(Date())
        return RideSessionEntity(
            identity = finishedIdentity,
            state = this.state.pause()
        )
    }

    fun isFinished(): Boolean {
        return identity.endTime != null
    }

    companion object {
        fun createNew(id: Long = 0L): RideSessionEntity {
            return RideSessionEntity(
                identity = SessionIdentity.createNew(id),
                state = SessionState.createActive()
            )
        }
    }
}
