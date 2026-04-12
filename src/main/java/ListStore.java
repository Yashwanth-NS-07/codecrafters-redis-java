import java.nio.ByteBuffer;
import java.util.*;

public class ListStore {
    private static final Map<String, LinkedList<String>> map;
    static {
        map = new HashMap<>();
    }

    private static void add(String listName, String value) {
        map.putIfAbsent(listName, new LinkedList<>());
        map.get(listName).add(value);
    }
    private static void addFirst(String listName, String value) {
        map.putIfAbsent(listName, new LinkedList<>());
        map.get(listName).addFirst(value);
    }

    private static boolean isListExists(String listName) {
        return map.containsKey(listName);
    }
    private static String getElement(String listName, int index) {
        return map.get(listName).get(index);
    }
    private static int size(String listName) {
        if(map.containsKey(listName)) {
            return map.get(listName).size();
        }
        return -1;
    }

    public static void handleRPUSH(Request request, ByteBuffer byteBuffer) {
        String listName = request.getParameter(1);
        for (int i = 2; i < request.getParameterCount(); i++) {
            String valueToAppend = request.getParameter(i);
            ListStore.add(listName, valueToAppend);
        }
        writeSizeResponse(listName, byteBuffer);
    }

    public static void handleLPUSH(Request request, ByteBuffer byteBuffer) {
        String listName = request.getParameter(1);
        for (int i = 2; i < request.getParameterCount(); i++) {
            String valueToAppend = request.getParameter(i);
            ListStore.addFirst(listName, valueToAppend);
        }
        writeSizeResponse(listName, byteBuffer);
    }

    public static void handleLRANGE(Request request, ByteBuffer byteBuffer) {
        String listName = request.getParameter(1);
        if(!isListExists(listName)) {
            byteBuffer.put("*0\r\n".getBytes());
            return;
        }
        int listSize = size(listName);
        int from = Integer.parseInt(request.getParameter(2));
        int to = Integer.parseInt(request.getParameter(3));
        if (from >= 0 && to >= 0) {
            to = Math.min(to, listSize - 1);
        } else {
            to = Math.max(0, listSize + to);
            if (from < 0) {
                from = Math.max(0, listSize + from);
            }
        }
        int pCount = to - from + 1;
        if (pCount <= 0) {
            byteBuffer.put("*0\r\n".getBytes());
        } else {
            ListStore.Response response = new ListStore.Response(pCount);
            for (int i = from; i <= to; i++) {
                response.add(getElement(listName, i));
            }
            writeLRangeResponse(response, byteBuffer);
        }
    }

    public static void handleLLEN(Request request, ByteBuffer byteBuffer) {
        String listName = request.getParameter(1);
        writeSizeResponse(listName, byteBuffer);
    }

    private static void writeSizeResponse(String listName, ByteBuffer byteBuffer) {
        int listSize = 0;
        if(isListExists(listName)) {
            listSize = map.get(listName).size();
        }
        byteBuffer.put((":" + listSize + "\r\n").getBytes());
    }

    private static void writeLRangeResponse(ListStore.Response response, ByteBuffer byteBuffer) {
        int pCount = response.getParameterCount();
        byteBuffer.put(("*" + pCount + "\r\n").getBytes());
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
}
