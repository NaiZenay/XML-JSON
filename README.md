# XML-JSON Conversor Concurrente

Este proyecto proporciona dos estrategias para convertir documentos JSON en XML: una implementación secuencial y otra concurrente basada en `ForkJoinPool`. Además incluye una interfaz de usuario Swing que permite comparar ambas ejecuciones y visualizar métricas de rendimiento en tiempo real.

## Estructura principal

- `src/main/java/org/ian/JsonParser.java`: conversor secuencial usado como referencia.
- `src/main/java/org/ian/JsonParserForkJoin.java`: conversor concurrente que reparte el trabajo entre múltiples tareas `ForkJoinTask`.
- `src/main/java/org/ian/UI/XML_JSON.java`: interfaz gráfica que dispara las conversiones y muestra estadísticas.

## Conversión concurrente

La clase `JsonParserForkJoin` crea un `ForkJoinPool` dedicado cuyo tamaño por defecto se ajusta al número de núcleos disponibles. Cada subtarea del parser o de la conversión a XML notifica su ciclo de vida al `ExecutionMonitor` interno:

1. **`taskScheduled`**: se incrementa el contador de tareas planificadas antes de encolar una subtarea.
2. **`taskStarted`**: se incrementa el número de hilos activos cuando la subtarea inicia su ejecución.
3. **`taskFinished`**: se decrementa el número de hilos activos y se incrementa el contador de tareas completadas.

Esta instrumentación se aplica tanto al análisis del JSON (`JsonParseTask`) como a la fase de generación de XML (`XmlConversionTask`), lo que permite conocer con precisión cuántas unidades de trabajo se ejecutaron realmente en paralelo.

### Estadísticas del `ForkJoinPool`

El método `getPoolStats()` expone un contenedor inmutable (`PoolStats`) con las métricas relevantes del `ForkJoinPool` subyacente:

- `parallelism`: nivel de paralelismo objetivo del pool.
- `poolSize`: cantidad de hilos creados.
- `activeThreadCount`: hilos actualmente ejecutando tareas.
- `runningThreadCount`: hilos que no están en espera (incluye hilos que realizan robos de trabajo).
- `queuedSubmissionCount`: tareas enviadas desde fuera del pool aún en cola.
- `queuedTaskCount`: tareas internas en cola esperando ser robadas.
- `stealCount`: cantidad de robos realizados entre hilos.
- `isQuiescent`: indica si el pool está ocioso.

## Interfaz de usuario

La clase `XML_JSON` crea una ventana que permite lanzar ambas modalidades de conversión y muestra la evolución de las métricas del monitor y del `ForkJoinPool`:

- **Tiempo**: diferencia entre el inicio y el momento actual de la ejecución concurrente.
- **Tareas**: número de subtareas completadas frente a las planificadas.
- **Hilos activos**: contador instantáneo de subtareas en ejecución.
- **Progreso**: porcentaje `tareas completadas / tareas planificadas`.
- **Paralelismo / Tamaño del pool / Activos / Ejecutando / Robos**: valores obtenidos directamente desde `PoolStats` para reflejar el comportamiento del `ForkJoinPool`.

La UI arranca un `Timer` Swing que consulta el monitor cada 50 ms durante la ejecución. Una vez que la conversión finaliza, el `Timer` se detiene y se muestran las métricas finales.

## Ejecución

1. **Compilación**:

   ```bash
   mvn -DskipTests package
   ```

2. **Ejecución de la interfaz** (requiere entorno gráfico):

   ```bash
   mvn -DskipTests exec:java -Dexec.mainClass="org.ian.UI.XML_JSON"
   ```

   En entornos sin soporte gráfico puede ejecutarse la conversión concurrente desde código creando una instancia de `JsonParserForkJoin` y llamando a `toXML(String json)`.

## Solución de problemas

- Si Maven no puede descargar plugins (por ejemplo, `maven-resources-plugin`), verifique la conectividad a internet o configure un mirror accesible.
- Para cerrar correctamente la aplicación concurrente fuera de la UI, llame a `JsonParserForkJoin.shutdown()` y espere a que el pool finalice su trabajo.

