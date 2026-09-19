"""
importar_inventario_mla.py
==========================
Lee todos los archivos .xlsx de INV.DIG MLA y genera:
  - import_categorias.sql   → INSERT de categorías con código CONAC
  - import_bienes.sql       → INSERT de productos en la tabla products
  - import_resguardos.sql   → INSERT de resguardos + resguardo_items

Uso:
    python tools/importar_inventario_mla.py

Salida: tools/output/  (se crea automáticamente)

Requisitos: pip install openpyxl
"""

import os
import re
import uuid
import datetime
import unicodedata
from pathlib import Path
from openpyxl import load_workbook

# ── Configuración ──────────────────────────────────────────────────────────────

INVENTARIO_DIR = Path(__file__).parent.parent / "INV.DIG MLA"
OUTPUT_DIR     = Path(__file__).parent / "output"

# Estados físicos reconocidos (normalizar variantes con espacios/tildes)
ESTADOS_VALIDOS = {"BUENO", "REGULAR", "MALO", "DEFICIENTE"}

# Valores que significan "sin dato"
SIN_DATO = {"SIN SERIE", "SIN MARCA", "SIN MODELO", "SIN FECHA",
            "SIN DOCUMENTO", "S/M", "S/N", "S/S", ""}

# Mínimo de columnas para que una fila sea de datos (no header ni vacía)
MIN_COLS_DATOS = 5

# Columnas en los xlsx (0-indexed desde col A=0):
# Row 10 (idx 9): N°BIENES | N°INV | UBICACIÓN | <fusionada DATOS> | CANTIDAD | DESC | MARCA | MODELO | SERIE | ...FACTURA/FECHA| IMPORTE | ESTADO | OBS
# Varía entre archivos — buscamos por cabecera

# ── Categorías CONAC ────────────────────────────────────────────────────────────

# Mapeamos código CONAC → (id_categoria, nombre, color, icono)
CONAC_CATS = {
    "1.2.4.1.1.511": ("cat-conac-511", "Muebles de Oficina y Estantería",          "#8B5CF6", "Armchair"),
    "1.2.4.1.2.512": ("cat-conac-512", "Muebles y Equipos Educativos",             "#7C3AED", "GraduationCap"),
    "1.2.4.1.3.515": ("cat-conac-515", "Equipo de Cómputo y Tecnologías de Información", "#3B82F6", "Laptop"),
    "1.2.4.1.4.513": ("cat-conac-513", "Equipo e Instrumental Médico",             "#EF4444", "FirstAid"),
    "1.2.4.2.1.521": ("cat-conac-521", "Vehículos y Equipo de Transporte",         "#F59E0B", "Car"),
    "1.2.4.2.2.522": ("cat-conac-522", "Carrocerías y Remolques",                  "#D97706", "Truck"),
    "1.2.4.3.1.531": ("cat-conac-531", "Equipo de Generación Eléctrica",           "#10B981", "Zap"),
    "1.2.4.3.2.532": ("cat-conac-532", "Herramientas y Máquinas-Herramienta",      "#059669", "Wrench"),
    "1.2.4.3.9.539": ("cat-conac-539", "Otros Equipos y Aparatos",                 "#6B7280", "Package"),
    "1.2.4.4.1.541": ("cat-conac-541", "Sistemas de Aire Acondicionado",           "#0EA5E9", "Wind"),
    "1.2.4.5.1.551": ("cat-conac-551", "Equipo de Comunicación y Telecomunicación","#06B6D4", "Radio"),
    "1.2.4.5.2.552": ("cat-conac-552", "Equipo de Seguridad Pública",              "#1D4ED8", "Shield"),
    "1.2.4.6.1.561": ("cat-conac-561", "Maquinaria y Equipo Agropecuario",         "#84CC16", "Tractor"),
    "1.2.4.6.2.562": ("cat-conac-562", "Maquinaria y Equipo Industrial",           "#65A30D", "Factory"),
    "1.2.4.6.3.563": ("cat-conac-563", "Maquinaria y Equipo de Construcción",      "#78350F", "HardHat"),
    "1.2.4.6.4.564": ("cat-conac-564", "Sistemas de Aire Acondicionado y Calefacción", "#F97316", "Thermometer"),
    "1.2.4.7.1.571": ("cat-conac-571", "Equipo Fotográfico y Audiovisual",         "#EC4899", "VideoCamera"),
    "1.2.4.9.9.599": ("cat-conac-599", "Otros Bienes Muebles",                     "#64748B", "Box"),
}

