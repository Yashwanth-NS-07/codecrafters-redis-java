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
                String response = String.format("*%d\r\n%s\r\n", value.length(), value);
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
            case "LRANGE": {
                ListStore.handleLRANGE(request, byteBuffer);
                break;
            }
        }
        byteBuffer.flip();
        channel.write(byteBuffer);
    }
}
