import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class User {

    public String id = "";
    public String name = "";
    public String email = "";
    public String passwordHash = "";
    public String salt = "";
    public String createdAt = "";

    public User() {
        this.createdAt = Instant.now().toString();
    }

    public User(String id, String name, String email, String passwordHash, String salt, String createdAt) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.email = email == null ? "" : email.trim().toLowerCase();
        this.passwordHash = passwordHash == null ? "" : passwordHash;
        this.salt = salt == null ? "" : salt;
        this.createdAt = createdAt == null ? Instant.now().toString() : createdAt;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("email", email);
        map.put("passwordHash", passwordHash);
        map.put("salt", salt);
        map.put("createdAt", createdAt);
        return map;
    }

    public Map<String, Object> toPublicMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("email", email);
        map.put("createdAt", createdAt);
        return map;
    }

    public static User fromMap(Map<String, Object> map) {
        User user = new User();
        user.id = (String) map.getOrDefault("id", "");
        user.name = (String) map.getOrDefault("name", "");
        user.email = (String) map.getOrDefault("email", "");
        user.passwordHash = (String) map.getOrDefault("passwordHash", "");
        user.salt = (String) map.getOrDefault("salt", "");
        user.createdAt = (String) map.getOrDefault("createdAt", "");
        return user;
    }
}
