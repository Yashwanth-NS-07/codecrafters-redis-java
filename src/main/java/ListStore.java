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

    private static String remove(String listName, int index) {
        return map.get(listName).remove(index);
    }

    private static String removeFirst(String listName) {
        return map.get(listName).removeFirst();
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
            add(listName, valueToAppend);
        }
        writeSizeResponse(listName, byteBuffer);
    }

    public static void handleLPUSH(Request request, ByteBuffer byteBuffer) {
        String listName = request.getParameter(1);
        for (int i = 2; i < request.getParameterCount(); i++) {
            String valueToAppend = request.getParameter(i);
            addFirst(listName, valueToAppend);
        }
        writeSizeResponse(listName, byteBuffer);
    }

    public static void handleLPOP(Request request, ByteBuffer byteBuffer) {
        String listName = request.getParameter(1);
        if(!isListExists(listName) || size(listName) <= 0) {
            byteBuffer.put("$-1\r\n".getBytes());
            return;
        }
        int count = 1;
        if(request.getParameterCount() >= 3) {
            count = Integer.parseInt(request.getParameter(2));
        }
        ListStore.Response response = new ListStore.Response();
        while(count-- > 0 && size(listName) > 0) {
            response.add(removeFirst(listName));
        }
        if(response.getParameterCount() <= 0) {
            byteBuffer.put("$-1\r\n".getBytes());
        } else if(response.getParameterCount() == 1) {
            writeBulkStringResponse(response, byteBuffer);
        } else {
            writeArrayResponse(response, byteBuffer);
        }
    }

    public synchronized static void handleBLPOP(Request request, ByteBuffer byteBuffer) {
        long remainingTime = 0;
        final long startTimestamp = System.currentTimeMillis();
        List<String> listOfLists = new ArrayList<>();
        for(int i = 1; i < request.getParameterCount(); i++) {
            String val = request.getParameter(i);
            try {
                remainingTime = Long.parseLong(val) * 1000;
                break;
            } catch (NumberFormatException e) {
                listOfLists.add(val);
            }
        }
        if(remainingTime == 0) remainingTime = 10000 * 10000;
        ListStore.Response response = new ListStore.Response();
        while(!listOfLists.isEmpty() && System.currentTimeMillis() - startTimestamp < remainingTime) {
            for(int i = 0; i < listOfLists.size(); ) {
                String list = listOfLists.get(i);
                if(size(list) > 0) {
                    response.add(removeFirst(list));
                    listOfLists.remove(i);
                } else i++;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        if(response.getParameterCount() == 1) {
            writeBulkStringResponse(response, byteBuffer);
        } else {
            writeArrayResponse(response, byteBuffer);
        }
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
            ListStore.Response response = new ListStore.Response();
            for (int i = from; i <= to; i++) {
                response.add(getElement(listName, i));
            }
            writeArrayResponse(response, byteBuffer);
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

    private static void writeArrayResponse(ListStore.Response response, ByteBuffer byteBuffer) {
        int pCount = response.getParameterCount();
        byteBuffer.put(("*" + pCount + "\r\n").getBytes());
        for(int i = 0; i < pCount; i++) {
            String value = response.getParameter(i);
            byteBuffer.put(("$" + value.length() + "\r\n").getBytes());
            byteBuffer.put(value.getBytes());
            byteBuffer.put("\r\n".getBytes());
        }
    }

    private static void writeBulkStringResponse(ListStore.Response response, ByteBuffer byteBuffer) {
        String value = response.getParameter(0);
        byteBuffer.put(("$" + value.length() + "\r\n").getBytes());
        byteBuffer.put(value.getBytes());
        byteBuffer.put("\r\n".getBytes());
    }
    private static class Response {

        private final List<String> parameterList;

        private Response() {
            this.parameterList = new ArrayList<>();
        }

        private void add(String parameter) {
            parameterList.add(parameter);
        }

        private String getParameter(int i) {
            return parameterList.get(i);
        }

        private int getParameterCount() {
            return this.parameterList.size();
        }
    }
}
