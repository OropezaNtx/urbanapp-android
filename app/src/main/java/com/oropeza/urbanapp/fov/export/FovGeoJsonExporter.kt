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

object FovGeoJsonExporter {

    private val mxLocale = Locale("es", "MX")
    private val dtf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", mxLocale)

    private fun json(value: String?): String {
        val safe = value.orEmpty()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
        return "\"$safe\""
    }

    private fun fmtDateTime(ms: Long?): String {
        return if (ms == null || ms == 0L) "" else dtf.format(Date(ms))
    }

    private fun validPoint(o: FovObservation): Boolean {
        return o.lat != 0.0 && o.lon != 0.0 && o.locationStatus != "NO_FIX"
    }

    suspend fun exportSession(
        context: Context,
        uri: Uri,
        session: FovSession,
        observations: List<FovObservation>
    ) = withContext(Dispatchers.IO) {
        val features = observations
            .filter { validPoint(it) }
            .sortedBy { it.timeMs }
            .map { o ->
                """
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [${o.lon}, ${o.lat}]
                  },
                  "properties": {
                    "sessionId": ${session.sessionId},
                    "folio": ${json(o.folio)},
                    "seqInSession": ${o.seqInSession},
                    "fechaHora": ${json(fmtDateTime(o.timeMs))},
                    "estacion": ${json(session.estacion)},
                    "ubicacion": ${json(session.ubicacion)},
                    "sentido": ${json(session.sentido)},
                    "poiKey": ${json(session.poiKey)},
                    "observableId": ${o.observableId},
                    "routeUid": ${json(o.routeUid)},
                    "ruta": ${json(o.ruta)},
                    "numeroRutaEmpresa": ${json(o.numeroRutaEmpresa)},
                    "derroteroLetrero": ${json(o.derroteroLetrero)},
                    "eco": ${json(o.eco)},
                    "placa": ${json(o.placa)},
                    "gradoOcupacion": ${json(o.gradoOcupacion)},
                    "tipoVehiculo": ${json(o.tipoVehiculo)},
                    "descTipoVehiculo": ${json(o.descTipoVehiculo)},
                    "observaciones": ${json(o.observaciones)},
                    "accM": ${o.accM},
                    "provider": ${json(o.provider)},
                    "fixTime": ${json(fmtDateTime(o.fixTime))},
                    "locationStatus": ${json(o.locationStatus)},
                    "statusSession": ${json(if (session.endedAt == null) "ABIERTA" else "CERRADA")}
                  }
                }
                """.trimIndent()
            }

        val geoJson = """
        {
          "type": "FeatureCollection",
          "name": "FOV_${session.sessionId}",
          "features": [
            ${features.joinToString(",\n")}
          ]
        }
        """.trimIndent()

        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: error("No se pudo abrir el archivo GeoJSON de salida")

        outputStream.use { os ->
            os.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(geoJson)
            }
        }
    }
}
