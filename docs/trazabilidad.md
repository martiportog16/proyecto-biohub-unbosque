# Trazabilidad · del Entregable 2 al código

Este documento dice **dónde quedó cada cosa prometida en el Entregable 2**: en qué clase o archivo está, qué prueba lo demuestra y qué resultado dio. Sirve para responder en la sustentación "¿y esto dónde está?".

Todas las rutas de Java son relativas a `src/main/java/co/edu/unbosque/biohub/`. Las pruebas están en `src/test/java/...`, las de navegador en `pruebas-ui/pruebas/` y los scripts en `scripts/`.

## 1. Estilo y capas

| Entregable 2 | Dónde quedó | Cómo se comprueba |
| --- | --- | --- |
| Monolito por capas: un artefacto, tres roles (científico, público, batch) | Un solo `biohub.jar` con los perfiles `cientifico`, `publico` y `batch` (`src/main/resources/application-*.yml`) | `docker compose up` levanta 2 + 2 + 1 instancias de la misma imagen |
| Capas cerradas: presentación → aplicación → dominio, y persistencia (ADR-02) | Paquetes `presentacion`, `aplicacion`, `dominio`, `persistencia` y `configuracion` | `CapasTest` (ArchUnit) en cada `mvnw verify` |
| Zona expuesta y zona interna (RNF-07) | Solo Nginx publica un puerto (`docker-compose.yml`) | `scripts/verificar-red.sh` (PR-10) |
| Credenciales distintas por rol (T-14) | `app_cientifico`, `app_publico` y `app_batch` (`V1`) | `PermisosBaseDeDatosIT` (PR-09) |
| Interfaz científica, portal público y API externa | `infra/nginx/html/consola/`, `infra/nginx/html/index.html` y `/api/v1` | 49 pruebas en navegador (`scripts/pruebas-ui.sh`) y `scripts/suite-externa.sh` |

## 2. Los 7 módulos

| Módulo (diapositivas) | Backend | Pantalla de la consola | Pruebas principales |
| --- | --- | --- | --- |
| Registro y fichas | `aplicacion/hallazgos/ServicioRegistroHallazgos`, `aplicacion/fichas/ServicioFichas`, `ServicioGruposTaxonomicos`, `dominio/fichas/*` | Registrar hallazgo, Hallazgos, Especies y fichas, Grupos taxonómicos | `FichasIT`, `HallazgosIT`, `GruposTaxonomicosIT`, `ValidadoresTest`, `registro.spec.js`, `fichas.spec.js` |
| Equipos de campo | `aplicacion/equipos/ServicioEquipos`, `dominio/equipos/EquipoCampo` | Equipos de campo | `EquipoCampoTest`, `ServicioEquiposTest`, `EquiposIT`, `equipos.spec.js` |
| Consulta pública | `aplicacion/consultapublica/LectorPublico`, `InvalidadorCachePublica`, `persistencia/cache/AdaptadorRedis` | Portal público (`/`) | `ConsultaPublicaIT`, `FichaNuevaVisibleIT`, `LectorPublicoTest` |
| Reclasificación y auditoría | `aplicacion/historial/ServicioReclasificacion`, `ServicioHistorial`, `dominio/historial/*` | Historial y reclasificación | `ReclasificacionIT`, `HistorialIT`, `historial.spec.js` |
| Datos protegidos y autorización | `aplicacion/autorizacion/AutorizacionCoordenadas`, `dominio/coordenadas/ReglaAccesoCoordenadas`, `persistencia/coordenadas/*` | Mapa de coordenadas, Accesos a coordenadas | `CoordenadasIT`, `ReglaAccesoCoordenadasTest`, `SeguridadArquitecturaTest`, `coordenadas.spec.js` |
| Integración externa | `presentacion/integracion/ApiExternaController`, `FiltroApiKey`, `aplicacion/integracion/*` | Consumidores externos | `InventarioIT`, `LimitePeticionesIT`, `ConsumidoresIT`, `consumidores.spec.js`, `scripts/suite-externa.sh` |
| Analítica | `aplicacion/analitica/ProcesoInformes`, `GeneradorInforme`, `dominio/analitica/CalculadoraIndicePresion` | Índices de presión | `BatchIT`, `InformesIT`, `CalculadoraIndicePresionTest`, `analista.spec.js` |

