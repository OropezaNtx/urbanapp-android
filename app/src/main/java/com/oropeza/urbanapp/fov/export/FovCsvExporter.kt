package com.oropeza.urbanapp.fov.export

import android.content.Context
import android.net.Uri
import com.oropeza.urbanapp.asd.data.local.FovObservation
import com.oropeza.urbanapp.asd.data.local.FovSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FovCsvExporter {

    private val mxLocale = Locale("es", "MX")
    private val df = SimpleDateFormat("dd/MM/yyyy", mxLocale)
    private val tf = SimpleDateFormat("HH:mm:ss", mxLocale)
    private val dtf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", mxLocale)

    private fun escape(value: Any?): String {
        val text = value?.toString().orEmpty()
        return if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            "\"${text.replace("\"", "\"\"")}\""
        } else {
            text
        }
    }

    private fun fmtDate(ms: Long?): String = if (ms == null || ms == 0L) "" else df.format(Date(ms))
    private fun fmtTime(ms: Long?): String = if (ms == null || ms == 0L) "" else tf.format(Date(ms))
    private fun fmtDateTime(ms: Long?): String = if (ms == null || ms == 0L) "" else dtf.format(Date(ms))

    suspend fun exportSession(
        context: Context,
        uri: Uri,
        session: FovSession,
        observations: List<FovObservation>
    ) = withContext(Dispatchers.IO) {
        val headers = listOf(
            "sessionId",
            "folio",
            "seqInSession",
            "fecha",
            "hora",
            "fechaHora",
            "estacion",
            "ubicacion",
            "sentido",
            "esFs",
            "supervisor",
            "aforador",
            "poiKey",
            "observableId",
            "routeUid",
            "ruta",
            "numeroRutaEmpresa",
            "derroteroLetrero",
            "eco",
            "placa",
            "gradoOcupacion",
            "tipoVehiculo",
            "descTipoVehiculo",
            "observaciones",
            "lat",
            "lon",
            "accM",
            "provider",
            "fixTime",
            "locationStatus",
            "createdAtSession",
            "endedAtSession",
            "statusSession"
        )

        val rows = observations.sortedBy { it.timeMs }.map { o ->
            listOf(
                session.sessionId,
                o.folio,
                o.seqInSession,
                fmtDate(o.timeMs),
                fmtTime(o.timeMs),
                fmtDateTime(o.timeMs),
                session.estacion,
                session.ubicacion,
                session.sentido,
                session.esFs ?: "",
                session.supervisor ?: "",
                session.aforador ?: "",
                session.poiKey,
                o.observableId,
                o.routeUid,
                o.ruta,
                o.numeroRutaEmpresa,
                o.derroteroLetrero,
                o.eco ?: "",
                o.placa ?: "",
                o.gradoOcupacion ?: "",
                o.tipoVehiculo ?: "",
                o.descTipoVehiculo ?: "",
                o.observaciones ?: "",
                if (o.lat == 0.0) "" else o.lat,
                if (o.lon == 0.0) "" else o.lon,
                if (o.accM == 0.0 || o.accM >= 9999.0) "" else o.accM,
                o.provider,
                fmtDateTime(o.fixTime),
                o.locationStatus,
                fmtDateTime(session.createdAt),
                fmtDateTime(session.endedAt),
                if (session.endedAt == null) "ABIERTA" else "CERRADA"
            )
        }

        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: error("No se pudo abrir el archivo de salida")

        outputStream.use { os ->
            os.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            os.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.appendLine(headers.joinToString(","))
                rows.forEach { row ->
                    writer.appendLine(row.joinToString(",") { escape(it) })
                }
            }
        }
    }
}
