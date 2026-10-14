import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class TestRunner {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("Running Job Tracker Test Suite (Java 17)");
        System.out.println("==========================================\n");

        runTest("Json: Primitive Serialization and Parsing", TestRunner::testJsonPrimitives);
        runTest("Json: Objects, Arrays, and Nesting", TestRunner::testJsonStructures);
        runTest("Json: Escapes and Unicode Handling", TestRunner::testJsonEscapes);
        runTest("Json: Syntax Errors and Trailing Garbage Detection", TestRunner::testJsonErrors);
        runTest("JobApplication: Mapping, Null Safety, and UserId", TestRunner::testJobApplicationModel);
        runTest("AuthSecurity: Password Hashing, Salts, and Verification", TestRunner::testAuthSecurity);
        runTest("Database: User Registration, Duplicates, and Sessions", TestRunner::testDatabaseUsersAndSessions);
        runTest("Database: Multi-Tenant Job Isolation", TestRunner::testDatabaseMultiTenantJobs);
        runTest("Database: File Persistence and Atomic Reloading", TestRunner::testDatabasePersistence);
        runTest("API: Full Auth, Multi-Tenancy, and HTTP Lifecycle", TestRunner::testApiIntegration);

        System.out.println("\n==========================================");
        System.out.printf("Results: %d PASSED, %d FAILED%n", passed, failed);
        System.out.println("==========================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void runTest(String name, TestCase testCase) {
        try {
            testCase.run();
            System.out.printf("  [PASS] %s%n", name);
            passed++;
        } catch (Throwable t) {
            System.out.printf("  [FAIL] %s: %s%n", name, t.getMessage());
            t.printStackTrace(System.out);
            failed++;
        }
    }

    private static void assertEq(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError(message + " (expected: " + expected + ", got: " + actual + ")");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void testJsonPrimitives() {
        assertEq("\"hello world\"", Json.stringify("hello world"), "String stringify");
        assertEq("hello world", Json.parse("\"hello world\""), "String parse");

        assertEq("42", Json.stringify(42), "Number stringify");
        assertEq(42L, Json.parse("42"), "Integer parse");
        assertEq(3.14, Json.parse("3.14"), "Double parse");

        assertEq("true", Json.stringify(true), "Boolean stringify");
        assertEq(Boolean.TRUE, Json.parse("true"), "Boolean parse true");
        assertEq(Boolean.FALSE, Json.parse("false"), "Boolean parse false");

        assertEq("null", Json.stringify(null), "Null stringify");
        assertEq(null, Json.parse("null"), "Null parse");
    }

    @SuppressWarnings("unchecked")
    private static void testJsonStructures() {
        Map<String, Object> map = Map.of("key", "val", "num", 100);
        String json = Json.stringify(map);
        Object parsed = Json.parse(json);
        assertTrue(parsed instanceof Map<?, ?>, "Parsed should be Map");
        Map<String, Object> parsedMap = (Map<String, Object>) parsed;
        assertEq("val", parsedMap.get("key"), "Map value matches");
        assertEq(100L, parsedMap.get("num"), "Map number matches");

        List<Object> list = List.of("a", 1, true);
        String listJson = Json.stringify(list);
        Object parsedList = Json.parse(listJson);
        assertTrue(parsedList instanceof List<?>, "Parsed should be List");
        List<Object> resultList = (List<Object>) parsedList;
        assertEq(3, resultList.size(), "List size matches");
        assertEq("a", resultList.get(0), "List element 0 matches");
    }

    private static void testJsonEscapes() {
        String original = "Quotes: \" Backslash: \\ Newline: \n Tab: \t Carriage: \r Formfeed: \f Backspace: \b";
        String encoded = Json.stringify(original);
        String decoded = (String) Json.parse(encoded);
        assertEq(original, decoded, "Escaped characters roundtrip correctly");

        // Unicode escape
        String unicode = (String) Json.parse("\"\\u0041\\u0042\\u0043\"");
        assertEq("ABC", unicode, "Unicode escapes parse correctly");
    }

    private static void testJsonErrors() {
        assertThrows(() -> Json.parse(null), "Null input should throw");
        assertThrows(() -> Json.parse("{"), "Unclosed object should throw");
        assertThrows(() -> Json.parse("[1, 2,"), "Unclosed array should throw");
        assertThrows(() -> Json.parse("{\"a\": 1} trailing"), "Trailing characters should throw");
        assertThrows(() -> Json.parse("{invalid}"), "Invalid key should throw");
    }

    private static void testJobApplicationModel() {
        JobApplication job = new JobApplication();
        job.userId = "user-123";
        job.company = "Acme Corp";
        job.role = "Software Engineer";
        job.status = "Interview";
        job.dateApplied = "2026-10-01";
        job.location = "Remote";
        job.salary = "$150k";
        job.applicationUrl = "https://acme.com/jobs/1";
        job.notes = "Great recruiter conversation";

        Map<String, Object> map = job.toMap();
        assertEq("user-123", map.get("userId"), "UserId in map");
        assertEq("Acme Corp", map.get("company"), "Company in map");
        assertEq("Interview", map.get("status"), "Status in map");

        JobApplication reconstructed = JobApplication.fromMap(map);
        assertEq(job.userId, reconstructed.userId, "Reconstructed userId");
        assertEq(job.company, reconstructed.company, "Reconstructed company");
        assertEq(job.role, reconstructed.role, "Reconstructed role");
        assertEq(job.salary, reconstructed.salary, "Reconstructed salary");
        assertEq(job.notes, reconstructed.notes, "Reconstructed notes");

        // Null-safety check
        JobApplication empty = new JobApplication();
        Map<String, Object> emptyMap = empty.toMap();
        assertEq("", emptyMap.get("company"), "Null field defaults to empty string");
        assertEq("", emptyMap.get("userId"), "Null userId defaults to empty string");
    }

    private static void testAuthSecurity() {
        String salt1 = AuthSecurity.generateSalt();
        String salt2 = AuthSecurity.generateSalt();
        assertTrue(!salt1.equals(salt2), "Salts must be randomly generated and unique");

        String password = "SecretPassword!123";
        String hash1 = AuthSecurity.hashPassword(password, salt1);
        String hash2 = AuthSecurity.hashPassword(password, salt1);
        assertEq(hash1, hash2, "Hashing same password and salt must produce identical hash");

        assertTrue(AuthSecurity.verifyPassword(password, salt1, hash1), "Verification succeeds for valid password");
        assertTrue(!AuthSecurity.verifyPassword("WrongPassword", salt1, hash1), "Verification fails for invalid password");
        assertTrue(!AuthSecurity.verifyPassword(password, salt2, hash1), "Verification fails with wrong salt");

        String token1 = AuthSecurity.generateToken();
        String token2 = AuthSecurity.generateToken();
        assertTrue(!token1.equals(token2), "Session tokens must be distinct");
        assertTrue(token1.length() >= 32, "Tokens must have adequate cryptographic entropy");
    }

    private static void testDatabaseUsersAndSessions() throws IOException {
        Path tempDir = Files.createTempDirectory("test-db-users");
        try {
            Database db = new Database(tempDir);
            // Verify default demo user exists
            User demoUser = db.getUserByEmail(Database.DEMO_EMAIL);
            assertTrue(demoUser != null, "Default demo user seeded");
            assertTrue(db.authenticate(Database.DEMO_EMAIL, Database.DEMO_PASSWORD) != null, "Demo login succeeds");

            // Register new user
            User registered = db.registerUser("Jane Doe", "jane@example.com", "mypassword123");
            assertTrue(registered.id != null && !registered.id.isBlank(), "Registered user has id");
            assertEq("Jane Doe", registered.name, "User name matches");
            assertEq("jane@example.com", registered.email, "User email matches");

            // Test duplicate email rejection
            assertThrows(() -> {
                try {
                    db.registerUser("Jane Clone", "JANE@example.com", "otherpassword");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, "Duplicate email should throw");

            // Test authentication
            User authUser = db.authenticate("jane@example.com", "mypassword123");
            assertTrue(authUser != null, "Authentication succeeds with correct password");
            assertEq(registered.id, authUser.id, "Authenticated user id matches");

            assertTrue(db.authenticate("jane@example.com", "wrong") == null, "Authentication fails with wrong password");
            assertTrue(db.authenticate("unknown@example.com", "any") == null, "Authentication fails for nonexistent email");

            // Test sessions
            Session session = db.createSession(registered.id);
            assertTrue(session.token != null && !session.token.isBlank(), "Session token generated");

            User tokenUser = db.validateToken(session.token);
            assertTrue(tokenUser != null, "Token validation returns user");
            assertEq(registered.id, tokenUser.id, "Token user matches registered user");

            // Revoke session
            boolean revoked = db.revokeSession(session.token);
            assertTrue(revoked, "Revocation returns true");
            assertTrue(db.validateToken(session.token) == null, "Revoked token is no longer valid");

        } finally {
            cleanupDir(tempDir);
        }
    }

    private static void testDatabaseMultiTenantJobs() throws IOException {
        Path tempDir = Files.createTempDirectory("test-db-multitenant");
        try {
            Database db = new Database(tempDir);
            User userA = db.registerUser("Alice", "alice@test.com", "passAlice123");
            User userB = db.registerUser("Bob", "bob@test.com", "passBob123");

            JobApplication jobA = new JobApplication();
            jobA.company = "Stripe";
            jobA.role = "Platform Engineer";
            jobA.status = "Applied";
            jobA.dateApplied = "2026-10-01";
            JobApplication createdA = db.createJob(userA.id, jobA);

            JobApplication jobB = new JobApplication();
            jobB.company = "Netflix";
            jobB.role = "Systems Engineer";
            jobB.status = "Interview";
            jobB.dateApplied = "2026-10-02";
            JobApplication createdB = db.createJob(userB.id, jobB);

            // Verify User A only sees their own jobs
            List<JobApplication> jobsA = db.queryJobs(userA.id, null, null, null);
            assertEq(1, jobsA.size(), "User A only sees 1 job");
            assertEq("Stripe", jobsA.get(0).company, "User A job is Stripe");

            // Verify User B only sees their own jobs
            List<JobApplication> jobsB = db.queryJobs(userB.id, null, null, null);
            assertEq(1, jobsB.size(), "User B only sees 1 job");
            assertEq("Netflix", jobsB.get(0).company, "User B job is Netflix");

            // Cross-user access prevention
            assertTrue(db.findJob(userA.id, createdB.id) == null, "User A cannot find User B's job");
            assertTrue(db.findJob(userB.id, createdA.id) == null, "User B cannot find User A's job");

            // Cross-user update prevention
            JobApplication maliciousUpdate = new JobApplication();
            maliciousUpdate.company = "Hacked";
            maliciousUpdate.role = "Hacker";
            maliciousUpdate.status = "Offer";
            maliciousUpdate.dateApplied = "2026-10-01";
            assertTrue(db.updateJob(userA.id, createdB.id, maliciousUpdate) == null, "User A cannot update User B's job");

            // Cross-user delete prevention
            assertTrue(!db.deleteJob(userA.id, createdB.id), "User A cannot delete User B's job");
            assertEq(1, db.queryJobs(userB.id, null, null, null).size(), "User B job remains untouched");

            // User A can delete own job
            assertTrue(db.deleteJob(userA.id, createdA.id), "User A can delete own job");
            assertEq(0, db.queryJobs(userA.id, null, null, null).size(), "User A has 0 jobs after deletion");
        } finally {
            cleanupDir(tempDir);
        }
    }

    private static void testDatabasePersistence() throws IOException {
        Path tempDir = Files.createTempDirectory("test-db-persist");
        try {
            Database db1 = new Database(tempDir);
            User u = db1.registerUser("Charlie", "charlie@test.com", "passCharlie123");
            JobApplication j = new JobApplication();
            j.company = "OpenAI";
            j.role = "Research Scientist";
            j.status = "Interview";
            j.dateApplied = "2026-10-05";
            db1.createJob(u.id, j);

            // Re-instantiate database with same folder
            Database db2 = new Database(tempDir);
            User reloadedUser = db2.getUserByEmail("charlie@test.com");
            assertTrue(reloadedUser != null, "Persisted user loaded");
            assertEq("Charlie", reloadedUser.name, "User name persisted");

            List<JobApplication> userJobs = db2.queryJobs(reloadedUser.id, null, null, null);
            assertEq(1, userJobs.size(), "Persisted job loaded");
            assertEq("OpenAI", userJobs.get(0).company, "Job company persisted");
        } finally {
            cleanupDir(tempDir);
        }
    }

    @SuppressWarnings("unchecked")
    private static void testApiIntegration() throws Exception {
        Path tempDir = Files.createTempDirectory("test-api-suite");
        Path webDir = Files.createTempDirectory("test-web-dir");
        Files.writeString(webDir.resolve("index.html"), "<!doctype html><title>Northstar | Job Tracker</title>");
        Files.writeString(webDir.resolve("styles.css"), "body { margin: 0; }");

        HttpServer server = null;
        try {
            server = Main.startServer(0, tempDir, webDir);
            int port = server.getAddress().getPort();
            String base = "http://localhost:" + port;
            HttpClient client = HttpClient.newHttpClient();

            // 1. Health check
            HttpRequest healthReq = HttpRequest.newBuilder().uri(URI.create(base + "/api/health")).GET().build();
            HttpResponse<String> healthRes = client.send(healthReq, HttpResponse.BodyHandlers.ofString());
            assertEq(200, healthRes.statusCode(), "Health check 200");
            Map<String, Object> healthJson = (Map<String, Object>) Json.parse(healthRes.body());
            assertEq("UP", healthJson.get("status"), "Health status UP");

            // 2. Unauthenticated GET /api/jobs -> 401 Unauthorized
            HttpRequest unauthReq = HttpRequest.newBuilder().uri(URI.create(base + "/api/jobs")).GET().build();
            HttpResponse<String> unauthRes = client.send(unauthReq, HttpResponse.BodyHandlers.ofString());
            assertEq(401, unauthRes.statusCode(), "Unauthenticated request returns 401");

            // 3. Register user via POST /api/auth/register
            String registerPayload = "{\"name\":\"Sara Connor\",\"email\":\"sara@cyberdyne.com\",\"password\":\"terminator2026\"}";
            HttpRequest registerReq = HttpRequest.newBuilder().uri(URI.create(base + "/api/auth/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(registerPayload)).build();
            HttpResponse<String> registerRes = client.send(registerReq, HttpResponse.BodyHandlers.ofString());
            assertEq(201, registerRes.statusCode(), "Register returns 201");
            Map<String, Object> regMap = (Map<String, Object>) Json.parse(registerRes.body());
            String token = (String) regMap.get("token");
            assertTrue(token != null && !token.isBlank(), "Register returned session token");

            // 4. Verify /api/auth/me with Bearer token
            HttpRequest meReq = HttpRequest.newBuilder().uri(URI.create(base + "/api/auth/me"))
                    .header("Authorization", "Bearer " + token)
                    .GET().build();
            HttpResponse<String> meRes = client.send(meReq, HttpResponse.BodyHandlers.ofString());
            assertEq(200, meRes.statusCode(), "GET /api/auth/me returns 200");
            Map<String, Object> meMap = (Map<String, Object>) Json.parse(meRes.body());
            assertEq("sara@cyberdyne.com", meMap.get("email"), "Authenticated user email matches");

            // 5. POST create job with Bearer token
            String newJobJson = "{\"company\":\"Cyberdyne\",\"role\":\"Defense AI Engineer\",\"status\":\"Applied\",\"dateApplied\":\"2026-10-12\",\"applicationUrl\":\"https://cyberdyne.com/careers\"}";
            HttpRequest postReq = HttpRequest.newBuilder().uri(URI.create(base + "/api/jobs"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(newJobJson)).build();
            HttpResponse<String> postRes = client.send(postReq, HttpResponse.BodyHandlers.ofString());
            assertEq(201, postRes.statusCode(), "POST job returns 201");
            Map<String, Object> created = (Map<String, Object>) Json.parse(postRes.body());
            String jobId = (String) created.get("id");
            assertTrue(jobId != null && !jobId.isEmpty(), "Created job has id");

            // 6. GET single job
            HttpRequest getOneReq = HttpRequest.newBuilder().uri(URI.create(base + "/api/jobs/" + jobId))
                    .header("Authorization", "Bearer " + token)
                    .GET().build();
            HttpResponse<String> getOneRes = client.send(getOneReq, HttpResponse.BodyHandlers.ofString());
            assertEq(200, getOneRes.statusCode(), "GET single job returns 200");

            // 7. PUT update job
            String updatedJobJson = "{\"company\":\"Cyberdyne\",\"role\":\"Lead Defense AI Architect\",\"status\":\"Interview\",\"dateApplied\":\"2026-10-12\"}";
            HttpRequest putReq = HttpRequest.newBuilder().uri(URI.create(base + "/api/jobs/" + jobId))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .PUT(HttpRequest.BodyPublishers.ofString(updatedJobJson)).build();
            HttpResponse<String> putRes = client.send(putReq, HttpResponse.BodyHandlers.ofString());
            assertEq(200, putRes.statusCode(), "PUT returns 200");
            Map<String, Object> updatedMap = (Map<String, Object>) Json.parse(putRes.body());
            assertEq("Lead Defense AI Architect", updatedMap.get("role"), "Role updated");
            assertEq("Interview", updatedMap.get("status"), "Status updated");

            // 8. Query parameters
            HttpRequest queryReq = HttpRequest.newBuilder().uri(URI.create(base + "/api/jobs?status=Interview&search=Cyberdyne"))
                    .header("Authorization", "Bearer " + token)
                    .GET().build();
            HttpResponse<String> queryRes = client.send(queryReq, HttpResponse.BodyHandlers.ofString());
            assertEq(200, queryRes.statusCode(), "Query returns 200");
            List<?> queryList = (List<?>) Json.parse(queryRes.body());
            assertEq(1, queryList.size(), "Query returns 1 match");

            // 9. Static file serving
            HttpRequest staticHtmlReq = HttpRequest.newBuilder().uri(URI.create(base + "/")).GET().build();
            HttpResponse<String> staticHtmlRes = client.send(staticHtmlReq, HttpResponse.BodyHandlers.ofString());
            assertEq(200, staticHtmlRes.statusCode(), "Static index.html 200");

            // 10. DELETE job
            HttpRequest deleteReq = HttpRequest.newBuilder().uri(URI.create(base + "/api/jobs/" + jobId))
                    .header("Authorization", "Bearer " + token)
                    .DELETE().build();
            HttpResponse<String> deleteRes = client.send(deleteReq, HttpResponse.BodyHandlers.ofString());
            assertEq(204, deleteRes.statusCode(), "DELETE returns 204");

            // 11. GET deleted job -> 404
            HttpRequest getDeletedReq = HttpRequest.newBuilder().uri(URI.create(base + "/api/jobs/" + jobId))
                    .header("Authorization", "Bearer " + token)
                    .GET().build();
            HttpResponse<String> getDeletedRes = client.send(getDeletedReq, HttpResponse.BodyHandlers.ofString());
            assertEq(404, getDeletedRes.statusCode(), "GET deleted job returns 404");

            // 12. Logout via POST /api/auth/logout
            HttpRequest logoutReq = HttpRequest.newBuilder().uri(URI.create(base + "/api/auth/logout"))
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<String> logoutRes = client.send(logoutReq, HttpResponse.BodyHandlers.ofString());
            assertEq(204, logoutRes.statusCode(), "Logout returns 204");

            // 13. Verify token is now invalid -> 401
            HttpResponse<String> postLogoutRes = client.send(meReq, HttpResponse.BodyHandlers.ofString());
            assertEq(401, postLogoutRes.statusCode(), "Revoked token returns 401");

        } finally {
            if (server != null) {
                server.stop(0);
            }
            cleanupDir(webDir);
            cleanupDir(tempDir);
        }
    }

    private static void cleanupDir(Path dir) {
        try {
            if (Files.exists(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {}
                    });
                }
            }
        } catch (Exception ignored) {}
    }

    private static void assertThrows(Runnable runnable, String message) {
        try {
            runnable.run();
            throw new AssertionError(message + " (expected exception but completed normally)");
        } catch (Throwable t) {
            if (t instanceof AssertionError) throw (AssertionError) t;
            // Exception expected, test passed
        }
    }

    @FunctionalInterface
    private interface TestCase {
        void run() throws Throwable;
    }
}
