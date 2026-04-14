import java.awt.datatransfer.FlavorListener;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;

public class ListStore {
    private static final Map<String, LinkedList<String>> map;
    private static final Map<String, LinkedList<BLPOPEntity>> blockingMap;
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

        if(blockingMap.containsKey(listName) && !blockingMap.get(listName).isEmpty()) {
            BLPOPEntity entity = blockingMap.get(listName).removeFirst();
            if(entity.remainingTime < System.currentTimeMillis() - entity.startTime)
                return;
            ListStore.Response response = new ListStore.Response();
            response.add(listName);
            response.add(removeFirst(listName));
            ByteBuffer tempByteBuffer = ByteBuffer.allocate(1000);
            writeArrayResponse(response, tempByteBuffer);
            try {
                tempByteBuffer.flip();
                entity.channel.write(tempByteBuffer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
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

    public static void handleBLPOP(Request request, ByteBuffer byteBuffer, SocketChannel channel) {
        long remainingTime = 0;
        List<String> listOfLists = new ArrayList<>();
        for(int i = 1; i < request.getParameterCount(); i++) {
            String val = request.getParameter(i);
            try {
                remainingTime = (long) (Double.parseDouble(val) * 1000);
                break;
            } catch (NumberFormatException e) {
                listOfLists.add(val);
            }
        }
        if(remainingTime == 0) remainingTime = 10000 * 10000;
        ListStore.Response response = new ListStore.Response();
        for(String listName: listOfLists) {
            if(isListExists(listName) && size(listName) > 0) {
                response.add(listName);
                response.add(removeFirst(listName));
            } else {
                blockingMap.putIfAbsent(listName, new LinkedList<>());
                blockingMap.get(listName).add(new BLPOPEntity(listName, channel, remainingTime));
            }
        }
        if(response.getParameterCount() > 0) {
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

    private static class BLPOPEntity {
        private final long remainingTime;
        private final long startTime;
        private final SocketChannel channel;
        private final String listName;
        private BLPOPEntity(String listName, SocketChannel channel, long remainingTime) {
            this.remainingTime = remainingTime;
            this.startTime = System.currentTimeMillis();
            this.channel = channel;
            this.listName = listName;
        }
    }
}
