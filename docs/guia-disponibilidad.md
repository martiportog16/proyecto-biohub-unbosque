# Guía de disponibilidad · BioHub

Qué hacer cuando algo se cae, y cómo se prueba (RNF-04, RNF-07, P-21).
Todos los comandos se corren desde la carpeta del proyecto, con el sistema arriba (`docker compose up -d`).

| Prueba | Qué demuestra | Script |
| --- | --- | --- |
| PR-04 | Si se cae una instancia científica, el registro no tiene errores | `scripts/prueba-caida-instancia.sh` |
| PR-05 | Si se cae la base primaria, se promueve una réplica y se mide cuánto tarda (MTTR) | `scripts/promover-replica.sh` |
| PR-10 | Desde afuera solo responde Nginx | `scripts/verificar-red.sh` |

---

## 1. Se cae una instancia científica (PR-04)

**Qué pasa sin hacer nada:** hay 2 instancias científicas. Nginx deja de enviarle tráfico a la que no responde y lo pasa a la otra. El investigador no se entera.

**Cómo se prueba:**

```bash
./scripts/prueba-caida-instancia.sh                 # apagado ordenado (docker stop)
MODO=abrupto ./scripts/prueba-caida-instancia.sh    # caída brusca (docker kill)
```

El script:
1. registra hallazgos durante 30 s con 4 trabajadores a la vez;
2. en el segundo 8 apaga una instancia;
3. cuenta los errores;
4. compara con la base: deben haberse guardado **exactamente** los registros que salieron bien, ni perdidos ni duplicados;
5. vuelve a encender la instancia.

**Resultado medido (16 de noviembre de 2026):**

| Modo | Registros | Errores | Guardados en la base | Máximo |
| --- | --- | --- | --- | --- |
| Ordenado | 3636 | 0 | 3636 | 1,0 s |
| Abrupto | 3571 | 0 | 3571 | 1,0 s |

**Lo que hubo que ajustar para llegar a 0 errores** (está en `infra/nginx/nginx.conf`):

- **`max_fails=1 fail_timeout=15s`, solo en el grupo científico:** a la primera falla, Nginx saca la instancia de la rotación 15 s.
    - El grupo **público** usa `max_fails=3 fail_timeout=5s`, más tolerante.
    - Se descubrió en la prueba de carga (paquete 13): con `max_fails=1` en el público, una respuesta lenta suelta durante el pico de 300 peticiones/s sacaba las **dos** instancias y Nginx respondía 502 al 18 % de las peticiones.
    - Con el ajuste: 0 %.
- **`proxy_connect_timeout 1s` en `/api/cientifico/`:** en la red interna conectar tarda milisegundos. Si en 1 s no conecta, la instancia está caída. El resto usa 2 s.
- **Reintentar POST, pero con límite:**
    - Por defecto Nginx **no** reintenta un POST que ya envió, porque podría guardar el hallazgo dos veces.
    - En las primeras pruebas eso dejó 1 error (502) de unos 3500 registros: una petición que llegó justo cuando la instancia se apagaba.
    - Se permite el reintento (`non_idempotent`) solo si la falla ocurre en los **primeros 3 s** (`proxy_next_upstream_timeout 3s`). Eso pasa cuando la instancia está apagada o apagándose y corta la conexión sin procesar nada.
    - Una respuesta lenta nunca se reintenta.
    - El script lo comprueba: los registros guardados en la base coinciden **exactamente** con los que salieron bien.
- **Apagado ordenado en Spring (`server.shutdown: graceful`):** al detener una instancia, termina las peticiones que ya recibió antes de apagarse.

**Riesgo aceptado:** en un caso extremo, si una instancia guarda el hallazgo y se cae en el milisegundo exacto antes de responder, el reintento podría guardarlo dos veces. En las pruebas no pasó ni una vez en más de 7000 registros con caídas.

---

## 2. Se cae la base primaria (PR-05)

**Qué pasa sin hacer nada:**

| Parte | Estado |
| --- | --- |
| Portal público y API externa | **Sigue funcionando**: lee de su réplica y de Redis |
| Registro de hallazgos | **Detenido**: responde error, no se puede escribir |
| Batch | Sin efecto inmediato (el informe se genera una vez al mes) |

Medido: con la primaria apagada, el registro respondió 504 y la ficha pública y la búsqueda respondieron 200.

**Qué hacer:**

```bash
./scripts/promover-replica.sh
```

El script pide escribir `SI` y hace 5 pasos:

1. **Apaga la primaria vieja y el batch.**
    - Si la primaria vieja reviviera, habría **dos** primarias aceptando datos distintos.
    - El batch se detiene porque ya no tiene réplica propia, y no debe cargar la nueva primaria (RNF-09).
2. **Promueve la réplica de analítica:** deja de ser de solo lectura.
3. **Conecta la réplica pública a la nueva primaria**, para que el portal siga recibiendo datos nuevos.
4. **Guarda `DB_HOST_PRIMARIA` en `.env`** y reinicia el grupo científico apuntando a la nueva primaria.
5. **Espera** a que el registro responda y muestra cuánto tardó.

