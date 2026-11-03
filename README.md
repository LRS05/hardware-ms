# Hardware - Microservices API

A REST API for managing a hardware store, built with Java, Spring Boot and Spring Cloud. The project separates authentication, users, products and orders into microservices, with an API Gateway, service discovery and centralized configuration.

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

The gateway routes public API requests to the corresponding service. Order-service calls product-service directly through service discovery to retrieve products and modify stock.

Configuration files are located in `config-data/`. By default, Config Server reads that directory from the `main` branch of this repository on GitHub.

## API Endpoints

Public requests use the API Gateway at `http://localhost:8080`.

### Authentication

| Method | Endpoint | Description |
| --- | --- | --- |
| POST | `/api/v1/auth/register` | Register a customer and issue JWT cookies |
| POST | `/api/v1/auth/login` | Authenticate and issue JWT cookies |
| POST | `/api/v1/auth/refresh` | Issue a new access token using the refresh cookie |
| POST | `/api/v1/auth/logout` | Clear JWT cookies; authentication required |

Authentication uses `access-token` and `refresh-token` cookies. Cookies are configured with `HttpOnly`, `Secure` and `SameSite=Strict`. Account for the `Secure` attribute when configuring a browser or HTTP client for local testing.

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

Product statuses are `ACTIVE`, `OUT_OF_STOCK` and `INACTIVE`. The public catalog and administrative endpoints apply different visibility rules.

Paginated customer and catalog endpoints use zero-based `page` and a default `size` of 20, with a maximum size of 100.

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

Order statuses are `ACTIVE`, `PENDING`, `CANCELED` and `COMPLETED`. The internal order-completion endpoint exists in the source, but its payment-service security configuration is not enabled yet.

### Administration

Administrative routes require ADMIN or STAFF. Some user operations are restricted further through method-level authorization.

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

Product-service exposes these routes directly. They require an `X-API-Key` header with the corresponding permission and are not routed through the public gateway.

| Service | Method | Endpoint | Permission |
| --- | --- | --- | --- |
| product-service | GET | `/internal/products/{id}` | `PRODUCT_READ` |
| product-service | PATCH | `/internal/products/{id}/decrease` | `STOCK_DECREASE` |
| product-service | PATCH | `/internal/products/{id}/increase` | `STOCK_INCREASE` |

The order-service route `PATCH /internal/orders/{orderId}/complete` is reserved for payment integration. Its intended permission is `ORDER_COMPLETE`; the active security configuration currently denies access to this route.

ProductClient uses a 2-second connection timeout and a 5-second response timeout. Product business errors retain their 404 or 409 status. Other HTTP integration errors return 502, communication failures return 503, and timeouts return 504 from order-service. ProductClient does not add application-level retries.

Stock changes commit in product-service independently of the order-service transaction. A local order rollback does not undo stock changes already applied by product-service.

## Docker Deployment

Docker containerization and a Docker Compose setup are coming soon. Setup and execution instructions will be added when the containerized environment is ready.
