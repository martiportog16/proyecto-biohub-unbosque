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

## Cómo levantarlo

Requisitos: Docker Desktop (o Docker + Docker Compose).

```bash
cp .env.example .env        # y cambiar las claves si se quiere
docker compose up --build
```

Luego abrir:

| Qué | Dirección |
| --- | --- |
| Portal público | http://localhost:8080 |
| Estado del grupo público | http://localhost:8080/api/publico/estado |
| Estado del grupo científico | http://localhost:8080/api/cientifico/estado |
| Documentación de la API (Swagger) | http://localhost:8080/swagger-ui.html |

Para apagar todo: `docker compose down`. Para borrar también los datos: `docker compose down -v`.

## Cómo correr las pruebas

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