## 3. Las secuencias UML

| Secuencia | Flujo | Clases que la siguen | Prueba |
| --- | --- | --- | --- |
| 1 | Registrar un hallazgo | `HallazgosController` → `ServicioRegistroHallazgos` → `FichaTaxonomica.validar` → `RepositorioHallazgos` + `EscrituraCoordenadas` (C-3) → `InvalidadorCachePublica` (paso 18) | `HallazgosIT`, `ServicioRegistroHallazgosTest` |
| 2 | Consultar una ficha pública | `ConsultaPublicaController` → `LectorPublico` → `AlmacenCache` (Redis) o, si no está, `RepositorioConsultaPublica` (réplica) | `ConsultaPublicaIT`, `LectorPublicoTest` |
| 3 | Reclasificar | `HistorialController` → `ServicioReclasificacion` → `Reclasificacion` (dominio) → `RepositorioHistorial` → `InvalidadorCachePublica` (pasos 20 y 21) | `ReclasificacionIT`, `ServicioReclasificacionTest` |
| 3b | Historial y comparación | `HistorialController` → `ServicioHistorial` → `HistorialHallazgo.especieEn(fecha)` | `HistorialIT`, `HistorialHallazgoTest` |
| 4 | Coordenadas protegidas | `CoordenadasController` → `AutorizacionCoordenadas` → `ReglaAccesoCoordenadas` → `RegistroAccesos` → `LecturaCoordenadas` (solo si se permite) | `CoordenadasIT` (matriz 8 × 2), `AutorizacionCoordenadasTest` |
| 5 | Informe mensual | `ProgramacionBatchConfig` → `ProcesoInformes` → `LecturaAnalitica` (réplica de analítica) → `CalculadoraIndicePresion` → `RepositorioInformes` (C-1) | `BatchIT`, `GeneradorInformeTest` |
| 6 | Asignar equipo y consultar responsable | `EquiposController` → `ServicioEquipos` → `EquipoCampo.crear` → `RepositorioUsuarios` (C-2) → `RepositorioEquipos` | `EquiposIT`, `ServicioEquiposTest` |
| 7 | Definir o retirar atributos | `FichasController` → `ServicioFichas` → `FichaTaxonomica.conAtributo` / `sinAtributo` → nueva versión | `FichasIT` (10 modificaciones) |
| 8 | Inventario por el punto de entrada | Nginx → `FiltroApiKey` → `ApiExternaController` → `ServicioInventario` → `AtributosPublicos` | `InventarioIT` |
| 9 | Configurar y aplicar el límite | `ConsumidoresController` → `ServicioConsumidores` (configurar); `FiltroApiKey` → `ServicioLimitePeticiones` → `ContadorPeticionesRedis` (aplicar) | `ConsumidoresIT`, `LimitePeticionesIT` |

## 4. Las 24 tácticas

