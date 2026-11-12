# Especificación de requisitos de software

**Producto:** Máscaras (stb-body-masks)
**Versión del documento:** 1.0
**Fecha:** 2026-11-12
**Estado:** vigente para el MVP
**Estilo:** IEEE Std 830-1998, adaptado a un único documento de producto

## Historial

| Versión | Fecha | Descripción |
| --- | --- | --- |
| 1.0 | 2026-11-12 | Requisitos del MVP y de la versión futura con personaje de ficción |
| 1.1 | 2026-11-12 | Detector por defecto: lite, GPU y análisis 320×240, con respaldo seleccionable |

## 1. Introducción

### 1.1 Propósito

Este documento fija qué debe hacer la aplicación Android «Máscaras» para el equipo que la construye, quien la prueba y quien después sustituya el disfraz de ejemplo por un personaje de ficción. El texto manda sobre el comportamiento observable, el formato de las máscaras y las restricciones del decodificador. El código del repositorio es la implementación de esta versión.

### 1.2 Alcance

El producto superpone un disfraz 2D al cuerpo visto por una webcam, en tiempo real, en un decodificador Android TV. El disfraz es un conjunto de piezas (cabeza, torso, brazos, piernas) colocadas con los puntos de pose de MediaPipe.

Incluye:

- la aplicación Android del MVP, con dos máscaras originales de ejemplo (robot y esqueleto);
- el formato de datos de una máscara, pensado para cambiar el personaje sin tocar código;
- la construcción del APK de 32 bits y su publicación.

No incluye tienda de aplicaciones, cuentas, red, grabación de vídeo ni el arte de un personaje con copyright de terceros.

### 1.3 Definiciones y acrónimos

| Término | Significado |
| --- | --- |
| ABI | Application Binary Interface. Aquí, la arquitectura nativa del APK. |
| APK | Paquete instalable de Android. |
| Cámara frontal | Cámara cuyo `LENS_FACING` es FRONT. En el decodificador objetivo la webcam USB se presenta así. |
| D-pad | Cruceta del mando: arriba, abajo, izquierda, derecha, más OK y atrás. |
| FPS | Fotogramas de inferencia por segundo. |
| Landmark | Punto del cuerpo normalizado que devuelve el detector de pose. |
| Leanback | Categoría de launcher de Android TV. |
| Máscara | Disfraz 2D descrito por `rig.json` y sus imágenes. |
| MediaPipe | Biblioteca de Google usada para el Pose Landmarker. |
| MVP | Versión mínima que se puede instalar y usar en el decodificador. |
| Pivote | Punto de la imagen de una pieza que se apoya en el ancla del cuerpo. |
| Pose Landmarker | Grafo de MediaPipe que estima 33 puntos del cuerpo. |
| Rig | Descripción de cómo se ata cada pieza a un par de puntos. |
| UVC | Webcam USB que Android expone como cámara externa. |
| Userspace de 32 bits | El sistema no tiene `lib64`. Una biblioteca solo arm64 no llega a cargarse. |

### 1.4 Referencias

1. IEEE Std 830-1998, Recommended Practice for Software Requirements Specifications.
2. MediaPipe Pose Landmarker para Android. Modelo `pose_landmarker_lite`, float16, revisión 1.
3. Artefacto Maven `com.google.mediapipe:tasks-vision:1.2.3` y `tasks-core:1.2.3`.
4. Guía de aplicaciones para Android TV: categoría `LEANBACK_LAUNCHER`, banner y mando direccional.
5. Restricciones del dispositivo aportadas para este producto (sección 2.4). No se han medido de nuevo en hardware durante la redacción.

### 1.5 Visión general

La sección 2 describe el producto, el dispositivo y las dependencias. La sección 3 enumera requisitos funcionales del MVP, requisitos de la versión con personaje de ficción, requisitos no funcionales, el formato de máscara, los criterios de aceptación y los riesgos.

## 2. Descripción general

### 2.1 Perspectiva

La aplicación es un programa autónomo. Lee la cámara con CameraX (Camera2 por debajo), estima la pose en el propio equipo y dibuja la máscara encima de la vista previa. No hay servidor. El manifiesto no pide permiso de internet.

La máscara no está dibujada en código. Cada personaje es una carpeta en `assets/masks/<id>/`. Así, la versión de ficción puede añadir un personaje nuevo sin cambiar el motor.

