# BioHub

Plataforma de registro y vigilancia de biodiversidad.
Proyecto final de **Ingeniería de Software 2**, Universidad El Bosque, 2026.

**Equipo:** Daniel Delgadillo · Martin Porto · Paula Buitrago · Sergio Mahecha · Karen Morera

## Arquitectura en una línea

Un **monolito en capas** (un solo código, un solo artefacto) que se despliega en **tres roles**:

| Rol | Qué hace | Lee de | Escribe en |
| --- | --- | --- | --- |
| `cientifico` (×2) | Registra hallazgos, reclasifica, consulta coordenadas | Primaria | Primaria (único que escribe) |
| `publico` (×2) | Portal público y API externa `/api/v1` | Redis + réplica pública | — |
| `batch` | Informe mensual de presión sobre ecosistemas | Réplica de analítica | Primaria (solo el informe) |

**Nginx** es el único punto de entrada. Ningún otro contenedor publica puertos hacia afuera.

Capas (cerradas, verificadas con ArchUnit):

```
presentacion  ->  aplicacion  ->  persistencia  ->  PostgreSQL
                       |               |
                       +-->  dominio <-+
```

Las decisiones y su justificación están en `docs/` y en el documento *BioHub — Decisiones de implementación*.

| Documento | Para qué |
| --- | --- |
| [`docs/trazabilidad.md`](docs/trazabilidad.md) | Dónde quedó cada cosa del Entregable 2: módulos, secuencias, tácticas, pruebas y patrones |
| [`docs/modelo-de-datos.md`](docs/modelo-de-datos.md) | Diagrama entidad-relación de la base **real** y los permisos de cada usuario |
| [`docs/guia-disponibilidad.md`](docs/guia-disponibilidad.md) | Caídas, promoción de réplica, red y monitoreo de salud |
| [`docs/guia-pruebas-carga.md`](docs/guia-pruebas-carga.md) | Pruebas de carga y sus resultados |
| [`docs/prueba-usabilidad.md`](docs/prueba-usabilidad.md) | Guion y hoja de registro de la prueba con 10 personas (RNF-10) |

## Cómo levantarlo

Requisitos: Docker Desktop (o Docker + Docker Compose).

```bash
./scripts/preparar-env.sh   # una vez: crea .env con claves aleatorias (sin él no arranca)
docker compose up --build
```

Luego abrir:

| Qué | Dirección |
| --- | --- |
| Portal público | http://localhost:8080 |
| **Consola científica** (personal del Instituto) | http://localhost:8080/consola/ |
| Estado del grupo público | http://localhost:8080/api/publico/estado |
| Estado del grupo científico | http://localhost:8080/api/cientifico/estado |
| Documentación de la API (Swagger) | http://localhost:8080/swagger-ui.html |

Para la sustentación, con datos que tienen historia (hallazgos de meses pasados, uno reclasificado dos veces, equipos que cambiaron de manos):

```bash
./scripts/sembrar-demo.sh
```

Y el día de la sustentación, para que no aparezcan los datos que dejan las pruebas (**borra todo**):

```bash
./scripts/preparar-demo.sh
```

`docker compose up` espera a que cada parte esté **sana** antes de arrancar la siguiente (T-11): primero la base y Redis, luego el grupo científico (que corre las migraciones), luego el público y el batch, y al final Nginx. Para ver la salud de todo:

```bash
./scripts/estado-salud.sh        # contenedores, atraso de las réplicas y servicios por Nginx
```

Para apagar todo: `docker compose down`. Para borrar también los datos: `docker compose down -v`.

## Cómo correr las pruebas

**No hace falta instalar Maven:** el proyecto trae el Maven Wrapper, que lo descarga solo la primera vez. Solo se necesitan **Java 21** y **Docker**.

| Dónde | Comando |
| --- | --- |
| Windows (CMD o PowerShell) | `mvnw.cmd verify` |
| Git Bash, macOS o Linux | `./mvnw verify` |

