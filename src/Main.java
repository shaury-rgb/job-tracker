
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public final class Main {

    private static final String[] STATUSES = {"Applied", "Interview", "Rejected", "Offer"};
    private final JobStore store;
    private final Path webRoot;

    private Main(JobStore store, Path webRoot) {
        this.store = store;
        this.webRoot = webRoot;
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        JobStore store = new JobStore(Path.of("data", "jobs.json"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        Main app = new Main(store, Path.of("web"));
        server.createContext("/api/jobs", app::handleJobs);
        server.createContext("/", app::handleStatic);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Job Tracker running at http://localhost:" + port);
        CountDownLatch shutdown = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            shutdown.countDown();
        }));
        shutdown.await();
    }

    private void handleJobs(HttpExchange exchange) throws IOException {
        addCors(exchange.getResponseHeaders());
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            send(exchange, 204, "");
            return;
        }
        try {
            String method = exchange.getRequestMethod();
            String id = pathId(exchange.getRequestURI());
            if ("GET".equals(method)) {
                if (id == null) {
                    sendJson(exchange, 200, store.all());
                } else {
                    sendEntity(exchange, store.find(id));
                }
            } else if ("POST".equals(method)) {
                if (id != null) {
                    sendError(exchange, 400, "POST does not accept an id");
                } else {
                    JobApplication job = readJob(exchange);
                    sendJson(exchange, 201, store.create(job));
                }
            } else if ("PUT".equals(method)) {
                if (id == null) {
                    sendError(exchange, 400, "PUT requires a job id");
                } else {
                    JobApplication job = store.update(id, readJob(exchange));
                    sendEntity(exchange, job);
                }
            } else if ("DELETE".equals(method)) {
                if (id == null) {
                    sendError(exchange, 400, "DELETE requires a job id");
                } else if (store.delete(id)) {
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

    private JobApplication readJob(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Object parsed = Json.parse(body);
        if (!(parsed instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Request body must be a JSON object");
        }
        JobApplication job = JobApplication.fromMap((Map<String, Object>) parsed);
        validate(job);
        return job;
    }

    private void validate(JobApplication job) {
        if (blank(job.company) || blank(job.role) || blank(job.status) || blank(job.dateApplied)) {
            throw new IllegalArgumentException("Company, role, status and date applied are required");
        }
        boolean validStatus = false;
        for (String status : STATUSES) {
            if (status.equals(job.status)) {
                validStatus = true;
            }
        }
        if (!validStatus) {
            throw new IllegalArgumentException("Status must be Applied, Interview, Rejected or Offer");
        }
        if (!job.applicationUrl.isBlank() && !(job.applicationUrl.startsWith("http://") || job.applicationUrl.startsWith("https://"))) {
            throw new IllegalArgumentException("Application URL must start with http:// or https://");
        }
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        String requested = exchange.getRequestURI().getPath();
        if (requested.equals("/")) {
            requested = "/index.html";
        }
        Path file = webRoot.resolve(requested.substring(1)).normalize();
        if (!file.startsWith(webRoot.normalize()) || Files.notExists(file) || Files.isDirectory(file)) {
            sendError(exchange, 404, "File not found");
            return;
        }
        String type = requested.endsWith(".css") ? "text/css" : requested.endsWith(".js") ? "application/javascript" : "text/html";
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        byte[] body = Files.readAllBytes(file);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
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
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private void addCors(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private String pathId(URI uri) {
        String path = uri.getPath().substring("/api/jobs".length());
        return path.isBlank() ? null : path.substring(path.startsWith("/") ? 1 : 0);
    }
}