### 2.2 Funciones

- Mostrar la cámara en horizontal y, por defecto, en espejo si es frontal.
- Estimar una pose y superponer las piezas del disfraz que tengan puntos visibles.
- Cambiar de máscara, el espejo y una vista de depuración con el mando.
- Explicar y recuperarse si falta el permiso o la cámara.
- Mostrar los FPS de inferencia.

### 2.3 Usuarios

La persona que se pone delante de la webcam, de pie o de medio cuerpo, con un mando de televisor. Quien prueba en un teléfono usa la misma interfaz en horizontal. Quien añade un personaje edita archivos, no la pantalla.

### 2.4 Restricciones del dispositivo

Equipo objetivo: decodificador Claro ZTE B866V2H.

| Aspecto | Restricción |
| --- | --- |
| SoC | Amlogic S905X4, Cortex-A55, GPU Mali-G31 MP2 |
| RAM | 2 GB, compartidos con Android TV |
| Sistema | Android TV 14, API 34 |
| Userspace | Solo 32 bits. No existe `lib64` |
| ABI del APK | Únicamente `armeabi-v7a` |
| Cámara | Webcam USB UVC. Camera2 la expone como id `"0"`, `LENS_FACING` FRONT, nivel de hardware EXTERNAL (proveedor interno de Amlogic) |
| Pantalla | Televisor, horizontal, sin panel táctil |
| Entrada | Mando: D-pad, OK, atrás |

El mismo APK debe instalarse en un teléfono para pruebas. Por eso el táctil y la cámara no son requisitos de instalación, y además de `LEANBACK_LAUNCHER` existe la categoría `LAUNCHER`.

### 2.5 Suposiciones y dependencias

- En el decodificador, CameraX puede abrir la webcam como cámara frontal. Si no, se usa la trasera o la primera cámara, y el espejo se puede apagar.
- MediaPipe Tasks Vision 1.2.3 es adecuado: `tasks-vision` no trae `.so` propio, y `tasks-core` 1.2.3 incluye `jni/armeabi-v7a/libmediapipe_tasks_jni.so` (ELF 32 bits ARM) con el grafo `PoseLandmarkerGraph`. Se comprobó el contenido de las AAR. La 0.10.26 de `tasks-vision` es solo arm64 y no sirve. Versiones futuras pueden volver a quitar el ABI de 32 bits; por eso la versión queda fijada.
- El modelo `pose_landmarker_lite.task` (float16, revisión 1, SHA-256 `59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a`) se descarga en la compilación y entra en los assets. En ejecución no hace falta red.
- El delegado por defecto es la GPU del Mali-G31. Si no arranca, se usa la CPU sin grabar ese respaldo como preferencia. El modelo por defecto es el lite; el completo queda en el mando.
- Hay una persona como mucho (`numPoses = 1`). No hay máscara de segmentación ni landmarker de cara en el MVP.
- El arte de ejemplo es original. Un personaje de ficción con derechos de terceros no se incluye en el repositorio.

## 3. Requisitos específicos

### 3.1 Interfaces externas

**Interfaz de usuario.** Pantalla completa, fondo negro, horizontal. Un panel de texto en español (FPS, estado, nombre de la máscara, espejo y ayuda del mando) en una esquina, con contraste suficiente para verse a distancia de televisor. El botón de error o permiso tiene borde blanco cuando recibe el foco.

**Mando.**

| Tecla | Acción |
| --- | --- |
| Izquierda / derecha | Máscara anterior / siguiente |
| Arriba, OK o Enter | Alternar espejo |
| Abajo | Mostrar u ocultar puntos y huesos de depuración |
| 4 | Alternar modelo lite y completo, y reiniciar el detector |
| 5 | Alternar CPU y GPU, y reiniciar el detector |
| 6 | Alternar el análisis entre 640×480 y 320×240, y reabrir la cámara |
| Atrás | Cerrar la actividad |
| OK sobre el botón de aviso | Ejecutar la acción de ese aviso (permiso, reintento o ajustes) |

Mientras hay un aviso modal, las flechas mueven el foco y no cambian la máscara.

**Cámara.** Permiso `CAMERA` en tiempo de ejecución. Vista previa con CameraX en modo compatible (TextureView) para poder espejar con `scaleX`, a 640×480. El análisis va en RGBA_8888 (YUV si el HAL no lo acepta), por defecto 320×240 y 4:3, con dos bitmaps reutilizados para convertir el siguiente fotograma mientras hay una inferencia en curso. Si el HAL entrega otra medida, se escala al tamaño pedido.

