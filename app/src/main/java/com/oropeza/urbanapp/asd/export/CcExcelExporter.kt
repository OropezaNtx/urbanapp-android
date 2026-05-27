package com.oropeza.urbanapp.asd.export

import android.content.Context
import android.net.Uri
import com.oropeza.urbanapp.asd.data.local.CcEvent
import com.oropeza.urbanapp.asd.data.local.CcSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CcExcelExporter {

    private val df = SimpleDateFormat("dd/MM/yyyy", Locale("es", "MX"))
    private val tf = SimpleDateFormat("HH:mm:ss", Locale("es", "MX"))

    private fun fmtDate(ms: Long): String = df.format(Date(ms))
    private fun fmtTime(ms: Long): String = tf.format(Date(ms))

    private fun escXml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun colName(index0: Int): String {
        var n = index0
        var name = ""
        do {
            val r = n % 26
            name = ('A'.code + r).toChar() + name
            n = (n / 26) - 1
        } while (n >= 0)
        return name
    }

    private fun cellRef(col0: Int, row1: Int): String = "${colName(col0)}$row1"

    private fun cellInlineStr(ref: String, value: String): String =
        """<c r="$ref" t="inlineStr"><is><t>${escXml(value)}</t></is></c>"""

    private fun cellNumber(ref: String, value: Number): String =
        """<c r="$ref"><v>${value}</v></c>"""

    private fun rowXml(row1: Int, values: List<Any?>): String {
        val cells = buildString {
            values.forEachIndexed { col, v ->
                val ref = cellRef(col, row1)
                when (v) {
                    null -> append(cellInlineStr(ref, ""))
                    is Number -> append(cellNumber(ref, v))
                    else -> append(cellInlineStr(ref, v.toString()))
                }
            }
        }
        return """<row r="$row1">$cells</row>"""
    }

    private fun sheetXml(headers: List<String>, rows: List<List<Any?>>): String {
        val body = buildString {
            // Row 1: headers
            append(rowXml(1, headers))
            // Data rows start at 2
            var r = 2
            rows.forEach { vals ->
                append(rowXml(r++, vals))
            }
        }

        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                       xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheetData>
                $body
              </sheetData>
            </worksheet>
        """.trimIndent()
    }

    suspend fun exportCcXlsx(
        context: Context,
        uri: Uri,
        session: CcSession,
        events: List<CcEvent>
    ) {
        withContext(Dispatchers.IO) {

            val headers = listOf(
                "session_id",
                "planning_id",
                "location_name",
                "aforador",
                "derrotero",
                "company_name",
                "terminal_origin",
                "terminal_destination",
                "base",
                "direction",
                "seq_in_session",
                "event_type",
                "date",
                "time",
                "time_is_manual",
                "plate",
                "eco",
                "vehicle_type",
                "pax",
                "luggage_count",
                "lat",
                "lon",
                "acc_m",
                "provider",
                "fix_time",
                "location_status"
            )

            fun toRow(e: CcEvent, base: String): List<Any?> = listOf(
                session.sessionId,
                session.planningId,
                session.locationName,
                session.aforador,
                session.derrotero,
                session.companyName,
                session.terminalOrigin,
                session.terminalDestination,
                base,
                session.direction,

                e.seqInSession,
                e.eventType,
                fmtDate(e.timeMs),
                fmtTime(e.timeMs),
                if (e.timeIsManual) "Y" else "N",

                e.plate ?: "",
                e.eco ?: "",
                e.vehicleType ?: "",
                e.pax,
                if (base.uppercase() == "PERIFERIA") (e.luggageCount ?: 0) else "",

                e.lat,
                e.lon,
                e.accM,
                e.provider ?: "",
                e.fixTime,
                e.locationStatus ?: ""
            )

            val baseUpper = session.base.uppercase()
            val rowsCentro = if (baseUpper == "CENTRO") events.map { toRow(it, "CENTRO") } else emptyList()
            val rowsPeri = if (baseUpper == "PERIFERIA") events.map { toRow(it, "PERIFERIA") } else emptyList()

            val sheet1 = sheetXml(headers, rowsCentro)     // CENTRO
            val sheet2 = sheetXml(headers, rowsPeri)       // PERIFERIA

            // Minimal required OpenXML parts
            val contentTypes = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                </Types>
            """.trimIndent()

            val rels = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
            """.trimIndent()

            val workbook = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="CENTRO" sheetId="1" r:id="rId1"/>
                    <sheet name="PERIFERIA" sheetId="2" r:id="rId2"/>
                  </sheets>
                </workbook>
            """.trimIndent()

            val workbookRels = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
                  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                </Relationships>
            """.trimIndent()

            val styles = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
                  <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
                  <borders count="1"><border/></borders>
                  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                  <cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>
                </styleSheet>
            """.trimIndent()

            val os = context.contentResolver.openOutputStream(uri)
            requireNotNull(os) { "No se pudo abrir OutputStream para: $uri" }

            os.use { out ->
                ZipOutputStream(out).use { zip ->
                    fun put(path: String, content: String) {
                        zip.putNextEntry(ZipEntry(path))
                        zip.write(content.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }

                    put("[Content_Types].xml", contentTypes)
                    put("_rels/.rels", rels)
                    put("xl/workbook.xml", workbook)
                    put("xl/_rels/workbook.xml.rels", workbookRels)
                    put("xl/styles.xml", styles)
                    put("xl/worksheets/sheet1.xml", sheet1)
                    put("xl/worksheets/sheet2.xml", sheet2)
                }
            }
        }
    }
}
