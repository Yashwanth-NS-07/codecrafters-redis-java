import java.nio.ByteBuffer;
import java.util.*;

public class StreamStore {
    private static final Map<String, List<Map<String, String>>> stream;
    static {
        stream = new HashMap<>();
    }

    public static boolean isStreamExists(String key) {
        return stream.containsKey(key);
    }

    private static void put(String streamName, Map<String, String> values) {
        stream.putIfAbsent(streamName, new ArrayList<>());
        stream.get(streamName).add(values);
    }

    public static void handleXADD(Request request, ByteBuffer byteBuffer) {
        String streamName = request.getParameter(1);
        String id = request.getParameter(2);
        Map<String, String> map = new HashMap<>();
        map.put("Id", id);
        for(int i = 3; i < request.getParameterCount(); i+=2) {
            String key = request.getParameter(i);
            String value = request.getParameter(i+1);
            map.put(key, value);
        }
        put(streamName, map);
        Response response = new Response();
        response.add(id);
        ResponseUtils.writeBulkStringResponse(response, byteBuffer);
    }
}