En los ejemplos de abajo, `mvn` se puede cambiar por cualquiera de los dos.

Las pruebas de integración usan **PostgreSQL real**, nunca H2 (S21).

```bash
# Opción A: con Docker abierto, Testcontainers levanta PostgreSQL solo
mvn verify

# Opción B: contra un PostgreSQL ya instalado
export BIOHUB_TEST_DB_URL=jdbc:postgresql://localhost:5432/biohub
export BIOHUB_TEST_DB_USUARIO=biohub
export BIOHUB_TEST_DB_CLAVE=biohub
mvn verify
```

- `*Test.java` = pruebas unitarias y de arquitectura (`mvn test`).
- `*IT.java` = pruebas de integración (`mvn verify`).
- Reporte de cobertura: `target/site/jacoco/index.html`.

### Pruebas en navegador (con `docker compose up -d`; no hay que instalar Node)

```bash
./scripts/pruebas-ui.sh               # abre un Chrome real y usa la consola con cada rol
```

### Pruebas de disponibilidad y red (con `docker compose up -d`)

```bash
./scripts/prueba-caida-instancia.sh   # PR-04: se apaga una instancia y el registro no falla
./scripts/verificar-red.sh            # PR-10: desde afuera solo responde Nginx
./scripts/promover-replica.sh         # PR-05: si se cae la primaria (¡apaga la primaria actual!)
./scripts/estado-salud.sh             # T-11: salud de cada contenedor y atraso de las réplicas
```

Qué hace cada una, los resultados medidos y qué hacer si algo se cae: [`docs/guia-disponibilidad.md`](docs/guia-disponibilidad.md).

### Pruebas de carga (con `docker compose up -d`; no hay que instalar k6)

```bash
./scripts/carga.sh pico-publico       # PR-02 y PR-03: 300 peticiones/s al portal mientras se registra (5 min)
./scripts/sembrar-millon.sh           # 1 millón de hallazgos
./scripts/prueba-pr13.sh              # PR-13: el batch no frena el registro
```

Resultados y cómo leerlos: [`docs/guia-pruebas-carga.md`](docs/guia-pruebas-carga.md).

## Forma de trabajo en Git

1. Una rama corta por tarea: `feature/hu-06-reclasificar`.
2. Commits pequeños con la historia en el mensaje: `HU-06: rechaza reclasificar sin motivo`.
3. Pull request revisado por otro integrante antes de unir a `main`.
4. El CI (GitHub Actions) debe quedar en verde.
5. Integrar a `main` al menos una vez al día.

## Estructura

```
src/main/java/co/edu/unbosque/biohub/
  presentacion/    controladores REST (lo que recibe HTTP)
  aplicacion/      casos de uso (coordinan dominio y persistencia)
  dominio/         reglas del negocio (sin base de datos)
  persistencia/    repositorios (acceso a PostgreSQL y Redis)
  configuracion/   seguridad, perfiles y beans transversales
src/main/resources/db/migration/   migraciones Flyway (V1, V2, ...)
infra/nginx/                       punto de entrada y portal estático
infra/postgres/                    replicación primaria -> réplicas
docs/                              decisiones y resultados de pruebas
```

### Migraciones reservadas por módulo

Para que nadie choque con el número de versión de Flyway:

| Versión | Módulo | Responsable |
| --- | --- | --- |
| V1 | Base: usuarios de BD y permisos | Sergio |
| V2 | Usuarios y roles | Martin |
| V3 | Especies y fichas flexibles | Daniel |
| V3_1 | Hallazgos y coordenadas | Daniel |
| V4 | Historial de reclasificaciones | Martin |
| V5 | Accesos a coordenadas | Martin |
| V6 | Consumidores externos | Martin |
| V7 | Informes mensuales | Daniel |
| V8 | Grupos taxonómicos y fichas por grupo | Daniel |
| V9 | Equipos de campo | Daniel |
