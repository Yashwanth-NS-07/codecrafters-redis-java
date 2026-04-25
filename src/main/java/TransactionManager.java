import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransactionManager {
    private static final Map<SocketAddress, List<Request>> transactions = new HashMap<>();
    public static void handleRequest(Request request, SocketChannel channel) throws IOException {
        if("MULTI".equalsIgnoreCase(request.getParameter(0))) {
            transactions.put(channel.getRemoteAddress(), new ArrayList<>());
            ByteBuffer tempByteBuffer = ByteBuffer.allocate(10);
            tempByteBuffer.put("+OK\r\n".getBytes());
            tempByteBuffer.flip();
            channel.write(tempByteBuffer);
            return;
        } else if(
                "EXEC".equalsIgnoreCase(request.getParameter(0)) &&
                !transactions.containsKey(channel.getRemoteAddress())
        ) {
                ByteBuffer tempByteBuffer = ByteBuffer.allocate(50);
                tempByteBuffer.put("-ERR EXEC without MULTI\r\n".getBytes());
                tempByteBuffer.flip();
                channel.write(tempByteBuffer);
                return;
        }

        // checking if the client is having ongoing transaction
        SocketAddress clientAddress = channel.getRemoteAddress();
        if(transactions.containsKey(clientAddress)) {
            if("EXEC".equalsIgnoreCase(request.getParameter(0))) {
                processAllQueuedRequest(channel);
            } else {
                transactions.get(clientAddress).add(request);
            }
        } else {
            // normal request
            ProcessRequest.process(request, channel);
        }
    }

    private static void processAllQueuedRequest(SocketChannel channel) throws IOException {
        List<Request> requests = transactions.get(channel.getRemoteAddress());
        if(requests.isEmpty()) {
            ByteBuffer tempByteBuffer = ByteBuffer.allocate(5);
            tempByteBuffer.put("*0\r\n".getBytes());
            tempByteBuffer.flip();
            channel.write(tempByteBuffer);
            return;
        }
        for(Request request: requests) {
            ProcessRequest.process(request, channel);
        }
    }
}
