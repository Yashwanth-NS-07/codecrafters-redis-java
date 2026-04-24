import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class StreamStore {
    private static final Map<String, List<Record>> stream;
    static {
        stream = new HashMap<>();
    }

    public static boolean isStreamExists(String key) {
        return stream.containsKey(key);
    }

    private static void put(String streamName, Record record) {
        stream.putIfAbsent(streamName, new ArrayList<>());
        stream.get(streamName).add(record);
    }

    private static int getStreamSize(String streamName) {
        if(!isStreamExists(streamName)) return -1;
        return stream.get(streamName).size();
    }

    private static Optional<Record> getRecord(String streamName, int index) {
        if(!isStreamExists(streamName) ||
                index >= getStreamSize(streamName)) {
            return Optional.empty();
        }
        return Optional.of(stream.get(streamName).get(index));
    }

    private static Record getLast(String streamName) {
        List<Record> streamList = stream.get(streamName);
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
        Record record = new Record(new Record.Id(id));
        for(int i = 3; i < request.getParameterCount(); i+=2) {
            String key = request.getParameter(i);
            String value = request.getParameter(i+1);
            record.put(key, value);
        }
        put(streamName, record);
        Response response = new Response();
        response.add(id);
        ResponseUtils.writeBulkStringResponse(response, byteBuffer);
    }

    public static void handleXREAD(Request request, ByteBuffer byteBuffer, SocketChannel channel) {

        if("BLOCK".equalsIgnoreCase(request.getParameter(1))) {
            handleBLOCKXREAD(request, byteBuffer, channel);
            return;
        }
        String to = finalTo("+"); // maximum to
        Record.Id toId = new Record.Id(to);
        Response response = new Response();
        int keyCount = ((request.getParameterCount() - 1) / 2);
        for(int i = 2; i < keyCount + 2; i++) {
            String streamName = request.getParameter(i);

            String from = request.getParameter(i + keyCount);
            from = finalFrom(from);
            Record.Id fromId = new Record.Id(from);

            Response streamResponse = new Response();
            streamResponse.add(streamName);
            streamResponse.add(getResponseFromToId(streamName, fromId, toId));

            response.add(streamResponse);
        }
        ResponseUtils.writeArrayResponse(response, byteBuffer);
    }

    private static void handleBLOCKXREAD(Request request, ByteBuffer byteBuffer, SocketChannel channel) {
        final long millis = System.currentTimeMillis() + Long.parseLong(request.getParameter(2));

        String streamName = request.getParameter(4);
        String from = finalFrom(request.getParameter(5));
        from = incrementSeqByOne(from);
        System.out.println("From: " + from);
        String to = finalTo("+");
        Record.Id fromId = new Record.Id(from);
        Record.Id toId = new Record.Id(to);

        CompletableFuture.runAsync(() -> {
            ByteBuffer tempByteBuffer = ByteBuffer.allocate(1000);
            Response response = new Response();
            System.out.println("inside async");
            synchronized (ListStore.class) {
                System.out.println("inside synchr");
                Response streamResponse = new Response();
                streamResponse.add(streamName);
                while(System.currentTimeMillis() < millis) {
                    System.out.println("inside while");
                    Response response1 = getResponseFromToId(streamName, fromId, toId);
                    if(response1.getParameterCount() > 0) {
                        streamResponse.add(response1);
                        response.add(streamResponse);
                        System.out.println("breaking");
                        break;
                    }
                }
            }
            ResponseUtils.writeArrayResponse(response, tempByteBuffer);
            tempByteBuffer.flip();
            try {
                channel.write(tempByteBuffer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static void handleXRANGE(Request request, ByteBuffer byteBuffer) {
        String streamName = request.getParameter(1);
        String from = request.getParameter(2);
        String to = request.getParameter(3);
        from = finalFrom(from);
        to = finalTo(to);
        Record.Id fromId = new Record.Id(from);
        Record.Id toId = new Record.Id(to);
        Response response = getResponseFromToId(streamName, fromId, toId);
        ResponseUtils.writeArrayResponse(response, byteBuffer);
    }

    private static String incrementSeqByOne(String id) {
        String[] parts = id.split("-");
        return parts[0] + "-" + (Integer.parseInt(parts[1]) + 1);
    }

    private static String finalFrom(String from) {
        if(!from.contains("-")) {
            from = from + "-0";
        } else if(from.length() == 1 && from.contains("-")) {
            from = "0-0";
        }
        return from;
    }

    private static String finalTo(String to) {
        if(to.contains("+")) {
            to = Long.MAX_VALUE + "-" + Integer.MAX_VALUE;
        } else if(!to.contains("-")) {
            to = to + "-" + Integer.MAX_VALUE;
        }
        return to;
    }

    private static Response getResponseFromToId(String streamName, Record.Id fromId, Record.Id toId) {
        Response response  = new Response();
        int indexOfFromId = getIndexOfIdGreaterThanOrEqual(streamName, fromId);
        System.out.println("index of from id: " + indexOfFromId);
        for(int i = indexOfFromId; i < getStreamSize(streamName); i++) {
            Record record = getRecord(streamName, i).get();
            if(
                    record.id.milli > toId.milli ||
                            (
                                    record.id.milli == toId.milli &&
                                            record.id.seq > toId.seq
                            )
            ) {
                break;
            }
            Response resp = new Response();
            Response resp1 = new Response();
            for(Map.Entry<String, String> entry: record.map.entrySet()) {
                resp1.add(entry.getKey());
                resp1.add(entry.getValue());
            }
            resp.add(record.id.milli + "-" + record.id.seq);
            resp.add(resp1);
            response.add(resp);
        }
        return response;
    }

    private static int getIndexOfIdGreaterThanOrEqual(String streamName, Record.Id target) {
        // not handling when end = -1;
        int start = 0, end = getStreamSize(streamName) - 1;
        while(start < end) {
            int mid = (start + end) >> 1;
            Record.Id midRecordId = getRecord(streamName, mid).get().id;
            if(midRecordId.milli == target.milli) {
                start = mid;
                break;
            } else if(midRecordId.milli > target.milli) {
                start = mid + 1;
            } else {
                end = mid - 1;
            }

        }
        while(start - 1 >= 0 && getRecord(streamName, start - 1).get().id.milli >= target.milli) {
            start--;
        }
        while(getRecord(streamName, start).get().id.seq < target.seq) start++;
        return start;
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
            Record.Id lastRecordId = getLast(streamName).id;
            long lastRecordMilli = lastRecordId.milli;
            int lastRecordSeq = lastRecordId.seq;
            if((milli < lastRecordMilli) || (milli == lastRecordMilli && seq <= lastRecordSeq)) {
                byteBuffer.put("-ERR The ID specified in XADD is equal or smaller than the target stream top item\r\n".getBytes());
                return false;
            }
        }
        return true;
    }

    private static String generateId(String streamName, String id) {
        String[] parts = id.split("-");
        if("*".equals(parts[0])) {
            long milli;
            int seq;
            long currentMilli = System.currentTimeMillis();
            if(isStreamExists(streamName)) {
                Record.Id lastRecordId = getLast(streamName).id;
                long lastRecordMilli = lastRecordId.milli;
                int lastRecordSeq = lastRecordId.seq;
                assert  currentMilli > lastRecordMilli;
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
                Record.Id lastRecordId = getLast(streamName).id;
                long lastRecordMilli = lastRecordId.milli;
                int lastRecordSeq = lastRecordId.seq;
                assert milli > lastRecordMilli;
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

    private static class Record {
        private final Id id;
        private final Map<String, String> map;
        public Record(Id id) {
            this.id = id;
            this.map = new HashMap<>();
        }

        private void put(String key, String value) {
            map.put(key, value);
        }
        private static class Id {
            private final long milli;
            private final int seq;
            public Id(String id) {
                String[] idParts = id.split("-");
                this.milli = Long.parseLong(idParts[0]);
                this.seq = Integer.parseInt(idParts[1]);
            }
        }
    }
}