| Táctica | Estado | Dónde | Evidencia |
| --- | --- | --- | --- |
| T-01 Fichas como metadatos | Implementada | `definicion_ficha.atributos` (JSONB), `dominio/fichas/FichaTaxonomica` | PR-01: 10 modificaciones, 0 cambios de código, 0 registros afectados |
| T-02 Validación genérica (Strategy) | Implementada | `dominio/fichas/validacion/CatalogoValidadores` + un validador por tipo | PR-15: `ValidadoresTest` |
| T-03 Caché de fichas públicas (Adapter) | Implementada | `LectorPublico`, `AlmacenCache`, `AdaptadorRedis`; vida de 3 s | PR-02: p95 de 1,16 ms (PC del equipo) |
| T-04 Réplica de lectura pública | Implementada | `postgres-replica-publica`, perfil `publico` | PR-02, PR-05 (el portal sigue con la primaria caída) |
| T-05 Escalado horizontal del grupo público | Implementada | 2 instancias `app-publico` detrás de Nginx (`deploy.replicas`) | PR-02 |
| T-06 Capacidad base dimensionada | Medida con un supuesto | 300 peticiones/s = 60 × 5 (P-12) | PR-02: 0 % de caídas; CPU de la primaria 5,8 % |
| T-07 Invalidación de caché | Implementada (la precarga no hizo falta) | `InvalidadorCachePublica`: después del commit y de nuevo a los 500 ms | PR-16: hallazgo visible en el portal en unos 35 ms |
| T-08 Índices para la ficha pública | Implementada | `idx_hallazgo_especie`, `idx_hallazgo_departamento_fecha` (V3_1) | PR-02, PR-13 con 1 millón de hallazgos |
| T-09 Partición de recursos por rol | Implementada | Instancias, pools y bases separadas por grupo; el batch tiene su propia réplica | PR-03: registro p95 de 7,5 ms durante el pico |
| T-10 Redundancia activa del grupo científico | Implementada | 2 instancias `app-cientifico`, `max_fails=1`, reintento acotado | PR-04: 0 errores al apagar una instancia |
| T-11 Monitoreo de salud | Implementada | Healthchecks en `docker-compose.yml`, `/actuator/health/readiness`, `scripts/estado-salud.sh` | `SaludIT`; réplica caída detectada en 36 s |
| T-12 Conmutación de la primaria | Implementada, **manual** (P-21) | `scripts/promover-replica.sh` | PR-05: 30 s, 0 hallazgos perdidos |
| T-13 Auditoría de solo inserción | Implementada | Triggers y `REVOKE` en `V4` | PR-06: `ReclasificacionIT` (también por SQL directo) |
| T-14 Privilegios mínimos | Implementada | Usuarios de base por rol (`V1` y siguientes) | PR-09: `PermisosBaseDeDatosIT` |
| T-15 Autorización en un único punto | Implementada | `AutorizacionCoordenadas` | PR-07, PR-08, PR-14 |
| T-16 Coordenadas separadas | Implementada | Esquema `protegido`; `EscrituraCoordenadas` y `LecturaCoordenadas` separadas (C-3) | PR-08 (ArchUnit), PR-09 |
| T-17 Autenticación | Implementada | JWT HS256 de 60 min, BCrypt (`AutenticacionController`, `JwtConfig`) | `AutenticacionIT` |
| T-18 Intermediario único hacia el exterior | Implementada | Nginx | PR-10 |
| T-19 Límite por consumidor | Implementada, en la aplicación (P-10) | `FiltroApiKey`, `ServicioLimitePeticiones`, Redis | PR-11: 429 con `Retry-After`; cambio en caliente en 143 ms |
| T-20 Direcciones internas por registro de servicios | Implementada con el DNS de Docker | `nginx.conf` usa nombres de servicio (`app-publico`), no direcciones IP | PR-12: `scripts/prueba-portabilidad.sh` |
| T-21 Contrato externo versionado `/v1` | Implementada | `ApiExternaController` (`/api/v1/...`) | PR-11, PR-12 |
| T-22 Proceso separado para los reportes | Implementada | Perfil `batch`, contenedor propio | PR-13 |
| T-23 Fuente de datos propia para analítica | Implementada | `postgres-replica-analitica`; `LecturaAnalitica` | PR-13: el registro no sube (−4,9 ms) |
| T-24 Ejecución en horario de baja demanda | Implementada | `ProgramacionBatchConfig`: día 1 de cada mes a las 2:00 a. m., hora de Bogotá (`application-batch.yml`) | Configuración; el proceso que se ejecuta lo prueba `ProcesoInformesTest` |

## 5. Las 17 pruebas planeadas (PR)

