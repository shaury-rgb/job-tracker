# Northstar | Job Tracker

Job Tracker is a high-performance, zero-dependency full-stack application for organizing job applications, managing user accounts, tracking hiring pipeline progress, analyzing conversion metrics, and keeping notes about career opportunities in one place.

The project features an **industrial-grade Java 17 backend** utilizing Java's built-in `com.sun.net.httpserver.HttpServer`, an ACID-compliant file-backed database engine (`Database.java`), and a custom RFC 8259 JSON parser, paired with a modern, responsive frontend built in vanilla HTML5, CSS3, and JavaScript.

---

## Key Features

- **User Accounts & Authentication**:
  - **Account Creation**: Register with Full Name, Work Email, and secure password.
  - **Secure Sign In**: Session authentication with Bearer tokens and per-user cryptographic salts.
  - **Quick Demo Access**: Instant one-click login with pre-configured demo account (`demo@northstar.app` / `password123`).
  - **Session Management**: Automatic token refresh, session persistence via `localStorage`, and graceful 401 handling.
- **Multi-Tenant Data Isolation**:
  - Full data segregation per user — each user only accesses, creates, edits, and removes their own applications.
- **Full CRUD Application Management**:
  - Create, view, edit, and delete job opportunities.
- **Dual Display Modes**:
  - Seamless toggle between a dense, structured **Table View** and an expansive, visual **Card View**.
- **Interactive Opportunity Details**:
  - Dedicated inspection modal displaying compensation, notes, application links, and status history.
- **Pipeline Insights Dashboard**:
  - **Conversion Funnel**: Tracks conversion from Applied → Interviews Secured → Offers Received.
  - **Status Distribution**: Multi-stage visual distribution across Applied, Interview, Offer, and Rejected.
  - **Work Model Analytics**: Remote vs Hybrid/Onsite breakdown and salary disclosure rates.
  - **Actionable Strategic Recommendations**: Contextual advice based on current pipeline momentum.
- **Search & Multi-field Filtering**:
  - Instant search across Company, Role, Location, and Notes.
  - Filter by status (`Applied`, `Interview`, `Offer`, `Rejected`).
  - Sort by Newest, Oldest, Company (A-Z), and Company (Z-A).
- **Data Backup & Mobility**:
  - One-click **Export to JSON** and **Import from JSON** for complete data ownership and backup safety.
- **Zero External Dependencies**:
  - Standard Java 17 runtime only. No Maven, Gradle, Spring Boot, Node.js, npm, or external JavaScript/CSS frameworks.
- **Automated Test Suite**:
  - 10-suite unit and integration test runner (`TestRunner.java`) verifying auth, crypto, database, multi-tenancy, and REST API.
- **Production Containerization**:
  - Multi-stage Docker image with Eclipse Temurin JRE runtime and built-in health checks.

---

## System Architecture

```text
  ┌─────────────────────────────────────────────────────────────┐
  │                        User Browser                         │
  │     (HTML5 / CSS3 / Vanilla JS Single-Page Application)     │
  │       - Token Authentication & User Profile State           │
  │       - Table & Card Views / Pipeline Insights Dashboard    │
  └──────────────────────────────┬──────────────────────────────┘
                                 │
                 REST API (Bearer Token) / Static Assets
                                 │
  ┌──────────────────────────────▼──────────────────────────────┐
  │                 Java 17 HttpServer (Port 8080)              │
  │                                                             │
  │   Public Routes:                                            │
  │   - GET, HEAD /              -> Serves index.html           │
  │   - GET, HEAD /*             -> Static assets (CSS, JS)     │
  │   - GET /api/health          -> Service health check        │
  │   - POST /api/auth/register  -> Create new account          │
  │   - POST /api/auth/login     -> Sign in and issue token     │
  │   - OPTIONS /*               -> CORS preflight handler      │
  │                                                             │
  │   Protected Routes (Authorization: Bearer <token>):         │
  │   - GET /api/auth/me         -> Current user profile        │
  │   - POST /api/auth/logout    -> Invalidate session token    │
  │   - GET /api/jobs            -> List user's applications    │
  │   - GET /api/jobs/{id}       -> Retrieve single application │
  │   - POST /api/jobs           -> Create application          │
  │   - PUT /api/jobs/{id}       -> Update application          │
  │   - DELETE /api/jobs/{id}    -> Remove application          │
  └──────────────────────────────┬──────────────────────────────┘
                                 │
  ┌──────────────────────────────▼──────────────────────────────┐
  │                 ACID Database Engine Layer                  │
  │                 (Thread-Safe + Atomic Swaps)                │
  │   - AuthSecurity: PBKDF2WithHmacSHA256 (65,536 iterations)  │
  │   - UserStore / SessionStore / Multi-Tenant JobStore        │
  └──────────────────────────────┬──────────────────────────────┘
                                 │
  ┌──────────────────────────────▼──────────────────────────────┐
  │                    Persistent Data Store                    │
  │   - data/users.json          -> User accounts               │
  │   - data/sessions.json       -> Active auth tokens          │
  │   - data/jobs.json           -> Multi-tenant job records    │
  └─────────────────────────────────────────────────────────────┘
```

