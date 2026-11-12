# Máscaras

Disfraz 2D que sigue el cuerpo delante de una webcam, pensado para el decodificador **Claro ZTE B866V2H** (Android TV 14, Amlogic S905X4, 2 GB de RAM, userspace solo de 32 bits). También se puede instalar en un teléfono para probar.

La pose sale de MediaPipe Pose Landmarker (modelo lite, CPU). Cada máscara es una carpeta con `rig.json` y PNG propios: no hay personajes con copyright. La especificación está en [`docs/SRS.md`](docs/SRS.md).

La imagen de la cámara se procesa en el dispositivo. La app no pide permiso de internet.

## ABI

El APK lleva **solo `armeabi-v7a`**. En este decodificador no existe `lib64`: una biblioteca arm64 no llega a cargarse. Se usa MediaPipe Tasks Vision **1.2.3** porque `tasks-core` de esa versión incluye `libmediapipe_tasks_jni.so` en 32 bits (el grafo de pose va dentro). La integración continua rechaza el APK si aparece otra ABI o si falta esa biblioteca.

## Descargar e instalar

Cuando el código está en `main`, el APK de depuración queda en el release `latest`:

[https://github.com/CravApp/stb-body-masks/releases/download/latest/app-debug.apk](https://github.com/CravApp/stb-body-masks/releases/download/latest/app-debug.apk)

En un pull request, el mismo APK está en los artefactos de la workflow «Compilar APK» (`app-debug-armeabi-v7a`).

En el ordenador, con la depuración del decodificador activada:

```bash
adb connect <ip-del-decodificador>:5555
adb install -r app-debug.apk
```

Si el equipo está enchufado por USB, basta el `adb install`. La firma de depuración es estable (keystore del repositorio, contraseña pública `android`), así que `-r` sustituye la versión anterior. Esa clave no sirve para publicar en una tienda.

La primera vez el mando debe aceptar el permiso de cámara. Sin cámara, o si se niega el permiso, la app lo explica y no se cierra.

## Mando

La interfaz está en horizontal y en español.

| Tecla | Acción |
| --- | --- |
| ← → | Cambiar máscara (Robot, Esqueleto, y las que se añadan) |
| ↑ o OK | Activar o quitar el espejo |
| ↓ | Mostrar u ocultar los puntos de la pose |
| Atrás | Salir |

El espejo empieza activado porque la webcam del decodificador se presenta como cámara frontal (Camera2 id `"0"`, `LENS_FACING` FRONT).

## Añadir una máscara

Crea `app/src/main/assets/masks/<id>/rig.json` y los PNG junto a él. `<id>` es minúsculas, números, `_` o `-`, y tiene que coincidir con el campo `id`. El nombre visible puede llevar mayúsculas y espacios.

Ejemplo mínimo (la pieza cuelga del hombro al codo):

```json
{
  "formatVersion": 1,
  "id": "capa",
  "name": "Capa de ejemplo",
  "parts": [
    {
      "id": "torso",
      "src": "torso.png",
      "zIndex": 10,
      "anchor": "MID_SHOULDERS",
      "aimFrom": "NOSE",
      "aimTo": "MID_SHOULDERS",
      "sizeFrom": "LEFT_SHOULDER",
      "sizeTo": "RIGHT_SHOULDER",
      "pivot": { "x": 0.5, "y": 0.1 },
      "lengthScale": 1.4,
      "widthScale": 1.1
    }
  ]
}
```

Reglas cortas, con el detalle en la sección 3.5 del SRS:

- El PNG se dibuja en vertical. El pivote es la fracción (0–1) que se apoya en `anchor`. En un brazo, el pivote va arriba, en el centro.
- El eje hacia abajo del PNG sigue el vector `aimFrom` → `aimTo`.
- El alto en pantalla es la distancia `sizeFrom` → `sizeTo` por `lengthScale`. El ancho respeta la proporción del PNG y `widthScale`.
- Si un punto se ve poco, esa pieza no se dibuja. Cabeza y torso de los ejemplos usan nariz y hombros, así que funcionan en un plano de medio cuerpo. Las piernas aparecen cuando se ven cadera, rodilla y tobillo.
- Puntos virtuales: `MID_SHOULDERS`, `MID_HIPS`, `MID_EARS`, además de los 33 nombres de MediaPipe (`LEFT_SHOULDER`, `NOSE`, …).
- `flipHorizontal` y `opacity` son opcionales. Un campo desconocido se ignora. `formatVersion` tiene que ser 1.

Vuelve a compilar e instalar. No hace falta tocar Kotlin. `tools/generate_placeholder_masks.py` regenera el robot y el esqueleto de ejemplo (hace falta Pillow).

## Compilar

Hace falta JDK 21, Android SDK 34 y Build-Tools 34.0.0. La primera compilación descarga el modelo `pose_landmarker_lite.task` (se comprueba el SHA-256).

```bash
export ANDROID_HOME="$HOME/android-sdk"
./gradlew :app:testDebugUnitTest :app:assembleDebug
bash tools/verify_apk_abi.sh app/build/outputs/apk/debug/app-debug.apk
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Integración continua

`.github/workflows/build-apk.yml` corre en cada push, cada pull request y con `workflow_dispatch`. Compila, ejecuta las pruebas de unidad, comprueba el ABI y sube el APK. En `main` actualiza el release `latest`.

Ese release necesita que el token de Actions pueda escribir contenidos. Si el job falla con un 403, en el repositorio: **Settings → Actions → General → Workflow permissions → Read and write permissions**.

## Limitaciones

- El objetivo es **15 FPS o más** en el decodificador. La CI no tiene ese equipo y no lo mide. El diseño (modelo lite, una persona, 640×480, CPU, un fotograma a la vez, sin cara ni segmentación) apunta a ese número; en el S905X4 puede quedar por debajo.
- No hay Face Landmarker. La cabeza del disfraz sigue la nariz y los hombros. La cara queda para una versión posterior, y solo si no rompe los FPS.
- Los dedos del Pose Landmarker lite son poco finos. El formato ya los nombra por si un personaje futuro los usa.
- La vista previa y la máscara se alinean con letterbox 4:3. Si el HAL de la cámara recorta distinto, el disfraz puede desplazarse un poco.
- El APK es de depuración, no un paquete de tienda.
- El arte de ejemplo es geométrico y original. Un personaje de ficción se añade con el mismo formato; no se incluye ninguno con copyright.

## Terceros

- [MediaPipe Tasks](https://github.com/google-ai-edge/mediapipe) (Apache 2.0), versión 1.2.3.
- Modelo `pose_landmarker_lite` float16 revisión 1, de Google, descargado en la compilación desde `storage.googleapis.com`.
- CameraX 1.3.4 y el resto de AndroidX, con sus licencias respectivas.