| PR | Requisito | Prueba o script | Resultado |
| --- | --- | --- | --- |
| PR-01 | RNF-01 | `FichasIT.diezModificacionesDeAtributosSinCambiarCodigo` | 10 cambios, 0 despliegues, 0 registros afectados |
| PR-02 | RNF-02, RNF-03 | `scripts/carga.sh pico-publico` | p95 de 1,16 ms (PC del equipo) y 6,8 ms (entorno de pruebas); 0 % de caídas |
| PR-03 | RNF-04 | El mismo script, con el registro en paralelo | p95 de 7,5 ms, 0 errores |
| PR-04 | RNF-04 | `scripts/prueba-caida-instancia.sh` | 0 errores; la base coincide exactamente con lo registrado |
| PR-05 | RNF-04 | `scripts/promover-replica.sh` | 30 s, 0 perdidos; disponibilidad estimada de 99,96 % |
| PR-06 | RNF-05 | `ReclasificacionIT` | Editar o borrar el historial falla por la API y por SQL |
| PR-07 | RNF-06 | `CoordenadasIT`, `DatosDeUbicacionIT` | 8 roles × 2 tipos de especie: 0 accesos indebidos, tampoco por la ruta GPS ni los archivos |
| PR-08 | RNF-06 | `SeguridadArquitecturaTest`, `LasReglasSiAtrapanTest` | 5 reglas, y se comprueba que atrapan una clase tramposa |
| PR-09 | RNF-06 | `PermisosBaseDeDatosIT` | `app_publico` no puede entrar al esquema protegido |
| PR-10 | RNF-07 | `scripts/verificar-red.sh` | Solo Nginx publica un puerto |
| PR-11 | RF-13 | `LimitePeticionesIT`, `scripts/suite-externa.sh` | 429 con `Retry-After`; el otro consumidor sigue con 200 |
| PR-12 | RNF-08 | `scripts/prueba-portabilidad.sh` | La misma suite pasa antes y después del cambio de direcciones |
| PR-13 | RNF-09 | `scripts/prueba-pr13.sh` | +5 ms (entorno de pruebas) y −4,9 ms (PC del equipo); el límite es +1 000 ms |
| PR-14 | RNF-06 | `ReglaAccesoCoordenadasTest` | Cada rol, aislado |
| PR-15 | RNF-01 | `ValidadoresTest` | Partición de equivalencia y valores límite por tipo |
| PR-16 | RNF-03 | `FichaNuevaVisibleIT` | Ficha nueva, hallazgo nuevo y reclasificación visibles en ≤ 5 s |
| PR-17 | RNF-03 | `LectorPublicoTest` con `AlmacenCacheEnMemoria` | El lector funciona sin Redis |
| — | RNF-10 | `docs/prueba-usabilidad.md` | **La hace el equipo con 10 personas** |

**Cobertura (S21).** JaCoCo mide las pruebas unitarias y de integración juntas en cada `mvnw verify` y en el CI: **95,6 % de las líneas y 85,1 % de las ramas** (reporte en `target/site/jacoco/index.html`). La cobertura dice qué código se ejecutó, no que esté bien probado: por eso cada prueba revisa un resultado esperado.

**Dobles de prueba (S20 y S21).** *Stub*: reloj fijo (`ServicioRegistroHallazgosTest`, `ServicioReclasificacionTest`) y emisor de tokens (`ServicioAutenticacionTest`). *Spy*: la escritura de coordenadas que anota lo que recibe (`ServicioRegistroHallazgosTest`) y la ficha que cuenta cuántas veces se le pide (`ServicioInventarioTest`). *Fake*: repositorios en memoria (`EquiposEnMemoria`) y `AlmacenCacheEnMemoria` (PR-17). No se usaron *mocks* con Mockito: en ningún caso la interacción misma era el comportamiento que había que probar (S21).

## 6. Patrones

En total se usan **dos patrones de diseño**: Strategy y Adapter (caché). Lo demás de la tabla son decisiones de diseño que conviene no presentar como patrones.

