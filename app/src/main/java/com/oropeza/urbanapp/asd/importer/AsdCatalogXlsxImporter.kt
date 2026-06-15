package com.oropeza.urbanapp.asd.importer

import android.content.Context
import android.net.Uri
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.AsdCatalogSyncState
import com.oropeza.urbanapp.asd.data.local.AsdFieldPersonCatalogItem
import com.oropeza.urbanapp.asd.data.local.AsdRouteCatalogItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream

data class ImportResult(
    val routesImported: Int,
    val peopleImported: Int,
    val warnings: List<String> = emptyList()
)

object AsdCatalogXlsxImporter {

    suspend fun importFromUri(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val warnings = mutableListOf<String>()
        var routesCount = 0
        var peopleCount = 0

        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                return@withContext ImportResult(0, 0, listOf("No se pudo abrir el archivo."))
            }

            val workbook = XSSFWorkbook(inputStream)

            // 1. CATALOGO_RUTAS
            val routeSheet = workbook.getSheet("CATALOGO_RUTAS")
            if (routeSheet == null) {
                warnings.add("Falta la hoja 'CATALOGO_RUTAS'.")
            } else {
                val routes = mutableListOf<AsdRouteCatalogItem>()
                val headerRow = routeSheet.getRow(0)
                if (headerRow == null) {
                    warnings.add("Hoja 'CATALOGO_RUTAS' está vacía.")
                } else {
                    val colMap = mapHeader(headerRow)
                    for (i in 1..routeSheet.lastRowNum) {
                        val row = routeSheet.getRow(i) ?: continue
                        val id = getStringValue(row, colMap["ID"])
                        val ruta = getStringValue(row, colMap["RUTA"])
                        val sentido = getStringValue(row, colMap["SENTIDO"])?.trim()?.uppercase()

                        if (id.isNullOrBlank() || ruta.isNullOrBlank() || sentido.isNullOrBlank()) {
                            warnings.add("Fila ${i + 1} en RUTAS saltada: ID, RUTA o SENTIDO faltantes.")
                            continue
                        }

                        val normSentido = if (sentido.contains("REGRESO")) "REGRESO" else "IDA"

                        routes.add(
                            AsdRouteCatalogItem(
                                catalogId = id.trim().uppercase(),
                                direction = normSentido,
                                routeName = ruta.trim().uppercase(),
                                company = getStringValue(row, colMap["EMPRESA"])?.trim()?.uppercase(),
                                derrotero = getStringValue(row, colMap["DERROTERO"])?.trim()?.uppercase(),
                                cromatica = getStringValue(row, colMap["CROMATICA"])?.trim()?.uppercase(),
                                baseStart = getStringValue(row, colMap["BASE_INICIO"])?.trim()?.uppercase(),
                                baseEnd = getStringValue(row, colMap["BASE_FINAL"])?.trim()?.uppercase(),
                                observacion = getStringValue(row, colMap["OBSERVACION"])?.trim()?.uppercase(),
                                active = parseActive(getStringValue(row, colMap["ACTIVO"]))
                            )
                        )
                    }
                    if (routes.isNotEmpty()) {
                        AsdGraph.repo.replaceAsdRouteCatalog(routes)
                        routesCount = routes.size
                    }
                }
            }

            // 2. GENTE_CAMPO
            val peopleSheet = workbook.getSheet("GENTE_CAMPO")
            if (peopleSheet == null) {
                warnings.add("Falta la hoja 'GENTE_CAMPO'.")
            } else {
                val people = mutableListOf<AsdFieldPersonCatalogItem>()
                val headerRow = peopleSheet.getRow(0)
                if (headerRow == null) {
                    warnings.add("Hoja 'GENTE_CAMPO' está vacía.")
                } else {
                    val colMap = mapHeader(headerRow)
                    for (i in 1..peopleSheet.lastRowNum) {
                        val row = peopleSheet.getRow(i) ?: continue
                        val personId = getStringValue(row, colMap["PERSON_ID"])
                        val name = getStringValue(row, colMap["NOMBRE"])

                        if (personId.isNullOrBlank() || name.isNullOrBlank()) {
                            warnings.add("Fila ${i + 1} en GENTE saltada: ID o NOMBRE faltantes.")
                            continue
                        }

                        val rawRole = getStringValue(row, colMap["ROL"])?.trim()?.uppercase()
                        val role = if (rawRole in listOf("OBSERVADOR", "SUPERVISOR", "AMBOS")) rawRole!! else "AMBOS"

                        val rawSex = getStringValue(row, colMap["SEXO_DEFAULT"])?.trim()?.uppercase()
                        val sex = when {
                            rawSex in listOf("H", "HOMBRE") -> "H"
                            rawSex in listOf("M", "MUJER") -> "M"
                            else -> null
                        }

                        people.add(
                            AsdFieldPersonCatalogItem(
                                personId = personId.trim().uppercase(),
                                name = name.trim().uppercase(),
                                role = role,
                                defaultSex = sex,
                                active = parseActive(getStringValue(row, colMap["ACTIVO"]))
                            )
                        )
                    }
                    if (people.isNotEmpty()) {
                        AsdGraph.repo.replaceAsdPeopleCatalog(people)
                        peopleCount = people.size
                    }
                }
            }

            workbook.close()
            inputStream.close()

            val result = ImportResult(routesCount, peopleCount, warnings)
            
            // Actualizar estado de sincronización
            AsdGraph.repo.updateCatalogSyncState(
                AsdCatalogSyncState(
                    source = "XLSX",
                    lastSyncAt = System.currentTimeMillis(),
                    routesCount = routesCount,
                    peopleCount = peopleCount,
                    status = if (routesCount > 0) "READY" else "ERROR",
                    message = if (routesCount > 0) {
                        "Importación exitosa: $routesCount rutas, $peopleCount personas." + (if (warnings.isNotEmpty()) " Con ${warnings.size} advertencias." else "")
                    } else {
                        "Error en importación: no se encontraron rutas."
                    }
                )
            )

            result
        } catch (e: Exception) {
            val errResult = ImportResult(0, 0, listOf("Error crítico: ${e.message}"))
            AsdGraph.repo.updateCatalogSyncState(
                AsdCatalogSyncState(
                    source = "XLSX",
                    lastSyncAt = System.currentTimeMillis(),
                    status = "ERROR",
                    message = "Error crítico: ${e.message}"
                )
            )
            errResult
        }
    }

    private fun mapHeader(row: Row): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        for (i in 0 until row.lastCellNum) {
            val cell = row.getCell(i) ?: continue
            val value = cell.toString().trim().uppercase()
            map[value] = i
        }
        return map
    }

    private fun getStringValue(row: Row, index: Int?): String? {
        if (index == null) return null
        val cell = row.getCell(index) ?: return null
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> {
                val num = cell.numericCellValue
                if (num == num.toLong().toDouble()) num.toLong().toString() else num.toString()
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> {
                try {
                    cell.stringCellValue
                } catch (e: Exception) {
                    cell.numericCellValue.toString()
                }
            }
            else -> cell.toString()
        }
    }

    private fun parseActive(value: String?): Boolean {
        if (value.isNullOrBlank()) return true
        val v = value.trim().uppercase()
        return v in listOf("SI", "SÍ", "TRUE", "1", "Y", "YES")
    }
}
