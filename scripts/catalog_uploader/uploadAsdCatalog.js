const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore, FieldValue } = require('firebase-admin/firestore');
const XLSX = require('xlsx');
const fs = require('fs');
const readline = require('readline');

const EXCEL_FILE = 'CATALOGO_ASD.xlsx';
const SERVICE_ACCOUNT = './serviceAccountKey.json';
const CATALOG_VERSION = 'V1.0';

async function run() {
    console.log("🚀 Iniciando uploader de catálogo ASD...");

    if (!fs.existsSync(SERVICE_ACCOUNT)) {
        console.error("❌ ERROR: No se encontró serviceAccountKey.json");
        process.exit(1);
    }

    if (!fs.existsSync(EXCEL_FILE)) {
        console.error(`❌ ERROR: No se encontró ${EXCEL_FILE}`);
        process.exit(1);
    }

    const serviceAccount = require(fs.realpathSync(SERVICE_ACCOUNT));

    initializeApp({
        credential: cert(serviceAccount)
    });

    const db = getFirestore();
    const workbook = XLSX.readFile(EXCEL_FILE);
    const now = Date.now();

    // 1. PROCESAR RUTAS
    const routeSheet = workbook.Sheets['CATALOGO_RUTAS'];
    if (!routeSheet) {
        console.error("❌ ERROR: No se encontró la hoja 'CATALOGO_RUTAS'");
        process.exit(1);
    }

    // Usar raw: false para intentar conservar formato de texto (ceros a la izquierda)
    const rawRoutes = XLSX.utils.sheet_to_json(routeSheet, { raw: false });
    const routesToUpload = [];
    const routeWarnings = [];

    rawRoutes.forEach((row, i) => {
        // Normalizar claves de la fila (quitar espacios y a mayúsculas)
        const normalizedRow = {};
        Object.keys(row).forEach(k => {
            normalizedRow[k.trim().toUpperCase()] = row[k];
        });

        const id = normalizedRow['ID']?.toString().trim();
        const ruta = normalizedRow['RUTA']?.toString().trim().toUpperCase();
        const sentido = normalizedRow['SENTIDO']?.toString().trim().toUpperCase();

        if (!id || !ruta || !sentido) {
            routeWarnings.push(`Fila ${i + 2} en RUTAS: ID, RUTA o SENTIDO faltantes.`);
            return;
        }

        const normSentido = sentido.includes("REGRESO") ? "REGRESO" : "IDA";
        routesToUpload.push({
            docId: `${id}_${normSentido}`,
            data: {
                catalogId: id,
                direction: normSentido,
                routeName: ruta,
                company: normalizedRow['EMPRESA']?.toString().trim().toUpperCase() || null,
                derrotero: normalizedRow['DERROTERO']?.toString().trim().toUpperCase() || null,
                cromatica: normalizedRow['CROMATICA']?.toString().trim().toUpperCase() || null,
                baseStart: normalizedRow['BASE_INICIO']?.toString().trim().toUpperCase() || null,
                baseEnd: normalizedRow['BASE_FINAL']?.toString().trim().toUpperCase() || null,
                observacion: normalizedRow['OBSERVACION']?.toString().trim().toUpperCase() || null,
                active: parseActive(normalizedRow['ACTIVO']),
                updatedAt: now
            }
        });
    });

    // 2. PROCESAR GENTE
    const peopleSheet = workbook.Sheets['GENTE_CAMPO'];
    const peopleToUpload = [];
    const peopleWarnings = [];
    if (peopleSheet) {
        const rawPeople = XLSX.utils.sheet_to_json(peopleSheet, { raw: false });
        rawPeople.forEach((row, i) => {
            const normalizedRow = {};
            Object.keys(row).forEach(k => {
                normalizedRow[k.trim().toUpperCase()] = row[k];
            });

            const personId = normalizedRow['PERSON_ID']?.toString().trim();
            const name = normalizedRow['NOMBRE']?.toString().trim().toUpperCase();
            if (!personId || !name) {
                peopleWarnings.push(`Fila ${i + 2} en GENTE: PERSON_ID o NOMBRE faltantes.`);
                return;
            }
            peopleToUpload.push({
                docId: personId,
                data: {
                    personId: personId,
                    name: name,
                    role: normalizedRow['ROL']?.toString().trim().toUpperCase() || "AMBOS",
                    defaultSex: normalizeSex(normalizedRow['SEXO_DEFAULT']),
                    active: parseActive(normalizedRow['ACTIVO']),
                    updatedAt: now
                }
            });
        });
    }

    // 3. PROCESAR TIPOS UNIDAD
    const vehicleSheet = workbook.Sheets['TIPOS_UNIDAD'];
    const vehiclesToUpload = [];
    const vehicleWarnings = [];
    if (vehicleSheet) {
        const rawVehicles = XLSX.utils.sheet_to_json(vehicleSheet, { raw: false });
        rawVehicles.forEach((row, i) => {
            const normalizedRow = {};
            Object.keys(row).forEach(k => {
                normalizedRow[k.trim().toUpperCase()] = row[k];
            });

            const vId = normalizedRow['VEHICLE_TYPE_ID']?.toString().trim().toUpperCase();
            const name = normalizedRow['NAME']?.toString().trim().toUpperCase();
            if (!vId || !name) {
                vehicleWarnings.push(`Fila ${i + 2} en UNIDADES: VEHICLE_TYPE_ID o NAME faltantes.`);
                return;
            }
            vehiclesToUpload.push({
                docId: vId,
                data: {
                    vehicleTypeId: vId,
                    name: name,
                    displayName: normalizedRow['DISPLAY_NAME']?.toString().trim().toUpperCase() || name,
                    defaultSeatCapacity: parseInt(normalizedRow['DEFAULT_SEAT_CAPACITY']) || 0,
                    capacityApplies: parseActive(normalizedRow['CAPACITY_APPLIES']),
                    sortOrder: parseInt(normalizedRow['SORT_ORDER']) || 100,
                    active: parseActive(normalizedRow['ACTIVE']),
                    updatedAt: now
                }
            });
        });
    }

    // RESUMEN
    console.log("\n========================================");
    console.log("   RESUMEN DE CARGA CATÁLOGO ASD");
    console.log("========================================");
    console.log(`Archivo:          ${EXCEL_FILE}`);
    console.log(`Versión:          ${CATALOG_VERSION}`);
    console.log(`Rutas válidas:    ${routesToUpload.length}`);
    console.log(`Personal válido:  ${peopleToUpload.length}`);
    console.log(`Unidades válidas: ${vehiclesToUpload.length}`);
    console.log(`Advertencias:     ${routeWarnings.length + peopleWarnings.length + vehicleWarnings.length}`);
    console.log("========================================\n");

    if (routeWarnings.length > 0 || peopleWarnings.length > 0 || vehicleWarnings.length > 0) {
        console.log("⚠️  ADVERTENCIAS (estas filas se omitirán):");
        [...routeWarnings, ...peopleWarnings, ...vehicleWarnings].slice(0, 10).forEach(w => console.log(` - ${w}`));
        if (routeWarnings.length + peopleWarnings.length + vehicleWarnings.length > 10) console.log(" ... y más.");
        console.log("----------------------------------------\n");
    }

    if (routesToUpload.length === 0) {
        console.error("❌ ERROR: No se encontraron rutas válidas para subir.");
        process.exit(1);
    }

    const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout
    });

    rl.question('¿Confirmas la subida a Firestore? Escribe "SI" para continuar: ', async (answer) => {
        if (answer.trim().toUpperCase() !== 'SI') {
            console.log("❌ Carga cancelada.");
            rl.close();
            process.exit(0);
        }

        try {
            console.log("\n🚀 Iniciando subida a Firestore...");
            const masterDocRef = db.collection('asd_catalog_versions').doc('current');
            const batchSize = 400;

            // Subir Rutas
            for (let i = 0; i < routesToUpload.length; i += batchSize) {
                const batch = db.batch();
                routesToUpload.slice(i, i + batchSize).forEach(item => {
                    batch.set(masterDocRef.collection('routes').doc(item.docId), item.data);
                });
                await batch.commit();
                console.log(` ✅ Rutas: ${Math.min(i + batchSize, routesToUpload.length)}/${routesToUpload.length}`);
            }

            // Subir Gente
            if (peopleToUpload.length > 0) {
                for (let i = 0; i < peopleToUpload.length; i += batchSize) {
                    const batch = db.batch();
                    peopleToUpload.slice(i, i + batchSize).forEach(item => {
                        batch.set(masterDocRef.collection('people').doc(item.docId), item.data);
                    });
                    await batch.commit();
                    console.log(` ✅ Personal: ${Math.min(i + batchSize, peopleToUpload.length)}/${peopleToUpload.length}`);
                }
            }

            // Subir Unidades
            if (vehiclesToUpload.length > 0) {
                for (let i = 0; i < vehiclesToUpload.length; i += batchSize) {
                    const batch = db.batch();
                    vehiclesToUpload.slice(i, i + batchSize).forEach(item => {
                        batch.set(masterDocRef.collection('vehicle_types').doc(item.docId), item.data);
                    });
                    await batch.commit();
                    console.log(` ✅ Unidades: ${Math.min(i + batchSize, vehiclesToUpload.length)}/${vehiclesToUpload.length}`);
                }
            }

            // Actualizar documento maestro
            await masterDocRef.set({
                version: CATALOG_VERSION,
                active: true,
                updatedAt: now
            });

            console.log(`\n🎉 ÉXITO: Catálogo ${CATALOG_VERSION} actualizado.`);
        } catch (err) {
            console.error("\n❌ ERROR DURANTE LA SUBIDA:", err);
        } finally {
            rl.close();
            process.exit(0);
        }
    });
}

function parseActive(val) {
    if (val === undefined || val === null || val === "") return false;
    const v = val.toString().toUpperCase().trim();
    const trueValues = ["SI", "SÍ", "TRUE", "1", "ACTIVO", "Y"];
    return trueValues.includes(v);
}

function normalizeSex(val) {
    if (!val) return null;
    const v = val.toString().toUpperCase().trim();
    if (v === "HOMBRE" || v === "H") return "Hombre";
    if (v === "MUJER" || v === "M") return "Mujer";
    return null;
}

run();
