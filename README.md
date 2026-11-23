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

```bash
docker compose up -d --build --wait
```

Con eso se construyen las imágenes, se arranca PostgreSQL, se aplican las migraciones de Liquibase y se espera a que los tres contenedores estén *healthy*. No hace falta ninguna configuración previa: todas las variables tienen valores por defecto de desarrollo.

Para personalizarlas:

```bash
cp .env.example .env   # editar JWT_SECRET, credenciales de BD, etc.
```

| Variable | Por defecto | Descripción |
|---|---|---|
| `JWT_SECRET` | valor *dev-only* | Clave HS256 (mínimo 32 bytes). **Cámbiala fuera de desarrollo** (`openssl rand -base64 48`) |
| `JWT_EXPIRATION` | `1h` | Validez del token |
| `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | `bitbox_inventory` / `bitbox` / `bitbox` | Base de datos |
| `POSTGRES_PORT` | `5432` | Puerto publicado de PostgreSQL en el host |

Parar y borrar los datos:

```bash
docker compose down -v
```

### Ejecutar sin Docker (desarrollo)

Con un PostgreSQL local accesible en `localhost:5432` (o solo `docker compose up -d postgres`):

```bash
./mvnw -pl inventory-service spring-boot:run
./mvnw -pl ticket-gateway-service spring-boot:run
```

### DevContainer

`.devcontainer/devcontainer.json` ofrece un entorno con JDK 17 y Docker-in-Docker (necesario para Testcontainers y Compose). Ábrelo con *Dev Containers: Reopen in Container* en VS Code o en GitHub Codespaces.

---

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
| 400 | `VALIDATION_ERROR` | Body inválido (incluye `errors` por campo) |
| 401 | `INVALID_CREDENTIALS` / `MISSING_BEARER_TOKEN` / `UNAUTHORIZED` | Login fallido, sin token, o token inválido o expirado |
| 404 | `EVENT_NOT_FOUND` | Evento inexistente |
| 409 | `EMAIL_ALREADY_REGISTERED` | Email duplicado |
| 409 | `SOLD_OUT` | No quedan entradas |
| 409 | `PURCHASE_CONFLICT` | Contención extrema: se agotaron los reintentos. **Reintentable** |
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

El endpoint de compra usa **optimistic locking** con el campo `@Version` de `Event`, junto con dos redes de seguridad:

1. **Cada intento es una transacción corta.** Se lee el evento, se descuenta 1 entrada y se hace `flush`. En ese momento Hibernate ejecuta `UPDATE events … WHERE id = ? AND version = ?`. Si otro comprador confirmó antes, la versión ya no coincide, la actualización afecta a 0 filas y toda la transacción (descuento y venta) se revierte.
2. **Reintento acotado fuera de la transacción**, con backoff exponencial y *jitter*. Cada reintento relee datos frescos. Si ya no queda stock, se responde `409 SOLD_OUT`. Si se agota el presupuesto de reintentos, se responde `409 PURCHASE_CONFLICT` (reintentable), de modo que un conflicto nunca se confunde con "agotado". Se usa `TransactionOperations` en lugar de `@Transactional` para garantizar una transacción nueva por intento sin depender de la auto-invocación de proxies.
3. **Restricción `CHECK (available_tickets BETWEEN 0 AND total_tickets)`** en PostgreSQL (Liquibase). Aunque hubiera un bug en la aplicación, la base de datos nunca aceptaría stock negativo.

Alternativas evaluadas: el lock pesimista (`SELECT … FOR UPDATE`) también es correcto, pero bloquea la fila durante toda la transacción y deja `@Version` sin uso. El `UPDATE` atómico condicional es el más rápido, pero se salta el modelo de entidades. Los locks en memoria (`synchronized`) fallan con varias instancias, y un lock distribuido en Redis es infraestructura innecesaria cuando la base de datos ya es la fuente de verdad.

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
| inventory | `PurchaseConcurrencyTest` | 50 compradores para 10 entradas venden exactamente 10. La última entrada se vende una sola vez entre 20 compradores. Con N = stock, los reintentos no pierden ventas. Con 30 peticiones HTTP concurrentes reales (JWT incluido) hay exactamente 5 × 201 y 25 × 409. En todos los casos se comprueba `available_tickets = 0` y que `COUNT(ticket_sales)` es igual al stock |
| inventory | `PurchaseServiceRetryTest` | La política de reintentos y que un conflicto agotado da `PURCHASE_CONFLICT`, no `SOLD_OUT` |
| inventory | `PurchaseIntegrationTest`, `EventIntegrationTest`, `AuthIntegrationTest` | Flujos principales, hash BCrypt, validación de JWT (sin token o manipulado → 401), errores 400/404/409, y que `/stats` no queda público por la regla de `/{id}` |
| inventory | `StatsIntegrationTest` | Valores de la consulta nativa: ocupación, compradores únicos, empates en el ranking y eventos sin ventas |
| gateway | `InventoryClientTest` | Propagación del header `Authorization`, relay de status y body de error, 503 si inventory está caído y 504 por timeout |
| gateway | `AvailabilityStreamServiceTest` | Emisión cada 2 s (tiempo virtual), errores transitorios que no rompen el stream y 404 antes de empezar |
| gateway | `GatewayApiTest` | Stack WebFlux completo: proxy de auth, buy sin token (no llama a inventory), relay de 409, SSE `text/event-stream` y documento OpenAPI |

---

## CI/CD

`.github/workflows/ci.yml` se ejecuta en cada push y pull request:

1. **Build & test**: `./mvnw -B verify` con JDK 17, que compila ambos módulos y ejecuta todos los tests, incluidos los de concurrencia con Testcontainers.
2. **Docker**: `docker compose build` construye las dos imágenes. Después se levanta el stack (`up --wait`) y se ejecuta un smoke test a través del gateway (registro, login, compra 201 y compra agotada 409).

---

## Estructura del monorepo

```
├── pom.xml                       # aggregator (parent: spring-boot-starter-parent)
├── mvnw, .mvn/                   # Maven Wrapper
├── docker-compose.yml, .env.example
├── .github/workflows/ci.yml
├── .devcontainer/devcontainer.json
├── inventory-service/
│   ├── Dockerfile
│   └── src/main/java/com/bitbox/inventory/
│       ├── auth/     registro, login, DTOs
│       ├── user/     entidad User
│       ├── event/    entidad Event (@Version), creación y consulta
│       ├── sale/     entidad TicketSale, compra concurrente con reintentos
│       ├── stats/    consulta SQL nativa de estadísticas
│       ├── security/ emisión y validación de JWT
│       ├── config/   seguridad, OpenAPI, reloj
│       └── common/   errores RFC 7807
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
