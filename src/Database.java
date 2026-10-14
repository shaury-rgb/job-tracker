import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public final class Database {

    public static final String DEMO_USER_ID = "00000000-0000-0000-0000-000000000001";
    public static final String DEMO_EMAIL = "demo@northstar.app";
    public static final String DEMO_PASSWORD = "password123";

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final Path dataDir;
    private final Path usersFile;
    private final Path sessionsFile;
    private final Path jobsFile;

    private final List<User> users = new ArrayList<>();
    private final List<Session> sessions = new ArrayList<>();
    private final List<JobApplication> jobs = new ArrayList<>();

    public Database(Path dataDir) throws IOException {
        if (dataDir != null && dataDir.toString().toLowerCase().endsWith(".json")) {
            this.dataDir = dataDir.getParent() != null ? dataDir.getParent() : Path.of(".");
        } else {
            this.dataDir = dataDir == null ? Path.of("data") : dataDir;
        }
        Files.createDirectories(this.dataDir);
        this.usersFile = this.dataDir.resolve("users.json");
        this.sessionsFile = this.dataDir.resolve("sessions.json");
        this.jobsFile = this.dataDir.resolve("jobs.json");

        loadAll();
        ensureSeedData();
    }

    // ==========================================
    // User Operations
    // ==========================================

    public synchronized User registerUser(String name, String email, String password) throws IOException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Full name is required");
        }
        if (name.trim().length() > 80) {
            throw new IllegalArgumentException("Name cannot exceed 80 characters");
        }
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email address is required");
        }
        String cleanEmail = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(cleanEmail).matches()) {
            throw new IllegalArgumentException("Invalid email address format");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long");
        }
        if (password.length() > 100) {
            throw new IllegalArgumentException("Password cannot exceed 100 characters");
        }

        for (User u : users) {
            if (u.email.equalsIgnoreCase(cleanEmail)) {
                throw new IllegalArgumentException("An account with this email already exists");
            }
        }

        String salt = AuthSecurity.generateSalt();
        String hash = AuthSecurity.hashPassword(password, salt);
        String userId = UUID.randomUUID().toString();

        User user = new User(userId, name.trim(), cleanEmail, hash, salt, null);
        users.add(user);
        saveUsers();
        return user;
    }

    public synchronized User authenticate(String email, String password) {
        if (email == null || password == null) return null;
        String cleanEmail = email.trim().toLowerCase();
        for (User u : users) {
            if (u.email.equalsIgnoreCase(cleanEmail)) {
                if (AuthSecurity.verifyPassword(password, u.salt, u.passwordHash)) {
                    return u;
                }
                return null;
            }
        }
        return null;
    }

    public synchronized User getUserById(String id) {
        if (id == null) return null;
        return users.stream().filter(u -> id.equals(u.id)).findFirst().orElse(null);
    }

    public synchronized User getUserByEmail(String email) {
        if (email == null) return null;
        String cleanEmail = email.trim().toLowerCase();
        return users.stream().filter(u -> cleanEmail.equalsIgnoreCase(u.email)).findFirst().orElse(null);
    }

    public synchronized List<User> getAllUsers() {
        return new ArrayList<>(users);
    }

    // ==========================================
    // Session Operations
    // ==========================================

    public synchronized Session createSession(String userId) throws IOException {
        User user = getUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("Cannot create session: User not found");
        }
        // Cleanup old expired sessions
        sessions.removeIf(Session::isExpired);

        String token = AuthSecurity.generateToken();
        Session session = new Session(token, userId, 30); // 30 days valid
        sessions.add(session);
        saveSessions();
        return session;
    }

    public synchronized User validateToken(String token) throws IOException {
        if (token == null || token.isBlank()) return null;
        Session matched = null;
        for (Session s : sessions) {
            if (token.equals(s.token)) {
                matched = s;
                break;
            }
        }
        if (matched == null) return null;
        if (matched.isExpired()) {
            sessions.remove(matched);
            saveSessions();
            return null;
        }
        return getUserById(matched.userId);
    }

    public synchronized boolean revokeSession(String token) throws IOException {
        if (token == null) return false;
        boolean removed = sessions.removeIf(s -> token.equals(s.token));
        if (removed) {
            saveSessions();
        }
        return removed;
    }

    // ==========================================
    // Scoped Job Operations (Per-User Isolation)
    // ==========================================

    public synchronized List<JobApplication> queryJobs(String userId, String status, String search, String sort) {
        if (userId == null || userId.isBlank()) {
            return new ArrayList<>();
        }
        List<JobApplication> result = new ArrayList<>();
        String queryTerm = (search == null) ? "" : search.trim().toLowerCase();

        for (JobApplication job : jobs) {
            if (!userId.equals(job.userId)) {
                continue;
            }
            if (status != null && !status.isBlank() && !"All".equalsIgnoreCase(status) && !job.status.equalsIgnoreCase(status)) {
                continue;
            }
            if (!queryTerm.isEmpty()) {
                String haystack = (job.company + " " + job.role + " " + job.location + " " + job.notes).toLowerCase();
                if (!haystack.contains(queryTerm)) {
                    continue;
                }
            }
            result.add(job);
        }

        if ("oldest".equalsIgnoreCase(sort)) {
            result.sort((a, b) -> (a.dateApplied == null ? "" : a.dateApplied).compareTo(b.dateApplied == null ? "" : b.dateApplied));
        } else if ("company_asc".equalsIgnoreCase(sort)) {
            result.sort((a, b) -> (a.company == null ? "" : a.company).compareToIgnoreCase(b.company == null ? "" : b.company));
        } else if ("company_desc".equalsIgnoreCase(sort)) {
            result.sort((a, b) -> (b.company == null ? "" : b.company).compareToIgnoreCase(a.company == null ? "" : a.company));
        } else {
            // default: newest first
            result.sort((a, b) -> (b.dateApplied == null ? "" : b.dateApplied).compareTo(a.dateApplied == null ? "" : a.dateApplied));
        }
        return result;
    }

    public synchronized JobApplication findJob(String userId, String jobId) {
        if (userId == null || jobId == null) return null;
        return jobs.stream().filter(j -> jobId.equals(j.id) && userId.equals(j.userId)).findFirst().orElse(null);
    }

    public synchronized JobApplication createJob(String userId, JobApplication job) throws IOException {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required to create a job");
        }
        job.id = UUID.randomUUID().toString();
        job.userId = userId;
        jobs.add(job);
        saveJobs();
        return job;
    }

    public synchronized JobApplication updateJob(String userId, String jobId, JobApplication replacement) throws IOException {
        if (userId == null || jobId == null) return null;
        for (int i = 0; i < jobs.size(); i++) {
            JobApplication current = jobs.get(i);
            if (jobId.equals(current.id) && userId.equals(current.userId)) {
                replacement.id = jobId;
                replacement.userId = userId;
                jobs.set(i, replacement);
                saveJobs();
                return replacement;
            }
        }
        return null;
    }

    public synchronized boolean deleteJob(String userId, String jobId) throws IOException {
        if (userId == null || jobId == null) return false;
        boolean removed = jobs.removeIf(j -> jobId.equals(j.id) && userId.equals(j.userId));
        if (removed) {
            saveJobs();
        }
        return removed;
    }

    public synchronized int countJobs() {
        return jobs.size();
    }

    // ==========================================
    // Atomic Persistence & Loading
    // ==========================================

    @SuppressWarnings("unchecked")
    private void loadAll() throws IOException {
        // 1. Users
        users.clear();
        if (Files.exists(usersFile)) {
            String content = Files.readString(usersFile, StandardCharsets.UTF_8).trim();
            if (!content.isEmpty()) {
                Object parsed = Json.parse(content);
                if (parsed instanceof List<?>) {
                    for (Object item : (List<?>) parsed) {
                        if (item instanceof Map<?, ?>) {
                            users.add(User.fromMap((Map<String, Object>) item));
                        }
                    }
                }
            }
        }

        // 2. Sessions
        sessions.clear();
        if (Files.exists(sessionsFile)) {
            String content = Files.readString(sessionsFile, StandardCharsets.UTF_8).trim();
            if (!content.isEmpty()) {
                Object parsed = Json.parse(content);
                if (parsed instanceof List<?>) {
                    for (Object item : (List<?>) parsed) {
                        if (item instanceof Map<?, ?>) {
                            sessions.add(Session.fromMap((Map<String, Object>) item));
                        }
                    }
                }
            }
        }

        // 3. Jobs
        jobs.clear();
        if (Files.exists(jobsFile)) {
            String content = Files.readString(jobsFile, StandardCharsets.UTF_8).trim();
            if (!content.isEmpty()) {
                Object parsed = Json.parse(content);
                if (parsed instanceof List<?>) {
                    for (Object item : (List<?>) parsed) {
                        if (item instanceof Map<?, ?>) {
                            jobs.add(JobApplication.fromMap((Map<String, Object>) item));
                        }
                    }
                }
            }
        }
    }

    private void ensureSeedData() throws IOException {
        boolean usersChanged = false;
        boolean jobsChanged = false;

        // Ensure Demo User exists
        User demoUser = users.stream().filter(u -> DEMO_EMAIL.equalsIgnoreCase(u.email)).findFirst().orElse(null);
        if (demoUser == null) {
            String salt = AuthSecurity.generateSalt();
            String hash = AuthSecurity.hashPassword(DEMO_PASSWORD, salt);
            demoUser = new User(DEMO_USER_ID, "Alex Mercer", DEMO_EMAIL, hash, salt, null);
            users.add(demoUser);
            usersChanged = true;
        }

        // Migrate any unassigned jobs to demo user
        for (JobApplication job : jobs) {
            if (job.userId == null || job.userId.isBlank()) {
                job.userId = demoUser.id;
                jobsChanged = true;
            }
        }

        // If no jobs exist at all, populate sample jobs for demo user
        if (jobs.isEmpty()) {
            populateDefaultSampleJobs(demoUser.id);
            jobsChanged = true;
        }

        if (usersChanged) saveUsers();
        if (jobsChanged) saveJobs();
    }

    private void populateDefaultSampleJobs(String userId) {
        JobApplication j1 = new JobApplication();
        j1.id = "6a9e1d80-5b1e-450a-9d90-bc4f1a271001";
        j1.userId = userId;
        j1.company = "Stripe";
        j1.role = "Staff Infrastructure Engineer";
        j1.location = "San Francisco, CA (Hybrid)";
        j1.salary = "$190,000 - $225,000";
        j1.applicationUrl = "https://stripe.com/jobs";
        j1.status = "Interview";
        j1.dateApplied = "2026-10-02";
        j1.notes = "Completed system design round with Tech Lead. Round 3 scheduled for Thursday.";
        jobs.add(j1);

        JobApplication j2 = new JobApplication();
        j2.id = "6a9e1d80-5b1e-450a-9d90-bc4f1a271002";
        j2.userId = userId;
        j2.company = "Figma";
        j2.role = "Senior Product Designer & Engineer";
        j2.location = "Remote";
        j2.salary = "$175,000 - $210,000";
        j2.applicationUrl = "https://figma.com/careers";
        j2.status = "Offer";
        j2.dateApplied = "2026-09-18";
        j2.notes = "Offer extended! $185k base + equity grant. Review benefits and respond by end of week.";
        jobs.add(j2);

        JobApplication j3 = new JobApplication();
        j3.id = "6a9e1d80-5b1e-450a-9d90-bc4f1a271003";
        j3.userId = userId;
        j3.company = "Apple";
        j3.role = "Platform Systems Engineer";
        j3.location = "Cupertino, CA";
        j3.salary = "$180,000 - $215,000";
        j3.applicationUrl = "https://apple.com/jobs";
        j3.status = "Applied";
        j3.dateApplied = "2026-10-08";
        j3.notes = "Referred by Michael from Core OS team. Recruiter acknowledged receipt.";
        jobs.add(j3);
    }

    private void saveUsers() throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (User u : users) list.add(u.toMap());
        atomicWrite(usersFile, Json.stringify(list));
    }

    private void saveSessions() throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Session s : sessions) list.add(s.toMap());
        atomicWrite(sessionsFile, Json.stringify(list));
    }

    private void saveJobs() throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JobApplication j : jobs) list.add(j.toMap());
        atomicWrite(jobsFile, Json.stringify(list));
    }

    private void atomicWrite(Path target, String content) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