**Sistema.** `minSdk` 26, `targetSdk` 34. Banner de Android TV. `android.hardware.touchscreen` requerido en falso. `android.hardware.camera`, `camera.any` y `camera.front` requeridos en falso. `android.software.leanback` requerido en falso. `largeHeap` en falso. Sin permiso `INTERNET`: la telemetría que arrastra MediaPipe declara `INTERNET` y `ACCESS_NETWORK_STATE`, y el manifiesto de la app los quita con `tools:node="remove"`.

**Salida de construcción.** APK de depuración firmado con el keystore de depuración del repositorio (contraseña pública `android`), para que `adb install -r` sustituya la instalación anterior. El APK no lleva la marca `testOnly`.

### 3.2 Requisitos funcionales del MVP

Los identificadores RF-MVP son comprobables.

| ID | Requisito |
| --- | --- |
| RF-MVP-01 | La actividad principal responde a `MAIN` + `LAUNCHER` y a `MAIN` + `LEANBACK_LAUNCHER`, con banner e icono. |
| RF-MVP-02 | La interfaz visible está en español y la actividad queda fija en horizontal. |
| RF-MVP-03 | Sin el permiso de cámara, se explica para qué sirve y hay una acción para pedirlo. La imagen no se envía fuera del equipo. |
| RF-MVP-04 | Si la persona niega el permiso, la app no se cierra por una excepción. Ofrece reintentar o, si el sistema ya no va a mostrar el diálogo, abrir los ajustes de la aplicación. |
| RF-MVP-05 | Si no hay ninguna cámara, o CameraX no puede enlazarla, se muestra un aviso en español y se puede reintentar. |
| RF-MVP-06 | Con permiso y cámara, se muestra la vista previa. Si existe cámara frontal, se usa esa. El espejo empieza activado. |
| RF-MVP-07 | Arriba u OK alterna el espejo de la vista previa y de la máscara a la vez, y el estado se recuerda. |
| RF-MVP-08 | El detector es MediaPipe Pose Landmarker en `LIVE_STREAM`, una persona, sin máscara de segmentación. Por defecto: modelo lite, delegado GPU y análisis 320×240. Las teclas 4, 5 y 6 cambian modelo, delegado y resolución. Un fallo de carga prueba el respaldo y no sobrescribe la preferencia guardada. La primera ejecución de esta versión reescribe una sola vez esas tres preferencias a lite, GPU y 320×240. |
| RF-MVP-09 | Sobre la vista se dibuja la máscara activa. Cada pieza sigue sus puntos mientras superan el umbral de visibilidad. Si un miembro no se ve, esa pieza no se dibuja; el resto sí. |
| RF-MVP-10 | La colocación de cada pieza obedece la sección 3.5. |
| RF-MVP-11 | Izquierda y derecha recorren las máscaras cargadas desde `assets/masks`, en orden alfabético del nombre visible, y la elección se recuerda. |
| RF-MVP-12 | Se muestra el número de resultados de inferencia por segundo, actualizado en ventanas de al menos medio segundo. Antes del primer dato se muestra «— FPS». |
| RF-MVP-13 | Abajo alterna una capa de depuración con los puntos de los 33 landmarks visibles y los segmentos principales del cuerpo. Esa capa no sustituye a la máscara. |
| RF-MVP-14 | El estado en pantalla distingue «Cargando modelo…», «Buscando cuerpo…» y «Cuerpo detectado». Hay cuerpo detectado cuando al menos dos de nariz, hombro izquierdo y hombro derecho superan visibilidad 0,45. |
| RF-MVP-15 | Al perder la pose, las piezas desaparecen y el estado vuelve a «Buscando cuerpo…». Un suavizado corto reduce el temblor; una medición casi invisible no arrastra la posición guardada. |
| RF-MVP-16 | Añadir una carpeta válida `assets/masks/<id>/` incorpora la máscara en el siguiente arranque, sin cambiar código Kotlin. |
| RF-MVP-17 | El repositorio incluye al menos las máscaras originales «Robot» y «Esqueleto». |
| RF-MVP-18 | La pantalla permanece encendida mientras la actividad está en primer plano. |
| RF-MVP-19 | Solo se procesa un fotograma de análisis a la vez. Si el detector tarda, los fotogramas intermedios se descartan. |
| RF-MVP-20 | Si el modelo o la biblioteca nativa no cargan, se muestra un error en español y se puede reintentar, sin cerrar el proceso por una excepción no capturada. |

