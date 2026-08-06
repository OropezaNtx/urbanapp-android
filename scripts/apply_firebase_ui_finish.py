from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
trip_list = ROOT / "app/src/main/java/com/oropeza/urbanapp/asd/ui/viewmodel/AsdTripListScreen.kt"
detail = ROOT / "app/src/main/java/com/oropeza/urbanapp/asd/ui/viewmodel/AsdTripDetailScreen.kt"

text = trip_list.read_text(encoding="utf-8")
old = '''    init {
        checkForRecovery()
    }
'''
new = '''    init {
        checkForRecovery()
        viewModelScope.launch {
            AsdGraph.repo.reactivateFailedSyncItems()
        }
    }
'''
if new not in text:
    if old not in text:
        raise RuntimeError("No se encontró el init de AsdTripListVM")
    text = text.replace(old, new, 1)

text = text.replace('"SINCRONIZAR AHORA"', '"REINTENTAR ERRORES"')
trip_list.write_text(text, encoding="utf-8")

detail_text = detail.read_text(encoding="utf-8")
detail_text = detail_text.replace('"SINCRONIZAR AHORA"', '"REINTENTAR ERRORES"')
detail.write_text(detail_text, encoding="utf-8")

print("Aplicado: reactivación automática de errores")
print("Aplicado: botón renombrado a REINTENTAR ERRORES")