**Por qué se promueve la réplica de analítica y no la pública:** la pública atiende el tráfico del portal. Si se volviera primaria, el registro y el portal volverían a compartir la misma base, que es justo lo que la arquitectura separa (RNF-02, HU-05).

**Resultado medido (16 de noviembre de 2026):**

| Medida | Valor |
| --- | --- |
| Tiempo del script, de aislar a registro funcionando | **30 s** |
| Hallazgos antes de la caída | 34 620 |
| Hallazgos en la nueva primaria | 34 620 (0 perdidos) + 1 registrado después |
| Réplica pública | Siguiendo a la nueva primaria (`streaming`) |
| Especie creada después de la promoción | Visible en el portal en menos de 5 s |

**Por qué no se perdió nada:** la primaria se apagó cuando no había registros en curso. Si se cae **en medio** de registros, se pueden perder los últimos milisegundos, porque la replicación es asíncrona. El script muestra la hora del último cambio que recibió la réplica, antes de promoverla.

**Después de promover, el sistema queda en "modo degradado":**
- no hay batch ni réplica de analítica;
- la primaria vieja está apagada. **No la encienda**: use siempre `docker compose up -d --no-deps <servicio>`.

**Volver a la normalidad:**
- **En la demo:** se reinicia todo desde cero:

  ```bash
  docker compose down -v
  rm .env      # o borre la línea DB_HOST_PRIMARIA
  docker compose up -d
  ```

  **Esto borra los datos.**
- **En producción:** habría que reconstruir la primaria vieja como réplica de la nueva, con `pg_basebackup`, en una ventana de mantenimiento. No está automatizado: queda como limitación.

### Disponibilidad estimada (RNF-04, S14)

No hay un mes de operación para medir, así que se **estima** con la fórmula de la S14:

```
Disponibilidad = MTBF / (MTBF + MTTR)
```

- **MTTR** (tiempo de recuperación) = detectar la caída + correr el script.
    - Script: **30 s** (medido).
    - Detectar y decidir: se supone **15 min**, porque la conmutación es manual.
    - Total: unos **15,5 min**.
- **MTBF** (tiempo entre fallas): se supone **1 caída de la primaria al mes** (720 h), que es pesimista.

```
720 / (720 + 0,26) = 99,96 %
```

- **99,9 % al mes** permite 43 min de caída. Con una caída de la primaria, BioHub usa unos 15,5 min.
- El punto débil es el **tiempo de detección humana**, no el script. Por eso RNF-04 queda **mejorado, no garantizado**. Con conmutación automática (Patroni, descartada por tiempo, P-21) el MTTR bajaría a segundos.

Las caídas de **una instancia** (sección 1) no cuentan como tiempo caído: el registro sigue funcionando con la otra.

---

## 3. Solo Nginx responde desde afuera (PR-10)

```bash
./scripts/verificar-red.sh
```

Revisa tres cosas:
1. **Puertos publicados:** solo el contenedor de Nginx publica un puerto hacia la máquina.
2. **Puertos internos:** BioHub no responde en 5432 (PostgreSQL), 6379 (Redis), 8081 ni 9090.
    - Si el script dice `[AVISO]`, ese puerto lo tiene abierto **otro programa** de su máquina, no BioHub. Por ejemplo, un PostgreSQL instalado aparte.
3. **Rutas internas:** `/actuator/health`, `/metrics` y `/env` responden **404** por Nginx.

**Resultado medido:** 10 contenedores. Solo `nginx` publica un puerto (`8080 -> 80`) y las 3 rutas internas responden 404. **Cumple.**

---

## 4. Otras caídas

| Se cae | Qué pasa | Qué hacer |
| --- | --- | --- |
| Una instancia pública | Nginx usa la otra, igual que en la sección 1 | Nada. `docker compose up -d --no-deps app-publico` y `docker compose exec nginx nginx -s reload` |
| Redis | El portal sigue: la caché responde "no está" y se lee de la réplica (paquete 08). La API externa (`/api/v1`) responde error, porque no puede contar el límite | `docker compose up -d --no-deps redis` |
| Réplica pública | El portal deja de responder. El registro sigue | `docker compose up -d --no-deps postgres-replica-publica` |
| Réplica de analítica | El batch reintenta cada vuelta. El registro y el portal siguen | `docker compose up -d --no-deps postgres-replica-analitica` |
| Nginx | **Todo** se cae desde afuera: es el único punto de entrada | `docker compose up -d --no-deps nginx`. Es un riesgo conocido (punto único de falla) |

**Detalle importante:** Nginx averigua las direcciones de las instancias **al arrancar**. Si una instancia se recrea, cambia de dirección. Después de `docker compose up` de una instancia, recargue Nginx:

```bash
docker compose exec nginx nginx -s reload
```

Los scripts de esta guía ya lo hacen.
