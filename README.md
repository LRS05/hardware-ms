# Hardware - Microservices API

A hardware store REST API built with Java, Spring Boot and Spring Cloud. Includes user, product and order services, an API Gateway, service discovery and centralized configuration.

## Features

- User registration and authentication with access and refresh JWT cookies.
- Role-based access control for ADMIN, STAFF and CUSTOMER.
- Personal profile and password management.
- Administrative user management and role updates.
- Public product catalog with pagination and search by name or category.
- Product creation, updates, deactivation and stock management.
- Customer orders with item management, price refresh, checkout and reopening.
- Stock reservation during checkout and release when reopening an order.
- Internal service authentication using API keys and permissions.
- Database migrations with Flyway.
- Row locking for concurrent stock and order updates.
- Controlled responses for product dependency errors and connection timeouts.
- Docker Compose environment with PostgreSQL and the six applications.
- Unit and integration tests with JUnit, Mockito, RestTestClient and Testcontainers.

## Technologies Used

- Java 17
- Spring Boot 4.1.1
- Spring Cloud 2025.1.3
- Spring Cloud Gateway Server Web MVC
- Spring Cloud Config
- Netflix Eureka and Spring Cloud LoadBalancer
- Spring Security and OAuth2 Resource Server
- JWT with JJWT
- Spring Data JPA and Hibernate
- PostgreSQL
- Flyway
- Jakarta Bean Validation
- MapStruct and Lombok
- Maven
- Docker and Docker Compose
- JUnit, Mockito and MockRestServiceServer
- RestTestClient and Testcontainers

## Microservices

| Module | Responsibility | Default port |
| --- | --- | --- |
| `config-server` | Centralized configuration | 8888 |
| `eureka-server` | Service registration and discovery | 8761 |
| `api-gateway` | Public entry point, routing and JWT propagation | 8080 |
| `user-service` | Authentication, profiles, users and roles | 8081 |
| `product-service` | Catalog, products and stock | 8082 |
| `order-service` | Orders, items and product stock requests | 8083 |

Each business service has its own PostgreSQL database: `user_db`, `product_db` and `order_db`.

The gateway routes API requests. Order-service uses service discovery to call product-service for product details and stock updates.

Config Server reads `config-data/` through its native backend. Compose mounts it read-only at `/config-data`; changes do not require a GitHub push. The Git backend is commented out.

## API Endpoints

Public requests use the API Gateway at `http://localhost:8080`.

### Authentication

| Method | Endpoint | Description |
| --- | --- | --- |
| POST | `/api/v1/auth/register` | Register a customer and issue JWT cookies |
| POST | `/api/v1/auth/login` | Authenticate and issue JWT cookies |
| POST | `/api/v1/auth/refresh` | Issue a new access token using the refresh cookie |
| POST | `/api/v1/auth/logout` | Clear JWT cookies; authentication required |

The `access-token` and `refresh-token` cookies use `HttpOnly`, `Secure` and `SameSite=Strict`. Local HTTP clients must account for `Secure` cookies.

Authentication uses JWTs without server-side sessions. Refresh returns a new access token and keeps the same refresh token until its original expiration. Logout clears cookies without revoking issued tokens.

Each cookie uses its JWT's configured lifetime. For example, `JWT_ACCESS_EXPIRATION=120000` gives both the access token and its cookie a lifetime of 2 minutes.

### Customer Profile

| Method | Endpoint | Description |
| --- | --- | --- |
| GET | `/api/v1/users/me` | Retrieve the authenticated customer |
| PATCH | `/api/v1/users/me` | Update profile information |
| PATCH | `/api/v1/users/me/password` | Change password |
| DELETE | `/api/v1/users/me` | Delete the account after password validation |

### Public Product Catalog

| Method | Endpoint | Description |
| --- | --- | --- |
| GET | `/api/v1/products` | List catalog products |
| GET | `/api/v1/products?name={name}` | Search products by name |
| GET | `/api/v1/products?category={category}` | Filter products by category |
| GET | `/api/v1/products/{id}` | Retrieve a product |

Product statuses: `ACTIVE`, `OUT_OF_STOCK` and `INACTIVE`. Visibility differs between public and administrative endpoints.

Customer and catalog pagination uses zero-based `page`, default `size=20` and maximum `size=100`.

### Customer Orders

| Method | Endpoint | Description |
| --- | --- | --- |
| GET | `/api/v1/orders` | List the customer's orders |
| GET | `/api/v1/orders?status={status}` | Filter orders by status |
| GET | `/api/v1/orders/{id}` | Retrieve an owned order |
| GET | `/api/v1/orders/active` | Retrieve the active order |
| GET | `/api/v1/orders/pending` | Retrieve the pending order |
| PATCH | `/api/v1/orders/active/items/products/{productId}` | Add a product to the active order |
| PATCH | `/api/v1/orders/active/items/{itemId}?quantity={quantity}` | Update item quantity |
| DELETE | `/api/v1/orders/active/items/{itemId}` | Remove an item |
| PATCH | `/api/v1/orders/active/refresh` | Refresh prices and product availability |
| PATCH | `/api/v1/orders/active/clear` | Clear the active order |
| PATCH | `/api/v1/orders/active/checkout` | Reserve stock and move the order to PENDING |
| PATCH | `/api/v1/orders/pending/reopen` | Release reserved stock and return the order to ACTIVE |

