# Modelo de datos real · BioHub

Este diagrama **se generó a partir de la base de datos funcionando** (`information_schema` de PostgreSQL 16, con las migraciones V1 a V9 aplicadas). No es un dibujo a mano: así es la base hoy.

GitHub dibuja el diagrama solo. En otro visor, copie el bloque en https://mermaid.live.

```mermaid
erDiagram
    PROTEGIDO_ACCESO_COORDENADAS {
        bigint id PK
        bigint usuario_id FK
        text rol
        bigint especie_id FK
        bigint hallazgo_id FK "opcional"
        boolean en_peligro
        boolean permitido
        text motivo
        timestamptz ocurrido_en
    }
    PROTEGIDO_COORDENADA {
        bigint hallazgo_id PK
        numeric latitud
        numeric longitud
        int precision_m "opcional"
        timestamptz registrada_en
    }
    ASIGNACION_EQUIPO {
        bigint id PK
        bigint equipo_id FK
        bigint responsable_id FK
        bigint asignado_por FK
        timestamptz asignado_en
        text motivo
    }
    CONSUMIDOR {
        bigint id PK
        varchar nombre
        char api_key_hash
        int limite_por_minuto
        boolean activo
        timestamptz creado_en
    }
    DEFINICION_FICHA {
        bigint id PK
        bigint especie_id FK
        int version
        jsonb atributos
        timestamptz creada_en
        bigint creada_por FK "opcional"
        bigint grupo_origen_id FK "opcional"
        int version_grupo_origen FK "opcional"
    }
    DEFINICION_FICHA_GRUPO {
        bigint id PK
        bigint grupo_id FK
        int version
        jsonb atributos
        bigint creada_por FK
        timestamptz creada_en
    }
    EQUIPO_CAMPO {
        bigint id PK
        varchar codigo
        varchar tipo
        text descripcion "opcional"
        bigint responsable_id FK
        bigint registrado_por FK
        timestamptz registrado_en
    }
    ESPECIE {
        bigint id PK
        varchar nombre_cientifico
        varchar nombre_comun
        boolean en_peligro
        timestamptz creada_en
        bigint grupo_id FK "opcional"
        boolean hereda_ficha
    }
    GRUPO_TAXONOMICO {
        bigint id PK
        varchar nombre
        text descripcion "opcional"
    }
    HALLAZGO {
        bigint id PK
        bigint especie_id FK
        bigint ficha_especie_id FK
        int version_ficha FK
        jsonb valores
        date fecha_observacion
        varchar departamento
        bigint investigador_id FK
        timestamptz registrado_en
    }
    INDICE_PRESION {
        bigint informe_id FK
        varchar departamento
        numeric valor "opcional"
        int especies_mes
        numeric base "opcional"
    }
    INFORME_MENSUAL {
        bigint id PK
        date periodo
        timestamptz generado_en
        int total_departamentos
    }
    RECLASIFICACION {
        bigint id PK
        bigint hallazgo_id FK
        bigint especie_anterior_id FK
        bigint especie_nueva_id FK
        text motivo
        bigint autor_id FK
        timestamptz realizada_en
    }
    SOLICITUD_INFORME {
        bigint id PK
        date periodo
        bigint solicitado_por FK
        timestamptz solicitado_en
        timestamptz atendida_en "opcional"
    }
    USUARIO {
        bigint id PK
        varchar correo
        varchar nombre
        varchar clave_hash
        varchar rol
        boolean activo
        timestamptz creado_en
    }
    ESPECIE ||--o{ PROTEGIDO_ACCESO_COORDENADAS : "especie_id"
    HALLAZGO ||--o{ PROTEGIDO_ACCESO_COORDENADAS : "hallazgo_id"
    USUARIO ||--o{ PROTEGIDO_ACCESO_COORDENADAS : "usuario_id"
    HALLAZGO ||--o| PROTEGIDO_COORDENADA : "hallazgo_id"
    EQUIPO_CAMPO ||--o{ ASIGNACION_EQUIPO : "equipo_id"
    USUARIO ||--o{ ASIGNACION_EQUIPO : "asignado_por, responsable_id"
    DEFINICION_FICHA_GRUPO ||--o{ DEFINICION_FICHA : "grupo_origen_id, version_grupo_origen"
    ESPECIE ||--o{ DEFINICION_FICHA : "especie_id"
    USUARIO ||--o{ DEFINICION_FICHA : "creada_por"
    GRUPO_TAXONOMICO ||--o{ DEFINICION_FICHA_GRUPO : "grupo_id"
    USUARIO ||--o{ DEFINICION_FICHA_GRUPO : "creada_por"
    USUARIO ||--o{ EQUIPO_CAMPO : "registrado_por, responsable_id"
    GRUPO_TAXONOMICO ||--o{ ESPECIE : "grupo_id"
    DEFINICION_FICHA ||--o{ HALLAZGO : "ficha_especie_id, version_ficha"
    ESPECIE ||--o{ HALLAZGO : "especie_id"
    USUARIO ||--o{ HALLAZGO : "investigador_id"
    INFORME_MENSUAL ||--o{ INDICE_PRESION : "informe_id"
    ESPECIE ||--o{ RECLASIFICACION : "especie_anterior_id, especie_nueva_id"
    HALLAZGO ||--o{ RECLASIFICACION : "hallazgo_id"
    USUARIO ||--o{ RECLASIFICACION : "autor_id"
    USUARIO ||--o{ SOLICITUD_INFORME : "solicitado_por"
```

