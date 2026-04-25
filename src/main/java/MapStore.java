import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

public class MapStore {
    private static final Map<String, Value> map;
    static {
        map = new HashMap<>();
    }
    public static boolean isKeyExists(String key) {
        return map.containsKey(key);
    }

    private static void put(String key, Object value, long liveTime) {
        map.put(key, new Value(value, liveTime));
    }

    private static Optional<Object> get(String key) {
        if(map.containsKey(key)) {
            Value value = map.get(key);
            if(value.liveTime == -1) {
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

    public static void handleSet(Request request, ByteBuffer byteBuffer) {
        String key = request.getParameter(1);
        String value = request.getParameter(2);

        if(request.getParameterCount() == 3) {
            put(key, value, -1);
        } else {
            String arg = request.getParameter(3);
            long expiryTime = Integer.parseInt(request.getParameter(4));
            if(arg.equals("EX")) {
                long liveTime = expiryTime * 1000L;
                put(key, value, liveTime);
            } else if(arg.equalsIgnoreCase("PX")) {
                put(key, value, expiryTime);
            }
        }
        byteBuffer.put("+OK\r\n".getBytes());
    }

    public static void handleINCR(Request request, ByteBuffer byteBuffer) {
        String key = request.getParameter(1);
        Optional<Object> optionalVal =  get(key);
        int num = 1;
        if(optionalVal.isPresent()) {
            Object val = optionalVal.get();
            if(val instanceof String s) {
                try {
                    num = Integer.parseInt(s) + 1;
                    put(key, num, -1);
                } catch (NumberFormatException e) {
                    byteBuffer.put("-ERR value is not an integer or out of range\r\n".getBytes());
                    break;
                }
            } else if(val instanceof Integer i) {
                num = i + 1;
                put(key, num, -1);
            } else {
                throw new IllegalArgumentException("Unknown type of INCR option");
            }
        } else {
            put(key, num, -1);
        }
        byteBuffer.put((":" + num + "\r\n").getBytes());
    }

    public static void handleGet(Request request, ByteBuffer byteBuffer) {
        Optional<Object> optionalVal = get(request.getParameter(1));
        if(optionalVal.isEmpty()) {
            byteBuffer.put("$-1\r\n".getBytes());
        } else {
            MapStore.Response response = new MapStore.Response(1);
            response.add(optionalVal.get().toString());
            writeResponse(response, byteBuffer);
        }
    }

    private static void writeResponse(MapStore.Response response, ByteBuffer byteBuffer) {
        int pCount = response.getParameterCount();
        if(pCount > 1) {
            byteBuffer.put(("*" + pCount + "\r\n").getBytes());
        }
        for(int i = 0; i < pCount; i++) {
            String value = response.getParameter(i);
            byteBuffer.put(("$" + value.length() + "\r\n").getBytes());
            byteBuffer.put(value.getBytes());
            byteBuffer.put("\r\n".getBytes());
        }
    }

    private static class Response {

        private final int parameterCount;
        private final List<String> parameterList;

        private Response(int parameterCount) {
            this.parameterCount = parameterCount;
            this.parameterList = new ArrayList<>(parameterCount);
        }

        private void add(String parameter) {
            assert parameterCount == parameterList.size();
            parameterList.add(parameter);
        }

        private String getParameter(int i) {
            return parameterList.get(i);
        }

        private int getParameterCount() {
            return this.parameterCount;
        }
    }

    private static class Value {
        private final long timestamp;
        private final long liveTime;
        private final Object value;
        // liveTime value expected in millis
        public Value(Object value,long liveTime) {
            this.value = value;
            this.liveTime = liveTime;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
