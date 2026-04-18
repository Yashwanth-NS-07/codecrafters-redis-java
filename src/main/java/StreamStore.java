import java.nio.ByteBuffer;
import java.util.*;

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

    public static void handleXRANGE(Request request, ByteBuffer byteBuffer) {
        String streamName = request.getParameter(1);
        String from = request.getParameter(2);
        String to = request.getParameter(3);
        if(!from.contains("-")) {
            if(from.length() == 1) {
                from = "0-0";
            } else {
                from = from + "-0";
            }
        }
        if(!to.contains("-")) {
            to = to + "-" + Integer.MAX_VALUE;
        }
        Record.Id fromId = new Record.Id(from);
        Record.Id toId = new Record.Id(to);
        Response response = new Response();
        int indexOfFromId = getIndexOfIdGreaterThanOrEqual(streamName, fromId);
        for(int i = indexOfFromId; i < getStreamSize(streamName); i++) {
            Record record = getRecord(streamName, i).get();
            if(record.id.milli > toId.milli) {
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
        ResponseUtils.writeArrayResponse(response, byteBuffer);
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
