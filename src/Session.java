import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Session {

    public String token = "";
    public String userId = "";
    public String createdAt = "";
    public String expiresAt = "";

    public Session() {}

    public Session(String token, String userId, long daysValid) {
        this.token = token;
        this.userId = userId;
        Instant now = Instant.now();
        this.createdAt = now.toString();
        this.expiresAt = now.plus(daysValid, ChronoUnit.DAYS).toString();
    }

    public boolean isExpired() {
        if (expiresAt == null || expiresAt.isBlank()) return true;
        try {
            return Instant.now().isAfter(Instant.parse(expiresAt));
        } catch (Exception e) {
            return true;
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("token", token);
        map.put("userId", userId);
        map.put("createdAt", createdAt);
        map.put("expiresAt", expiresAt);
        return map;
    }

    public static Session fromMap(Map<String, Object> map) {
        Session session = new Session();
        session.token = (String) map.getOrDefault("token", "");
        session.userId = (String) map.getOrDefault("userId", "");
        session.createdAt = (String) map.getOrDefault("createdAt", "");
        session.expiresAt = (String) map.getOrDefault("expiresAt", "");
        return session;
    }
}