| Patrón | Estado | Dónde |
| --- | --- | --- |
| Strategy, para validar cada tipo de dato | Adoptado | `ValidadorDeTipo` y sus 6 implementaciones; `CatalogoValidadores` |
| Adapter, para la caché | Adoptado | `AlmacenCache` (lo que la aplicación necesita) y `AdaptadorRedis` (cómo lo da Redis) |
| Adapter, para la identidad en el control de acceso | Descartado | Como dice el documento de decisiones: el rol sale de la base (`RepositorioUsuarios`), no de un sistema externo que haya que adaptar. `LecturaCoordenadas` y `EscrituraCoordenadas` son **interfaces de repositorio** (la corrección C-3), no un Adapter |
| Decorator, para la caché | Descartado | Se documentó en el Entregable 2: Adapter bastaba |
| Builder, para el informe | Descartado | El informe es un `record` inmutable (`InformeMensual`) que se arma de una vez |
| Factory Method | No se usa | `EquipoCampo.crear` y `FichaTaxonomica.inicial` son métodos de creación con validación, no el patrón |
| Segregación de interfaces (SOLID) | Aplicado | `RepositorioConsumidores` / `AdministracionConsumidores`; `RepositorioUsuarios` / `DirectorioPersonal` |

**Qué principio SOLID aplica cada patrón (S18 y S20):** Strategy aplica **OCP**: un tipo de dato nuevo es una clase nueva y `FichaTaxonomica` no se toca. Adapter aplica **DIP**: la aplicación depende de `AlmacenCache`, una interfaz suya, y no de Redis.

**SOLID en el código (S18):**

| Principio | Dónde se ve |
| --- | --- |
| S · Responsabilidad única | Un servicio por caso de uso (`ServicioRegistroHallazgos`, `ServicioReclasificacion`, `ServicioHistorial`…). La regla de coordenadas (`ReglaAccesoCoordenadas`, lógica pura) está separada del componente que lee y registra (`AutorizacionCoordenadas`) |
| O · Abierto/cerrado | `ValidadorDeTipo` (Strategy): agregar un tipo de dato no cambia la validación de la ficha |
| L · Sustitución de Liskov | Cualquier implementación de `AlmacenCache` sirve igual: con `AdaptadorRedis` en producción y con `AlmacenCacheEnMemoria` en las pruebas, `LectorPublico` se comporta igual (PR-17) |
| I · Segregación de interfaces | `LecturaCoordenadas` / `EscrituraCoordenadas` (C-3): el registro solo puede escribir, la autorización solo puede leer. También `RepositorioUsuarios` / `DirectorioPersonal` y `RepositorioConsumidores` / `AdministracionConsumidores` |
| D · Inversión de dependencias | La aplicación depende de interfaces de persistencia (`RepositorioHallazgos`, `RepositorioHistorial`…); las clases `Jdbc` las implementan. Por eso los servicios se prueban con dobles, sin base de datos |

## 7. Lo que cambió respecto al Entregable 2

| Qué | Entregable 2 | Implementado | Motivo |
| --- | --- | --- | --- |
| Conmutación de la primaria | Pendiente (P-21) | Manual, con un script | Patroni no cabía en el tiempo; el MTTR depende de la detección humana |
| Ubicación pública | "Región o departamento" | Departamento | Es una sola lista clara (33 valores) y cumple RF-09 |
| Fichas por grupo | Herencia en el diagrama de clases | Copia como versión nueva a cada especie que hereda | Mantiene la garantía de PR-01 sin tocar el registro |
| Mapa (RF-10) | Sin especificar | Leaflet con los límites de los departamentos guardados en el servidor, sin mapas de terceros | Pedir imágenes a terceros revelaría qué zona mira el alto rango |
| Precarga de caché (T-07) | Pendiente | No se hizo | Con caché de 3 s y doble borrado, la ficha se ve en milisegundos; precargar no mejoraba nada medible |
| Pantallas por rol | Cada actor con sus casos de uso (diagrama de casos de uso) | Igual, desde el paquete 26 (ver sección 10) | La primera versión de la consola mostraba las pantallas científicas a los 5 roles científicos; la auditoría de alineación lo detectó |
| Responsable de un equipo | "Investigador responsable" (HU-03, criterio 1) | Solo un investigador activo, desde el paquete 26 | Antes se aceptaba cualquier rol científico |
| Migración de datos (WBS 1.4.10) | Fuera de la entrega (P-22) | No se construyó | Ningún RF ni HU la pide y el Enunciado dice "no inventen alcance". La API de registro ya valida cada hallazgo: una migración futura solo tendría que llamarla |

