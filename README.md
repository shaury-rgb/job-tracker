# Job Tracker

A small job application tracker with a Java HTTP server and a plain HTML, CSS, and JavaScript frontend. Applications are stored in `data/jobs.json`.

## Requirements

- JDK 17 or newer
- Git, if you plan to publish the project

The backend uses Java's built-in `com.sun.net.httpserver.HttpServer`. There are no framework or package-manager dependencies.

## Run locally

From the project directory:

### Windows PowerShell

```powershell
New-Item -ItemType Directory -Force -Path out | Out-Null
javac -d out src\*.java
java -cp out Main
```

### macOS or Linux

```bash
mkdir -p out
javac -d out src/*.java
java -cp out Main
```

Open [http://localhost:8080](http://localhost:8080). Pass a different port as the first argument if needed:

```powershell
java -cp out Main 9090
```

Stop the server with `Ctrl+C`.

## Features

- Add, edit, and remove applications
- Search by company, role, or location
- Filter by status
- Sort by application date
- Table and card views
- Application totals, progress, offer, and response statistics
- JSON file persistence

## API

| Method | Endpoint | Description |
| --- | --- | --- |
| GET | `/api/jobs` | List applications |
| GET | `/api/jobs/{id}` | Get one application |
| POST | `/api/jobs` | Create an application |
| PUT | `/api/jobs/{id}` | Update an application |
| DELETE | `/api/jobs/{id}` | Delete an application |

Required fields are `company`, `role`, `status`, and `dateApplied`. Valid statuses are `Applied`, `Interview`, `Rejected`, and `Offer`.

## GitHub deployment

GitHub Pages can host the frontend, but it cannot run the Java server. The production setup uses a Docker host for the backend and GitHub Pages for the frontend.

1. Push the repository to GitHub.
2. Deploy the repository's `Dockerfile` on Railway, Render, Fly.io, or another Docker host.
3. Configure persistent storage at `/app/data` so `jobs.json` survives restarts.
4. Copy the backend's public URL.
5. Add a repository variable at **Settings > Secrets and variables > Actions > Variables**:

```text
Name: BACKEND_URL
Value: https://your-backend.example.com
```

6. Set **Settings > Pages > Source** to **GitHub Actions**.
7. Push to `main` or run the Pages workflow manually.

The Pages workflow writes `BACKEND_URL` into `web/config.js` before publishing. To test an already-published site without waiting for a workflow, append the backend URL once:

```text
https://your-user.github.io/your-repository/?api=https://your-backend.example.com
```

The browser remembers the URL for future visits.

## Project layout

```text
src/                    Java server and persistence code
web/                    Frontend files
data/jobs.json          Stored applications
Dockerfile              Container build for the backend
.github/workflows/      Build and Pages deployment workflows
```

## Checks

Compile the backend and open the dashboard. Create an application, edit it, change its status, search for it, and delete it. Restart the server and confirm that the data in `data/jobs.json` is still available.