### 3.3 Requisitos de la versión con personaje de ficción

Esta versión no forma parte del MVP. El formato de la sección 3.5 es el contrato para llegar a ella. No se admite arte con copyright de terceros dentro del repositorio.

| ID | Requisito |
| --- | --- |
| RF-FUT-01 | Un personaje nuevo se añade como otra carpeta de máscara. El selector del MVP ya lo mostrará. |
| RF-FUT-02 | Piezas extra (orejas, capa, cola, accesorios, manos) usan los mismos puntos, incluidos dedos, talones y los puntos virtuales. No hace falta un tipo de pieza nuevo para eso. |
| RF-FUT-03 | `flipHorizontal` y `opacity` permiten arte asimétrico y piezas translúcidas, por ejemplo una capa. |
| RF-FUT-04 | Si un personaje necesita la boca o los ojos, se podrá añadir el Face Landmarker como opción apagada por defecto. No se enciende si con ello el equipo baja del objetivo de 15 FPS de la sección 3.4. La pose del cuerpo sigue siendo obligatoria; la cara es un añadido. |
| RF-FUT-05 | Una `formatVersion` distinta de 1 solo se introduce si hay campos obligatorios nuevos. Un lector de la versión 1 ignora campos que no conoce, para que un rig nuevo no rompa una app vieja al revés: una app vieja rechaza `formatVersion` distinta de 1 con un error claro. |
| RF-FUT-06 | El orden de dibujo sigue siendo `zIndex`. Una evolución posterior puede mezclarlo con la coordenada z de los world landmarks; el MVP no lo hace. |
| RF-FUT-07 | Cada personaje de ficción debe indicar en su `description` o en la documentación del aporte quién tiene los derechos del dibujo. El motor no descarga arte en ejecución. |
| RF-FUT-08 | Los puntos de los dedos del Pose Landmarker son poco finos. Un personaje cuyo gesto dependa de los dedos deberá usar un Hand Landmarker aparte, también opcional y sujeto al mismo objetivo de FPS. El formato ya puede nombrar esos puntos. |

### 3.4 Requisitos no funcionales

| ID | Requisito |
| --- | --- |
| RNF-01 | `minSdk` 26, `targetSdk` 34, `compileSdk` 34. Lenguaje de la app: Kotlin. |
| RNF-02 | El APK de depuración contiene solo `lib/armeabi-v7a`. La integración continua falla si aparece `arm64-v8a`, `x86`, `x86_64` o cualquier otra ABI, o si falta `armeabi-v7a`. |
| RNF-03 | `libmediapipe_tasks_jni.so` está en ese ABI y es ELF de 32 bits ARM. |
| RNF-04 | Objetivo de rendimiento: 15 FPS o más en el decodificador de la sección 2.4, con una persona, modelo lite, análisis 320×240 y delegado GPU. Es un objetivo de diseño. La integración continua no tiene ese hardware y no lo certifica. Cada segundo se escribe en logcat, etiqueta `BodyMasksPerf`, una línea con fps, inferMs, convertMs, drawMs, modelo pedido y cargado, delegado y resolución. Al cargar el modelo se escribe `load requested=... loaded=... error=...`. |
| RNF-05 | Memoria contenida para un equipo de 2 GB: una persona, sin segmentación, sin segundo modelo, sin `largeHeap`, un solo fotograma de análisis en vuelo, piezas PNG pequeñas. La app no debe acercarse al total de RAM del equipo. |
| RNF-06 | La navegación principal no depende de una pantalla táctil. El foco del botón de aviso es visible. |
| RNF-07 | Textos de interfaz a 15 sp o más; el contador de FPS, a 22 sp, sobre un panel oscuro. |
| RNF-08 | La compilación de GitHub Actions produce el APK en cada push, cada pull request y cada `workflow_dispatch`, lo publica como artefacto, y en los push (o `workflow_dispatch`) a `main` actualiza el release `latest` con `app-debug.apk`. |
| RNF-09 | La firma de depuración es la del keystore versionado, para que las actualizaciones con `adb install -r` no choquen por certificado. Esa clave no es una clave de producción. |
| RNF-10 | Los nombres de máscara, rutas de imagen e identificadores del rig se validan. Una máscara rota se omite con un error en el registro; si no queda ninguna, la interfaz lo dice. |

