import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class ProcessRequest {
    private static final ByteBuffer byteBuffer = ByteBuffer.allocate(1000);
    public static void process(Request request, SocketChannel channel) throws IOException {
        String cmd = request.getParameter(0);
        byteBuffer.clear();
        switch (cmd) {
            case "PING":
            case "ping": {
                byteBuffer.put("+PONG\r\n".getBytes());
                break;
            }
            case "ECHO": {
                String value = request.getParameter(1);
                String response = String.format("$%d\r\n%s\r\n", value.length(), value);
                byteBuffer.put(response.getBytes());
                break;
            }
            case "SET": {
                MapStore.handleSet(request, byteBuffer);
                break;
            }
            case "GET": {
                MapStore.handleGet(request, byteBuffer);
                break;
            }
            case "RPUSH": {
                ListStore.handleRPUSH(request, byteBuffer);
                break;
            }
            case "LPUSH": {
                ListStore.handleLPUSH(request, byteBuffer);
                break;
            }
            case "LPOP": {
                ListStore.handleLPOP(request, byteBuffer);
                break;
            }
            case "BLPOP": {
                ListStore.handleBLPOP(request, byteBuffer, channel);
                break;
            }
            case "LRANGE": {
                ListStore.handleLRANGE(request, byteBuffer);
                break;
            }
            case "LLEN": {
                ListStore.handleLLEN(request, byteBuffer);
                break;
            }
            case "TYPE": {
                handleTYPE(request);
            }
        }
        byteBuffer.flip();
        channel.write(byteBuffer);
    }

    private static void handleTYPE(Request request) {
        String key = request.getParameter(1);
        if(MapStore.isKeyExists(key)) {
            byteBuffer.put("+string\r\n".getBytes());
            return;
        } else if(ListStore.isListExists(key)) {
            byteBuffer.put("+list\r\n".getBytes());
            return;
        } else if(StreamStore.isStreamExists(key)) {
            byteBuffer.put("+stream\r\n".getBytes());
        } else {
            byteBuffer.put("+none\r\n".getBytes());
        }
    }
}
