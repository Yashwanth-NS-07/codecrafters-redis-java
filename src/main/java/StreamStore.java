import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StreamStore {
    private static final Map<String, List<Map<String, String>>> stream;
    static {
        stream = new HashMap<>();
    }

    public static boolean isStreamExists(String key) {
        return stream.containsKey(key);
    }

    private static void put(String streamName, Map<String, String> values) {

    }

    public static void handleXADD(Request request, ByteBuffer byteBuffer) {

    }
}
