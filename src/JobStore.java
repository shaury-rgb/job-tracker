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
        Files.createDirectories(file.getParent());
        if (Files.notExists(file)) Files.writeString(file, "[]", StandardCharsets.UTF_8, StandardOpenOption.CREATE);
        load();
    }

    public synchronized List<JobApplication> all() { return new ArrayList<>(jobs); }
    public synchronized JobApplication find(String id) { return jobs.stream().filter(job -> job.id.equals(id)).findFirst().orElse(null); }
    public synchronized JobApplication create(JobApplication job) throws IOException { job.id = UUID.randomUUID().toString(); jobs.add(job); save(); return job; }
    public synchronized JobApplication update(String id, JobApplication replacement) throws IOException {
        for (int i = 0; i < jobs.size(); i++) if (jobs.get(i).id.equals(id)) { replacement.id = id; jobs.set(i, replacement); save(); return replacement; }
        return null;
    }
    public synchronized boolean delete(String id) throws IOException { boolean removed = jobs.removeIf(job -> job.id.equals(id)); if (removed) save(); return removed; }

    private void load() throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8).trim();
        if (content.isEmpty()) return;
        Object parsed = Json.parse(content);
        if (!(parsed instanceof List<?>)) throw new IOException("data/jobs.json must contain a JSON array");
        for (Object item : (List<?>) parsed) if (item instanceof Map<?, ?>) jobs.add(JobApplication.fromMap((Map<String, Object>) item));
    }

    private void save() throws IOException {
        List<Map<String, Object>> values = new ArrayList<>();
        for (JobApplication job : jobs) values.add(job.toMap());
        Files.writeString(file, Json.stringify(values), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