## 8. Nombres que cambiaron entre los diagramas y el código

| En el Entregable 2 | En el código | Por qué |
| --- | --- | --- |
| `ILecturaCoordenadas`, `IEscrituraCoordenadas` | `LecturaCoordenadas`, `EscrituraCoordenadas` | En Java no se usa el prefijo `I` para las interfaces; la implementación lleva el sufijo `Jdbc` |
| `ConsumidorExterno.configurarLimite` | `Consumidor.con(...)` + `ServicioConsumidores.cambiar` | El dominio no guarda; el servicio valida y la persistencia guarda (capas cerradas) |
| Repositorio de informes (C-1), de usuarios (C-2) | `RepositorioInformes`, `RepositorioUsuarios` | Iguales |
| `PoliticaAccesoCoordenadas.puedeVerCoordenadas` | `ReglaAccesoCoordenadas` (la regla) + `AutorizacionCoordenadas` (el único componente que decide, lee y registra) | La regla es lógica pura y se prueba sola (PR-14) |
| `HistorialReclasificacion` (agregar, entradas, estadoEn) | `HistorialHallazgo` (agregar, entradas, especieEn) | Mismo papel; el "estado" es la especie (P-29) |
| `Hallazgo.reclasificar`, `Hallazgo.estadoEn` | `HistorialHallazgo.agregar`, `HistorialHallazgo.especieEn` | El historial es el dueño de los cambios; el hallazgo guardado no se modifica |
| `Hallazgo.crear(...)` | `Hallazgo.erroresDeDatos` + `FichaTaxonomica.validar` | Se reúnen todos los errores antes de guardar (HU-01, criterio 2) |
| `CoordenadaProtegida` | `UbicacionExacta` y `Coordenada` (esquema `protegido`) | — |
| `ProyeccionPublica`, `FichaPublica` | `LectorPublico`, `VistasConsultaPublica`, `AtributosPublicos` | — |
| `ConsumidorExterno` | `Consumidor` | — |
| `Rol.PERSONAL_ALTO_RANGO`, `Usuario.roles[1..*]` | `Rol.ALTO_RANGO`, un rol por usuario | P-25 |
| `EquipoCampo.asignarResponsable` | `EquipoCampo.reasignarA` + `AsignacionEquipo` (solo inserción) | Queda el historial de responsables |
| `InformeMensual.agregarIndice` | `InformeMensual` es un `record` inmutable | Builder descartado |
| Región **y** departamento en `Hallazgo` | Solo departamento | Ver sección 7 |

Clases que no estaban en el diagrama: `MembresiaGrupo` (fichas por grupo), `AccesoCoordenadas` (registro de accesos, P-18), `AsignacionEquipo`, `SolicitudInforme` (el analista pide un mes, P-11) y `DatosDeUbicacion` (sección 9).

## 9. Lo que encontró la auditoría independiente y cómo se resolvió

Antes de la entrega, un revisor que no participó en la construcción revisó el proyecto contra el Entregable 2, como lo haría un evaluador.

| Hallazgo | Gravedad | Qué se hizo |
| --- | --- | --- |
| La **ruta GPS** (y los archivos) de un hallazgo de especie en peligro salía en `GET /hallazgos` para cualquier rol, sin pasar por la autorización | Alta | `DatosDeUbicacion` separa esos valores. Solo salen por `AutorizacionCoordenadas`, que revisa el rol y deja registro. Prueba: `DatosDeUbicacionIT` (7 roles) |
| Sin `.env`, el secreto de los tokens era uno **conocido** (estaba en el repositorio) | Alta | `docker compose` ya no arranca sin `JWT_SECRETO`; `scripts/preparar-env.sh` genera claves aleatorias |
| `pagina=2147483647` daba 500 | Media | Página entre 0 y 100 000, con 422 si no |
| Sin protección contra fuerza bruta en el inicio de sesión | Media | Nginx: 5 intentos por segundo por IP. Aplicación: un correo se bloquea 15 min después de 10 claves equivocadas (429) |
| Se aceptaban coordenadas de París y fechas del año 1500 | Media | La coordenada debe caer en el recuadro de Colombia y la fecha ser de 1900 o después |
| No se podía dar de alta una universidad u ONG sin SQL | Media | `POST /api/cientifico/consumidores`: genera la API key y la muestra una sola vez |
| Errores 400, 405 y 415 en inglés y con otro formato | Baja | Todos con `{error, detalles}` y en español |
| La comparación por fecha decía "no cambió" aunque hubo cambios de ida y vuelta | Baja | La pantalla cuenta los cambios aunque la especie de hoy coincida con la de esa fecha |
| Nginx mostraba su versión | Baja | `server_tokens off` |