# ── Helpers ────────────────────────────────────────────────────────────────────

def clean(val):
    """Limpia un valor de celda: convierte a str, quita espacios, normaliza nulos."""
    if val is None:
        return None
    s = str(val).strip().strip('"').strip()
    if s.upper() in SIN_DATO or s == "0":
        return None
    return s or None


def clean_money(val):
    """Devuelve float o None para el importe."""
    s = clean(val)
    if s is None:
        return None
    s = s.replace("$", "").replace(",", "").replace(" ", "").strip()
    try:
        f = float(s)
        return f if f > 0 else None
    except ValueError:
        return None


def clean_date(val):
    """Devuelve 'YYYY-MM-DD' o None."""
    if val is None:
        return None
    if isinstance(val, (datetime.date, datetime.datetime)):
        return val.strftime("%Y-%m-%d")
    # Excel date serial (int/float sin formato de fecha aplicado)
    if isinstance(val, (int, float)) and 10000 < float(val) < 60000:
        try:
            from openpyxl.utils.datetime import from_excel
            return from_excel(int(val)).strftime("%Y-%m-%d")
        except Exception:
            pass
    s = clean(val)
    if s is None:
        return None
    # Intenta varios formatos incluyendo con hora (openpyxl → str convierte a "YYYY-MM-DD HH:MM:SS")
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%dT%H:%M:%S",
                "%d/%m/%Y", "%d/%m/%y", "%Y-%m-%d", "%d-%m-%Y"):
        try:
            return datetime.datetime.strptime(s, fmt).strftime("%Y-%m-%d")
        except ValueError:
            pass
    return None


def sql_str(val):
    """Escapa para SQL: None → NULL, string → 'valor_escapado'."""
    if val is None:
        return "NULL"
    escaped = str(val).replace("'", "''")
    return f"'{escaped}'"


def new_uuid():
    return str(uuid.uuid4())


def is_conac_row(row_vals):
    """True si la fila es un separador de categoría CONAC (ej. '1.2.4.1.1.511')."""
    first = str(row_vals[0] or "").strip()
    return bool(re.match(r"^\d+\.\d+\.\d+\.\d+\.\d+\.\d+$", first))


def is_data_row(row_vals):
    """True si la fila parece un bien (primer campo es número secuencial)."""
    first = str(row_vals[0] or "").strip()
    return bool(re.match(r"^\d+$", first))


# ── Lectura de un archivo xlsx ──────────────────────────────────────────────────