### 3.5 Formato de máscara

#### 3.5.1 Archivos

```
assets/masks/<id>/rig.json
assets/masks/<id>/<src>
```

`<id>` coincide con el campo `id` del JSON y con la expresión `^[a-z0-9][a-z0-9_-]{0,40}$`. `<src>` es una ruta relativa a esa carpeta, sin `..`, sin barra inicial y sin `:`. Varias piezas pueden compartir el mismo PNG.

#### 3.5.2 Puntos

Los 33 landmarks usan el nombre y el orden de MediaPipe:

`NOSE`, `LEFT_EYE_INNER`, `LEFT_EYE`, `LEFT_EYE_OUTER`, `RIGHT_EYE_INNER`, `RIGHT_EYE`, `RIGHT_EYE_OUTER`, `LEFT_EAR`, `RIGHT_EAR`, `MOUTH_LEFT`, `MOUTH_RIGHT`, `LEFT_SHOULDER`, `RIGHT_SHOULDER`, `LEFT_ELBOW`, `RIGHT_ELBOW`, `LEFT_WRIST`, `RIGHT_WRIST`, `LEFT_PINKY`, `RIGHT_PINKY`, `LEFT_INDEX`, `RIGHT_INDEX`, `LEFT_THUMB`, `RIGHT_THUMB`, `LEFT_HIP`, `RIGHT_HIP`, `LEFT_KNEE`, `RIGHT_KNEE`, `LEFT_ANKLE`, `RIGHT_ANKLE`, `LEFT_HEEL`, `RIGHT_HEEL`, `LEFT_FOOT_INDEX`, `RIGHT_FOOT_INDEX`.

Puntos virtuales, con visibilidad igual al mínimo de sus componentes:

- `MID_SHOULDERS`: punto medio de los hombros.
- `MID_HIPS`: punto medio de las caderas.
- `MID_EARS`: punto medio de las orejas.

«Izquierda» y «derecha» son las de la persona, como en MediaPipe, no las de la pantalla. El espejo se aplica después, al proyectar.

La visibilidad de un landmark es el mínimo entre `visibility` y `presence`. Si uno de los dos no viene en el resultado, se toma como 1.

Coordenadas normalizadas: origen en la esquina superior izquierda de la imagen de análisis ya orientada, x hacia la derecha, y hacia abajo. El rango habitual es 0..1 y puede salirse un poco.

#### 3.5.3 Esquema `rig.json` (formatVersion 1)

```json
{
  "formatVersion": 1,
  "id": "robot",
  "name": "Robot",
  "description": "Texto libre para quien elige o documenta la máscara.",
  "defaults": { "minVisibility": 0.5 },
  "parts": [
    {
      "id": "left_upper_arm",
      "src": "upper_arm.png",
      "zIndex": 30,
      "anchor": "LEFT_SHOULDER",
      "aimFrom": "LEFT_SHOULDER",
      "aimTo": "LEFT_ELBOW",
      "sizeFrom": "LEFT_SHOULDER",
      "sizeTo": "LEFT_ELBOW",
      "pivot": { "x": 0.5, "y": 0.12 },
      "lengthScale": 1.16,
      "widthScale": 1.0,
      "minVisibility": 0.4,
      "flipHorizontal": false,
      "opacity": 1.0
    }
  ]
}
```

Obligatorios en la raíz: `formatVersion` (entero 1), `id`, `name`, `parts` (lista no vacía). `description` y `defaults` son opcionales. `defaults.minVisibility` es opcional y vale 0,5 si se omite.

Obligatorios en cada pieza: `id`, `src`, `zIndex`, `anchor`, `aimFrom`, `aimTo`, `sizeFrom`, `sizeTo`, `pivot.x`, `pivot.y`, `lengthScale`, `widthScale`.

Opcionales en cada pieza: `minVisibility` (si falta, el de `defaults`), `flipHorizontal` (falso), `opacity` (1).

Rangos: visibilidad y opacidad en 0..1; escalas en 0,05..8; pivote en -0,25..1,25 (lo habitual es 0..1). Los `id` de pieza no se repiten y usan el mismo patrón que el id de la máscara. Un campo desconocido se ignora. Un `formatVersion` distinto de 1 es un error.

