"""
Borrón y recarga de la base de datos SIBIM en Supabase.

Uso (desde la carpeta del proyecto, con la app CERRADA):

    python scripts/reset_supabase.py borrar                 # simulación: muestra qué se borraría
    python scripts/reset_supabase.py borrar --ejecutar      # borra de verdad (pide escribir BORRAR)

    python scripts/reset_supabase.py cargar-cache           # simulación: muestra qué se subiría
    python scripts/reset_supabase.py cargar-cache --ejecutar  # sube de verdad (pide escribir CARGAR)

Qué hace "borrar":
  1. Respalda TODAS las tablas de Supabase a un JSON en %APPDATA%\\SIBIM\\respaldos-reset\\.
  2. Vacía todas las tablas de datos (bienes, categorías, movimientos, resguardos,
     préstamos, comodatos, actas, conteos, auditoría, folios, fotos, historial...).
     Se CONSERVAN: users (para que puedas entrar), configuracion (nombre del
     ayuntamiento, logo, correo) y el historial de migraciones.
  3. Aparta el caché offline local (~/.sibim/offline.db.*) renombrándolo a .bak-<fecha>
     para que la app lo reconstruya desde el servidor y no reenvíe datos viejos.
  Todo el vaciado va en UNA transacción: si algo falla, no se borra nada.

Qué hace "cargar-cache":
  Sube categorías y bienes desde la copia rescatada del caché offline
  (%APPDATA%\\SIBIM\\rescate-2026-09-25\\offline.db.work), omitiendo los registros
  creados por los tests. No sube movimientos (los 222 del caché eran de prueba).
  Los campos que el caché no guarda (fecha de adquisición, vida útil, clave
  armonizada, color, no. de motor, etc.) quedan vacíos.
  Alternativa a este paso: reimportar los Excel de "INV.DIG MLA" desde la app.

Requiere: pip install psycopg2-binary
"""

import argparse
import datetime as dt
import decimal
import json
import os
import re
import sqlite3
import sys
from pathlib import Path

import psycopg2
from psycopg2.extras import execute_values

PROYECTO = Path(__file__).resolve().parent.parent
APPDATA = Path(os.environ.get("APPDATA", Path.home() / "AppData" / "Roaming"))
CACHE_RESCATADO = APPDATA / "SIBIM" / "rescate-2026-09-25" / "offline.db.work"
DIR_RESPALDOS = APPDATA / "SIBIM" / "respaldos-reset"
DIR_CACHE_LOCAL = Path.home() / ".sibim"

# Tablas que NO se tocan.
CONSERVAR = {"users", "configuracion", "flyway_schema_history", "schema_version"}

# Registros creados por los tests dentro del caché (no son inventario real).
CATEGORIAS_DE_PRUEBA = {"Cat A", "Cat B", "Cat C"}
CODIGOS_DE_PRUEBA = {"skasjnokns", "DAAC/01", "SGM/02", "RH/01", "SIL-001", "SGM/01", "ACT-001"}

ZONA_HORARIA = "America/Mexico_City"   # el caché guarda hora local sin zona


# ── Conexión ────────────────────────────────────────────────────────────────

def leer_env():
    env = {}
    for ruta in (APPDATA / "SIBIM" / ".env", PROYECTO / ".env"):
        if ruta.exists():
            for linea in ruta.read_text(encoding="utf-8-sig").splitlines():
                m = re.match(r"\s*([A-Z_]+)\s*=\s*(.*)", linea)
                if m and not linea.lstrip().startswith("#"):
                    env.setdefault(m.group(1), m.group(2).strip())
    if "DB_URL" not in env:
        sys.exit("No encontré DB_URL en %APPDATA%\\SIBIM\\.env ni en .env del proyecto.")
    return env


def conectar(env):
    url = env["DB_URL"].replace("jdbc:postgresql://", "")
    hostport, resto = url.split("/", 1)
    host, port = hostport.split(":")
    dbname = resto.split("?")[0]
    conn = psycopg2.connect(
        host=host, port=port, dbname=dbname,
        user=env["DB_USER"], password=env["DB_PASSWORD"],
        sslmode=env.get("DB_SSL_MODE", "require"),
    )
    conn.autocommit = False
    with conn.cursor() as cur:
        cur.execute("SET TIME ZONE %s", (ZONA_HORARIA,))
        cur.execute("SELECT current_database(), inet_server_addr()")
        db, ip = cur.fetchone()
    print(f"Conectado a: {host} (base '{db}')")
    return conn


