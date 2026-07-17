# 🛒 E-Commerce Backend

A production-ready RESTful API for an e-commerce platform built with **Spring Boot 4** and **Java 21**. Features JWT-based authentication with refresh token rotation, role-based access control, idempotent order placement, and an outbox-pattern scheduler for inventory restoration.

---

## ✨ Features

- **JWT Authentication** — Stateless auth with access/refresh token pairs and automatic token rotation
- **Role-Based Access Control** — `ADMIN` and `USER` roles with fine-grained endpoint authorization
- **Product Catalog** — Full CRUD with pagination, filtering, and full-text search
- **Shopping Cart** — Per-user persistent cart tied to customer accounts
- **Order Management** — Idempotent order placement via `Idempotency-Key` header with order lifecycle (pending → shipped → delivered / canceled)
- **Category System** — Product categorization with many-to-many linking
- **Inventory Outbox Pattern** — Scheduled background task that reliably restores inventory for canceled orders
- **Virtual Threads** — Enabled for high-throughput request handling (Project Loom)
- **Global Exception Handling** — Centralized `@ControllerAdvice` with structured error responses
- **Bean Validation** — Request DTO validation via Jakarta Validation annotations
- **Database Seeder** — Auto-creates roles and an admin account on first startup
- **H2 Test Profile** — In-memory database for integration testing without external dependencies

---

## 🏗️ Tech Stack

| Layer          | Technology                          |
|----------------|-------------------------------------|
| Framework      | Spring Boot 4.0.6                   |
| Language       | Java 21                             |
| Database       | MySQL 8+                            |
| ORM            | Spring Data JPA / Hibernate         |
| Security       | Spring Security + JWT (jjwt 0.12.6) |
| Validation     | Jakarta Bean Validation             |
| Build          | Maven                               |
| Testing        | Spring Boot Test, H2 (in-memory)    |

---

## 📁 Project Structure

```
src/main/java/com/sivan/ecommerce/
├── advice/              # Global exception handler (@ControllerAdvice)
├── config/              # Security config, database seeder
├── controller/          # REST controllers (auth, cart, category, customer, order, product)
├── dto/                 # Request/Response DTOs with validation
├── entity/              # JPA entities with UUID primary keys
├── exception/           # Custom business exceptions
├── mapper/              # Entity ↔ DTO mappers
├── repository/          # Spring Data JPA repositories
├── response/            # Standardized error response objects
├── scheduler/           # Outbox-pattern inventory restoration scheduler
├── security/            # JWT filter, utilities, entry points
└── service/             # Business logic layer
```

---

## 🔐 Authentication & Authorization

The API uses a **stateless JWT architecture** with refresh token rotation:

| Scenario                     | Behavior                                                  |
|------------------------------|-----------------------------------------------------------|
| Login                        | Returns `accessToken` + `refreshToken`                    |
| Access protected endpoint    | Send `Authorization: Bearer <accessToken>`                |
| Access token expired         | Call `/auth/refresh` with the refresh token               |
| Refresh token expired        | User must re-authenticate (hard logout)                   |
| Logout                       | Refresh token is invalidated server-side                  |

### Role Permissions

| Action                           | Public | USER | ADMIN |
|----------------------------------|:------:|:----:|:-----:|
| Browse products & categories     |   ✅   |  ✅  |  ✅   |
| Register a new account           |   ✅   |  ✅  |  ✅   |
| View profile                     |   ❌   |  ✅  |  ✅   |
| Manage cart                      |   ❌   |  ✅  |  ✅   |
| Place / view / cancel orders     |   ❌   |  ✅  |  ✅   |
| Create / update / delete products|   ❌   |  ❌  |  ✅   |
| Create categories & link to products |   ❌   |  ❌  |  ✅   |

---

## 📡 API Endpoints

All endpoints are prefixed with `/api/v1/`.

### Auth
| Method | Endpoint          | Access  | Description             |
|--------|-------------------|---------|-------------------------|
| POST   | `/auth/login`     | Public  | Authenticate & get tokens |
| POST   | `/auth/refresh`   | Public  | Refresh token pair       |
| POST   | `/auth/logout`    | Public  | Invalidate refresh token |

### Customers
| Method | Endpoint          | Access  | Description             |
|--------|-------------------|---------|-------------------------|
| POST   | `/customers`      | Public  | Register a new customer |
| GET    | `/customers/me`   | USER    | Get authenticated user's profile |

### Products
| Method | Endpoint                                    | Access  | Description                    |
|--------|---------------------------------------------|---------|--------------------------------|
| GET    | `/products`                                 | Public  | List products (paginated, filterable) |
| GET    | `/products/{productId}`                     | Public  | Get product details            |
| POST   | `/products`                                 | ADMIN   | Create a new product           |
| PATCH  | `/products/{productId}`                     | ADMIN   | Partially update a product     |
| DELETE | `/products/{productId}`                     | ADMIN   | Soft-delete a product          |
| PUT    | `/products/{productId}/categories/{categoryId}` | ADMIN   | Link a category to a product   |

### Categories
| Method | Endpoint          | Access  | Description             |
|--------|-------------------|---------|-------------------------|
| GET    | `/categories`     | Public  | List all active categories |
| POST   | `/categories`     | ADMIN   | Create a new category   |

### Cart
| Method | Endpoint                  | Access | Description              |
|--------|---------------------------|--------|--------------------------|
| GET    | `/cart`                   | USER   | View cart items          |
| POST   | `/cart/items`             | USER   | Add item to cart         |
| PATCH  | `/cart/items/{productId}` | USER   | Update item quantity     |
| DELETE | `/cart/items/{productId}` | USER   | Remove item from cart    |

### Orders
| Method | Endpoint                      | Access | Description              |
|--------|-------------------------------|--------|--------------------------|
| POST   | `/orders`                     | USER   | Place order (requires `Idempotency-Key` header) |
| GET    | `/orders`                     | USER   | List orders (paginated, filterable) |
| GET    | `/orders/{orderId}`           | USER   | Get order details        |
| POST   | `/orders/{orderId}/cancel`    | USER   | Cancel an order          |

---

## 🔧 Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **UUID primary keys** | Avoids sequential ID enumeration; IDs are generated application-side before INSERT |
| **Outbox pattern for inventory** | Ensures reliable inventory restoration for canceled orders without distributed transactions |
| **Idempotency keys on orders** | Prevents duplicate order placement from network retries |
| **Soft deletes** (`is_active` flag) | Preserves referential integrity for historical orders |
| **Virtual threads** | Maximizes throughput for I/O-bound database operations |
| **Refresh token rotation** | Mitigates token theft by invalidating old tokens on each refresh |
| **Pagination limits** (`max-page-size=50`) | Prevents RAM exhaustion from unbounded queries |

---

## 📄 License

This project is for educational purposes.
