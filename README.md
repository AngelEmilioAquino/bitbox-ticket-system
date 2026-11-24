# BITBOX Ticket System

Sistema de gestión de entradas para eventos, formado por dos microservicios en un monorepo Maven:

| Servicio | Puerto | Stack | Responsabilidad |
|---|---|---|---|
| **inventory-service** | 8081 | Spring Boot MVC · Spring Data JPA · PostgreSQL · Liquibase · Spring Security (JWT) | Autenticación, catálogo de eventos, inventario, ventas y estadísticas |
| **ticket-gateway-service** | 8080 | Spring Boot WebFlux · Reactor · WebClient | Puerta de entrada no bloqueante: proxy de auth, compra con propagación del token y disponibilidad en tiempo real por SSE. **No accede a la base de datos.** |

```
Cliente ──▶ ticket-gateway-service :8080 (WebFlux/Netty) ──WebClient──▶ inventory-service :8081 (MVC/Tomcat) ──▶ PostgreSQL
              ├─ /api/auth/register|login  (proxy Mono)
              ├─ /api/tickets/buy           (propaga Authorization: Bearer)
              └─ /api/tickets/stream/{id}   (Flux + SSE cada 2 s)
```

Versiones: **Java 17**, **Spring Boot 4.1.3**, springdoc-openapi 3.1.1, PostgreSQL 16.

---

## Requisitos previos

| Para… | Necesitas |
|---|---|
| Levantar la solución completa | **Docker** con Docker Compose v2 (`docker compose`) |
| Compilar y ejecutar los tests | **JDK 17** y **Docker** (los tests usan Testcontainers con PostgreSQL real). Maven **no** es necesario: se incluye el wrapper `mvnw` |

Puertos libres: `8080`, `8081` y `5432` (el de PostgreSQL se puede cambiar con `POSTGRES_PORT`).

---

## Levantar el proyecto

### Desarrollo (un solo comando, sin configuración)

```bash
docker compose up -d --build --wait
```

Compose carga automáticamente `docker-compose.override.yml` (overlay de desarrollo). Ese comando construye las imágenes, arranca PostgreSQL, aplica las migraciones de Liquibase y espera a que los tres contenedores estén *healthy*. inventory corre con el perfil Spring `dev`, que trae credenciales y un secreto JWT de desarrollo, públicos y predecibles a propósito. Por comodidad se publican también `8081` (inventory) y `5432` (PostgreSQL).

Parar y borrar los datos: `docker compose down -v`.

### Producción

```bash
cp .env.example .env    # rellenar JWT_SECRET y DB_PASSWORD (nunca se versiona: .env está en .gitignore)
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build --wait
```

- **Solo se expone el gateway (8080).** inventory-service y PostgreSQL están en una red Docker `internal`, sin puertos publicados ni salida a Internet.
- `JWT_SECRET` y `DB_PASSWORD` son **obligatorios**: si faltan, Compose aborta. Además inventory se niega a arrancar si falta el secreto o si tiene menos de 32 bytes.
- No se activa el perfil `dev`, así que no hay ningún valor por defecto inseguro.

| Variable | Desarrollo | Producción | Descripción |
|---|---|---|---|
| `JWT_SECRET` | valor *dev-only* del perfil `dev` | **obligatoria** | Clave HS256 (≥ 32 bytes): `openssl rand -base64 48` |
| `DB_PASSWORD` | `bitbox` | **obligatoria** | Contraseña de PostgreSQL |
| `DB_NAME` / `DB_USERNAME` | `bitbox_inventory` / `bitbox` | opcionales | Base de datos |
| `JWT_EXPIRATION` | `1h` | opcional | Validez del token |
| `POSTGRES_PORT` | `5432` | (no se publica) | Puerto de depuración en el host |

### Ejecutar sin Docker (desarrollo)

Con un PostgreSQL local en `localhost:5432` (o solo `docker compose up -d postgres`):

```bash
./mvnw -pl inventory-service spring-boot:run -Dspring-boot.run.profiles=dev
./mvnw -pl ticket-gateway-service spring-boot:run
```

Sin el perfil `dev`, inventory exige `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` y `JWT_SECRET`.

### DevContainer

`.devcontainer/devcontainer.json` ofrece un entorno con JDK 17 y Docker-in-Docker (necesario para Testcontainers y Compose). Ábrelo con *Dev Containers: Reopen in Container* en VS Code o en GitHub Codespaces.

---

## Colección de Postman

En la raíz: [`BITBOX-Ticket-System.postman_collection.json`](BITBOX-Ticket-System.postman_collection.json).

1. Postman → **Import** → selecciona el archivo.
2. Con el entorno levantado, ejecuta las carpetas en orden, o la colección completa con **Run collection**. Desmarca la carpeta 6 (stream SSE infinito).