**Riesgos que se aceptan y se dicen** (no se cambiaron por tiempo o porque el costo no lo justifica):
- **Usuarios y API keys de demostración en las migraciones (V2, V6), con la clave publicada en la pantalla de inicio.** Sirven para la sustentación. En producción se desactivarían con una migración y se crearían usuarios reales.
- **El rol del token vale hasta que vence (60 min).** Un usuario desactivado conserva sus permisos ese tiempo, **excepto en coordenadas**: ahí la autorización consulta el rol en la base en cada petición.
- **Todo va por HTTP, sin TLS.** En producción, Nginx terminaría HTTPS con un certificado; no cambia nada de la aplicación.
- **Swagger es público.** Solo documenta la API: todas las operaciones científicas siguen pidiendo token.
- **El portal público no tiene CSP**, porque usa estilos y código dentro de la página. Escapa todo el texto que muestra y no tiene datos sensibles.
- **El departamento declarado no se cruza con la coordenada.** Se valida que el punto esté en Colombia, no en qué departamento cae exactamente.

## 10. Pantallas por rol: cada actor con sus casos de uso

El diagrama de casos de uso del Entregable 2 une a cada actor solo con sus casos. La consola hace lo mismo: el menú de cada rol muestra solo las pantallas de sus casos de uso, más la lista de hallazgos para quien necesita encontrar uno. **El permiso real lo decide el backend** (403), así que ocultar una pantalla es comodidad, no seguridad. La prueba `consola-base.spec.js` revisa el menú exacto de los 7 roles.

| Rol | Pantallas | Casos de uso |
| --- | --- | --- |
| Investigador | Registrar hallazgo, Hallazgos, Equipos de campo | CU-01, CU-03, CU-04 |
| Administrador científico | Especies y fichas, Grupos taxonómicos | CU-02 |
| Director científico | Hallazgos, Historial y reclasificación | CU-06 (y reconstruir la historia, Acta) |
| Auditor científico | Hallazgos, Historial y reclasificación (solo lectura), Accesos a coordenadas | CU-07, P-18 |
| Personal de alto rango | Hallazgos, Mapa de coordenadas (y "Ver la ubicación exacta" en cada hallazgo) | CU-08 |
| Analista | Índices de presión | CU-13, CU-14 |
| Administrador de la plataforma | Consumidores externos | CU-12 |

**El mapa y los enlaces.** Si una persona del personal científico abre un enlace al mapa (por ejemplo, uno que le compartieron), la consola no decide: le pregunta a `AutorizacionCoordenadas`. Para una especie en peligro responde 403 con el motivo, y el intento queda en el registro del auditor. Para una especie fuera de peligro lo permite (P-24). Así se ve en la demostración cómo actúa la autorización centralizada (RNF-06) sin herramientas técnicas.

## 11. Las decisiones pendientes del Entregable 2 y cómo se resolvieron

