import java.util.HashMap;
import java.util.Map;

public class InfoHandler {
    private static final Map<String, String> info = new HashMap<>();
    static {
        info.put("role", "master");
        info.put("master_replid", "8371b4fb1155b71f4a04d3e1bc3e18c4a990aeeb");
        info.put("master_repl_offset", "0");
    }

    public static void put(String key, String value) {
        if(key.equals("--replicaof")) {
            info.put("role", "slave");
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
            sb.append("\r\n");
        }
        sb.delete(sb.length() - 2, sb.length());
        Response response = new Response();
        response.add(sb.toString());
        return ResponseUtils.writeBulkStringResponse(response);
    }
}
