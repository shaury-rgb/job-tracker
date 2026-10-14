import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public final class Main {

    private static final String[] STATUSES = {"Applied", "Interview", "Rejected", "Offer"};
    private final Database db;
    private final Path webRoot;

    public Main(Database db, Path webRoot) {
        this.db = db;
        this.webRoot = webRoot;
    }

    public static HttpServer startServer(int port, Path dataPath, Path webRoot) throws IOException {
        Database database = new Database(dataPath);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        Main app = new Main(database, webRoot);
        server.createContext("/api/auth", app::handleAuth);
        server.createContext("/api/jobs", app::handleJobs);
        server.createContext("/api/health", app::handleHealth);
        server.createContext("/", app::handleStatic);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        }));
        server.start();
        return server;
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = startServer(port, Path.of("data"), Path.of("web"));
        System.out.println("Job Tracker running at http://localhost:" + server.getAddress().getPort());
        CountDownLatch shutdown = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            shutdown.countDown();
        }));
        shutdown.await();
    }

    // ==========================================
    // Authentication Handlers
    // ==========================================

    private void handleAuth(HttpExchange exchange) throws IOException {
        addCors(exchange.getResponseHeaders());
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            send(exchange, 204, "");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        try {
            if ("/api/auth/register".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                handleRegister(exchange);
            } else if ("/api/auth/login".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                handleLogin(exchange);
            } else if ("/api/auth/logout".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                handleLogout(exchange);
            } else if ("/api/auth/me".equals(path) && "GET".equals(exchange.getRequestMethod())) {
                handleMe(exchange);
            } else {
                sendError(exchange, 404, "Authentication endpoint not found");
            }
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, 500, "Authentication processing error: " + e.getMessage());
        }
    }

    private void handleRegister(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readMap(exchange);
        String name = (String) body.get("name");
        String email = (String) body.get("email");
        String password = (String) body.get("password");

        User user = db.registerUser(name, email, password);
        Session session = db.createSession(user.id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", session.token);
        response.put("user", user.toPublicMap());
        sendJson(exchange, 201, response);
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readMap(exchange);
        String email = (String) body.get("email");
        String password = (String) body.get("password");

        if (blank(email) || blank(password)) {
            sendError(exchange, 400, "Email and password are required");
            return;
        }

        User user = db.authenticate(email, password);
        if (user == null) {
            sendError(exchange, 401, "Invalid email or password");
            return;
        }

        Session session = db.createSession(user.id);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", session.token);
        response.put("user", user.toPublicMap());
        sendJson(exchange, 200, response);
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authHeader.substring(7).trim();
            db.revokeSession(token);
        }
        send(exchange, 204, "");
    }

    private void handleMe(HttpExchange exchange) throws IOException {
        User user = authenticateUser(exchange);
        if (user == null) {
            sendError(exchange, 401, "Unauthorized");
            return;
        }
        sendJson(exchange, 200, user.toPublicMap());
    }

    // ==========================================
    // Job Application Handlers (Scoped to User)
    // ==========================================

    private void handleJobs(HttpExchange exchange) throws IOException {
        addCors(exchange.getResponseHeaders());
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            send(exchange, 204, "");
            return;
        }

        User user = authenticateUser(exchange);
        if (user == null) {
            sendError(exchange, 401, "Authentication required. Please sign in or create an account.");
            return;
        }

        try {
            String method = exchange.getRequestMethod();
            String id = pathId(exchange.getRequestURI());
            if ("GET".equals(method)) {
                if (id == null) {
                    Map<String, String> query = parseQueryParams(exchange.getRequestURI());
                    sendJson(exchange, 200, db.queryJobs(user.id, query.get("status"), query.get("search"), query.get("sort")));
                } else {
                    sendEntity(exchange, db.findJob(user.id, id));
                }
            } else if ("POST".equals(method)) {
                if (id != null) {
                    sendError(exchange, 400, "POST does not accept an id");
                } else {
                    JobApplication job = readJob(exchange);
                    sendJson(exchange, 201, db.createJob(user.id, job));
                }
            } else if ("PUT".equals(method)) {
                if (id == null) {
                    sendError(exchange, 400, "PUT requires a job id");
                } else {
                    JobApplication job = db.updateJob(user.id, id, readJob(exchange));
                    sendEntity(exchange, job);
                }
            } else if ("DELETE".equals(method)) {
                if (id == null) {
                    sendError(exchange, 400, "DELETE requires a job id");
                } else if (db.deleteJob(user.id, id)) {
                    send(exchange, 204, "");
                } else {
                    sendError(exchange, 404, "Job not found");
                }
            } else {
                sendError(exchange, 405, "Method not allowed");
            }
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, 500, "The server could not complete that request");
        }
    }

    private User authenticateUser(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            return null;
        }
        return db.validateToken(token);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Object parsed = Json.parse(body);
        if (!(parsed instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Request body must be a JSON object");
        }
        return (Map<String, Object>) parsed;
    }

    private JobApplication readJob(HttpExchange exchange) throws IOException {
        Map<String, Object> map = readMap(exchange);
        JobApplication job = JobApplication.fromMap(map);
        validate(job);
        return job;
    }

    private void validate(JobApplication job) {
        if (blank(job.company) || blank(job.role) || blank(job.status) || blank(job.dateApplied)) {
            throw new IllegalArgumentException("Company, role, status and date applied are required");
        }
        if (job.company.trim().length() > 120) {
            throw new IllegalArgumentException("Company name cannot exceed 120 characters");
        }
        if (job.role.trim().length() > 120) {
            throw new IllegalArgumentException("Role cannot exceed 120 characters");
        }
        boolean validStatus = false;
        for (String status : STATUSES) {
            if (status.equalsIgnoreCase(job.status)) {
                job.status = status;
                validStatus = true;
                break;
            }
        }
        if (!validStatus) {
            throw new IllegalArgumentException("Status must be Applied, Interview, Rejected or Offer");
        }
        if (!blank(job.applicationUrl)) {
            String url = job.applicationUrl.trim();
            if (!(url.startsWith("http://") || url.startsWith("https://"))) {
                throw new IllegalArgumentException("Application URL must start with http:// or https://");
            }
        }
        if (!job.dateApplied.trim().matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            throw new IllegalArgumentException("Date applied must be in YYYY-MM-DD format");
        }
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    // ==========================================
    // System Health & Static Asset Handlers
    // ==========================================

    private void handleHealth(HttpExchange exchange) throws IOException {
        addCors(exchange.getResponseHeaders());
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            send(exchange, 204, "");
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod()) && !"HEAD".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("timestamp", Instant.now().toString());
        body.put("jobsCount", db.countJobs());
        body.put("usersCount", db.getAllUsers().size());
        sendJson(exchange, 200, body);
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        addCors(exchange.getResponseHeaders());
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            send(exchange, 204, "");
            return;
        }
        boolean isHead = "HEAD".equals(exchange.getRequestMethod());
        if (!"GET".equals(exchange.getRequestMethod()) && !isHead) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        String requested = exchange.getRequestURI().getPath();
        if (requested.equals("/") || requested.isBlank()) {
            requested = "/index.html";
        }
        Path file = webRoot.resolve(requested.startsWith("/") ? requested.substring(1) : requested).normalize();
        if (!file.startsWith(webRoot.normalize()) || Files.notExists(file) || Files.isDirectory(file)) {
            sendError(exchange, 404, "File not found");
            return;
        }
        String contentType = getContentType(requested);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        byte[] body = Files.readAllBytes(file);
        if (isHead) {
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(body.length));
            exchange.sendResponseHeaders(200, -1);
            exchange.getResponseBody().close();
            return;
        }
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private String getContentType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".txt")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }

    private void sendEntity(HttpExchange exchange, JobApplication job) throws IOException {
        if (job == null) {
            sendError(exchange, 404, "Job not found");
        } else {
            sendJson(exchange, 200, job);
        }
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        send(exchange, status, Json.stringify(body));
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("error", message);
        sendJson(exchange, status, body);
    }

    private void send(HttpExchange exchange, int status, String body) throws IOException {
        boolean isHead = "HEAD".equalsIgnoreCase(exchange.getRequestMethod());
        if (status == 204 || body == null || isHead) {
            exchange.sendResponseHeaders(status, -1);
            exchange.getResponseBody().close();
            return;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private void addCors(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private String pathId(URI uri) {
        String fullPath = uri.getPath();
        if (!fullPath.startsWith("/api/jobs")) {
            return null;
        }
        String path = fullPath.substring("/api/jobs".length());
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path.isBlank() ? null : path.trim();
    }

    private Map<String, String> parseQueryParams(URI uri) {
        Map<String, String> params = new HashMap<>();
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String val = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                params.put(key, val);
            } else if (!pair.isBlank()) {
                params.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
            }
        }
        return params;
    }
}
