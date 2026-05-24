package com.pedallog.app.shared.util

import com.pedallog.app.modules.tracking.infrastructure.persistence.room.models.PedalPointModel
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * Utilitário para converter listas de [PedalPointModel] em CSV compacto e comprimir com GZIP.
 */
object GzipCsvUtils {

    /**
     * Converte a lista de pontos GPS em um CSV compacto e o comprime com GZIP.
     * Formato por linha: `latitude,longitude,speed_kmh,distance_km,timestamp_ms,segmentBreak,elevation`
     */
    fun compressPoints(points: List<PedalPointModel>): ByteArray {
        val csv = buildPointsCsv(points)
        return gzip(csv)
    }

    /**
     * Converte a lista de pontos GPS em um CSV compacto.
     */
    private fun buildPointsCsv(points: List<PedalPointModel>): String = buildString(
        capacity = points.size * 56
    ) {
        points.forEach { point ->
            append("%.6f".format(point.latitude)).append(',')
            append("%.6f".format(point.longitude)).append(',')
            append("%.2f".format(point.speed)).append(',')
            append("%.4f".format(point.distance)).append(',')
            append(point.timestamp).append(',')
            append(point.segmentBreak).append(',')
            append("%.2f".format(point.elevation)).appendLine()
        }
    }

    /**
     * Comprime uma string com GZIP.
     */
    private fun gzip(data: String): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzipStream ->
            gzipStream.write(data.toByteArray(Charsets.UTF_8))
        }
        return bos.toByteArray()
    }
}