| P | Decisión pendiente | Cómo quedó |
| --- | --- | --- |
| P-01 | Fecha de la entrega | Martes 24 de noviembre (Sesión 26); el 25 es para los grupos que no alcancen a pasar |
| P-02 | Uno o dos grupos de instancias | Dos grupos (público y científico) y un proceso batch, con el mismo artefacto |
| P-03 | Fuente de la analítica y destino de los informes | Réplica dedicada para la analítica (`postgres-replica-analitica`); el informe se guarda en la primaria por una conexión aparte |
| P-04 | Registro de servicios estático o dinámico | Estático: nombres de servicio en `nginx.conf`, resueltos por el DNS de Docker (PR-12) |
| P-05 | Organismos internacionales como actores | No son un actor propio: consumen los datos por la API `/v1`, como las universidades y ONG |
| P-06 | Historias comprometidas | Las 8 comprometidas y además HU-01, HU-03 y HU-10: 11 de 11 |
| P-07 | Tecnologías | Java 21 y Spring Boot 3.3, PostgreSQL 16, Redis 7, Nginx 1.27, Docker Compose; pruebas con JUnit 5, Testcontainers, REST Assured, ArchUnit, Playwright y k6 (ver `docs/arquitectura-c4.md`) |
| P-08 | Corregir el SRS (v3.1) | No se publicó una v3.1. Las dos diferencias conocidas (columna del backlog de HU-06 a HU-08) están explicadas en el Entregable 2 |
| P-09 | Formato de las fichas | JSONB versionado en `definicion_ficha`; retirar un atributo lo marca inactivo |
| P-10 | Dónde se aplica el límite por consumidor | En la aplicación (`FiltroApiKey` + Redis); Nginx solo pone un límite global al portal |
| P-11 | Forma de ejecución del reporte | Programado el día 1 de cada mes a las 2:00 a. m., y el analista también puede pedir un mes (202 y el batch lo atiende) |
| P-12 | Tráfico habitual | Supuesto de 5 peticiones/s, así que el pico es de 300 peticiones/s |
| P-13 | Estadístico de RNF-03 y RNF-09 | p95 |
| P-14 | Desfase de fichas recién anunciadas | 5 s como máximo; medido en unos 35 ms (PR-16) |
| P-15 | Nombres de las capas | `presentacion`, `aplicacion`, `dominio`, `persistencia` |
| P-16 | Qué es una "caída" | Una respuesta 5xx o más lenta que 5 s; ningún minuto puede pasar del 1 % |
| P-17 | Proteger el historial del administrador de la base | Triggers que impiden UPDATE y DELETE, y `REVOKE` a los usuarios de la aplicación (V4, PR-06) |
| P-18 | Registrar los accesos a coordenadas | Sí: cada consulta, permitida o negada, con su motivo (`AccesoCoordenadas`) |
| P-19 | Punto de entrada compartido | Un solo Nginx, con límite global para las rutas públicas (400 peticiones/s) y su propio grupo de instancias para la ruta científica |
| P-20 | Umbrales de suficiencia del monolito | CPU de la primaria por debajo del 80 % durante el pico; medido: 7,4 % de un núcleo |
| P-21 | Conmutación de la primaria | Manual, con `scripts/promover-replica.sh` (30 s, 0 hallazgos perdidos) |
| P-22 | Migración de datos (WBS 1.4.10) | Fuera de la entrega: ningún RF ni HU la pide (ver sección 7) |
| P-24 | Coordenadas de especies fuera de peligro | Las ve el personal científico; las de especies en peligro, solo el alto rango |
| P-25 | Varios roles por usuario | Uno solo |
| P-26 | Fórmula del índice y "zona" | Zona = departamento. Índice = max(0, (B − E) / B), donde E es el número de especies vistas en el mes y B el promedio de los hasta 3 meses anteriores |
| P-27 | Permisos fuera de las coordenadas | Con `@PreAuthorize` en cada endpoint; el componente central queda solo para las coordenadas (RF-11) |
| P-28 | Autenticación | JWT HS256 de 60 minutos y claves con BCrypt; bloqueo tras 10 intentos fallidos |
| P-29 | El "estado" de un hallazgo | La especie que tenía asignada en ese momento |
| P-30 | Datos para consumidores externos | Solo atributos de texto, número, fecha y lista; nunca rutas GPS, archivos ni coordenadas |
| P-31 | Dónde se guardan los consumidores | En la base (tabla `consumidor`), con la huella SHA-256 de la API key |
| P-32 | Tipos de dato iniciales | Seis: texto, número, fecha, lista, ruta GPS y archivo |