- El **login guarda el token** automáticamente y **crear evento guarda `eventId`**; no hay que copiar nada a mano.
- Cada ejecución registra un email nuevo, así que la colección se puede repetir.
- Cada petición incluye tests: flujo feliz (registro → login → evento → 3 compras → `409 SOLD_OUT` → estadísticas) y errores esperados (401, 400, 404, 409).
- Las URLs son variables de la colección: `gatewayUrl` (8080) e `inventoryUrl` (8081).

## Documentación OpenAPI (Swagger UI)

| Servicio | Swagger UI | OpenAPI JSON |
|---|---|---|
| gateway | http://localhost:8080/swagger-ui.html | http://localhost:8080/v3/api-docs |
| inventory | http://localhost:8081/swagger-ui.html | http://localhost:8081/v3/api-docs |

Para probar los endpoints protegidos: ejecuta `login`, copia `accessToken`, pulsa **Authorize** e introduce el token (sin el prefijo `Bearer`).

---

## Endpoints

### ticket-gateway-service (8080)

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/api/auth/register` | Público | Proxy reactivo (`Mono`) hacia inventory |
| POST | `/api/auth/login` | Público | Proxy reactivo (`Mono`) hacia inventory |
| POST | `/api/tickets/buy` | Bearer | Body `{"eventId": n}`. Propaga el token a inventory |
| GET | `/api/tickets/stream/{eventId}` | Público | `text/event-stream`: un evento `availability` cada 2 s |

### inventory-service (8081)

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/api/auth/register` | Público | Registra un usuario (contraseña en BCrypt) |
| POST | `/api/auth/login` | Público | Devuelve un JWT firmado (HS256) |
| POST | `/api/events` | JWT | Crea un evento con su inventario inicial |
| GET | `/api/events/{id}` | Público | Evento y disponibilidad actual (lo consume el stream SSE) |
| POST | `/api/events/{id}/purchase` | JWT | Compra 1 entrada. El comprador se obtiene del JWT. **409** si no hay stock |
| GET | `/api/events/stats` | JWT | Estadísticas por evento (consulta SQL nativa) |

Los errores siguen el formato RFC 7807 (`application/problem+json`) con una propiedad `code` estable:

| Status | `code` | Cuándo |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Body inválido (incluye `errors` por campo). Solo se valida **después** de autenticar |
| 401 | `AUTHENTICATION_REQUIRED` / `MISSING_BEARER_TOKEN` | Sin token (inventory / gateway). Incluye `WWW-Authenticate: Bearer` |
| 401 | `INVALID_TOKEN` / `INVALID_CREDENTIALS` | Token inválido, manipulado o expirado / login fallido |
| 403 | `ACCESS_DENIED` | Autenticado pero sin permiso. El enunciado no define roles, así que hoy ningún endpoint lo produce, pero el formato está preparado |
| 404 | `EVENT_NOT_FOUND` | Evento inexistente |
| 409 | `EMAIL_ALREADY_REGISTERED` | Email duplicado |
| 409 | `SOLD_OUT` | **Falta real de inventario** (definitivo) |
| 503 | `PURCHASE_TEMPORARILY_UNAVAILABLE` | **Fallo transitorio** de concurrencia en la BD (timeout de lock, deadlock). No se vendió nada; reintentable (`Retry-After: 1`) |
| 503 / 504 | `INVENTORY_UNAVAILABLE` / `INVENTORY_TIMEOUT` | El gateway no puede contactar con inventory o este no responde a tiempo |

---

## Ejemplos de uso (curl)

> En Windows usa Git Bash o WSL, o sustituye `curl` por `curl.exe` y adapta las comillas en PowerShell.

```bash
GW=http://localhost:8080
INV=http://localhost:8081

# 1. Registro (a través del gateway)
curl -s -X POST $GW/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"ana@bitbox.com","password":"S3cret-pass"}'

# 2. Login → token
TOKEN=$(curl -s -X POST $GW/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ana@bitbox.com","password":"S3cret-pass"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

# 3. Crear un evento (inventory-service)
curl -s -X POST $INV/api/events -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Concierto BITBOX","eventDate":"2027-03-15T20:00:00","totalTickets":100}'

# 4. Disponibilidad en tiempo real (déjalo abierto en otra terminal)
curl -N $GW/api/tickets/stream/1
#   event:availability
#   data:{"eventId":1,"eventName":"Concierto BITBOX","totalTickets":100,"availableTickets":100,"timestamp":"..."}

# 5. Comprar una entrada (a través del gateway)
curl -s -X POST $GW/api/tickets/buy -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"eventId":1}'

# 6. Estadísticas
curl -s $INV/api/events/stats -H "Authorization: Bearer $TOKEN"
#   [{"eventId":1,"eventName":"Concierto BITBOX","totalTickets":100,"availableTickets":99,
#     "ticketsSold":1,"occupancyPercentage":1.00,"uniqueBuyers":1,"salesRank":1}]

# 7. Prueba de sobreventa: 100 compras simultáneas (con un evento de 10 entradas → 10 × 201 y 90 × 409)
seq 100 | xargs -P 100 -I{} curl -s -o /dev/null -w '%{http_code}\n' -X POST $GW/api/tickets/buy \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"eventId":2}' | sort | uniq -c
```

