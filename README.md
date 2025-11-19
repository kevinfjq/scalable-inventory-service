# Scalable Inventory Service 

A concurrency-safe inventory management service designed to handle high-traffic scenarios.

This project demonstrates advanced backend patterns including **Two-Level Caching (L1/L2)**, **Distributed Locking** for race condition prevention, and **Event-Driven Cache Invalidation**.

---

## 🏗️ Architecture & Design

The system is built to balance read performance (via caching) with write consistency (via locks).

```
┌─────────┐
│ Client  │
└────┬────┘
     │
     ▼
┌─────────────────┐
│  Spring Boot    │
│      API        │
└────┬────────────┘
     │
     ├─────────────────────────────────┐
     │                                 │
     ▼                                 ▼
┌──────────────┐              ┌──────────────────┐
│   L1 Cache   │              │  Consistency     │
│  (Caffeine)  │◄─────────────│    Layer         │
│   In-Memory  │              │   (RabbitMQ)     │
└──────┬───────┘              └──────────────────┘
       │                              │
       ▼                              │
┌──────────────┐                      │
│   L2 Cache   │                      │
│   + Lock     │                      │
│   (Redis)    │                      │
└──────┬───────┘                      │
       │                              │
       ▼                              │
┌──────────────┐                      │
│   Database   │◄─────────────────────┘
│  (H2/Postgres)│
└──────────────┘
```

### Read Path
1. Check L1 (Caffeine) - ~50ns latency
2. Check L2 (Redis) - ~2ms latency
3. Fetch from Database - Last resort

### Write Path
1. Acquire Distributed Lock (Redis)
2. Transactional Update (Database)
3. Invalidate L2 Cache (Redis)
4. Publish Invalidation Event (RabbitMQ)
5. Clear L1 Cache (All instances via RabbitMQ)

---

## Key Features

### Distributed Locking (Redisson)
- Prevents **Race Conditions** (overselling stock) when multiple users try to buy the last item simultaneously
- Uses Redis-based locks to serialize access to critical sections during the purchase transaction
- Configurable timeout and wait time for lock acquisition

### Two-Level Caching (L1 + L2)
- **L1 (Caffeine)**: In-memory local cache for microsecond latency (reduces network I/O)
- **L2 (Redis)**: Distributed shared cache for persistence across application restarts
- **Cache-Aside Pattern**: Data is lazily loaded into caches on read

### Event-Driven Invalidation (RabbitMQ)
- Uses a **Fanout Exchange** pattern to broadcast cache invalidation events
- Ensures that when one instance updates a product, all other instances clear their stale L1 (Caffeine) cache immediately
- Each application instance creates its own temporary queue bound to the fanout exchange

### Resilience & Testing
- **Integration Tests**: Includes a concurrency test suite that spawns multiple threads to prove the locking mechanism works (0 overselling)
- **Unit Tests**: Full coverage of service logic using Mockito

---

## 🛠️ Tech Stack

| Component | Technology |
|-----------|-----------|
| **Language** | Java 21 |
| **Framework** | Spring Boot 3.5.7 (Web, Data JPA, AMQP) |
| **Database** | H2 (In-Memory for Lab)|
| **Caching** | Caffeine (L1) + Redis (L2 via Redisson) |
| **Locking** | Redis (Redisson Client) |
| **Messaging** | RabbitMQ |
| **Infrastructure** | Docker |

---

## 🚀 How to Run

### Prerequisites
- Docker
- Java 21+ 
- Maven

### 1. Start Infrastructure

Start Redis and RabbitMQ using Docker Compose:

```bash
docker-compose up -d
```

This will start:
- **Redis** on port `6379`
- **RabbitMQ** on port `5672` (management UI on `15672`)

### 2. Run the Application

```bash
./mvnw spring-boot:run
```

The application will start on `http://localhost:8080`.

### 3. Access Services

- **Application**: http://localhost:8080
- **H2 Console**: http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:mem:testdb`
  - Username: `sa`
  - Password: _(empty)_
- **RabbitMQ Management**: http://localhost:15672
  - Username: `guest`
  - Password: `guest`

---

## Testing the Concurrency Logic

This project includes a specific integration test designed to **break the system** if the lock is not working.

Run the concurrency simulation with:

```bash
./mvnw test -Dtest=ConcurrencyTest
```

### Scenario
- Sets product stock to **1**
- Spawns **5 concurrent threads** trying to buy the product simultaneously
- **Success Criteria**: Only 1 thread succeeds, 4 fail, and final stock is **0** (not negative)

### Expected Output
```
Final stock: 0
Successful purchases: 1
Failed purchases: 4
```

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/products` | Creates a new product |
| `GET` | `/products/{id}` | Fetches product details (Uses L1 → L2 → DB strategy) |
| `POST` | `/products/{id}/buy?quantity=N` | Atomic Purchase. Uses Distributed Lock to safely deduct stock |

### Example Requests

#### Create a Product
```bash
curl -X POST http://localhost:8080/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "PlayStation 5",
    "price": 499.99,
    "stock": 10
  }'
```

#### Get a Product
```bash
curl http://localhost:8080/products/1
```

#### Buy a Product
```bash
curl -X POST "http://localhost:8080/products/1/buy?quantity=1"
```

---

## Cache Behavior Visualization

### Cache Hit Flow
```
Request → L1 (Caffeine) ✓
└─→ Response (50ns)
```

### Cache Miss Flow
```
Request → L1 (Caffeine) ✗
        → L2 (Redis) ✗
        → Database ✓
        → Store in L2
        → Store in L1
        → Response
```

### Cache Invalidation Flow
```
Purchase Request → Acquire Lock
                → Update Database
                → Delete from L2 (Redis)
                → Publish to RabbitMQ
                → All instances clear L1
```

---

## Configuration

Key configuration properties in `application.properties`:

```properties
# Database
spring.datasource.url=jdbc:h2:mem:testdb
spring.jpa.hibernate.ddl-auto=create-drop

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379

# RabbitMQ
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
```

### Cache Configuration
- **L1 (Caffeine)**: 1 minute TTL, max 100 entries
- **L2 (Redis)**: 10 minutes TTL

### Lock Configuration
- **Wait time**: 5 seconds
- **Lease time**: 10 seconds

---

## 📝 License

This project is licensed under the MIT License - see the LICENSE file for details.