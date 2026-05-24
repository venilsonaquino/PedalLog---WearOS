package com.pedallog.app.sync

import android.content.Context
import android.util.Log
import com.pedallog.app.data.PedalPoint
import com.pedallog.app.data.PedalSession
import com.pedallog.app.util.GzipCsvUtils
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.Asset
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Responsável por serializar e enviar os dados de uma sessão finalizada
 * para o app mobile via Wearable Data Layer (Google Play Services).
 *
 * ## Protocolo de entrega
 * - Path: `/pedal_session/{syncUuid}` — único por sessão; funciona como
 *   chave de idempotência no app mobile.
 * - Formato dos pontos: CSV compacto comprimido com GZIP.
 */
object WearSyncManager {

    private const val TAG = "WearSyncManager"

    /** Prefixo do path no Wearable Data Layer. */
    private const val DATA_PATH = "/pedal_session"

    /**
     * Limite de aviso de tamanho (80 KB). O DataLayer aceita até ~100 KB,
     * mas mantemos margem de segurança de 20% para metadados internos do GMS.
     */
    private const val SIZE_WARN_BYTES = 80_000

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Serializa e publica uma sessão finalizada na Wearable Data Layer.
     *
     * @param context  Preferencialmente `applicationContext`.
     * @param session  A sessão já finalizada.
     * @param points   Todos os pontos GPS associados à sessão.
     * @throws Exception se a publicação no Data Layer falhar.
     */
    suspend fun syncSession(
        context: Context,
        session: PedalSession,
        points: List<PedalPoint>,
        activeDurationMs: Long
    ) {
        // ── 1. Serializar pontos → CSV → GZIP via Utils ───────────────────────
        val compressedPoints = GzipCsvUtils.compressPoints(points)

        Log.d(
            TAG,
            "Sincronizando sessão ${session.syncUuid}: " +
            "${points.size} pontos | " +
            "GZIP=${compressedPoints.size} bytes | " +
            "Duração=${activeDurationMs}ms"
        )

        if (compressedPoints.size > SIZE_WARN_BYTES) {
            Log.w(
                TAG,
                "Payload comprimido (${compressedPoints.size} B) excede ${SIZE_WARN_BYTES} B"
            )
        }

        // ── 2. Montar PutDataMapRequest ───────────────────────────────────────
        Log.d(TAG, "Montando DataMap para envio ao Mobile:")
        Log.d(TAG, " -> sync_uuid: ${session.syncUuid}")
        Log.d(TAG, " -> session_id: ${session.id}")
        Log.d(TAG, " -> start_time: ${session.startTime}")
        Log.d(TAG, " -> end_time: ${session.endTime}")
        Log.d(TAG, " -> total_distance: ${session.totalDistance}km")
        Log.d(TAG, " -> active_duration_ms: $activeDurationMs")
        Log.d(TAG, " -> point_count: ${points.size}")
        Log.d(TAG, " -> points_gz size: ${compressedPoints.size} bytes")

        val asset = Asset.createFromBytes(compressedPoints)

        val request = PutDataMapRequest
            .create("$DATA_PATH/${session.syncUuid}")
            .apply {
                dataMap.run {
                    putString("sync_uuid",            session.syncUuid)
                    putLong("session_id",             session.id)
                    putLong("start_time",             session.startTime.time)
                    putLong("end_time",               session.endTime?.time ?: 0L)
                    putFloat("total_distance",        session.totalDistance)
                    putFloat("total_elevation_gain",   session.totalElevationGain)
                    putLong("active_duration_ms",     activeDurationMs)
                    putInt("point_count",             points.size)
                    putAsset("points_asset",          asset)
                    putLong("sync_timestamp",         System.currentTimeMillis())
                }
            }
            .asPutDataRequest()
            .setUrgent()

        // ── 3. Publicar no Data Layer ─────────────────────────────────────────
        val dataClient = Wearable.getDataClient(context)

        suspendCancellableCoroutine<Unit> { cont ->
            dataClient.putDataItem(request)
                .addOnSuccessListener { dataItem ->
                    Log.d(TAG, "DataItem publicado: ${dataItem.uri}")
                    cont.resume(Unit)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Falha ao publicar DataItem: ${e.message}")
                    cont.resumeWithException(e)
                }
        }
    }
}
