import java.net.SocketAddress;
import java.util.*;

public class MapStore {
    private static final Map<String, Value> map;
    private static final Map<SocketAddress, Map<String, String>> keysToWatch;
    static {
        map = new HashMap<>();
        keysToWatch = new HashMap<>();
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

    public static String handleSet(Request request) {
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
        return "+OK\r\n";
    }

    public static String handleINCR(Request request) {
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
                    return "-ERR value is not an integer or out of range\r\n";
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
        return ":" + num + "\r\n";
    }

    public static String handleGet(Request request) {
        Optional<Object> optionalVal = get(request.getParameter(1));
        if(optionalVal.isEmpty()) {
            return "$-1\r\n";
        } else {
            MapStore.Response response = new MapStore.Response(1);
            response.add(optionalVal.get().toString());
            return writeResponse(response);
        }
    }

    public static boolean isKeysModified(SocketAddress socketAddress) {
        if(keysToWatch.containsKey(socketAddress)) {
            Map<String, String> keysAndExpectedValues = keysToWatch.get(socketAddress);
            for(Map.Entry<String, String> entry: keysAndExpectedValues.entrySet()) {
                String key = entry.getKey();
                String expectedValue = entry.getValue();
                String currentValue = get(key).get().toString();
                if(!expectedValue.equals(currentValue)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String handleWATCH(Request request) {
        keysToWatch.putIfAbsent(request.getSocketAddress(), new HashMap<>());
        for(int i = 1; i < request.getParameterCount(); i++) {
            String key = request.getParameter(i);
            String value = "-1";
            if(get(key).isPresent()) {
                value = get(key).get().toString();
            }
            keysToWatch.get(request.getSocketAddress()).put(key, value);
        }
        return "+OK\r\n";
    }

    public static String handleUNWATCH(Request request) {
        keysToWatch.remove(request.getSocketAddress());
        return "+OK\r\n";
    }

    private static String writeResponse(MapStore.Response response) {
        int pCount = response.getParameterCount();
        if(pCount > 1) {
            return "*" + pCount + "\r\n";
        }
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < pCount; i++) {
            String value = response.getParameter(i);
            sb.append(("$" + value.length() + "\r\n"));
            sb.append(value);
            sb.append("\r\n");
        }
        return sb.toString();
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
