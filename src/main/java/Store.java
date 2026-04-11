import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Store {
    private static final Map<String, Value> map;
    static {
        map = new HashMap<>();
    }
    public static void put(String key, String value, long liveTime) {
        map.put(key, new Value(value, liveTime));
    }
    public static Optional<String> get(String key) {
        if(map.containsKey(key)) {
            Value value = map.get(key);
            if(value.timestamp == -1) {
                return Optional.of(value.value);
            } else {
                long duration = System.currentTimeMillis() - value.timestamp;
                if(duration < value.liveTime) {
                    return Optional.of(value.value);
                } else {
                    map.remove(key);
                }
            }
        }
        return Optional.empty();
    }

    private static class Value {
        private long timestamp;
        private long liveTime;
        private String value;
        // liveTime value expected in millis
        public Value(String value,long liveTime) {
            this.value = value;
            this.liveTime = liveTime;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
