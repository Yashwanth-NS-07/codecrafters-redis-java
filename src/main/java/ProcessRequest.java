import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Optional;

public class ProcessRequest {
    private static final ByteBuffer byteBuffer = ByteBuffer.allocate(1000);
    public static void process(Request request, SocketChannel channel) throws IOException {
        String value = request.getParameter(0);
        if(value.equalsIgnoreCase("ping")) {
            byteBuffer.clear();
            byteBuffer.put("+PONG\r\n".getBytes());
            byteBuffer.flip();
            channel.write(byteBuffer);
        } else if (value.equals("ECHO")) {
            Response response = new Response(1);
            response.add(request.getParameter(1));
            Response.writeResponse(response, channel, byteBuffer);
        } else if (value.equals("SET")) {
            Store.put(request.getParameter(1), request.getParameter(2));
            byteBuffer.clear();
            byteBuffer.put("+OK\r\n".getBytes());
            byteBuffer.flip();
            channel.write(byteBuffer);
        } else if(value.equals("GET")) {
            Optional<String> optionalVal = Store.get(request.getParameter(1));
            if(optionalVal.isEmpty()) {
                byteBuffer.clear();
                byteBuffer.put("$-1\r\n".getBytes());
                byteBuffer.flip();
                channel.write(byteBuffer);
            } else {
                Response response = new Response(1);
                response.add(optionalVal.get());
                Response.writeResponse(response, channel, byteBuffer);
            }
        }
    }
}
