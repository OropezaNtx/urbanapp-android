from pathlib import Path

p = Path(__file__).resolve().parents[1] / "app/src/main/java/com/oropeza/urbanapp/asd/ui/viewmodel/AsdTripDetailScreen.kt"
s = p.read_text(encoding="utf-8")
old = '''    LaunchedEffect(tripId, trip?.endTime) {
    if (trip?.endTime == null) {
        startTS()
    } else {
        stopTS()
        snackbarHostState.showSnackbar("Recorrido finalizado ✅")
        onBack()
    }
}
'''
new = '''    LaunchedEffect(tripId, trip?.endTime) {
        if (trip?.endTime == null) startTS() else stopTS()
    }
'''
if old not in s:
    raise RuntimeError("No se encontró el flujo automático de salida")
s = s.replace(old, new, 1)
old2 = '''confirmButton = { Button(onClick = { scope.launch { if (onEndTrip(currentFix(System.currentTimeMillis()))) showCloseTripConfirm = false } },'''
new2 = '''confirmButton = { Button(onClick = { scope.launch { if (onEndTrip(currentFix(System.currentTimeMillis()))) { showCloseTripConfirm = false; snackbarHostState.showSnackbar("Recorrido finalizado ✅"); onBack() } } },'''
if old2 not in s:
    raise RuntimeError("No se encontró el botón de cierre")
s = s.replace(old2, new2, 1)
p.write_text(s, encoding="utf-8")
print("Corrección de navegación histórica aplicada.")