## Las 15 tablas

| Tabla | Para qué | Migración | Regla importante |
| --- | --- | --- | --- |
| `usuario` | Personal del Instituto y su rol | V2 | La clave se guarda con BCrypt, nunca en texto |
| `especie` | Catálogo de especies | V3, V8 | `en_peligro` decide quién ve sus coordenadas; `grupo_id` y `hereda_ficha` (V8) |
| `definicion_ficha` | Cada **versión** de la ficha de una especie (atributos en JSONB) | V3, V8 | No se edita ni se borra: un cambio es una versión nueva (RNF-01) |
| `grupo_taxonomico` | Grupos como Primates o Aves | V8 | Nombre único |
| `definicion_ficha_grupo` | Cada versión de la ficha de un grupo | V8 | Al cambiar, se copia a las especies que heredan (`grupo_origen_id`, `version_grupo_origen`) |
| `hallazgo` | Cada registro de campo | V3_1 | Apunta a la versión exacta de la ficha con que se validó; solo guarda el **departamento** |
| `protegido.coordenada` | Latitud y longitud exactas | V3_1 | Otro **esquema**: `app_publico` y `app_batch` no pueden ni entrar (PR-09) |
| `protegido.acceso_coordenadas` | Cada consulta de coordenadas, permitida o negada | V5 | Solo se agrega; el auditor la consulta |
| `reclasificacion` | Historial de cambios de especie | V4 | Triggers y permisos impiden `UPDATE`, `DELETE` y `TRUNCATE` (RNF-05) |
| `consumidor` | Universidades y ONG de la API `/v1` | V6 | Guarda la **huella** SHA-256 de la clave, no la clave |
| `informe_mensual` y `indice_presion` | Informes del batch | V7 | Un informe por mes; nunca uno vacío |
| `solicitud_informe` | Pedidos del analista al batch | V7 | Estados PENDIENTE, ATENDIDA, SIN_DATOS |
| `equipo_campo` | Drones, cámaras, sensores | V9 | `responsable_id` es **NOT NULL** (HU-03, criterio 4); solo se puede cambiar el responsable |
| `asignacion_equipo` | Historial de responsables | V9 | Solo se agrega |

## Diferencias con el diagrama del Entregable 2

| Entregable 2 | Base real | Por qué |
| --- | --- | --- |
| Ubicación pública: "región o departamento" (RF-09) | Solo `departamento` (33 valores, los mismos en la validación y en el mapa) | Región es ambigua (natural, administrativa…). El departamento cumple RF-09 y es lo que entiende un visitante |
| Ficha por especie **o** grupo | `definicion_ficha` y `definicion_ficha_grupo`, con copia a las especies que heredan | Así el hallazgo sigue apuntando a una versión fija de la ficha de **su** especie (decisión del paquete 14) |
| Un solo rol por usuario (P-25) | `usuario.rol` | Igual que en el Entregable 2 |
| Repositorios de usuarios e informes (C-1, C-2) y escritura y lectura de coordenadas separadas (C-3) | `RepositorioInformes`, `RepositorioUsuarios`, `EscrituraCoordenadas` y `LecturaCoordenadas` | Se aprobaron las tres correcciones propuestas |
| Base primaria con dos réplicas | 1 primaria + réplica pública + réplica de analítica, con replicación asíncrona | Igual que en el Entregable 2 (consistencia eventual, medida en milisegundos) |

## Usuarios de base de datos (privilegios mínimos, T-14)

| Usuario | Lo usa | Puede |
| --- | --- | --- |
| dueño (`DB_DUENO`) | Solo Flyway, al arrancar el grupo científico | Todo. Su clave va solo en `.env` |
| `app_cientifico` | Grupo científico | Leer y escribir lo suyo; **no** puede cambiar ni borrar coordenadas ni el historial, ni borrar hallazgos o fichas |
| `app_publico` | Grupo público (réplica pública) | Solo leer, y nada del esquema `protegido` ni la tabla de usuarios |
| `app_batch` | Batch (réplica de analítica) | Solo leer hallazgos e informes, y escribir informes en la primaria por un pool aparte |

Lo comprueba `PermisosBaseDeDatosIT` (PR-09), conectándose como cada usuario.
