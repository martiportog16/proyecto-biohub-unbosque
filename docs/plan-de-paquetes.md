# Plan de paquetes de trabajo

Cada paquete es una tarea pequeña con dueño. Cada dueño:

1. Crea su rama: `git checkout -b <rama>`.
2. Copia los archivos del paquete en el repositorio.
3. **Lo corre y lo entiende** (`mvn verify` y, si aplica, `docker compose up --build`).
4. Hace los commits indicados, desde su propia cuenta.
5. Abre un pull request; otro integrante lo revisa y lo une a `main`.

En la sustentación el presentador se elige al azar: todos deben poder explicar
cualquier parte, no solo la suya.

Entrega final: **martes 24 de noviembre**.

| # | Paquete | Dueño | Historias / pruebas | Depende de | Fecha objetivo |
| --- | --- | --- | --- | --- | --- |
| 01 | Base: capas, perfiles, Docker, Nginx, réplicas, CI | Sergio | PR-08 (capas) | — | dom 15 |
| 02 | Usuarios, login con JWT y roles | Martin | P-28, P-25 | 01 | lun 16 |
| 03 | Fichas flexibles con Strategy | Daniel | HU-02, PR-01, PR-15 | 01 (y 02 para la API) | lun 16 |
| 04 | Registrar hallazgo (+ escritura de coordenadas, C-3) | Daniel | HU-01 | 02, 03 | mar 17 |
| 05 | Reclasificar con historial inmutable | Martin | HU-06, PR-06 | 04 | mar 17 |
| 06 | Consultar historial y comparar estados | Martin | HU-07 | 05 | mié 18 |
| 07 | Coordenadas protegidas + registro de accesos | Paula | HU-08, PR-07, PR-14 | 04 | jue 19 |
| 08 | Consulta pública con caché (Adapter) + portal | Sergio | HU-04, PR-16, PR-17 | 03, 04 | jue 19 |
| 09a | API key y límite por consumidor (sin hallazgos) | Paula | RF-13, PR-11 | 02 | lun 16 |
| 09b | Inventario externo /v1 y prueba de portabilidad | Paula | HU-09, PR-12 | 04, 09a | vie 20 |
| 10a | Fórmula del índice de presión y tablas de informes | Karen | HU-11 | 02 | lun 16 |
| 10b | Informe mensual en el proceso batch | Karen | HU-11 | 04, 10a | vie 20 |
| 11 | Disponibilidad: caída de instancia y promoción de réplica | Sergio | HU-05, PR-04, PR-05, PR-10 | 08 | vie 20 |
| 12 | Pruebas de seguridad de arquitectura y credenciales | Paula | PR-08, PR-09 | 07 | sáb 21 |
| 13 | Pruebas de carga con k6 y siembra de datos | Karen | PR-02, PR-03, PR-13 | 08, 10 | sáb 21 |
| 14 | Resultados de pruebas y tabla "propuesta frente a real" | Todos | — | todo | lun 23 |

Martes 17 en clase: integrar lo que haya y consultar al profesor P-02 y P-22.
Lunes 23: congelar el código y ensayar la demo dos veces.
