import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class JobStore {
    private final Path file;
    private final List<JobApplication> jobs = new ArrayList<>();

    public JobStore(Path file) throws IOException {
        this.file = file;
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        if (Files.notExists(file)) {
            Files.writeString(file, "[]", StandardCharsets.UTF_8, StandardOpenOption.CREATE);
        }
        load();
    }

    public synchronized List<JobApplication> all() {
        return query(null, null, null);
    }

    public synchronized List<JobApplication> query(String status, String search, String sort) {
        List<JobApplication> result = new ArrayList<>();
        String queryTerm = (search == null) ? "" : search.trim().toLowerCase();
        for (JobApplication job : jobs) {
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

    public synchronized JobApplication find(String id) {
        if (id == null) return null;
        return jobs.stream().filter(job -> id.equals(job.id)).findFirst().orElse(null);
    }

    public synchronized JobApplication create(JobApplication job) throws IOException {
        job.id = UUID.randomUUID().toString();
        jobs.add(job);
        save();
        return job;
    }

    public synchronized JobApplication update(String id, JobApplication replacement) throws IOException {
        if (id == null) return null;
        for (int i = 0; i < jobs.size(); i++) {
            if (id.equals(jobs.get(i).id)) {
                replacement.id = id;
                jobs.set(i, replacement);
                save();
                return replacement;
            }
        }
        return null;
    }

    public synchronized boolean delete(String id) throws IOException {
        if (id == null) return false;
        boolean removed = jobs.removeIf(job -> id.equals(job.id));
        if (removed) {
            save();
        }
        return removed;
    }

    public synchronized void reload() throws IOException {
        load();
    }

    @SuppressWarnings("unchecked")
    private void load() throws IOException {
        jobs.clear();
        String content = Files.readString(file, StandardCharsets.UTF_8).trim();
        if (content.isEmpty()) return;
        Object parsed = Json.parse(content);
        if (!(parsed instanceof List<?>)) {
            throw new IOException("Jobs file must contain a JSON array");
        }
        for (Object item : (List<?>) parsed) {
            if (item instanceof Map<?, ?>) {
                jobs.add(JobApplication.fromMap((Map<String, Object>) item));
            }
        }
    }

    private void save() throws IOException {
        List<Map<String, Object>> values = new ArrayList<>();
        for (JobApplication job : jobs) {
            values.add(job.toMap());
        }
        Files.writeString(file, Json.stringify(values), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