Convención del dibujo: el PNG está en vertical. Su eje local +Y (hacia abajo en el archivo) es el eje que se alineará con el vector de apuntamiento. El pivote es la fracción del ancho y del alto donde está la articulación proximal. Para un brazo, el pivote queda cerca del borde superior, en el centro.

#### 3.5.4 Algoritmo de colocación

Para cada pieza, de menor a mayor `zIndex`:

1. Resolver los cinco puntos `anchor`, `aimFrom`, `aimTo`, `sizeFrom` y `sizeTo`. Si falta alguno, o su visibilidad es estrictamente menor que el umbral de la pieza, no se dibuja.
2. Pasar de coordenadas normalizadas a la vista con letterbox `FIT_CENTER`, el mismo criterio que la vista previa:
   - `escala = min(anchoVista / anchoImagen, altoVista / altoImagen)`
   - centrar el sobrante en los dos ejes.
   - Si el espejo está activo, usar `x' = 1 - x` antes de escalar. Así coincide con un `PreviewView` con `scaleX = -1` y letterbox simétrico.
3. El pivote de la pieza se coloca en `anchor`.
4. El ángulo es `atan2(aimTo.y - aimFrom.y, aimTo.x - aimFrom.x) - π/2`, en píxeles de vista. Así el +Y local del PNG sigue ese vector. Un hueso que apunta hacia abajo en la pantalla queda sin rotación extra.
5. `alto = distancia(sizeFrom, sizeTo) * lengthScale`. `ancho = alto * (anchoBitmap / altoBitmap) * widthScale`.
6. Si el vector de apuntamiento o el de tamaño miden menos de 4 px en la vista, no se dibuja (evita piezas enormes o giradas al azar cuando dos puntos coinciden).
7. Si `flipHorizontal` es verdadero, se invierte el eje X local después de rotar, alrededor del pivote.
8. Se dibuja con la opacidad indicada.

La cabeza y el torso de los ejemplos no dependen de las caderas: apuntan de la nariz al centro de los hombros y se escalan con el ancho de hombros. Así el disfraz sigue visible en un encuadre de medio cuerpo, que es lo habitual con una webcam de televisor. Las piernas usan cadera, rodilla y tobillo, y simplemente no aparecen si esos puntos no se ven.

### 3.6 Criterios de aceptación

| ID | Criterio | Cubre |
| --- | --- | --- |
| CA-01 | `./gradlew :app:testDebugUnitTest` pasa, incluidas las pruebas del rig de ejemplo, del espejo, del letterbox, de la rotación y del suavizado. | RF-MVP-10, RF-MVP-15, RF-MVP-16 |
| CA-02 | `./gradlew :app:assembleDebug` produce `app/build/outputs/apk/debug/app-debug.apk`. | RNF-01 |
| CA-03 | `tools/verify_apk_abi.sh` acepta ese APK: solo `lib/armeabi-v7a`, `.so` ELF 32 bits ARM, presente `libmediapipe_tasks_jni.so`, presentes el modelo y los dos `rig.json`, y `aapt dump badging` no muestra otra ABI ni `testOnly`. | RNF-02, RNF-03, RNF-09 |
| CA-04 | El manifiesto contiene las dos categorías de launcher, el banner, orientación horizontal, y las features de táctil y cámara en `required=false`. | RF-MVP-01, RNF-06 |
| CA-05 | En un dispositivo con cámara, tras conceder el permiso, se ve la vista previa, el contador de FPS y, con una persona en cuadro, las piezas que correspondan a los miembros visibles. Izquierda y derecha cambian entre Robot y Esqueleto. Arriba u OK cambian el espejo. Abajo muestra los puntos. | RF-MVP-02, RF-MVP-06 a RF-MVP-14 |
| CA-06 | Sin permiso, y con el permiso concedido pero sin cámara, la app muestra el aviso en español y no se cierra sola. | RF-MVP-03, RF-MVP-04, RF-MVP-05 |
| CA-07 | `adb install -r app-debug.apk` instala o sustituye la app cuando la firma es la del keystore del repositorio. | RNF-09 |
| CA-08 | La workflow de GitHub Actions termina en verde en el pull request, sube el APK como artefacto y, al integrar en `main`, deja `app-debug.apk` en el release `latest`. | RNF-08 |

