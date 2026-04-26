import java.util.HashMap;
import java.util.Map;

public class InfoHandler {
    private static final Map<String, String> info = new HashMap<>();
    static {
        info.put("role", "master");
    }

    public static void put(String key, String value) {
        if(key.equals("--replicaof")) {
            info.put("role", "replica");
        } else {
            info.put(key, value);
        }
    }
    public static String handleINFO(Request request) {
        StringBuilder sb = new StringBuilder();
        for(Map.Entry<String, String> entry: info.entrySet()) {
            sb.append(entry.getKey());
            sb.append(":");
            sb.append(entry.getValue());
        }
        Response response = new Response();
        response.add(sb.toString());
        return ResponseUtils.writeBulkStringResponse(response);
    }
}
