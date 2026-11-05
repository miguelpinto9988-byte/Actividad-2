# Hunter Training

Aplicación Android nativa en **Kotlin y XML**, en español, con estética de fantasía oscura, misiones y rangos. Identidad gráfica original; no afiliada a Solo Leveling.

## Descargar la APK

En [Releases](https://github.com/miguelpinto9988-byte/Actividad-2/releases/latest), descarga **Hunter-Training-1.0.0.apk**. Requiere Android 8.0 (API 26) o posterior. Autoriza la instalación desde el navegador o gestor de archivos utilizado.

La entrega inicial es una APK de prueba firmada con certificado de depuración, instalable directamente. No es una publicación en Google Play. Cada compilación de CI puede generar otra clave de depuración; para distribuir actualizaciones conservando datos se debe configurar una clave de producción estable y privada. No se almacenan claves en el repositorio.

## Funciones

- 100 familias de ejercicios, con variantes agrupadas, músculos, equipo, dificultad, técnica en español y enlace a la fuente.
- Generador local por objetivo, experiencia, equipamiento y 1–4 sesiones semanales.
- Rutinas manuales; edición de nombre, ejercicios, series, cantidades y descansos.
- Registro individual de series, repeticiones o segundos y kilogramos; deshacer última serie.
- Sesión activa persistente, temporizador de descanso y guardado parcial.
- Historial, niveles y rangos E–S. Diez XP por serie, máximo 400 por sesión. Guardado idempotente para evitar premios duplicados.
- Room/SQLite local, sin cuenta, anuncios, analítica ni permisos de red. Los enlaces de fuentes se abren en el navegador externo. Desinstalar elimina los datos.

## Catálogo y criterios

Fuente: [Free Exercise DB](https://github.com/yuhonas/free-exercise-db), Unlicense/dominio público, consultada el 5 de noviembre de 2026. Licencia incluida en `app/src/main/assets/DATA_LICENSE.txt`.

El catálogo contiene exactamente 100 **familias**, no 100 variantes. El mapa `tools/catalog_audit.json` documenta 211 registros de origen agrupados. Cambiar agarre, inclinación, implemento o apoyo no añade otra entrada. El catálogo incluye fuerza, modalidades cardiovasculares, movilidad, estiramientos y equilibrio; no pretende contener 100 levantamientos de fuerza. Las indicaciones son adaptaciones breves al español, no transcripciones literales. Algunos movimientos se adaptan a una ejecución libre sin máquina (p. ej., caminar).

El generador es una selección por reglas, no una prescripción clínica ni una IA remota. Excluye automáticamente técnicas de potencia y equilibrio; pueden añadirse manualmente. Sin equipo no simula tirones con material inexistente. La opción Mancuernas no presupone disponer de banco. El usuario debe comprobar que dispone del equipamiento de cada ficha. En sesiones por lado, la cantidad indicada corresponde a cada lado.

## Arquitectura y compilación

- Kotlin 2.1.20; layouts y recursos XML; Activity nativa.
- Room 2.7.1, tareas de disco en un executor de un hilo.
- SDK 35, JDK 17, Android Gradle Plugin 8.9.2, Gradle 8.11.1.

Abre la carpeta raíz en Android Studio, instala SDK 35 y sincroniza. Con Gradle 8.11.1 instalado:

```sh
gradle testDebugUnitTest lintDebug assembleDebug
```

Resultado: `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions instala el entorno, valida el catálogo, ejecuta pruebas, verifica la firma y prueba en un emulador Android antes de publicar la APK. Los informes y capturas se conservan como artefactos `Android-verification`.

## Mejoras opcionales

- Firma de producción estable para actualizar sin reinstalar.
- Exportación e importación del historial para cambiar de teléfono.
- Ilustraciones de técnica propias y accesibles en cada ficha.
- Más pruebas en teléfonos físicos y tamaños de pantalla.
