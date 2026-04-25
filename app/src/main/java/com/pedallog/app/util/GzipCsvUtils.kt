package com.pedallog.app.util

import com.pedallog.app.data.PedalPoint
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * Utilitário para converter listas de [PedalPoint] em CSV compacto e comprimir com GZIP.
 */
object GzipCsvUtils {

    /**
     * Converte a lista de pontos GPS em um CSV compacto e o comprime com GZIP.
     * Formato por linha: `latitude,longitude,speed_kmh,distance_km,timestamp_ms`
     */
    fun compressPoints(points: List<PedalPoint>): ByteArray {
        val csv = buildPointsCsv(points)
        return gzip(csv)
    }

    /**
     * Converte a lista de pontos GPS em um CSV compacto.
     */
    private fun buildPointsCsv(points: List<PedalPoint>): String = buildString(
        capacity = points.size * 46
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
     * Comprime uma string com GZIP.
     */
    private fun gzip(data: String): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gz ->
            gz.write(data.toByteArray(Charsets.UTF_8))
        }
        return bos.toByteArray()
    }
}
