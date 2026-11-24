# Guía de pruebas de carga · BioHub

Cómo se comprueba que BioHub aguanta el pico de tráfico y que el batch no frena el registro (RNF-02, RNF-03, RNF-04, RNF-09).
Todo corre con Docker: **no hay que instalar k6**, se usa su imagen.

| Prueba | Qué demuestra | Cómo se corre | Duración |
| --- | --- | --- | --- |
| PR-02 | Portal con 60× el tráfico habitual (300 peticiones/s, P-12): p95 ≤ 1,0 s y ningún minuto con más del 1 % de caídas | `./scripts/carga.sh pico-publico` | 5 min |
| PR-03 | Durante ese mismo pico, el registro mantiene p95 ≤ 1,0 s y 0 errores | Va dentro de la misma prueba | — |
| PR-13 | Con 1 millón de hallazgos, el p95 del registro con el batch trabajando sube ≤ 1,0 s frente a sin batch | `./scripts/sembrar-millon.sh` y luego `./scripts/prueba-pr13.sh` | 5 min |

## Antes de empezar

1. Cierre programas pesados. **La prueba y BioHub corren en el mismo computador**, así que compiten por la CPU.
2. Levante todo desde cero:

   ```bash
   docker compose down -v && docker compose up -d --build
   ```

3. Espere a que el portal responda en http://localhost:8080, unos 60 s.

## Paso a paso (unos 15 minutos en total)

```bash
./scripts/carga.sh pico-publico         # PR-02 y PR-03 (5 min)
./scripts/sembrar-millon.sh             # siembra 1 millón de hallazgos (1 min)
./scripts/prueba-pr13.sh                # PR-13 (5 min)
```

Para ensayar rápido: `DURACION=1m ./scripts/carga.sh pico-publico`.

Cada prueba imprime al final si **cumple o no**. Los detalles quedan en `pruebas-carga/resultados/`, que no se sube a GitHub. Copie las cifras a la tabla de abajo.

## Qué mide cada número

- **p95 (P-13):** 95 de cada 100 respuestas tardaron menos que eso. Se reporta también el máximo, pero no decide: lo dispara una sola petición rara.
- **Caída (P-16):** una respuesta 5xx o que tarda más de 5 s. El criterio se revisa **minuto a minuto**: ningún minuto puede tener más del 1 %.
- **CPU de la primaria (P-20):** `carga.sh` la mide cada 5 s. Docker cuenta 100 % por núcleo, así que con 8 núcleos el máximo es 800 %. P-20 pide menos del 80 % del total.
- **429 en el portal:** Nginx tiene un límite global de 400 peticiones/s en las rutas públicas (P-19). A 300 peticiones/s no debería aparecer ninguno.
- **Peor caso de PR-13:** la consulta del informe tarda menos de 1 s, incluso con 1 millón de hallazgos. Si solo se pidiera un informe, el batch terminaría antes de que la medición empiece. Por eso, durante la fase "con batch":
    - el analista pide 22 informes, que el batch real calcula;
    - además, la consulta pesada recorre **todos** los hallazgos sin parar en la réplica de analítica.

  Es más exigente que la realidad. Si así cumple, en la realidad también.

## Resultados

### Ensayo en una máquina de pruebas (16 de noviembre de 2026)

Máquina de **2 núcleos y 7 GB de RAM**, con BioHub (10 contenedores) y k6 en la misma máquina.

| Prueba | Medido | Criterio | ¿Cumple? |
| --- | --- | --- | --- |
| PR-02, portal | 90 001 peticiones en 5 min (305/s); **p95 = 6,8 ms**; máx. 1,8 s; **0 % de caídas en cada uno de los 5 minutos** | p95 ≤ 1 000 ms; ningún minuto > 1 % | ✅ |
| PR-03, registro durante el pico | 1 501 registros; **p95 = 22,9 ms**; **0 errores** | p95 ≤ 1 000 ms; 0 errores | ✅ |
| P-20, CPU de la primaria durante el pico | máximo **7,4 %** de un núcleo | < 80 % | ✅ |
| PR-13, registro sin batch | p95 = 9,5 ms (1 011 957 hallazgos) | — | — |
| PR-13, registro con batch | p95 = 14,5 ms; 380 recorridos completos de la tabla y 23 informes generados durante la medición | sube ≤ 1 000 ms | ✅ (+5 ms) |

**Lo que se corrigió gracias a esta prueba:**
- En el primer ensayo, el portal dio p95 = 4,4 s y **18 % de respuestas 502**.
- La causa fue un ajuste de Nginx (`max_fails=1`) pensado para la caída de instancias (PR-04): bajo carga, una sola respuesta lenta sacaba de la rotación a las dos instancias públicas.
- Se dejó ese ajuste solo en el grupo científico y el público pasó a `max_fails=3 fail_timeout=5s`.
- Se repitió PR-04 con el cambio: sigue en 0 errores.

**Otro error que encontró el ensayo:**
- Los registros fallaban después de las 7 p. m., porque k6 mandaba la fecha de "hoy" en hora UTC, que en Colombia ya es mañana, y la API rechaza fechas futuras.
- Los scripts ahora usan la fecha de ayer.

### En un computador del equipo

| Dato | Valor |
| --- | --- |
| Quién y cuándo | Martin, noviembre de 2026, Windows con Docker Desktop y otros programas abiertos |
| Procesador, núcleos y RAM | AMD Ryzen 5 5600X (6 núcleos, 12 hilos), 16 GB a 3200 MHz |
| PR-02: peticiones, p95, % de caídas por minuto | 90 001 peticiones, **p95 = 1,16 ms**, **0 % de caídas en cada minuto** |
| PR-03: registros, p95, errores | 1 501 registros, **p95 = 7,5 ms**, **0 errores** |
| CPU máxima de la primaria | **5,81 %** |
| PR-13: p95 sin batch / con batch / diferencia | 10,3 ms / 5,4 ms / **−4,9 ms** (dentro del ruido; el límite es +1 000 ms), 315 consultas pesadas y 23 informes durante la medición |
| Siembra de 1 millón de hallazgos | 1 002 265 hallazgos en unos 22 s |
| PR-04 (repetida en este equipo) | 763 registros, **0 errores**, la base tiene exactamente 763 |

**Cómo leer la diferencia negativa de PR-13:** con el batch, el registro salió un poco **más rápido**. No es que el batch ayude: la primera fase ("sin batch") incluye el calentamiento de la JVM y de las conexiones. Lo que importa es que la diferencia está muy lejos del +1 000 ms permitido.

## Límites de estas pruebass

- **Todo corre en un solo computador.** En producción, BioHub, la base y quien genera la carga estarían en máquinas distintas. Aquí compiten por la misma CPU, así que los resultados son **pesimistas**, no optimistas.
- **El tráfico habitual (5 peticiones/s) es un supuesto del equipo** (P-12, S-06). El Instituto no dio cifras reales.
- **La caché ayuda mucho:** casi todas las fichas salen de Redis. Una prueba con miles de especies distintas, todas poco visitadas, daría tiempos mayores. Es una limitación conocida.
- **El p99 y el máximo** (cientos de ms a 1,8 s) vienen sobre todo de que k6 y BioHub compiten por solo 2 núcleos. El criterio usa el p95 (P-13).
