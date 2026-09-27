# Job Tracker

## Project Description

Job Tracker is a job application management website built with Java 17, Java's built-in `HttpServer`, HTML, CSS, and vanilla JavaScript. It stores applications in `data/jobs.json` and supports creating, editing, deleting, searching, filtering, and sorting applications.

# Job Tracker

Job Tracker is a full-stack job application dashboard for organizing applications, tracking progress, and keeping notes about opportunities in one place.

The project uses a Java 17 backend with Java's built-in `com.sun.net.httpserver.HttpServer` and a responsive frontend built with HTML, CSS, and vanilla JavaScript. Application data is stored in a local JSON file.

---

## Purpose & Overview

Keeping track of multiple applications can quickly become difficult when details are spread across notes, email, and browser tabs. Job Tracker provides a single dashboard for managing the application pipeline.

The dashboard supports:

- Creating job applications
- Editing existing applications
- Deleting applications
- Searching by company, role, or location
- Filtering by application status
- Sorting by application date
- Table and card views
- Application statistics
- Responsive layouts for desktop and mobile
- Loading, error, and empty states
- JSON file persistence

Supported statuses are:

- `Applied`
- `Interview`
- `Rejected`
- `Offer`

---

## System Architecture

```text
  [ User Browser ]
	  |
	  | HTML / CSS / JavaScript
	  v
  [ Job Tracker Dashboard ]
	  |
	  | REST requests
	  v
  [ Java HttpServer ]
	  |
	  | CRUD operations
	  v
  [ JobStore ]
	  |
	  | Read / write JSON
	  v
  [ data/jobs.json ]
```

When the frontend and backend are hosted separately, the frontend sends requests to the backend URL configured through `BACKEND_URL` or the runtime `?api=` query parameter.

---

## Technology Stack

| Layer | Technology | Purpose |
| --- | --- | --- |
| Frontend | HTML5, CSS3, JavaScript | Dashboard interface and client-side interactions |
| Backend | Java 17 | REST-style HTTP server |
| HTTP server | `com.sun.net.httpserver.HttpServer` | Serves the frontend and API routes |
| Persistence | JSON file | Stores application records in `data/jobs.json` |
| API format | JSON | Request and response payloads |
| Deployment | Docker, GitHub Actions, GitHub Pages | Backend container and frontend deployment |

The project does not require Spring Boot, Maven, Gradle, Node.js, or frontend frameworks.

---

## Application Data

Each job application contains the following fields:

| Field | Description |
| --- | --- |
| `id` | Generated unique identifier |
| `company` | Company name |
| `role` | Position or job title |
| `location` | Job location or remote status |
| `salary` | Salary range or compensation information |
| `applicationUrl` | Link to the job listing or application page |
| `status` | `Applied`, `Interview`, `Rejected`, or `Offer` |
| `dateApplied` | Date the application was submitted |
| `notes` | Additional notes about the opportunity |

The required fields are `company`, `role`, `status`, and `dateApplied`.

---

## Project Structure

```text
job-tracker/
├── data/
│   └── jobs.json                 # Persisted application data
├── src/
│   ├── JobApplication.java       # Application model
│   ├── JobStore.java             # JSON-backed CRUD store
│   ├── Json.java                 # Core Java JSON parser and serializer
│   └── Main.java                 # HTTP server and API routes
├── web/
│   ├── app.js                    # Dashboard behavior and API calls
│   ├── config.js                 # Frontend backend URL configuration
│   ├── index.html                # Dashboard markup
│   └── styles.css                # Responsive dashboard styles
├── .github/
│   └── workflows/
│       ├── build.yml             # Java build verification
│       └── deploy-pages.yml      # GitHub Pages deployment
├── Dockerfile                    # Backend container definition
├── .gitignore
└── README.md
```

---

## Getting Started

### Requirements

- JDK 17 or newer
- PowerShell on Windows, or a POSIX-compatible shell on macOS/Linux