CA-05 y la medida de 15 FPS (RNF-04) se hacen en el decodificador o en un teléfono. No hay emulador de ese SoC en la integración continua. CA-01 a CA-04 y CA-08 se automatizan.

### 3.7 Riesgos

| ID | Riesgo | Efecto | Mitigación |
| --- | --- | --- | --- |
| RR-01 | Una versión nueva de MediaPipe deja de publicar `armeabi-v7a` (ya pasó en `tasks-vision` 0.10.26). | El APK no arranca el detector en el decodificador. | Versión fijada en 1.2.3. La CI rechaza un APK sin ese `.so` de 32 bits o con arm64. |
| RR-02 | El Cortex-A55 no alcanza 15 FPS. | El disfraz va a tirones. | Lite, GPU, 320×240, conversión solapada con la inferencia, una persona, sin cara ni segmentación. El modelo completo y la CPU quedan como opción del mando. |
| RR-03 | La webcam no aparece como frontal, o el espejo queda al revés. | El disfraz no coincide con el cuerpo. | Preferir frontal y, si no hay, otra cámara. El mando alterna el espejo. |
| RR-04 | La vista previa y el análisis no salen con la misma relación de aspecto. | La máscara queda desplazada. | Misma estrategia 4:3 y `FIT_CENTER` en los dos. Sigue siendo un riesgo si el HAL recorta. |
| RR-05 | Los 2 GB se agotan entre el sistema, la cámara y los bitmaps. | El sistema mata la app. | Sin `largeHeap`, un fotograma en vuelo, bitmaps de máscara pequeños, modelo lite único. |
| RR-06 | El registro interno de MediaPipe (datatransport) se queja en un equipo sin servicios de Google. | Ruido en el log o, en el peor caso, un fallo al iniciar. | El arranque del detector está capturado y se muestra `msg_model_error`. |
| RR-11 | Un `.task` con el primer archivo en un offset no múltiplo de 4 no se mapea y el lite cae al modelo completo sin decirlo. | Se pierde el ajuste rápido. | La compilación comprueba la alineación y reescribe el paquete si hace falta. Los oficiales de pose ya empiezan el dato en el byte 52. Un fallo de carga no graba el respaldo. |
| RR-07 | `scaleX` no espeja un SurfaceView. | El cuerpo y la máscara no coinciden. | `PreviewView` en modo compatible (TextureView). |
| RR-08 | El token de Actions del repositorio es de solo lectura. | El APK se construye, pero el release `latest` no se crea. | El job de release pide `contents: write`. Si la política del repositorio lo impide, hay que cambiar «Workflow permissions» a lectura y escritura. El job de compilación no depende de ese permiso. |
| RR-09 | La clave de depuración está en el repositorio. | Cualquiera puede firmar una build con la misma clave. | Es una clave de depuración con contraseña pública, solo para sideload. No sirve para Play Store ni debe reutilizarse como clave de release. |
| RR-10 | Un personaje de ficción exige dedos o cara precisos. | El Pose Landmarker lite no alcanza esa finura y otro modelo puede romper los 15 FPS. | Queda fuera del MVP (RF-FUT-04, RF-FUT-08) y solo se añade si la medida de FPS lo permite. |

## 4. Matriz de trazabilidad

| Necesidad | Requisitos | Aceptación |
| --- | --- | --- |
| Instalar y abrir en el Android TV y en un teléfono | RF-MVP-01, RNF-06, RNF-09 | CA-04, CA-07 |
| Ver el cuerpo y el disfraz | RF-MVP-06 a RF-MVP-10, RF-MVP-14, RF-MVP-15, RF-MVP-19 | CA-05 |
| Mando, no táctil | RF-MVP-02, RF-MVP-07, RF-MVP-11, RF-MVP-13, RNF-06, RNF-07 | CA-05 |
| Sobrevivir a permiso o cámara ausentes | RF-MVP-03, RF-MVP-04, RF-MVP-05, RF-MVP-20 | CA-06 |
| Cambiar de personaje después | RF-MVP-16, RF-MVP-17, RF-FUT-01 a RF-FUT-08, sección 3.5 | CA-01 |
| Caber en el SoC de 32 bits y 2 GB, con objetivo de 15 FPS | RNF-02, RNF-03, RNF-04, RNF-05, RF-MVP-08, RF-MVP-19 | CA-02, CA-03 |
| Descargar el APK | RNF-08 | CA-08 |