def parse_sheet(ws):
    """
    Extrae del worksheet los metadatos de cabecera (resguardante, cargo, área,
    número de resguardo, fecha) y la lista de bienes.

    Devuelve: (meta_dict, [bien_dict, ...])
    donde bien_dict tiene las claves del inventario.
    """
    meta = {
        "area": None,
        "resguardante": None,
        "cargo": None,
        "numero_resguardo": None,
        "fecha_resguardo": None,
    }
    bienes = []
    current_conac = None

    # Leemos hasta 6 filas de cabecera buscando los metadatos
    for row_idx in range(1, 10):
        for col_idx in range(1, 20):
            cell_val = str(ws.cell(row_idx, col_idx).value or "").strip()
            if "DIRECCIÓN:" in cell_val.upper():
                # Extrae lo que viene después de "DIRECCIÓN:"
                m = re.search(r"DIRECCI[ÓO]N:\s*(.+)", cell_val, re.IGNORECASE)
                if m:
                    meta["area"] = m.group(1).strip()
            if "RESGUARDANTE:" in cell_val.upper():
                m = re.search(r"RESGUARDANTE:\s*(.+)", cell_val, re.IGNORECASE)
                if m:
                    meta["resguardante"] = m.group(1).strip()
            if "CARGO:" in cell_val.upper():
                m = re.search(r"CARGO\s*[:\.]?\s*(.+)", cell_val, re.IGNORECASE)
                if m:
                    meta["cargo"] = m.group(1).strip()
            if "NÚMERO DE RESGUARDO:" in cell_val.upper() or "NUMERO DE RESGUARDO:" in cell_val.upper():
                m = re.search(r"N[ÚU]MERO DE RESGUARDO:\s*(.+)", cell_val, re.IGNORECASE)
                if m:
                    meta["numero_resguardo"] = m.group(1).strip()
            if "FECHA:" in cell_val.upper():
                m = re.search(r"FECHA:\s*(.+)", cell_val, re.IGNORECASE)
                if m:
                    meta["fecha_resguardo"] = clean_date(m.group(1).strip())

        # Si la meta está de la misma celda con el área en col 4 y resguardante en col 4
        cell4 = str(ws.cell(row_idx, 4).value or "").strip()
        cell9 = str(ws.cell(row_idx, 9).value or "").strip()

        if not meta["area"] and "DIRECCIÓN" in cell9.upper():
            m = re.search(r"DIRECCI[ÓO]N:\s*(.+)", cell9, re.IGNORECASE)
            if m:
                meta["area"] = m.group(1).strip()
        if not meta["resguardante"] and "RESGUARDANTE" in cell4.upper():
            m = re.search(r"RESGUARDANTE:\s*(.+)", cell4, re.IGNORECASE)
            if m:
                meta["resguardante"] = m.group(1).strip()
        if not meta["cargo"] and "CARGO" in cell9.upper():
            m = re.search(r"CARGO\s*[:\.]?\s*(.+)", cell9, re.IGNORECASE)
            if m:
                meta["cargo"] = m.group(1).strip()
        if not meta["numero_resguardo"] and "NÚMERO DE RESGUARDO" in cell4.upper():
            m = re.search(r"N[ÚU]MERO DE RESGUARDO:\s*(.+)", cell4, re.IGNORECASE)
            if m:
                meta["numero_resguardo"] = m.group(1).strip()
        if not meta["fecha_resguardo"] and "FECHA" in cell9.upper():
            m = re.search(r"FECHA:\s*(.+)", cell9, re.IGNORECASE)
            if m:
                meta["fecha_resguardo"] = clean_date(m.group(1).strip())

    # Buscamos la fila de encabezados de columna.
    # Los xlsx tienen cabeceras en 2 filas: la fila N tiene "N° DE INV" y
    # la fila N+1 tiene "DESCRIPCIÓN", "MARCA", etc. — buscamos la combinación.
    header_row_idx = None
    col_map = {}  # nombre_campo → índice columna (1-based)
    for row_idx in range(8, 20):
        vals = [str(ws.cell(row_idx, c).value or "").strip().upper()
                for c in range(1, 20)]
        vals_next = [str(ws.cell(row_idx + 1, c).value or "").strip().upper()
                     for c in range(1, 20)]
        combined      = " ".join(vals)
        combined_next = " ".join(vals_next)
        combined_all  = combined + " " + combined_next
        has_inv  = "N DE INV" in combined_all or "N° DE INV" in combined_all or "INV." in combined_all
        has_desc = "DESCRIPCI" in combined_all
        if has_inv and has_desc:
            header_row_idx = row_idx
            # Mapear columnas en la fila header y la siguiente (sub-header)
            for c in range(1, 20):
                v = str(ws.cell(row_idx, c).value or "").strip().upper()
                v2 = str(ws.cell(row_idx + 1, c).value or "").strip().upper()
                combined_hdr = v + " " + v2
                if "N° DE INV" in combined_hdr or "N DE INV" in combined_hdr:
                    col_map["codigo"] = c
                if "UBICACI" in combined_hdr:
                    col_map["ubicacion"] = c
                if "CANTIDAD" in combined_hdr:
                    col_map["cantidad"] = c
                if "DESCRIPCI" in combined_hdr:
                    col_map["descripcion"] = c
                if "MARCA" in combined_hdr:
                    col_map["marca"] = c
                if "MODELO" in combined_hdr:
                    col_map["modelo"] = c
                if "SERIE" in combined_hdr:
                    col_map["serie"] = c
                has_factura  = "FACTURA"   in combined_hdr
                has_doc      = "DOCUMENTO" in combined_hdr
                has_fecha    = "FECHA"     in combined_hdr
                fecha_in_sub = "FECHA"     in v2   # sub-header específica de FECHA
                if has_factura:
                    col_map["factura"] = c
                elif has_doc and fecha_in_sub:
                    # "TIPO DE DOCUMENTO / FECHA" → la fecha va en esta columna
                    col_map["fecha_doc"] = c
                elif has_doc:
                    col_map["factura"] = c
                elif has_fecha:
                    col_map["fecha_doc"] = c
                if "IMPORTE" in combined_hdr or "MONTO" in combined_hdr:
                    col_map["importe"] = c
                if "ESTADO" in combined_hdr and "F" in combined_hdr:
                    col_map["estado_fisico"] = c
                if "OBSERV" in combined_hdr:
                    col_map["observaciones"] = c
            break

    if header_row_idx is None:
        return meta, []

    # Leer filas de datos (a partir de header_row_idx + 2)
    for row_idx in range(header_row_idx + 2, ws.max_row + 1):
        row_vals = [ws.cell(row_idx, c).value for c in range(1, 20)]

        if is_conac_row(row_vals):
            current_conac = str(row_vals[0] or "").strip()
            continue

        if not is_data_row(row_vals):
            continue

        def get(field):
            col = col_map.get(field)
            return row_vals[col - 1] if col else None

        codigo     = clean(get("codigo"))
        descripcion = clean(get("descripcion"))
        if not descripcion:
            continue  # fila vacía o sin descripción

        # Detectar orden de factura/fecha (varía entre hojas)
        factura_raw = clean(get("factura"))
        fecha_raw   = clean(get("fecha_doc"))
        # Si lo que está en "factura" parece una fecha → swap
        if factura_raw and re.match(r"\d{2}/\d{2}/\d{4}", factura_raw or ""):
            factura_raw, fecha_raw = fecha_raw, factura_raw

        estado_raw = str(get("estado_fisico") or "").strip().upper()
        estado_fisico = estado_raw if estado_raw in ESTADOS_VALIDOS else None

        bienes.append({
            "n_bien":        clean(row_vals[0]),
            "codigo":        codigo,
            "ubicacion":     clean(get("ubicacion")),
            "cantidad":      int(clean(get("cantidad")) or 1),
            "descripcion":   descripcion,
            "marca":         clean(get("marca")),
            "modelo":        clean(get("modelo")),
            "numero_serie":  clean(get("serie")),
            "numero_factura": factura_raw,
            "fecha_adquisicion": clean_date(fecha_raw),
            "precio_compra": clean_money(get("importe")),
            "estado_fisico": estado_fisico,
            "observaciones": clean(get("observaciones")),
            "conac":         current_conac,
        })

    return meta, bienes