Order statuses: `ACTIVE`, `PENDING`, `CANCELED` and `COMPLETED`. Payment integration is not enabled.

### Administration

Administrative routes require ADMIN or STAFF; some user operations require ADMIN.

| Endpoint | Description |
| --- | --- |
| `/api/v1/admin/me` | Administrative profile management |
| `/api/v1/admin/users/**` | User search, updates, role changes and deletion |
| `/api/v1/admin/products` | Create products or retrieve a product by SKU |
| `/api/v1/admin/products/{id}` | Retrieve or update a product |
| `/api/v1/admin/products/{id}/deactivate` | Deactivate a product |
| `/api/v1/admin/users/{userId}/orders` | List a user's orders |
| `/api/v1/admin/users/orders/{id}` | Retrieve an order |

### Internal Communication

Internal product routes require `X-API-Key` and the listed permission. They bypass the public gateway.

| Service | Method | Endpoint | Permission |
| --- | --- | --- | --- |
| product-service | GET | `/internal/products/{id}` | `PRODUCT_READ` |
| product-service | PATCH | `/internal/products/reserve` | `STOCK_DECREASE` |
| product-service | PATCH | `/internal/products/release` | `STOCK_INCREASE` |
| product-service | PATCH | `/internal/products/{id}/decrease` | `STOCK_DECREASE` |
| product-service | PATCH | `/internal/products/{id}/increase` | `STOCK_INCREASE` |

`PATCH /internal/orders/{orderId}/complete` is reserved for payment integration with `ORDER_COMPLETE`. Access is currently denied.

ProductClient timeouts: 2 seconds to connect, 5 seconds for a response. Order-service preserves product errors 404/409 and maps other HTTP errors to 502, connection failures to 503 and timeouts to 504. No application-level retries.

Checkout and reopening use atomic stock batches in product-service. Order and stock transactions are separate: an order rollback does not undo committed stock changes.

## Docker Deployment

### Requirements

- Docker Engine or Docker Desktop with Docker Compose v2 or later.
- Port `8080` available on the host.
- Internet access for the initial image and Maven dependency downloads.

Java and Maven run inside Docker; no host installation is required.

### Environment Variables

From the repository root, copy the environment template:

```bash
cp .env.example .env
```

Replace the fictitious credentials in `.env` before starting. Do not commit this file.

Keep these URLs for the Compose environment:

| Variable | Value |
| --- | --- |
| `CONFIG_SERVER_URL` | `http://config-server:8888` |
| `EUREKA_SERVER_URL` | `http://eureka-server:8761/eureka/` |
| `USER_DB_URL` | `jdbc:postgresql://postgres:5432/user_db` |
| `PRODUCT_DB_URL` | `jdbc:postgresql://postgres:5432/product_db` |
| `ORDER_DB_URL` | `jdbc:postgresql://postgres:5432/order_db` |

Use Compose service names for connections; `localhost` refers to the current container. Compose mounts `config-data/` and overrides `CONFIG_DATA_LOCATION` with `file:/config-data/`.

Configure the remaining variables:

| Variable | Purpose |
| --- | --- |
| `POSTGRES_USER` | PostgreSQL user shared by the three service databases |
| `POSTGRES_PASSWORD` | Password for that PostgreSQL user |
| `JWT_SECRET_KEY` | Shared JWT signing secret, at least 32 UTF-8 bytes |
| `JWT_ACCESS_EXPIRATION` | Access token lifetime in milliseconds; default example: `900000` (15 minutes) |
| `JWT_REFRESH_EXPIRATION` | Refresh token lifetime in milliseconds; default example: `86400000` (24 hours) |
| `PRODUCT_SERVICE_API_KEY` | Product-service internal authentication configuration |
| `ORDER_SERVICE_API_KEY` | Identifies order-service when calling product-service |
| `PAYMENT_SERVICE_API_KEY` | Reserved for payment integration; provide a placeholder while it remains unimplemented |

PostgreSQL creates the three databases on first initialization and stores data in a named volume. Editing `.env` does not change existing database credentials.

### Initial Administrator

Registration creates CUSTOMER accounts. For an initial ADMIN, configure these variables in `.env` before the first start:

```dotenv
DEV_ADMIN_ENABLED=true
DEV_ADMIN_USERNAME=admin
DEV_ADMIN_EMAIL=admin@example.com
DEV_ADMIN_PASSWORD=Admin123!
```

Replace the example credentials. User-service creates the ADMIN at startup with a BCrypt password. Log in at `/api/v1/auth/login`. Creation is skipped if any ADMIN exists or the username or email is taken.

After creation, set `DEV_ADMIN_ENABLED=false` for subsequent starts. The account persists; changing the initial credentials does not update it.

### Run

From the repository root, build and start the environment:

```bash
docker compose up --build
```

Access the API at `http://localhost:8080`. Other services remain on the internal network. Wait for startup and Eureka registration before sending requests.

### Shut Down

In another terminal at the repository root, stop and remove the containers:

```bash
docker compose down
```

The PostgreSQL volume preserves data for the next start.
