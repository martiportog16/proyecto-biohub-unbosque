# Plan de paquetes de trabajo

Cada paquete es una tarea pequeña con dueño. Cada dueño:

1. Crea su rama: `git checkout -b <rama>` (el nombre está en su LEEME).
2. Copia los archivos de cada `commit-N/` en el repositorio.
3. **Lo corre y lo entiende** (`mvnw verify` y, si aplica, `docker compose up --build`).
4. Hace los commits indicados, desde su propia cuenta.
5. Abre un pull request; otro integrante lo revisa y lo une a `main`.

En la sustentación presenta **una sola persona** en nombre del grupo, con máximo 15 minutos y demo en vivo (Sesión 26). Aun así, cualquiera puede recibir preguntas: todos deben poder explicar cualquier parte, no solo la suya.

Entrega final: **martes 24 de noviembre** (Sesión 26). El miércoles 25 es solo para los grupos que no alcancen a pasar el martes.

**Súbanlos en el orden de la tabla.** Cada paquete trae los archivos completos, así que uno que se sube después ya incluye los cambios de los anteriores. El orden se comprobó armando el proyecto desde cero con los 27 paquetes: el resultado es idéntico a `biohub-proyecto-completo.zip`.

| Orden | Paquete | Dueño | Qué deja | Historias / pruebas |
| --- | --- | --- | --- | --- |
| 1 | 01 | Sergio | Base: capas, perfiles, Docker, Nginx, réplicas, CI | PR-08 (capas) |
| 2 | 02 | Martin | Usuarios, login con JWT y roles | P-28, P-25 |
| 3 | 03 | Daniel | Fichas flexibles con Strategy | HU-02, PR-01, PR-15 |
| 4 | 04 | Daniel | Registrar hallazgo (+ escritura de coordenadas, C-3) | HU-01 |
| 5 | 05 | Martin | Reclasificar con historial inmutable | HU-06, PR-06 |
| 6 | 06 | Martin | Consultar historial y comparar estados | HU-07 |
| 7 | 07 | Martin | Coordenadas protegidas + registro de accesos | HU-08, PR-07, PR-14 |
| 8 | 08 | Sergio | Consulta pública con caché (Adapter) + portal | HU-04, PR-16, PR-17 |
| 9 | 09a | Martin | API key y límite por consumidor | RF-13, PR-11 |
| 10 | 09b | Sergio | Inventario externo /v1 y portabilidad | HU-09, HU-10, PR-12 |
| 11 | 10a | Daniel | Fórmula del índice de presión | HU-11 |
| 12 | 10b | Daniel | Informe mensual en el proceso batch | HU-11 |
| 13 | 12 | Martin | Pruebas de seguridad de arquitectura y de base de datos | PR-08, PR-09 |
| 14 | 11 | Sergio | Disponibilidad: caída de instancia y promoción de réplica | HU-05, PR-04, PR-05, PR-10 |
| 15 | 13 | Sergio | Pruebas de carga y siembra de 1 millón | PR-02, PR-03, PR-13 |
| 16 | 14 | Daniel | Grupos taxonómicos y fichas por grupo | HU-02 (criterios 1 y 4) |
| 17 | 15 | Daniel | Equipos de campo | HU-03 |
| 18 | 16 | Sergio | Caché al día y monitoreo de salud | T-07, T-11 |
| 19 | 17 | Karen | Consola científica: base y pantalla del analista | HU-11 |
| 20 | 18 | Karen | Pantallas de registro, hallazgos, fichas, grupos y equipos | HU-01, HU-02, HU-03 |
| 21 | 19 | Karen | Pantallas de historial y reclasificación + datos de demostración | HU-06, HU-07 |
| 22 | 20 | Paula | Mapa de coordenadas, accesos y consumidores | HU-08, HU-10 |
| 23 | 21 | Paula | Auditoría: datos de ubicación protegidos y alta de consumidores (+ ajuste visual del mapa) | RNF-06, RF-12, HU-08 |
| 24 | 22 | Paula | Auditoría: secretos obligatorios y límites en Nginx | T-14, T-17 |
| 25 | 23 | Paula | Auditoría: bloqueo por intentos fallidos, errores en español, comparación | T-17, HU-07 |
| 26 | 24 | Paula | Auditoría: ubicación dentro de Colombia, fecha desde 1900 | RF-01 |
| 27 | 25 | Karen | Cierre: documentos finales y últimos ajustes | Todos los RNF |
| 28 | 26 | Paula | Auditoría de alineación: cada rol ve las pantallas de sus casos de uso; equipos solo del investigador | CU-01 a CU-14, HU-03 |
| 29 | 27 | Karen | Auditoría de alineación: documentos (C4 con tecnologías, decisiones P-xx, SOLID, plan) | Sesiones 14, 18 y 26 |

(El 12 va antes que el 11: así se armó y se verificó. Si alguno ya subió el 11 primero, no pasa nada: el 12 no toca los archivos del 11.)

**Cuántos paquetes tiene cada uno:** Sergio 6 · Martin 6 · Daniel 6 · Paula 6 · Karen 5. Martin, Daniel y Sergio llevan las olas 1 a 8; Paula y Karen, las olas 9 a 15.

Desde el paquete 22, `docker compose up` exige el archivo `.env`: corran una vez `./scripts/preparar-env.sh`.
