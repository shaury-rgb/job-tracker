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

