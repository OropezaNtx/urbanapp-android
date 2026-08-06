from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
trip_list = ROOT / "app/src/main/java/com/oropeza/urbanapp/asd/ui/viewmodel/AsdTripListScreen.kt"
detail = ROOT / "app/src/main/java/com/oropeza/urbanapp/asd/ui/viewmodel/AsdTripDetailScreen.kt"

text = trip_list.read_text(encoding="utf-8")

reactivation_call = "AsdGraph.repo.reactivateFailedSyncItems()"
if reactivation_call in text:
    print("Ya aplicado: reactivación automática de errores")
else:
    class_start = text.find("class AsdTripListVM")
    if class_start < 0:
        raise RuntimeError("No se encontró AsdTripListVM")

    init_start = text.find("    init {", class_start)
    if init_start < 0:
        raise RuntimeError("No se encontró el bloque init de AsdTripListVM")

    brace_start = text.find("{", init_start)
    depth = 0
    init_end = -1
    for index in range(brace_start, len(text)):
        char = text[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                init_end = index
                break

    if init_end < 0:
        raise RuntimeError("El bloque init de AsdTripListVM no está balanceado")

    insertion = (
        "\n        viewModelScope.launch {\n"
        "            AsdGraph.repo.reactivateFailedSyncItems()\n"
        "        }\n"
    )
    text = text[:init_end] + insertion + text[init_end:]
    print("Aplicado: reactivación automática de errores")

old_button = '"SINCRONIZAR AHORA"'
new_button = '"REINTENTAR ERRORES"'
if old_button in text:
    text = text.replace(old_button, new_button)
    print("Aplicado: botón de lista renombrado a REINTENTAR ERRORES")
elif new_button in text:
    print("Ya aplicado: botón de lista renombrado")
else:
    print("Aviso: no se encontró texto de botón en la lista")

trip_list.write_text(text, encoding="utf-8")

detail_text = detail.read_text(encoding="utf-8")
if old_button in detail_text:
    detail_text = detail_text.replace(old_button, new_button)
    print("Aplicado: botón de detalle renombrado a REINTENTAR ERRORES")
elif new_button in detail_text:
    print("Ya aplicado: botón de detalle renombrado")
else:
    print("Aviso: no se encontró texto de botón en el detalle")

detail.write_text(detail_text, encoding="utf-8")
print("Finalización de UI Firebase aplicada correctamente.")
