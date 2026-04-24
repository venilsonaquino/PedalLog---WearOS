package com.example.pedallog.sync

import android.content.Context
import android.util.Log
import com.example.pedallog.data.PedalPoint
import com.example.pedallog.data.PedalSession
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Responsável por serializar e enviar os dados de uma sessão finalizada
 * para o app mobile via Wearable Data Layer (Google Play Services).
 *
 * ## Protocolo de entrega
 * - Path: `/pedal_session/{syncUuid}` — único por sessão; funciona como
 *   chave de idempotência no app mobile.
 * - Formato dos pontos: CSV compacto (5 campos) comprimido com GZIP.
 *   Isso reduz em ~70% o tamanho, mantendo o payload bem abaixo do
 *   limite de 100 KB do DataItem.
 *
 * ## Estrutura do DataMap enviado
 * | Chave            | Tipo       | Descrição                              |
 * |------------------|------------|----------------------------------------|
 * | `sync_uuid`      | String     | UUID único da sessão                   |
 * | `session_id`     | Long       | ID interno do banco (watch-side)       |
 * | `start_time`     | Long       | Epoch ms do início                     |
 * | `end_time`       | Long       | Epoch ms do fim                        |
 * | `total_distance` | Float      | Distância total em km                  |
 * | `point_count`    | Int        | Número de pontos no CSV                |
 * | `points_gz`      | ByteArray  | CSV dos pontos, GZIP-comprimido        |
 *
 * ## Formato do CSV (`points_gz` descomprimido)
 * Uma linha por ponto: `latitude,longitude,speed_kmh,distance_km,timestamp_ms`
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
     * Deve ser chamado em um escopo de coroutine com `Dispatchers.IO`
     * (ou será executado no dispatcher do caller).
     *
     * @param context  Preferencialmente `applicationContext` para evitar leaks.
     * @param session  A sessão já finalizada (com `endTime` preenchido).
     * @param points   Todos os pontos GPS associados à sessão.
     * @throws Exception se a publicação no Data Layer falhar.
     */
    suspend fun syncSession(
        context: Context,
        session: PedalSession,
        points: List<PedalPoint>
    ) {
        // ── 1. Serializar pontos → CSV → GZIP ─────────────────────────────────
        val csv = buildPointsCsv(points)
        val compressedPoints = gzip(csv)

        Log.d(
            TAG,
            "Sincronizando sessão ${session.syncUuid}: " +
            "${points.size} pontos | " +
            "CSV bruto=${csv.length} bytes | " +
            "GZIP=${compressedPoints.size} bytes"
        )

        if (compressedPoints.size > SIZE_WARN_BYTES) {
            Log.w(
                TAG,
                "Payload comprimido (${compressedPoints.size} B) excede ${SIZE_WARN_BYTES} B — " +
                "considere reduzir a frequência de amostragem ou dividir em múltiplos DataItems"
            )
        }

        // ── 2. Montar PutDataMapRequest ───────────────────────────────────────
        val request = PutDataMapRequest
            .create("$DATA_PATH/${session.syncUuid}")
            .apply {
                dataMap.run {
                    putString("sync_uuid",       session.syncUuid)
                    putLong("session_id",         session.id)
                    putLong("start_time",         session.startTime.time)
                    putLong("end_time",           session.endTime?.time ?: 0L)
                    putFloat("total_distance",    session.totalDistance)    // km
                    putInt("point_count",         points.size)
                    putByteArray("points_gz",     compressedPoints)
                }
            }
            .asPutDataRequest()
            .setUrgent()   // entrega prioritária — ignora otimizações de batching do GMS

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

    // ── Serialização ──────────────────────────────────────────────────────────

    /**
     * Converte a lista de pontos GPS em um CSV compacto.
     *
     * Formato por linha: `latitude,longitude,speed_kmh,distance_km,timestamp_ms`
     *
     * Escolhas de precisão:
     * - Lat/Lon: 6 casas decimais (~11 cm de resolução — mais que suficiente)
     * - Speed: 2 casas (0.01 km/h)
     * - Distance: 4 casas (0.1 m)
     * - Timestamp: inteiro (sem casas decimais)
     *
     * Estimativa de tamanho bruto: ~45 chars/linha × 3600 linhas (1h a 1 Hz) ≈ 162 KB.
     * Após GZIP: ~30–50 KB (GPS tem alta redundância, compress bem).
     */
    private fun buildPointsCsv(points: List<PedalPoint>): String = buildString(
        capacity = points.size * 46   // pre-aloca para evitar resize de StringBuilder
    ) {
        points.forEach { p ->
            append("%.6f".format(p.latitude)).append(',')
            append("%.6f".format(p.longitude)).append(',')
            append("%.2f".format(p.speed)).append(',')
            append("%.4f".format(p.distance)).append(',')
            appendLine(p.timestamp)
        }
    }

    /**
     * Comprime [data] com GZIP usando UTF-8.
     * A saída é determinística para o mesmo input (útil para debugging).
     */
    private fun gzip(data: String): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gz ->
            gz.write(data.toByteArray(Charsets.UTF_8))
        }
        return bos.toByteArray()
    }
}
