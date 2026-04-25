import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;

public class TransactionManager {
    private static final Map<SocketAddress, List<Request>> transactions = new HashMap<>();
    public static void handleRequest(Request request, SocketChannel channel) throws IOException {
        if("MULTI".equalsIgnoreCase(request.getParameter(0))) {
            transactions.put(channel.getRemoteAddress(), new ArrayList<>());
            writeToChannel("+OK\r\n", channel);
            return;
        } else if(
                "EXEC".equalsIgnoreCase(request.getParameter(0)) &&
                !transactions.containsKey(channel.getRemoteAddress())
        ) {
                writeToChannel("-ERR EXEC without MULTI\r\n", channel);
                return;
        }

        // checking if the client is having ongoing transaction
        SocketAddress clientAddress = channel.getRemoteAddress();
        if(transactions.containsKey(clientAddress)) {
            if("EXEC".equalsIgnoreCase(request.getParameter(0))) {
                processAllQueuedRequest(channel);
            } else {
                // queuing
                transactions.get(clientAddress).add(request);
                writeToChannel("+QUEUED\r\n", channel);
            }
        } else {
            // normal request
            ProcessRequest.process(request, channel);
        }
    }

    private static void processAllQueuedRequest(SocketChannel channel) throws IOException {
        List<Request> requests = transactions.get(channel.getRemoteAddress());
        transactions.remove(channel.getRemoteAddress());
        if(requests.isEmpty()) {
            writeToChannel("*0\r\n", channel);
            return;
        }
        for(Request request: requests) {
            ProcessRequest.process(request, channel);
        }
    }

    private static void writeToChannel(String value, SocketChannel channel) throws IOException {
        Objects.requireNonNull(value);
        ByteBuffer byteBuffer = ByteBuffer.wrap(value.getBytes());
        byteBuffer.flip();
        while(byteBuffer.hasRemaining()) {
            channel.write(byteBuffer);
        }
    }
}
