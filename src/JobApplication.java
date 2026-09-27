import java.util.LinkedHashMap;
import java.util.Map;

public final class JobApplication {
    public String id;
    public String company;
    public String role;
    public String location;
    public String salary;
    public String applicationUrl;
    public String status;
    public String dateApplied;
    public String notes;

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("company", company);
        map.put("role", role);
        map.put("location", location);
        map.put("salary", salary);
        map.put("applicationUrl", applicationUrl);
        map.put("status", status);
        map.put("dateApplied", dateApplied);
        map.put("notes", notes);
        return map;
    }

    public static JobApplication fromMap(Map<String, Object> map) {
        JobApplication job = new JobApplication();
        job.id = value(map, "id");
        job.company = value(map, "company");
        job.role = value(map, "role");
        job.location = value(map, "location");
        job.salary = value(map, "salary");
        job.applicationUrl = value(map, "applicationUrl");
        job.status = value(map, "status");
        job.dateApplied = value(map, "dateApplied");
        job.notes = value(map, "notes");
        return job;
    }

    private static String value(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