def tablas_publicas(cur):
    cur.execute("SELECT table_name FROM information_schema.tables "
                "WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY 1")
    return [r[0] for r in cur.fetchall()]


def contar(cur, tablas):
    out = {}
    for t in tablas:
        cur.execute(f'SELECT count(*) FROM "{t}"')
        out[t] = cur.fetchone()[0]
    return out


def confirmar(palabra):
    resp = input(f'\nEscribe {palabra} para continuar (cualquier otra cosa cancela): ').strip()
    if resp != palabra:
        sys.exit("Cancelado. No se modificó nada.")


# ── borrar ──────────────────────────────────────────────────────────────────

def respaldar(cur, tablas):
    DIR_RESPALDOS.mkdir(parents=True, exist_ok=True)
    sello = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    destino = DIR_RESPALDOS / f"supabase-antes-de-borrar-{sello}.json"

    def a_json(v):
        if isinstance(v, (dt.date, dt.datetime, dt.time)):
            return v.isoformat()
        if isinstance(v, decimal.Decimal):
            return str(v)
        if isinstance(v, (bytes, memoryview)):
            return bytes(v).hex()
        return str(v)

    datos = {}
    for t in tablas:
        cur.execute(f'SELECT * FROM "{t}"')
        cols = [d[0] for d in cur.description]
        datos[t] = [dict(zip(cols, fila)) for fila in cur.fetchall()]
    destino.write_text(json.dumps(datos, default=a_json, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"Respaldo completo guardado en: {destino}")
    return destino


def apartar_cache_local():
    sello = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    for nombre in ("offline.db.enc", "offline.db.work", "offline.db"):
        f = DIR_CACHE_LOCAL / nombre
        if f.exists():
            try:
                f.rename(f.with_name(f"{nombre}.bak-{sello}"))
                print(f"Caché local apartado: {f.name} -> {f.name}.bak-{sello}")
            except OSError as e:
                print(f"AVISO: no pude renombrar {f} ({e}). ¿La app sigue abierta? Ciérrala y renómbralo a mano.")


def cmd_borrar(ejecutar):
    conn = conectar(leer_env())
    cur = conn.cursor()
    todas = tablas_publicas(cur)
    a_vaciar = [t for t in todas if t not in CONSERVAR]
    antes = contar(cur, todas)

    print("\nSe VACIARÁN estas tablas:")
    for t in a_vaciar:
        print(f"  {t:<28} {antes[t]:>7} filas")
    print("\nSe CONSERVAN:")
    for t in todas:
        if t in CONSERVAR:
            print(f"  {t:<28} {antes[t]:>7} filas")

    if not ejecutar:
        print("\nSIMULACIÓN: no se modificó nada. Agrega --ejecutar para borrar de verdad.")
        return

    confirmar("BORRAR")
    respaldar(cur, todas)
    try:
        lista = ", ".join(f'"{t}"' for t in a_vaciar)
        cur.execute(f"TRUNCATE {lista} RESTART IDENTITY")
        despues = contar(cur, a_vaciar)
        restantes = {t: n for t, n in despues.items() if n}
        if restantes:
            raise RuntimeError(f"Quedaron filas en: {restantes}")
        conn.commit()
    except Exception:
        conn.rollback()
        print("\nERROR: se revirtió todo, la base quedó como estaba.")
        raise
    print("\nListo: tablas de datos vacías.")
    apartar_cache_local()
    print("\nSiguiente paso: 'cargar-cache' o reimportar los Excel desde la app.")


# ── cargar-cache ────────────────────────────────────────────────────────────

def a_decimal(v):
    if v is None or v == "":
        return None
    return decimal.Decimal(str(v)).quantize(decimal.Decimal("0.01"))


def a_bool(v):
    return None if v is None else bool(v)


def vacio_a_none(v):
    return None if v == "" else v


def columnas_pg(cur, tabla):
    cur.execute("SELECT column_name FROM information_schema.columns "
                "WHERE table_schema='public' AND table_name=%s", (tabla,))
    return {r[0] for r in cur.fetchall()}


def cmd_cargar_cache(ejecutar, ruta_cache):
    if not ruta_cache.exists():
        sys.exit(f"No encuentro la copia rescatada: {ruta_cache}")
    lt = sqlite3.connect(f"file:{ruta_cache}?mode=ro", uri=True)
    lt.row_factory = sqlite3.Row

    conn = conectar(leer_env())
    cur = conn.cursor()

    ocupadas = {t: n for t, n in contar(cur, ["categories", "products", "movements"]).items() if n}
    if ocupadas:
        sys.exit(f"Supabase aún tiene datos {ocupadas}. Corre primero 'borrar --ejecutar'.")

    # Categorías
    cats = [dict(r) for r in lt.execute("SELECT * FROM categories")]
    cats_ok = [c for c in cats if c["nombre"] not in CATEGORIAS_DE_PRUEBA]
    ids_cat = {c["id"] for c in cats_ok}

    # Bienes
    prods = [dict(r) for r in lt.execute("SELECT * FROM products")]
    omitidos, prods_ok = [], []
    for p in prods:
        if p["codigo"] in CODIGOS_DE_PRUEBA:
            omitidos.append((p["codigo"], p["nombre"], "registro de prueba"))
        elif p["categoria_id"] not in ids_cat:
            omitidos.append((p["codigo"], p["nombre"], "categoría inexistente"))
        else:
            prods_ok.append(p)

    fotos = []
    for p in prods_ok:
        try:
            urls = json.loads(p.get("fotos_urls") or "[]")
        except (ValueError, TypeError):
            urls = []
        fotos += [(f"{p['id']}-f{i}", p["id"], u, i) for i, u in enumerate(urls) if u]

    print(f"\nCategorías a subir: {len(cats_ok)}  (omitidas de prueba: {len(cats) - len(cats_ok)})")
    print(f"Bienes a subir:     {len(prods_ok)}  (omitidos: {len(omitidos)})")
    for cod, nom, motivo in omitidos:
        print(f"   - omitido {cod!r:<16} {nom!r:<24} {motivo}")
    print(f"Fotos adicionales:  {len(fotos)}")
    print("Movimientos:        0  (los del caché eran de prueba)")

    if not ejecutar:
        print("\nSIMULACIÓN: no se modificó nada. Agrega --ejecutar para subir de verdad.")
        return

    confirmar("CARGAR")
    try:
        # categories
        cols_c = [c for c in cats_ok[0].keys() if c in columnas_pg(cur, "categories")]
        execute_values(cur,
            f"INSERT INTO categories ({', '.join(cols_c)}) VALUES %s",
            [tuple(vacio_a_none(c[k]) for k in cols_c) for c in cats_ok])

        # products
        pg_cols = columnas_pg(cur, "products")
        cols_p = [c for c in prods_ok[0].keys() if c in pg_cols]
        conv = {"precio_compra": a_decimal, "precio_venta": a_decimal, "etiquetado": a_bool}

        def fila(p):
            return tuple(conv.get(k, vacio_a_none)(p[k]) for k in cols_p)

        execute_values(cur,
            f"INSERT INTO products ({', '.join(cols_p)}) VALUES %s",
            [fila(p) for p in prods_ok], page_size=500)

        if fotos:
            execute_values(cur,
                "INSERT INTO product_fotos (id, producto_id, foto_url, orden) VALUES %s", fotos)

        n = contar(cur, ["categories", "products", "product_fotos"])
        if n["categories"] != len(cats_ok) or n["products"] != len(prods_ok):
            raise RuntimeError(f"Conteo inesperado tras insertar: {n}")
        conn.commit()
    except Exception:
        conn.rollback()
        print("\nERROR: se revirtió todo, no se subió nada.")
        raise
    print(f"\nListo: {n['categories']} categorías, {n['products']} bienes, {n['product_fotos']} fotos.")


def main():
    ap = argparse.ArgumentParser(description="Borrón y recarga de SIBIM en Supabase")
    sub = ap.add_subparsers(dest="cmd", required=True)
    b = sub.add_parser("borrar", help="respalda y vacía las tablas de datos")
    b.add_argument("--ejecutar", action="store_true")
    c = sub.add_parser("cargar-cache", help="sube categorías y bienes desde la copia rescatada")
    c.add_argument("--ejecutar", action="store_true")
    c.add_argument("--cache", type=Path, default=CACHE_RESCATADO)
    a = ap.parse_args()
    if a.cmd == "borrar":
        cmd_borrar(a.ejecutar)
    else:
        cmd_cargar_cache(a.ejecutar, a.cache)


if __name__ == "__main__":
    main()
