import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;

public class ListStore {
    private static final Map<String, LinkedList<String>> map;
    private static final Map<String, LinkedList<SocketChannel>> blockingMap;
    static {
        map = new HashMap<>();
        blockingMap = new HashMap<>();
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
        // don't reuse this byteBuffer
        writeSizeResponse(listName, byteBuffer);

        checkInBlockingMap(listName);

    }

    public static void handleLPUSH(Request request, ByteBuffer byteBuffer) {
        String listName = request.getParameter(1);
        for (int i = 2; i < request.getParameterCount(); i++) {
            String valueToAppend = request.getParameter(i);
            addFirst(listName, valueToAppend);
        }
        writeSizeResponse(listName, byteBuffer);
        checkInBlockingMap(listName);
    }

    private static void checkInBlockingMap(String listName) {
        // indefinite blocks response
        if(blockingMap.containsKey(listName) && !blockingMap.get(listName).isEmpty()) {
            SocketChannel channel = blockingMap.get(listName).removeFirst();
            ListStore.Response response = new ListStore.Response();
            response.add(listName);
            response.add(removeFirst(listName));
            ByteBuffer tempByteBuffer = ByteBuffer.allocate(1000);
            writeArrayResponse(response, tempByteBuffer);
            try {
                tempByteBuffer.flip();
                channel.write(tempByteBuffer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
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

    public static void handleBLPOP(Request request, ByteBuffer byteBuffer, SocketChannel channel) {
        long tillTime = 0;
        String listName = null;
        ListStore.Response response = new ListStore.Response();
        for(int i = 1; i < request.getParameterCount() &&  i < 3; i++) {
            String val = request.getParameter(i);
            try {
                tillTime = (long) (Double.parseDouble(val) * 1000);
                break;
            } catch (NumberFormatException e) {
                listName = val;
                if(isListExists(listName) && size(listName) > 0) {
                    response.add(listName);
                    response.add(removeFirst(listName));
                    break;
                }
            }
        }

        if(response.getParameterCount() > 0) {
            writeArrayResponse(response, byteBuffer);
            return;
        }
        if(tillTime == 0) {
            blockingMap.get(listName).add(channel);
            return;
        }
        tillTime += System.currentTimeMillis();
        while(tillTime < System.currentTimeMillis()) {
            if(isListExists(listName) && size(listName) > 0) {
                response.add(listName);
                response.add(removeFirst(listName));
                break;
            }
        }
        if(response.getParameterCount() > 0) {
            writeArrayResponse(response, byteBuffer);
        } else {
            byteBuffer.put("$-1\r\n".getBytes());
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