---

## Concurrencia: cómo se evita la sobreventa

La compra (`PurchaseService`) es **una sola transacción** con un **`UPDATE` atómico condicional**:

```sql
UPDATE events SET available_tickets = available_tickets - 1, version = version + 1
 WHERE id = ? AND available_tickets > 0
```

1. **Sin sobreventa ni reintentos.** PostgreSQL toma un lock de fila para el `UPDATE`. Los compradores simultáneos del mismo evento esperan en ese lock y, cuando la transacción anterior confirma, **reevalúan el `WHERE` sobre el valor ya confirmado**. Al llegar a 0, la condición deja de cumplirse.
2. **Transaccionalidad.** El descuento de stock y el `INSERT` en `ticket_sales` están en el mismo `@Transactional`: o se confirman ambos o ninguno (lo verifica `PurchaseAtomicityTest`).
3. **Falta real vs. fallo transitorio.**
   - 0 filas actualizadas con el evento existente → `409 SOLD_OUT`, que es definitivo.
   - Un fallo de concurrencia de la BD (por ejemplo, `lock_timeout` de 3 s o un deadlock) → `503 PURCHASE_TEMPORARILY_UNAVAILABLE` con `Retry-After`. En ese caso no se vendió nada.
4. **`@Version` se conserva.** El `UPDATE` incrementa `version`, así que el optimistic locking sigue protegiendo cualquier otra modificación del evento hecha a través de JPA.
5. **`CHECK (available_tickets BETWEEN 0 AND total_tickets)`** en PostgreSQL como última red de seguridad.

**Por qué no optimistic locking con reintentos** (la primera versión): con mucha contención sobre una sola fila, casi todas las transacciones fallan la comprobación de versión. Hacen falta reintentos y, con un presupuesto acotado, compradores legítimos pueden fallar aunque quede stock. Aumentar los reintentos solo desplaza el problema. El `UPDATE` condicional convierte la contención en espera, no en fallo.

Otras alternativas descartadas: `SELECT … FOR UPDATE` (correcto, pero es una ida y vuelta extra con el mismo efecto), `synchronized` (falla con varias instancias) y un lock distribuido en Redis (infraestructura innecesaria: la BD ya es la fuente de verdad).

---

## Consulta SQL de estadísticas

`GET /api/events/stats` se resuelve con una única consulta nativa (`EventStatsRepository`):

- un **CTE** (`WITH sales_per_event`) que hace `JOIN` entre `ticket_sales` y `users` y agrega `COUNT(ts.id)` (vendidas) y `COUNT(DISTINCT u.id)` (compradores únicos) por evento;
- un `LEFT JOIN` desde `events`, para que los eventos sin ventas también aparezcan (con 0);
- `ROUND(vendidas * 100.0 / NULLIF(total, 0), 2)` para el porcentaje de ocupación;
- la **función de ventana** `RANK() OVER (ORDER BY vendidas DESC)` para el ranking (los empates comparten posición).

---

## Tests

```bash
./mvnw verify
```

Requiere Docker en marcha: los tests de inventory usan **PostgreSQL real con Testcontainers**, no H2, para que los bloqueos, las migraciones y el SQL (CTE y funciones de ventana) se comporten como en producción.