---

## Security & Authentication Architecture

1. **Password Hashing**:
   - Uses Java standard library `javax.crypto.SecretKeyFactory` with **`PBKDF2WithHmacSHA256`** configured for **65,536 iterations** and a 256-bit key length.
   - Each user is provisioned a unique 16-byte cryptographically secure random salt (`SecureRandom`).
   - Constant-time byte array comparison prevents timing side-channel attacks.
2. **Session Tokens**:
   - 32-byte cryptographic random tokens generated by `SecureRandom`, URL-safe Base64 encoded.
   - Session tokens have a 30-day expiration window with automatic cleanup of expired records.
   - Instant token revocation upon logout (`POST /api/auth/logout`).
3. **Multi-Tenant Scoping**:
   - All job records are indexed by `userId`.
   - Users cannot query, view, modify, or delete opportunities belonging to other accounts.

---

## Technology Stack

| Layer | Technology | Description |
|---|---|---|
| **Frontend** | HTML5, CSS3, Vanilla JavaScript | Responsive dashboard, cards, tables, modals, insights, auth flow |
| **Backend** | Java 17 (Eclipse Temurin) | Standalone REST API with zero third-party dependencies |
| **HTTP Server** | `com.sun.net.httpserver.HttpServer` | Built-in HTTP server with cached daemon thread pool |
| **Database** | Embedded File Database (`Database.java`) | Atomic file swaps, multi-table persistence, multi-tenant queries |
| **Security** | `PBKDF2WithHmacSHA256` + `SecureRandom` | Industrial standard password hashing and token generation |
| **JSON Engine** | Custom `Json.java` | RFC 8259 compliant parser and serializer |
| **Testing** | Pure Java `TestRunner.java` | 10 automated test suites covering auth, database, and HTTP lifecycle |
| **Container** | Docker | Multi-stage build (`temurin:17-jdk` -> `temurin:17-jre`) |
| **CI/CD** | GitHub Actions | Automated build, test verification, and GitHub Pages deployment |

---

## REST API Specification

### Authentication Endpoints

#### Register Account
- **`POST /api/auth/register`**
  - Body:
    ```json
    {
      "name": "Jane Doe",
      "email": "jane@example.com",
      "password": "securepassword123"
    }
    ```
  - **Response 201 Created**:
    ```json
    {
      "token": "4J9...",
      "user": {
        "id": "e3b0c442-...",
        "name": "Jane Doe",
        "email": "jane@example.com",
        "createdAt": "2026-10-14T15:40:00Z"
      }
    }
    ```

#### Sign In
- **`POST /api/auth/login`**
  - Body:
    ```json
    {
      "email": "jane@example.com",
      "password": "securepassword123"
    }
    ```
  - **Response 200 OK**: Returns session `token` and `user` profile.
  - **Response 401 Unauthorized**: Invalid email or password.

#### Current User Profile
- **`GET /api/auth/me`**
  - Headers: `Authorization: Bearer <token>`
  - **Response 200 OK**: Current authenticated user's profile.
  - **Response 401 Unauthorized**: If token is missing, expired, or invalid.

#### Sign Out
- **`POST /api/auth/logout`**
  - Headers: `Authorization: Bearer <token>`
  - **Response 204 No Content**: Revokes token.

---

### Job Application Endpoints (Protected)

*All application endpoints require the `Authorization: Bearer <token>` header.*

#### List / Query Applications
- **`GET /api/jobs`**
  - Query parameters (optional):
    - `status`: `Applied`, `Interview`, `Offer`, `Rejected`.
    - `search`: Search company, role, location, and notes.
    - `sort`: `newest` (default), `oldest`, `company_asc`, `company_desc`.
  - **Response 200 OK**: Array of application objects scoped to the authenticated user.

