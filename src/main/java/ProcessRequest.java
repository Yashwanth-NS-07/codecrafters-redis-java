import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class ProcessRequest {
    private static final ByteBuffer byteBuffer = ByteBuffer.allocate(1000);
    public static void process(Request request, SocketChannel channel) throws IOException {
        String value = request.getParameter(0);
        if(value.equals("ping")) {
            byteBuffer.clear();
            byteBuffer.put("+PONG\r\n".getBytes());
            byteBuffer.flip();
            channel.write(byteBuffer);
        } else if (value.equals("ECHO")) {
            Response response = new Response(1);
            response.add(request.getParameter(1));
            Response.writeResponse(response, channel, byteBuffer);
        }
    }
}