### Compile the backend

Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force -Path out | Out-Null
javac -d out src\*.java
```

macOS/Linux:

```bash
mkdir -p out
javac -d out src/*.java
```

### Start the application

```powershell
java -cp out Main
```

Open `http://localhost:8080` in a browser.

To use another port:

```powershell
java -cp out Main 9090
```

The server can also read the `PORT` environment variable when deployed in a container.

---

## REST API

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/jobs` | Return all applications |
| `GET` | `/api/jobs/{id}` | Return one application |
| `POST` | `/api/jobs` | Create an application |
| `PUT` | `/api/jobs/{id}` | Replace an application |
| `DELETE` | `/api/jobs/{id}` | Delete an application |

Example create request:

```json
{
  "company": "Acme Labs",
  "role": "Software Engineer",
  "location": "Remote",
  "salary": "$120k - $145k",
  "applicationUrl": "https://example.com/jobs/1",
  "status": "Applied",
  "dateApplied": "2026-09-27",
  "notes": "Follow up next week"
}
```

The API returns JSON responses and uses CORS headers so a separately hosted frontend can communicate with the backend.

---

## Deployment

GitHub Pages can host the static frontend, but it cannot run the Java server. For a complete deployment, run the backend on a Docker-capable host and publish the frontend through GitHub Pages.

### 1. Push the project to GitHub

```powershell
git init
git add .
git commit -m "Initial job tracker"
git branch -M main
git remote add origin https://github.com/YOUR-USERNAME/YOUR-REPOSITORY.git
git push -u origin main
```

### 2. Deploy the backend

Deploy the included `Dockerfile` on Railway, Render, Fly.io, Azure Container Apps, or another Docker hosting service.

Configure:

- Port: the value provided by the host through `PORT`
- Persistent volume: `/app/data`
- Public backend URL: for example, `https://your-backend.example.com`

Persistent storage is recommended because the application database is the file `data/jobs.json`.

### 3. Deploy the frontend

In the GitHub repository:

1. Open **Settings > Pages**.
2. Select **GitHub Actions** as the source.
3. Open **Settings > Secrets and variables > Actions > Variables**.
4. Add the backend URL:

```text
Name: BACKEND_URL
Value: https://your-backend.example.com
```

5. Push to `main` or run the `Deploy frontend to GitHub Pages` workflow manually.

The workflow writes the backend URL into `web/config.js` before publishing the `web` directory.

For an already published site, the backend can be configured once through the URL:

```text
https://YOUR-USERNAME.github.io/YOUR-REPOSITORY/?api=https://your-backend.example.com
```

The browser stores that backend URL for future visits.

---

## Validation

To verify the project locally:

1. Compile the Java files.
2. Start the server.
3. Open the dashboard.
4. Add an application.
5. Edit its details and status.
6. Search, filter, and sort the application list.
7. Delete the application.
8. Restart the server and confirm that saved data remains in `data/jobs.json`.

The `build.yml` GitHub Actions workflow also compiles the backend on pushes and pull requests.

## Deployment

GitHub Pages hosts the frontend. The Java backend must run separately on a Docker host such as Railway, Render, Fly.io, or Azure Container Apps.

1. Push the repository to GitHub.
2. Deploy the repository's `Dockerfile` on a Docker hosting service.
3. Mount persistent storage at `/app/data` so `data/jobs.json` survives restarts.
4. Copy the public URL of the deployed backend.
5. In GitHub, open **Settings > Secrets and variables > Actions > Variables** and add:

```text
Name: BACKEND_URL
Value: https://your-backend.example.com
```

6. In **Settings > Pages**, select **GitHub Actions** as the deployment source.
7. Push to the `main` branch or run the Pages workflow manually.

The Pages workflow writes `BACKEND_URL` into `web/config.js`, allowing the published frontend to use the Java backend for all application data and CRUD operations.
