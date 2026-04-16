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

    private static Map<String, String> getLast(String streamName) {
        List<Map<String, String>> streamList = stream.get(streamName);
        return streamList.get(streamList.size() - 1);
    }

    public static void handleXADD(Request request, ByteBuffer byteBuffer) {
        String streamName = request.getParameter(1);
        String id = request.getParameter(2);
        if(id.contains("*")) {
            id = generateId(streamName, id);
        } else if(!isIdProper(streamName, id, byteBuffer)) {
            return;
        }
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

    private static boolean isIdProper(String streamName, String id, ByteBuffer byteBuffer) {
        String[] parts = id.split("-");
        long milli = Long.parseLong(parts[0]);
        int seq = Integer.parseInt(parts[1]);
        if(milli <= 0 && seq <= 0) {
            byteBuffer.put("-ERR The ID specified in XADD must be greater than 0-0\r\n".getBytes());
            return false;
        }

        if(isStreamExists(streamName)) {
            String lastId = getLast(streamName).get("Id");
            String[] lastRecordParts = lastId.split("-");
            long lastRecordMilli = Long.parseLong(lastRecordParts[0]);
            int lastRecordSeq = Integer.parseInt(lastRecordParts[1]);
            if((milli < lastRecordMilli) || (milli == lastRecordMilli && seq <= lastRecordSeq)) {
                byteBuffer.put("-ERR The ID specified in XADD is equal or smaller than the target stream top item\r\n".getBytes());
                return false;
            }
        }
        return true;
    }

    private static String generateId(String streamName, String id) {
        String[] parts = id.split("-");
        if("*".equals(parts[0]) && "*".equals(parts[1])) {
            long milli;
            int seq;
            long currentMilli = System.currentTimeMillis();
            if(isStreamExists(streamName)) {
                String lastId = getLast(streamName).get("Id");
                String[] lastRecordParts = lastId.split("-");
                long lastRecordMilli = Long.parseLong(lastRecordParts[0]);
                int lastRecordSeq = Integer.parseInt(lastRecordParts[1]);
                assert  currentMilli < lastRecordMilli;
                if(currentMilli == lastRecordMilli) {
                    seq = lastRecordSeq + 1;
                    milli = currentMilli;
                } else {
                    milli = currentMilli;
                    seq = 0;
                }
            } else {
                milli = currentMilli;
                seq = 0;
            }
            return milli + "-" + seq;
        } else {
            long milli = Long.parseLong(parts[0]);
            int seq = 0;
            if(isStreamExists(streamName)) {
                String lastId = getLast(streamName).get("Id");
                String[] lastRecordParts = lastId.split("-");
                long lastRecordMilli = Long.parseLong(lastRecordParts[0]);
                int lastRecordSeq = Integer.parseInt(lastRecordParts[1]);
                assert milli < lastRecordMilli;
                if(milli == lastRecordMilli) {
                    seq = lastRecordSeq + 1;
                } else {
                    seq = 0;
                }
            } else {
                if(milli == 0) seq = 1;
                else seq = 0;
            }
            return milli + "-" + seq;
        }
    }
}