# ── Mapeo área Excel → nombre normalizado de areas.java ────────────────────────

AREA_NORMALIZE = {
    # Presidencia y sus direcciones
    "DESPACHO DE PRESIDENCIA":               "Despacho de Presidencia",
    "DESPACHO PRESIDENCIA":                  "Despacho de Presidencia",
    "SIPINNA":                               "SIPINNA",
    "DIRECCIÓN JURÍDICA":                    "Dirección Jurídica",
    "DIRECC. JURÍDICA":                      "Dirección Jurídica",
    "COMUNICACIÓN SOCIAL Y MARKETING DIGITAL":   "Comunicación Social y Marketing Digital",
    "DIRECCIÓN DE GOBIERNO":                 "Dirección de Gobierno",
    "DIRECCIÓN DE LOGÍSTICA Y EVENTOS":      "Dirección de Logística y Eventos",
    "DIRECCIÓN DE LOGÍSTICA, EVENTOS Y AYUDANTIA": "Dirección de Logística y Eventos",
    "INSTANCIA MUNICIPAL DE LA MUJER":       "Instancia Municipal de la Mujer",
    "INSTANCIA DE LA MUJER":                 "Instancia Municipal de la Mujer",
    "INSTANCIA MUNICIPAL DE LA JUVENTUD":    "Instancia Municipal de la Juventud",
    "INSTANCIA DE LA JUVENTUD":              "Instancia Municipal de la Juventud",
    # Secretaría General
    "SECRETARÍA GENERAL MUNICIPAL":          "Secretaría General Municipal",
    "SECRETARÍA GENERAL":                    "Secretaría General Municipal",
    "SECRETARIA GENERAL":                    "Secretaría General Municipal",
    "ARCHIVO MUNICIPAL":                     "Archivo Municipal",
    "OFICIALÍA DEL REGISTRO DEL ESTADO FAMILIAR": "Oficialía del Registro del Estado Familiar",
    "RECURSOS MATERIALES Y PATRIMONIO":      "Recursos Materiales y Patrimonio",
    # Contraloría
    "CONTRALORÍA MUNICIPAL":                 "Contraloría Municipal",
    # Asamblea
    "OFICIALÍA MAYOR DE LA ASAMBLEA":        "Oficialía Mayor de la Asamblea",
    "OFICIALÍA DE LA ASAMBLEA":              "Oficialía Mayor de la Asamblea",
    # Tesorería
    "SECRETARÍA DE TESORERÍA MUNICIPAL":     "Tesorería Municipal",
    "TESORERÍA MUNICIPAL":                   "Tesorería Municipal",
    "ADMINISTRACIÓN":                        "Tesorería — Administración",
    "TESORERÍA ADMINISTRACIÓN":              "Tesorería — Administración",
    "TESORERÍA INGRESOS":                    "Tesorería — Ingresos",
    "TESORERÍA EGRESOS":                     "Tesorería — Egresos",
    "CUENTA PÚBLICA":                        "Tesorería — Cuenta Pública",
    "RECURSOS HUMANOS Y NÓMINA":             "Tesorería — Recursos Humanos y Nómina",
    "TESORERÍA RECURSOS HUMANOS Y NÓMINA":   "Tesorería — Recursos Humanos y Nómina",
    # Obras Públicas
    "SECRETARÍA DE OBRAS PÚBLICAS":          "Secretaría de Obras Públicas",
    "OBRAS PÚBLICAS":                        "Secretaría de Obras Públicas",
    "OBRAS PUBLICAS":                        "Secretaría de Obras Públicas",
    "DIRECCIÓN DE DESARROLLO URBANO":        "Dirección de Desarrollo Urbano",
    "DESARROLLO URBANO":                     "Dirección de Desarrollo Urbano",
    "DIRECCIÓN DE MEDIO AMBIENTE":           "Dirección de Medio Ambiente",
    "MEDIO AMBIENTE":                        "Dirección de Medio Ambiente",
    "SERVICIOS MUNICIPALES":                 "Servicios Municipales",
    "SERVICIOS PÚBLICOS Y LIMPIAS":          "Servicios Públicos y Limpias",
    # Planeación / TI
    "SECRETARÍA DE PLANEACIÓN":              "Secretaría de Planeación",
    "PLANEACIÓN":                            "Secretaría de Planeación",
    "DIRECCIÓN DE TECNOLOGÍAS DE LA INFORMACIÓN": "Dirección de Tecnologías de la Información",
    "TECNOLOGÍAS DE LA INFORMACIÓN":         "Dirección de Tecnologías de la Información",
    # Catastro
    "DIRECCIÓN DE CATASTRO":                 "Dirección de Catastro",
    "CATASTRO":                              "Dirección de Catastro",
    # Conciliación
    "DIRECCIÓN DE CONCILIACIÓN MUNICIPAL":   "Dirección de Conciliación Municipal",
    "CONCILIACIÓN MUNICIPAL":                "Dirección de Conciliación Municipal",
    # Reglamentos (dos formas distintas)
    "REGLAMENTOS, COMERCIO Y ESPECTÁCULOS":  "Reglamentos, Comercio y Espectáculos",
    "REGLAMENTOS Y COMERCIO":                "Reglamentos, Comercio y Espectáculos",
    "REGLAMENTOS, COMERCIO, MERCADO Y ESPECTÁCULOS": "Reglamentos, Comercio y Espectáculos",
    # Bienestar Social
    "SECRETARÍA DE BIENESTAR SOCIAL":        "Secretaría de Bienestar Social",
    "BIENESTAR SOCIAL":                      "Secretaría de Bienestar Social",
    "PROGRAMAS SOCIALES":                    "Programas Sociales",
    "DIRECCIÓN DE EDUCACIÓN":                "Dirección de Educación",
    "EDUCACIÓN":                             "Dirección de Educación",
    "EDUCACION":                             "Dirección de Educación",
    "DIRECCIÓN DE CULTURA":                  "Dirección de Cultura",
    "CULTURA":                               "Dirección de Cultura",
    "DIRECCIÓN DEL DEPORTE":                 "Dirección del Deporte",
    "DIRECCION DEL DEPORTE":                 "Dirección del Deporte",
    "DIRECCIÓN DE SALUD":                    "Dirección de Salud",
    "SALUD":                                 "Dirección de Salud",
    "ATENCIÓN AL MIGRANTE":                  "Atención al Migrante",
    "JUNTA DE RECLUTAMIENTO":                "Junta de Reclutamiento",
    # Desarrollo Económico
    "SECRETARÍA DE DESARROLLO ECONÓMICO Y TURISMO": "Secretaría de Desarrollo Económico y Turismo",
    # Pueblos Indígenas
    "SECRETARÍA DE PUEBLOS INDÍGENAS":       "Secretaría de Pueblos Indígenas",
    "PUEBLOS INDÍGENAS":                     "Secretaría de Pueblos Indígenas",
    # Seguridad / Protección Civil
    "PROTECCIÓN CIVIL Y BOMBEROS":           "Protección Civil y Bomberos",
    "PROTECCION CIVIL":                      "Protección Civil y Bomberos",
    # Autónomos y otros
    "UNIDAD DE TRANSPARENCIA":               "Unidad de Transparencia",
    "TRANSPARENCIA":                         "Unidad de Transparencia",
    "CONTROL CANINO":                        "Control Canino",
    "PARQUE MUNICIPAL":                      "Parque Municipal",
    "MERCADO MUNICIPAL":                     "Mercado Municipal",
    "COORDINACIÓN BIBLIOTECAS":              "Coordinación de Bibliotecas",
    "BIBLIOTECAS":                           "Coordinación de Bibliotecas",
    "RASTRO MUNICIPAL":                      "Coordinación del Rastro Municipal",
    "COORDINACIÓN DEL RASTRO MUNICIPAL":     "Coordinación del Rastro Municipal",
}