#### Create Application
- **`POST /api/jobs`**
  - Headers: `Content-Type: application/json`, `Authorization: Bearer <token>`
  - Body:
    ```json
    {
      "company": "Stripe",
      "role": "Staff Infrastructure Engineer",
      "location": "San Francisco, CA (Hybrid)",
      "salary": "$190,000 - $225,000",
      "applicationUrl": "https://stripe.com/jobs",
      "status": "Interview",
      "dateApplied": "2026-10-02",
      "notes": "System design round scheduled."
    }
    ```
  - **Response 201 Created**: Newly created application with assigned `id`.

#### Get Single Application
- **`GET /api/jobs/{id}`**
  - **Response 200 OK**: Single application object.
  - **Response 404 Not Found**: If ID does not exist or belongs to another user.

#### Update Application
- **`PUT /api/jobs/{id}`**
  - **Response 200 OK**: Updated application object.
  - **Response 404 Not Found**: If ID does not exist or belongs to another user.

#### Delete Application
- **`DELETE /api/jobs/{id}`**
  - **Response 204 No Content**: Application successfully removed.
  - **Response 404 Not Found**: If ID does not exist or belongs to another user.

---

### System Health
- **`GET /api/health`**
  - **Response 200 OK**:
    ```json
    {
      "status": "UP",
      "timestamp": "2026-10-14T15:40:00Z",
      "jobsCount": 6,
      "usersCount": 1
    }
    ```

---

## Getting Started

### Prerequisites
- **Java Development Kit (JDK) 17** or higher.
- (Optional) **Docker** for container execution.

### 1. Compile the Code
```bash
mkdir -p out
javac -Xlint:unchecked -d out src/*.java
```

### 2. Run the Automated Test Suite
```bash
java -cp out TestRunner
```

Expected output:
```text
==========================================
Running Job Tracker Test Suite (Java 17)
==========================================

  [PASS] Json: Primitive Serialization and Parsing
  [PASS] Json: Objects, Arrays, and Nesting
  [PASS] Json: Escapes and Unicode Handling
  [PASS] Json: Syntax Errors and Trailing Garbage Detection
  [PASS] JobApplication: Mapping, Null Safety, and UserId
  [PASS] AuthSecurity: Password Hashing, Salts, and Verification
  [PASS] Database: User Registration, Duplicates, and Sessions
  [PASS] Database: Multi-Tenant Job Isolation
  [PASS] Database: File Persistence and Atomic Reloading
  [PASS] API: Full Auth, Multi-Tenancy, and HTTP Lifecycle

==========================================
Results: 10 PASSED, 0 FAILED
==========================================
```

### 3. Start the Server
```bash
java -cp out Main 8080
```

Open your browser and navigate to:
```text
http://localhost:8080
```

- **Default Demo Account**:
  - Email: `demo@northstar.app`
  - Password: `password123`
  - *(Or click "Quick Login with Demo Account" on the login modal)*

---

## Docker Deployment

Build and run the containerized application:

```bash
# Build multi-stage image (compiles code and runs tests during build)
docker build -t job-tracker:latest .

# Run container mapping port 8080 with persistent data volume
docker run -d -p 8080:8080 -v $(pwd)/data:/app/data --name job-tracker job-tracker:latest
```

---

## Project Structure

```text
job-tracker/
├── .github/
│   └── workflows/
│       ├── build.yml             # CI: Compile with linting and execute TestRunner
│       └── deploy-pages.yml      # CI: Deploy web client to GitHub Pages
├── data/
│   ├── jobs.json                 # Persistent multi-tenant application records
│   ├── users.json                # User credentials & cryptographic salts
│   └── sessions.json             # Active authentication tokens
├── src/
│   ├── AuthSecurity.java         # PBKDF2WithHmacSHA256 hashing & token generation
│   ├── Database.java             # Atomic file database, user auth & multi-tenant queries
│   ├── JobApplication.java       # Domain model with null-safe map translation & userId
│   ├── JobStore.java             # Compatibility CRUD store wrapper
│   ├── Json.java                 # RFC 8259 core Java JSON parser and serializer
│   ├── Main.java                 # HttpServer routes, auth handlers, health check
│   ├── Session.java              # Authenticated user session domain model
│   ├── TestRunner.java           # 10-suite automated unit & integration test runner
│   └── User.java                 # User account entity
├── web/
│   ├── app.js                    # SPA state, auth, dual views, insights, data mobility
│   ├── config.js                 # API base configuration for separate hosting
│   ├── index.html                # Semantic HTML5 markup with Auth & Details modals
│   └── styles.css                # Responsive stylesheet with auth UI & toast alerts
├── Dockerfile                    # Multi-stage Docker build with JRE runtime
├── .gitignore                    # Git ignore configuration
└── README.md                     # Comprehensive documentation
```

---

## License

This project is open source and available under the standard MIT License.
