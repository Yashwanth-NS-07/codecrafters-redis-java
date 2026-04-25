import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.CompletableFuture;

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

    public static boolean isListExists(String listName) {
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

    public static String handleRPUSH(Request request) {
        String listName = request.getParameter(1);
        String s;
        synchronized (ListStore.class) {
            for (int i = 2; i < request.getParameterCount(); i++) {
                String valueToAppend = request.getParameter(i);
                add(listName, valueToAppend);
            }
            s = writeSizeResponse(listName);
        }
        // don't reuse this byteBuffer
        checkInBlockingMap(listName);
        return s;
    }

    public static String handleLPUSH(Request request) {
        String listName = request.getParameter(1);
        for (int i = 2; i < request.getParameterCount(); i++) {
            String valueToAppend = request.getParameter(i);
            addFirst(listName, valueToAppend);
        }
        checkInBlockingMap(listName);
        return writeSizeResponse(listName);
    }

    private static void checkInBlockingMap(String listName) {
        // indefinite blocks response
        if(blockingMap.containsKey(listName) && !blockingMap.get(listName).isEmpty()) {
            SocketChannel channel = blockingMap.get(listName).removeFirst();
            Response response = new Response();
            response.add(listName);
            response.add(removeFirst(listName));
            ByteBuffer tempByteBuffer = ByteBuffer.allocate(1000);
            String s = ResponseUtils.writeArrayResponse(response);
            tempByteBuffer.put(s.getBytes());
            try {
                tempByteBuffer.flip();
                channel.write(tempByteBuffer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static String handleLPOP(Request request) {
        String listName = request.getParameter(1);
        if(!isListExists(listName) || size(listName) <= 0) {
            return "$-1\r\n";
        }
        int count = 1;
        if(request.getParameterCount() >= 3) {
            count = Integer.parseInt(request.getParameter(2));
        }
        Response response = new Response();
        while(count-- > 0 && size(listName) > 0) {
            response.add(removeFirst(listName));
        }
        if(response.getParameterCount() <= 0) {
            return "*-1\r\n";
        } else if(response.getParameterCount() == 1) {
            return ResponseUtils.writeBulkStringResponse(response);
        } else {
            return ResponseUtils.writeArrayResponse(response);
        }
    }

    public static String handleBLPOP(Request request, SocketChannel channel) {
        long tillTime = 0;
        String listName = null;
        Response response = new Response();
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
            return ResponseUtils.writeArrayResponse(response);
        }
        if(tillTime == 0) {
            blockingMap.putIfAbsent(listName, new LinkedList<>());
            blockingMap.get(listName).add(channel);
            return "";
        }
        tillTime += System.currentTimeMillis();
        final long tillTimeFinal = tillTime;
        final String listNameFinal = listName;
        CompletableFuture.runAsync(() -> {
            while(tillTimeFinal > System.currentTimeMillis()) {
                synchronized (ListStore.class) {
                    if (isListExists(listNameFinal) && size(listNameFinal) > 0) {
                        response.add(listNameFinal);
                        response.add(removeFirst(listNameFinal));
                        break;
                    }
                }
            }
            ByteBuffer tempByteBuffer = ByteBuffer.allocate(1000);
            if(response.getParameterCount() > 0) {
                tempByteBuffer.put(ResponseUtils.writeArrayResponse(response).getBytes());
            } else {
                tempByteBuffer.put("*-1\r\n".getBytes());
            }
            tempByteBuffer.flip();
            try {
                channel.write(tempByteBuffer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        return "";
    }

    public static String handleLRANGE(Request request) {
        String listName = request.getParameter(1);
        if(!isListExists(listName)) {
            return "*0\r\n";
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
            return "*0\r\n";
        } else {
            Response response = new Response();
            for (int i = from; i <= to; i++) {
                response.add(getElement(listName, i));
            }
            return ResponseUtils.writeArrayResponse(response);
        }
    }

    public static String handleLLEN(Request request) {
        String listName = request.getParameter(1);
        return writeSizeResponse(listName);
    }

    private static String writeSizeResponse(String listName) {
        int listSize = 0;
        if(isListExists(listName)) {
            listSize = map.get(listName).size();
        }
        return (":" + listSize + "\r\n");
    }
}