def _strip_accents(s):
    return unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode("ascii")


def normalize_area(raw):
    if not raw:
        return raw
    key_norm = _strip_accents(raw.upper().strip())
    # Exact match first (accent-insensitive)
    for k, v in AREA_NORMALIZE.items():
        if key_norm == _strip_accents(k.upper()):
            return v
    # Partial match fallback
    for k, v in AREA_NORMALIZE.items():
        k_norm = _strip_accents(k.upper())
        if k_norm in key_norm or key_norm in k_norm:
            return v
    return raw  # devuelve tal cual si no hay match


# ── Generación del SQL ──────────────────────────────────────────────────────────

def generate_sql(inventario_dir: Path, output_dir: Path):
    output_dir.mkdir(parents=True, exist_ok=True)

    cat_lines   = []
    prod_lines  = []
    rsg_lines   = []

    # Categorías CONAC
    cat_lines.append("-- Categorías CONAC (inventario físico MLA)")
    cat_lines.append("-- Ejecutar DESPUÉS de las migraciones (la tabla categories ya existe)")
    cat_lines.append("")
    for conac_code, (cat_id, nombre, color, icono) in CONAC_CATS.items():
        cat_lines.append(
            f"INSERT INTO categories (id, nombre, descripcion, color, icono, codigo_conac, created_at) VALUES "
            f"({sql_str(cat_id)}, {sql_str(nombre)}, {sql_str('Clasificación CONAC ' + conac_code)}, "
            f"{sql_str(color)}, {sql_str(icono)}, {sql_str(conac_code)}, NOW()) "
            f"ON CONFLICT (id) DO UPDATE SET nombre=EXCLUDED.nombre, codigo_conac=EXCLUDED.codigo_conac;"
        )

    prod_lines.append("-- Bienes del inventario MLA 2024/2026 — Municipio de Ixmiquilpan")
    prod_lines.append("-- Ejecutar DESPUÉS de import_categorias.sql")
    prod_lines.append("-- Cada INSERT usa ON CONFLICT (codigo) para ser re-ejecutable")
    prod_lines.append("")

    rsg_lines.append("-- Resguardos por área y resguardante")
    rsg_lines.append("-- Ejecutar DESPUÉS de import_bienes.sql")
    rsg_lines.append("")

    codigos_vistos  = set()
    rsg_nums_vistos = set()
    total_bienes    = 0
    total_skipped   = 0
    archivos_proc   = 0

    for dept_dir in sorted(inventario_dir.iterdir()):
        if not dept_dir.is_dir():
            continue

        xlsx_files = sorted(dept_dir.glob("*.xlsx"))
        if not xlsx_files:
            continue

        dept_nombre = dept_dir.name
        print(f"  Procesando: {dept_nombre}")

        for xlsx_path in xlsx_files:
            if xlsx_path.name.startswith("~$"):
                continue  # archivo de bloqueo de Office

            try:
                wb = load_workbook(xlsx_path, data_only=True)
            except Exception as e:
                print(f"    [SKIP] {xlsx_path.name}: {type(e).__name__}")
                continue

            archivos_proc += 1
            prod_lines.append(f"-- {'='*60}")
            prod_lines.append(f"-- {dept_nombre} → {xlsx_path.name}")
            prod_lines.append(f"-- {'='*60}")

            for sheet_name in wb.sheetnames:
                if sheet_name.upper() in ("INFORME", "BIENES MENORES"):
                    continue  # hojas de resumen, no de bienes

                ws = wb[sheet_name]
                meta, bienes = parse_sheet(ws)

                if not bienes:
                    continue

                area_raw = meta.get("area") or dept_nombre
                area = normalize_area(area_raw)
                resguardante = meta.get("resguardante") or ""
                cargo        = meta.get("cargo") or ""
                num_rsg      = meta.get("numero_resguardo") or ""
                fecha_rsg    = meta.get("fecha_resguardo")

                prod_lines.append(f"-- Hoja: {sheet_name} | Resguardante: {resguardante} | Cargo: {cargo}")

                rsg_id = new_uuid()
                rsg_numero_base = f"RSG-{num_rsg}" if num_rsg else f"RSG-{sheet_name[:20]}"
                rsg_numero = rsg_numero_base
                suffix_n = 2
                while rsg_numero in rsg_nums_vistos:
                    rsg_numero = f"{rsg_numero_base}-{suffix_n}"
                    suffix_n += 1
                rsg_nums_vistos.add(rsg_numero)
                if fecha_rsg:
                    rsg_date = sql_str(fecha_rsg)
                else:
                    rsg_date = "CURRENT_DATE"

                rsg_lines.append(
                    f"INSERT INTO resguardos (id, numero, resguardante_nombre, resguardante_cargo, "
                    f"resguardante_area, observaciones, estado, created_at) VALUES "
                    f"({sql_str(rsg_id)}, {sql_str(rsg_numero)}, {sql_str(resguardante)}, "
                    f"{sql_str(cargo)}, {sql_str(area)}, "
                    f"{sql_str('Importado de ' + xlsx_path.name + ' hoja ' + sheet_name)}, "
                    f"'ACTIVO', NOW()) ON CONFLICT (numero) DO NOTHING;"
                )

                for bien in bienes:
                    codigo = bien["codigo"]
                    if not codigo:
                        # Generar código provisional si no tiene
                        codigo = f"IMP-{dept_nombre[:6].upper().replace(' ', '')}-{new_uuid()[:8].upper()}"

                    # Desduplicar códigos
                    if codigo in codigos_vistos:
                        codigo = codigo + "-" + new_uuid()[:4].upper()
                    codigos_vistos.add(codigo)

                    # Determinar categoría por CONAC
                    conac = bien.get("conac")
                    cat_id = CONAC_CATS.get(conac, (None,))[0] if conac else None
                    if not cat_id:
                        cat_id = "cat-conac-511"  # Mobiliario como fallback

                    prod_id = new_uuid()
                    precio  = bien["precio_compra"] or 0.0
                    total_bienes += 1

                    prod_lines.append(
                        f"INSERT INTO products "
                        f"(id, nombre, codigo, descripcion, categoria_id, precio_compra, precio_venta, "
                        f"stock_actual, stock_minimo, stock_maximo, unidad, "
                        f"marca, modelo, numero_serie, ubicacion, area, resguardante, "
                        f"fecha_adquisicion, numero_factura, estado_fisico, etiquetado, "
                        f"created_at, updated_at) VALUES "
                        f"({sql_str(prod_id)}, "
                        f"{sql_str(bien['descripcion'])}, "
                        f"{sql_str(codigo)}, "
                        f"{sql_str(bien['observaciones'])}, "
                        f"{sql_str(cat_id)}, "
                        f"{precio:.2f}, {precio:.2f}, "
                        f"1, 0, 1, 'pieza', "
                        f"{sql_str(bien['marca'])}, "
                        f"{sql_str(bien['modelo'])}, "
                        f"{sql_str(bien['numero_serie'])}, "
                        f"{sql_str(bien['ubicacion'])}, "
                        f"{sql_str(area)}, "
                        f"{sql_str(resguardante)}, "
                        f"{sql_str(bien['fecha_adquisicion'])}, "
                        f"{sql_str(bien['numero_factura'])}, "
                        f"{sql_str(bien['estado_fisico'])}, "
                        f"FALSE, NOW(), NOW()) "
                        f"ON CONFLICT (codigo) DO UPDATE SET "
                        f"nombre=EXCLUDED.nombre, area=EXCLUDED.area, "
                        f"estado_fisico=EXCLUDED.estado_fisico, "
                        f"numero_factura=EXCLUDED.numero_factura, "
                        f"updated_at=NOW();"
                    )

                    # Ítem en el resguardo
                    rsg_item_id = new_uuid()
                    rsg_lines.append(
                        f"INSERT INTO resguardo_items "
                        f"(id, resguardo_id, producto_id, producto_nombre, producto_codigo, "
                        f"area, cantidad, numero_serie, valor_unitario) VALUES "
                        f"({sql_str(rsg_item_id)}, {sql_str(rsg_id)}, {sql_str(prod_id)}, "
                        f"{sql_str(bien['descripcion'][:200])}, {sql_str(codigo)}, "
                        f"{sql_str(area)}, 1, "
                        f"{sql_str(bien['numero_serie'])}, {precio:.2f}) "
                        f"ON CONFLICT DO NOTHING;"
                    )

            wb.close()

    # Escribir archivos de salida
    cat_sql  = output_dir / "1_import_categorias.sql"
    prod_sql = output_dir / "2_import_bienes.sql"
    rsg_sql  = output_dir / "3_import_resguardos.sql"

    cat_sql.write_text("\n".join(cat_lines), encoding="utf-8")
    prod_sql.write_text("\n".join(prod_lines), encoding="utf-8")
    rsg_sql.write_text("\n".join(rsg_lines), encoding="utf-8")

    print(f"\n{'='*60}")
    print(f"Archivos procesados : {archivos_proc}")
    print(f"Bienes generados    : {total_bienes}")
    print(f"Saltados/sin desc.  : {total_skipped}")
    print(f"\nArchivos de salida en: {output_dir}")
    print(f"  {cat_sql.name}")
    print(f"  {prod_sql.name}  ({total_bienes} INSERTs)")
    print(f"  {rsg_sql.name}")
    print(f"\nOrden de ejecución contra Postgres:")
    print(f"  psql -d sibim -f {cat_sql}")
    print(f"  psql -d sibim -f {prod_sql}")
    print(f"  psql -d sibim -f {rsg_sql}")


if __name__ == "__main__":
    print(f"Leyendo inventario desde: {INVENTARIO_DIR}")
    if not INVENTARIO_DIR.exists():
        print(f"ERROR: No se encontró la carpeta '{INVENTARIO_DIR}'")
        exit(1)
    generate_sql(INVENTARIO_DIR, OUTPUT_DIR)
