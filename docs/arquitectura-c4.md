# Arquitectura tal como quedó (C4)

Los diagramas C4 del Entregable 2 no nombraban tecnologías, porque estaban pendientes (P-07). La Sesión 14 pide que el **nivel 2 (contenedores)** muestre la tecnología de cada contenedor y cómo se comunica con los demás. Estos son los diagramas del sistema construido, escritos como código (Mermaid), como recomienda Simon Brown: viven en el repositorio y se revisan en cada pull request. GitHub los dibuja solo.

Lo que cambió frente al Entregable 2 está en [`trazabilidad.md`](trazabilidad.md), secciones 7, 8 y 11.

## Nivel 1 · Contexto

```mermaid
flowchart LR
    visitante["<b>Visitante</b><br/>público y prensa"]
    cientificos["<b>Personal científico</b><br/>investigador, administrador científico,<br/>director, auditor y alto rango"]
    instituto["<b>Personal del Instituto</b><br/>analista y administrador<br/>de la plataforma"]
    biohub[["<b>BioHub</b><br/>registro y vigilancia<br/>de biodiversidad"]]
    externos["<b>Sistemas de universidades y ONG</b><br/>(también organismos internacionales)<br/>[sistema externo]"]

    visitante -- "consulta fichas públicas<br/>(solo el departamento)" --> biohub
    cientificos -- "registra, define fichas,<br/>reclasifica, audita,<br/>consulta coordenadas" --> biohub
    instituto -- "índices de presión,<br/>límites por consumidor" --> biohub
    externos -- "consulta el inventario<br/>API /v1 con API key" --> biohub
```

## Nivel 2 · Contenedores

Un solo artefacto (`biohub.jar`) corre en tres roles: científico, público y batch. Solo Nginx publica un puerto hacia afuera (RNF-07).

```mermaid
flowchart TB
    navegador["<b>Navegador</b><br/>portal público y consola científica<br/>[HTML + JavaScript]"]
    externo["<b>Sistema de una universidad u ONG</b><br/>[sistema externo]"]

    subgraph expuesta["Zona expuesta"]
        nginx["<b>Punto de entrada</b><br/>[Nginx 1.27]<br/>sirve el portal y la consola · enruta · límite de tasa"]
    end

    subgraph interna["Zona interna (red de Docker, sin puertos hacia afuera)"]
        subgraph artefacto["Artefacto BioHub: biohub.jar · Java 21 · Spring Boot 3.3"]
            cientifico["<b>Grupo científico</b> × 2<br/>[perfil cientifico]<br/>registro, fichas, historial,<br/>autorización de coordenadas"]
            publico["<b>Grupo público</b> × 2<br/>[perfil publico]<br/>fichas públicas y API /v1"]
            batch["<b>Proceso de reportes</b> × 1<br/>[perfil batch]<br/>índice de presión mensual"]
        end
        primaria[("<b>Base primaria</b><br/>[PostgreSQL 16]<br/>única que recibe escrituras")]
        replicaPublica[("<b>Réplica pública</b><br/>[PostgreSQL 16]")]
        replicaAnalitica[("<b>Réplica de analítica</b><br/>[PostgreSQL 16]")]
        redis[("<b>Caché y contadores</b><br/>[Redis 7]")]
    end

    navegador -- "HTTP / JSON" --> nginx
    externo -- "HTTP / JSON · X-API-Key" --> nginx
    nginx -- "/api/cientifico/**<br/>HTTP" --> cientifico
    nginx -- "/api/publico/** y /api/v1/**<br/>HTTP" --> publico
    cientifico -- "JDBC · lee y escribe" --> primaria
    cientifico -- "RESP · invalida la caché,<br/>cuenta intentos de inicio de sesión" --> redis
    publico -- "JDBC · solo lectura" --> replicaPublica
    publico -- "RESP · caché de fichas,<br/>límite por API key" --> redis
    batch -- "JDBC · lee los hallazgos" --> replicaAnalitica
    batch -- "JDBC · guarda el informe" --> primaria
    primaria -. "replicación en streaming (asíncrona)" .-> replicaPublica
    primaria -. "replicación en streaming (asíncrona)" .-> replicaAnalitica
```

Qué decisión sostiene cada línea:

| Línea | Requisito |
| --- | --- |
| Solo Nginx está en la zona expuesta | RNF-07 (PR-10) |
| El grupo público nunca toca la primaria | RNF-02, RNF-03 y RNF-04 (PR-02, PR-03) |
| Dos instancias científicas detrás de Nginx | RNF-04 (PR-04) |
| El batch calcula sobre su propia réplica | RNF-09 (PR-13) |
| Nginx llama a los grupos por nombre de servicio, no por dirección | RNF-08 (PR-12) |
| Cada rol entra a la base con su propio usuario | RNF-06 (PR-09) |

## Despliegue

| Nodo (contenedor de Docker) | Cuántos | Imagen |
| --- | --- | --- |
| `nginx` | 1 | `nginx:1.27-alpine` |
| `app-cientifico` | 2 | `biohub` (el mismo `biohub.jar`) |
| `app-publico` | 2 | `biohub` |
| `batch` | 1 | `biohub` |
| `postgres-primaria`, `postgres-replica-publica`, `postgres-replica-analitica` | 1 cada una | `postgres:16` |
| `redis` | 1 | `redis:7-alpine` |

Todo se define en `docker-compose.yml` (infraestructura como código, S26) y se levanta con `docker compose up --build --wait`.
