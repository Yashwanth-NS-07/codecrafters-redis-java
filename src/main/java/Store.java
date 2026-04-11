import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Store {
    private static final Map<String, String> map;
    static {
        map = new HashMap<>();
    }
    public static void put(String key, String value) {
        map.put(key, value);
    }
    public static Optional<String> get(String key) {
        if(map.containsKey(key))
            return Optional.of(map.get(key));
        return Optional.empty();
    }
}
