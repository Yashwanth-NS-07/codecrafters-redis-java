import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;

public class TransactionManager {

    private static final Map<SocketAddress, List<Request>> transactions = new HashMap<>();

    public static void handleRequest(Request request, SocketChannel channel) throws IOException {

        SocketAddress clientAddress = channel.getRemoteAddress();
        String val = request.getParameter(0);
        switch (val) {
            case "MULTI": {
                transactions.put(clientAddress, new ArrayList<>());
                writeToChannel("+OK\r\n", channel);
                return;
            }
            case "EXEC": {
                if(!transactions.containsKey(clientAddress)) {
                    writeToChannel("-ERR EXEC without MULTI\r\n", channel);
                    return;
                }
                List<Request> queuedRequests = transactions.remove(clientAddress);
                processAllQueuedRequest(queuedRequests, channel);
                return;
            }
            case "DISCARD": {
                if(!transactions.containsKey(clientAddress)) {
                    writeToChannel("-ERR DISCARD without MULTI\r\n", channel);
                    return;
                }
                transactions.remove(clientAddress);
                writeToChannel("+OK\r\n", channel);
                return;
            }
            case "WATCH": {
                if(transactions.containsKey(clientAddress)) {
                    writeToChannel("-ERR WATCH inside MULTI is not allowed\r\n", channel);
                    return;
                }
            }
            default: {
                if(transactions.containsKey(clientAddress)) {
                    // queuing
                    transactions.get(clientAddress).add(request);
                    writeToChannel("+QUEUED\r\n", channel);
                } else {
                    // normal request
                    String response = ProcessRequest.process(request, channel);
                    writeToChannel(response, channel);
                }
            }
        }
    }

    private static void processAllQueuedRequest(List<Request> requests, SocketChannel channel) throws IOException {
        if(requests.isEmpty()) {
            writeToChannel("*0\r\n", channel);
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append('*');
        sb.append(requests.size());
        sb.append("\r\n");
        for(Request request: requests) {
            sb.append(ProcessRequest.process(request, channel));
        }
        writeToChannel(sb.toString(), channel);
    }

    private static void writeToChannel(String value, SocketChannel channel) throws IOException {
        Objects.requireNonNull(value);
        ByteBuffer byteBuffer = ByteBuffer.wrap(value.getBytes());
        while(byteBuffer.hasRemaining()) {
            channel.write(byteBuffer);
        }
    }
}
