# examen-java-1735

Examen de Java sobre el proyecto **CarMotors** (grupo 06): aplicación de consola en Java 17 + Maven + MySQL/JDBC.

## Estructura

```
examen-java-1735/
└── proyecto-java-grupo6-main/
    ├── proyecto-java-grupo06/   # código del proyecto (pom.xml, src, database, docs)
    └── purbas sql/              # scripts SQL de prueba
```

Se eliminó la carpeta `proyecto-java-grupo6-main` que estaba repetida (`proyecto-java-grupo6-main/proyecto-java-grupo6-main/`); ahora hay un solo nivel.

## Qué se hizo en el examen: reserva concurrente de repuestos

**Problema (condición de carrera).** Dos técnicos podían reservar al mismo tiempo la última unidad de un repuesto.
Reservar son tres pasos: leer cuánto hay disponible, verificar si alcanza y guardar la reserva. Si dos hilos
los ejecutan a la vez, ambos leen "hay 1" y ambos reservan: quedan dos reservas para una sola pieza.

**Solución.** `OrdenServicioServicio.agregarRepuesto` ahora es *thread-safe*:

- La sección crítica (lectura → verificación → escritura → `commit`) corre dentro de un bloque `synchronized`
  sobre un candado por repuesto. Solo un hilo a la vez reserva ese repuesto; el siguiente ya ve la reserva anterior.
- Los candados se guardan en un `ConcurrentHashMap` estático, así funcionan aunque cada técnico use su propia
  instancia del servicio. Repuestos distintos no se bloquean entre sí.
- En MySQL la fila del repuesto se sigue bloqueando con `SELECT ... FOR UPDATE` (protege si hay varios programas).
- Si no hay stock suficiente se lanza la nueva `StockInsuficienteException` (hereda de `IllegalArgumentException`,
  así los menús existentes siguen funcionando igual).

**Simulación.** `com.carmotors.simulacion.MainReservaConcurrente` crea el repuesto *Filtro de Aceite XY-123* con
1 unidad en stock y lanza dos hilos (`Hilo-Técnico-A` y `Hilo-Técnico-B`) que arrancan a la vez con un
`CountDownLatch`. Uno reserva la unidad y al otro se le rechaza por falta de stock.

Requiere una base MySQL con `database/schema.sql` y `database/data.sql` cargados y las variables `CARMOTORS_DB_*`.
Desde `proyecto-java-grupo6-main/proyecto-java-grupo06`:

```powershell
mvn compile exec:java "-Dexec.mainClass=com.carmotors.simulacion.MainReservaConcurrente"
```

También se puede ejecutar el `main` de esa clase desde IntelliJ. Salida esperada (el ganador puede ser A o B):

```text
Iniciando simulación de reserva concurrente para el repuesto 'Filtro de Aceite XY-123'...
Stock inicial: 1

Hilo-Técnico-A: Intentando reservar el repuesto...
Hilo-Técnico-B: Intentando reservar el repuesto...

Hilo-Técnico-A: Verificando stock. Hay 1 disponible.
Hilo-Técnico-A: ¡Reserva exitosa! Stock actualizado a 0.
Hilo-Técnico-B: Verificando stock. Hay 0 disponible.
Hilo-Técnico-B: Fallo en la reserva. No hay suficiente stock.

Simulación finalizada.
Reservó la última unidad: Hilo-Técnico-A
Reserva rechazada: Hilo-Técnico-B
Stock final del repuesto: 0
```

## Archivos modificados

| Archivo | Cambio |
|---|---|
| `servicio/OrdenServicioServicio.java` | Reserva con `synchronized` por repuesto, devuelve unidades disponibles, nuevo `disponibleDe` |
| `excepcion/StockInsuficienteException.java` | Nueva excepción para stock insuficiente |
| `simulacion/MainReservaConcurrente.java` | Nueva simulación con dos hilos |
| `pom.xml` | `exec.mainClass` configurable para ejecutar la simulación con Maven |
| `proyecto-java-grupo06/README.md` | Sección "Reserva concurrente de repuestos" con el detalle |

Más detalle en [`proyecto-java-grupo6-main/proyecto-java-grupo06/README.md`](proyecto-java-grupo6-main/proyecto-java-grupo06/README.md).
