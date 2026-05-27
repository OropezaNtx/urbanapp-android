package com.oropeza.urbanapp.fov.importer

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.oropeza.urbanapp.asd.data.local.AppDatabase
import com.oropeza.urbanapp.asd.data.local.FovPoiCatalogItem
import com.oropeza.urbanapp.asd.data.local.FovRouteMaster
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.util.UUID

data class FovImportResult(
    val mastersUpserted: Int,
    val poiItemsUpserted: Int,
    val rowsRead: Int,
    val rowsSkipped: Int,
    val collisionsResolved: Int,
    val errors: List<String> = emptyList()
)

class FovExcelImporter(
    private val context: Context,
    private val db: AppDatabase
) {
    suspend fun importFromXlsx(uri: Uri): FovImportResult {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("No se pudo abrir el archivo")

        val errors = mutableListOf<String>()
        var rowsRead = 0
        var rowsSkipped = 0
        var collisionsResolved = 0

        input.use { stream ->
            val wb = XSSFWorkbook(stream)
            val sheet = wb.getSheetAt(0)
            val header = sheet.getRow(0) ?: error("El Excel no tiene encabezados (fila 1).")

            fun colIndex(name: String): Int {
                for (i in 0 until header.lastCellNum) {
                    val v = header.getCell(i)?.toString()?.trim() ?: continue
                    if (v.equals(name, ignoreCase = true)) return i
                }
                return -1
            }

            val cEst = colIndex("Estacion")
            val cUbi = colIndex("Ubicación")
            val cSen = colIndex("Sentido")
            val cId  = colIndex("ID") // opcional
            val cRuta = colIndex("RUTA")
            val cEmp = colIndex("Numero de Ruta/Empresa")
            val cDer = colIndex("Derrotero/Letrero")

            if (cEst < 0 || cUbi < 0 || cSen < 0 || cRuta < 0 || cEmp < 0 || cDer < 0) {
                error("Faltan columnas obligatorias en el Excel: Estacion, Ubicación, Sentido, RUTA, Numero de Ruta/Empresa, Derrotero/Letrero (ID opcional).")
            }

            fun norm(s: String) = s.trim().uppercase()
            fun masterKey(ruta: String, emp: String, der: String) = "${norm(ruta)}|${norm(emp)}|${norm(der)}"
            fun poiKey(est: String, ubi: String, sen: String) = "${est.trim()}|${ubi.trim()}|${sen.trim()}"

            val mastersToUpsert = mutableListOf<FovRouteMaster>()
            val poiItemsToUpsert = mutableListOf<FovPoiCatalogItem>()

            // cache: max observableId por POI (evita golpear DB por cada fila)
            val maxByPoi = mutableMapOf<String, Int>()

            db.withTransaction {
                val masterDao = db.fovRouteMasterDao()
                val poiDao = db.fovPoiCatalogDao()

                for (r in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(r) ?: continue
                    rowsRead++

                    val est = row.getCell(cEst)?.toString()?.trim().orEmpty()
                    val ubi = row.getCell(cUbi)?.toString()?.trim().orEmpty()
                    val sen = row.getCell(cSen)?.toString()?.trim().orEmpty()

                    val ruta = row.getCell(cRuta)?.toString()?.trim().orEmpty()
                    val emp  = row.getCell(cEmp)?.toString()?.trim().orEmpty()
                    val der  = row.getCell(cDer)?.toString()?.trim().orEmpty()

                    // Validaciones mínimas
                    if (est.isBlank() || ubi.isBlank() || sen.isBlank() || ruta.isBlank() || emp.isBlank() || der.isBlank()) {
                        rowsSkipped++
                        continue
                    }

                    val pKey = poiKey(est, ubi, sen)
                    val mKey = masterKey(ruta, emp, der)

                    // ✅ routeUid estable por mKey (no se duplica en reimport)
                    val routeUid = UUID.nameUUIDFromBytes(mKey.toByteArray(Charsets.UTF_8)).toString()

                    // Upsert Master (siempre mismo UID)
                    mastersToUpsert.add(
                        FovRouteMaster(
                            routeUid = routeUid,
                            ruta = ruta,
                            numeroRutaEmpresa = emp,
                            derroteroLetrero = der
                        )
                    )

                    // ✅ Si ya existe la misma ruta para el POI, no duplicar
                    val existingSameRoute = poiDao.getByRouteOnce(pKey, routeUid)
                    if (existingSameRoute != null) {
                        rowsSkipped++
                        continue
                    }

                    // Max observableId actual (cacheado)
                    val currentMax = maxByPoi.getOrPut(pKey) {
                        poiDao.getMaxObservableId(pKey) ?: 0
                    }

                    // observableId:
                    // - si Excel trae ID, usar si está libre
                    // - si choca, asignar max+1
                    // - si no trae, max+1
                    val excelIdStr = if (cId >= 0) row.getCell(cId)?.toString()?.trim() else null
                    val excelId = excelIdStr?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 }

                    val obsId = if (excelId != null) {
                        val exists = poiDao.getByObservableOnce(pKey, excelId)
                        if (exists == null) excelId else {
                            collisionsResolved++
                            currentMax + 1
                        }
                    } else {
                        currentMax + 1
                    }

                    // actualizar cache si se incrementó
                    if (obsId > currentMax) maxByPoi[pKey] = obsId

                    poiItemsToUpsert.add(
                        FovPoiCatalogItem(
                            poiKey = pKey,
                            observableId = obsId,
                            routeUid = routeUid,
                            active = true
                        )
                    )
                }

                // Guardar en la misma transacción
                mastersToUpsert.forEach { masterDao.upsert(it) }
                poiItemsToUpsert.forEach { poiDao.upsert(it) }
            }

            return FovImportResult(
                mastersUpserted = mastersToUpsert.size,
                poiItemsUpserted = poiItemsToUpsert.size,
                rowsRead = rowsRead,
                rowsSkipped = rowsSkipped,
                collisionsResolved = collisionsResolved,
                errors = errors
            )
        }
    }
}
