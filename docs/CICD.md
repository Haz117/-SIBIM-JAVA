# CI/CD — SIBIM Desktop

Repositorio: [github.com/Haz117/-SIBIM-JAVA](https://github.com/Haz117/-SIBIM-JAVA)
Stack: JavaFX 21 · Maven · GitHub Actions · jpackage

---

## 1. Resumen del flujo CI/CD

```
Push a main / PR
        |
        v
  [ ci.yml ]
  ┌─────────────────────────────────────────┐
  │  1. Checkout                            │
  │  2. Setup JDK 21 Temurin               │
  │  3. mvn compile                         │
  │  4. mvn package -DskipTests            │
  │  5. Upload artifact (JAR, 7 días)      │
  └─────────────────────────────────────────┘
        |
        v
   Artifact disponible en Actions

─────────────────────────────────────────────

Push de tag  v*.*.*
        |
        v
  [ release.yml ]
  ┌─────────────────────────────────────────┐
  │  1. Extraer versión del tag             │
  │  2. mvn versions:set -DnewVersion=X.Y.Z │
  │  3. mvn package (fat JAR)              │
  │  4. jpackage --type app-image          │
  │  5. Zip → SIBIM-Desktop-X.Y.Z-win64    │
  │  6. Crear GitHub Release + release notes│
  └─────────────────────────────────────────┘
        |
        v
   GitHub Release publicado con zip adjunto
```

---

## 2. Integración Continua (CI)

**Archivo:** `.github/workflows/ci.yml`

**Disparadores:** push a `main`, pull requests contra `main`.

```yaml
name: CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: windows-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Configurar JDK 21 Temurin
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven

      - name: Compilar fuentes
        run: mvn compile

      - name: Empaquetar (sin tests)
        run: mvn package -DskipTests

      - name: Subir artefacto JAR
        uses: actions/upload-artifact@v4
        with:
          name: sibim-jar
          path: target/*.jar
          retention-days: 7
```

### Qué valida cada paso

| Paso | Qué valida |
|---|---|
| `mvn compile` | Que el código compila sin errores de sintaxis o dependencias faltantes |
| `mvn package -DskipTests` | Que el JAR se genera correctamente (incluyendo recursos y dependencias) |
| Upload artifact | Que el artefacto queda disponible para descarga e inspección durante 7 días |

---

## 3. Despliegue Continuo (CD)

**Archivo:** `.github/workflows/release.yml`

**Disparador:** push de tag que coincida con `v[0-9]+.[0-9]+.[0-9]+` (por ejemplo `v1.2.0`).

```yaml
name: Release

on:
  push:
    tags:
      - 'v[0-9]+.[0-9]+.[0-9]+'

jobs:
  release:
    runs-on: windows-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Configurar JDK 21 Temurin
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven

      - name: Extraer versión del tag
        id: version
        shell: pwsh
        run: |
          $tag = "${{ github.ref_name }}"
          $ver = $tag.TrimStart('v')
          echo "VERSION=$ver" >> $env:GITHUB_OUTPUT

      - name: Estampar versión en pom.xml
        run: mvn versions:set -DnewVersion=${{ steps.version.outputs.VERSION }} -DgenerateBackupPoms=false

      - name: Construir fat JAR
        run: mvn package -DskipTests

      - name: Empaquetar con jpackage
        shell: pwsh
        run: |
          $ver = "${{ steps.version.outputs.VERSION }}"
          jpackage `
            --type app-image `
            --name "SIBIM-Desktop" `
            --app-version $ver `
            --input target `
            --main-jar sibim-desktop-$ver.jar `
            --main-class com.tuempresa.sibim.Main `
            --dest dist

      - name: Crear zip de distribución
        shell: pwsh
        run: |
          $ver = "${{ steps.version.outputs.VERSION }}"
          Compress-Archive -Path dist\SIBIM-Desktop -DestinationPath "SIBIM-Desktop-$ver-win64.zip"

      - name: Publicar GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          generate_release_notes: true
          files: SIBIM-Desktop-${{ steps.version.outputs.VERSION }}-win64.zip
```

---

## 4. Cómo publicar una nueva versión

Sigue estos pasos desde tu máquina local:

```bash
# (Opcional) Ajusta pom.xml manualmente si lo deseas.
# El CD estampa la versión automáticamente desde el tag.
git add .
git commit -m "chore: prepare vX.Y.Z"
git push origin main

# Crear y publicar el tag
git tag vX.Y.Z
git push origin vX.Y.Z
```

Al detectar el tag, GitHub Actions ejecuta `release.yml` automáticamente: estampa la versión en `pom.xml`, compila, empaqueta con `jpackage`, crea el zip y publica el Release en GitHub. No se requiere ninguna acción manual adicional.

---

## 5. Convención de versiones (Semver)

El proyecto sigue [Semantic Versioning](https://semver.org/) con el formato `MAYOR.MENOR.PARCHE`.

| Tipo de cambio | Ejemplo | Cuándo usarlo |
|---|---|---|
| Bug fix | `v1.0.1` | Correcciones sin cambio de funcionalidad |
| Feature | `v1.1.0` | Funcionalidad nueva compatible con versiones anteriores |
| Breaking | `v2.0.0` | Cambios incompatibles con versiones anteriores |

---

## 6. Auto-actualización en la app

`UpdateChecker.java` consulta la API de GitHub al iniciar sesión:

```
GET https://api.github.com/repos/Haz117/-SIBIM-JAVA/releases/latest
```

Si la versión del release más reciente es mayor que la versión actual (comparación semver), la app muestra un toast con un botón **"Ver actualización"** que abre el navegador en la página del release.

La versión actual se inyecta en tiempo de compilación mediante Maven resource filtering. En `src/main/resources/app.properties`:

```properties
app.version=${project.version}
```

Maven reemplaza `${project.version}` con el valor de `<version>` en `pom.xml` durante la fase de build. El workflow de CD estampa esta versión antes de compilar, por lo que el binario distribuido siempre refleja el tag que lo originó.

---

## 7. Entorno del runner (GitHub Actions)

El runner utilizado es `windows-latest`.

| Herramienta | Version | Rol en el pipeline |
|---|---|---|
| JDK Temurin 21 | instalado por `actions/setup-java` | Compilacion y ejecucion de `jpackage` |
| Maven | disponible en PATH tras `setup-java` | Gestion de dependencias y ciclo de build |
| jpackage | incluido en JDK 21 | Empaquetado de la app como imagen nativa |
| PowerShell | 5.1 | Scripts de extraccion de version y compresion |

---

## 8. Build local (sin CI)

Para construir la app en tu maquina sin pasar por GitHub Actions, usa el script de PowerShell incluido en el repositorio:

```powershell
# App-image portable (sin WiX ni instalador)
.\packaging\build-installer.ps1

# Con zip listo para distribucion
.\packaging\build-installer.ps1 -ZipOutput

# Especificar version manualmente
.\packaging\build-installer.ps1 -Version 1.2.0 -ZipOutput
```

El script detecta si existe el directorio `maven-dist/` en el repositorio local y lo usa directamente. Si no esta presente, llama a `mvn` desde el PATH del sistema.

---

## 9. Solucion de problemas comunes

| Problema | Causa probable | Solucion |
|---|---|---|
| `jpackage not found` | `JAVA_HOME` no apunta a JDK 21 | `$env:JAVA_HOME = "C:\Path\To\JDK21"` |
| `mvn: command not found` | Maven no esta en PATH y no existe `maven-dist/` | Instalar Maven o asegurarse de tener `maven-dist/` en el repo |
| Release workflow no dispara | El tag no coincide con el patron `v*.*.*` | Verificar formato: `v1.0.0` es valido, `1.0.0` no lo es |
| `UpdateChecker` siempre retorna null | Sin releases publicados en GitHub o sin acceso a internet | Normal en entorno de desarrollo; en produccion crear al menos un release |

---

## 10. Diagrama de archivos relevantes

```
.github/
  workflows/
    ci.yml              <- CI: compila y empaqueta en cada push a main o PR
    release.yml         <- CD: crea release en GitHub al pushear un tag v*.*.*
packaging/
  build-installer.ps1  <- Build local (desarrollo) y referencia para el pipeline CI
src/main/resources/
  app.properties        <- Version inyectada por Maven (filtrada en tiempo de build)
src/main/java/.../util/
  UpdateChecker.java    <- Consulta GitHub API; notifica al usuario si hay version nueva
pom.xml                 <- versions-maven-plugin para el estampado de version desde el tag
```