| Módulo | Test | Qué demuestra |
|---|---|---|
| inventory | `PurchaseConcurrencyTest` | 50 compradores para 10 entradas venden exactamente 10. La última entrada se vende una sola vez entre 20 compradores. Con N = stock se venden todas. 200 compradores para 100 entradas → 100 ventas y `version = 100`. Dos eventos en paralelo no interfieren. Con 30 peticiones HTTP concurrentes reales (JWT incluido) hay exactamente 5 × 201 y 25 × 409. En todos los casos se comprueba `available_tickets = 0` y que `COUNT(ticket_sales)` es igual al stock |
| inventory | `PurchaseAtomicityTest` | Si falla el registro de la venta, el descuento de stock se revierte |
| inventory | `PurchaseServiceTest` | Clasificación de desenlaces: agotado (409), evento inexistente (404) y fallo transitorio (503) |
| inventory | `GlobalExceptionHandlerTest`, `SecurityResponsesTest`, `JwtConfigTest` | Traducción centralizada a HTTP. Orden 401 → 400 y formato de 401/403. El servicio no arranca sin `JWT_SECRET` ni con uno débil |
| inventory | `PurchaseIntegrationTest`, `EventIntegrationTest`, `AuthIntegrationTest` | Flujos principales, hash BCrypt, validación de JWT (sin token o manipulado → 401), errores 400/404/409, y que `/stats` no queda público por la regla de `/{id}` |
| inventory | `StatsIntegrationTest` | Valores de la consulta nativa: ocupación, compradores únicos, empates en el ranking y eventos sin ventas |
| gateway | `InventoryClientTest` | Propagación del header `Authorization`, relay de status y body de error, 503 si inventory está caído y 504 por timeout |
| gateway | `AvailabilityStreamServiceTest` | Emisión cada 2 s (tiempo virtual), errores transitorios que no rompen el stream y 404 antes de empezar |
| gateway | `GatewayApiTest` | Stack WebFlux completo: proxy de auth, buy sin token (no llama a inventory), relay de 409, SSE `text/event-stream` y documento OpenAPI |

---

## CI/CD

`.github/workflows/ci.yml` se ejecuta en cada push y pull request:

1. **Build & test**: `./mvnw -B verify` con JDK 17, que compila ambos módulos y ejecuta todos los tests, incluidos los de concurrencia con Testcontainers.
2. **Docker**:
   - Valida que la configuración de producción rechaza arrancar sin secretos y que solo publica el puerto 8080.
   - Construye las dos imágenes.
   - Levanta el stack de desarrollo (`up --wait`) y ejecuta un smoke test a través del gateway: registro, login, compra 201 y compra agotada 409.

---

## Estructura del monorepo

```
├── pom.xml                       # aggregator (parent: spring-boot-starter-parent)
├── mvnw, .mvn/                   # Maven Wrapper
├── docker-compose.yml            # base: solo el gateway es público, red interna para inventory + postgres
├── docker-compose.override.yml   # desarrollo (se carga solo): perfil dev, puertos 8081/5432
├── docker-compose.prod.yml       # producción: secretos obligatorios
├── .env.example                  # plantilla de variables de producción (.env nunca se versiona)
├── .github/workflows/ci.yml
├── .devcontainer/devcontainer.json
├── inventory-service/
│   ├── Dockerfile
│   └── src/main/java/com/bitbox/inventory/
│       ├── auth/     registro, login, DTOs
│       ├── user/     entidad User
│       ├── event/    entidad Event (@Version), creación y consulta
│       ├── sale/     entidad TicketSale, compra concurrente (UPDATE atómico condicional)
│       ├── stats/    StatsService + consulta SQL nativa en el repositorio
│       ├── security/ emisión y validación de JWT
│       ├── config/   seguridad, OpenAPI, reloj
│       └── common/   BusinessException y traducción centralizada a HTTP (RFC 7807)
│   └── src/main/resources/db/changelog/  migraciones Liquibase
└── ticket-gateway-service/
    ├── Dockerfile
    └── src/main/java/com/bitbox/gateway/
        ├── auth/    proxy de registro y login
        ├── ticket/  compra y stream SSE
        ├── client/  InventoryClient (WebClient) y DTOs del contrato
        ├── config/  WebClient (timeouts), OpenAPI, propiedades
        └── error/   manejo de errores reactivos
```

## Decisiones de diseño

- **Separación bloqueante/reactivo**: JPA solo existe en inventory. El gateway solo incluye WebFlux, porque añadir `spring-boot-starter-web(mvc)` lo haría arrancar en modo servlet.
- **El JWT lo valida inventory**, el único que posee la clave. El gateway comprueba que haya un `Bearer`, lo rechaza pronto si falta y lo propaga sin modificarlo.
- **Sin módulo compartido de DTOs**: el contrato entre servicios es HTTP/JSON, y cada servicio declara sus propios records para poder desplegarse de forma independiente.
- **El esquema lo gestiona Liquibase** (`ddl-auto=validate`), con índices explícitos en las FKs y restricciones `CHECK`.
- **`open-in-view=false`**: nunca se serializan entidades. Todas las respuestas son records.
- **Endpoint derivado `GET /api/events/{id}` público**: el stream SSE es público y necesita consultar el stock en inventory, y `/stats` exige JWT.
- **El SSE consulta inventory en cada suscripción** (C clientes generan C/2 peticiones por segundo). Es suficiente para el alcance de la prueba. Compartir el polling por evento o publicar cambios (Redis o RabbitMQ, opcionales en el enunciado) es la evolución natural.
