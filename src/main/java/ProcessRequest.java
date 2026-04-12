import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Optional;

public class ProcessRequest {
    private static final ByteBuffer byteBuffer = ByteBuffer.allocate(1000);
    public static void process(Request request, SocketChannel channel) throws IOException {
        String cmd = request.getParameter(0);
        if(cmd.equalsIgnoreCase("ping")) {
            byteBuffer.clear();
            byteBuffer.put("+PONG\r\n".getBytes());
            byteBuffer.flip();
            channel.write(byteBuffer);
        } else if (cmd.equals("ECHO")) {
            Response response = new Response(1);
            response.add(request.getParameter(1));
            Response.writeResponse(response, byteBuffer);
            byteBuffer.flip();
            channel.write(byteBuffer);
        } else if (cmd.equals("SET")) {
            byteBuffer.clear();
            byteBuffer.put("+OK\r\n".getBytes());
            byteBuffer.flip();
            String key = request.getParameter(1);
            String value = request.getParameter(2);
            if(request.getParameterCount() == 3) {
                MapStore.put(key, value, -1);
            } else {
                String arg = request.getParameter(3);
                long expiryTime = Integer.parseInt(request.getParameter(4));
                if(arg.equals("EX")) {
                    long liveTime = expiryTime * 1000L;
                    MapStore.put(key, value, liveTime);
                } else if(arg.equalsIgnoreCase("PX")) {
                    MapStore.put(key, value, expiryTime);
                }
            }
            channel.write(byteBuffer);
        } else if(cmd.equals("GET")) {
            Optional<String> optionalVal = MapStore.get(request.getParameter(1));
            if(optionalVal.isEmpty()) {
                byteBuffer.clear();
                byteBuffer.put("$-1\r\n".getBytes());
                byteBuffer.flip();
                channel.write(byteBuffer);
            } else {
                Response response = new Response(1);
                response.add(optionalVal.get());
                Response.writeResponse(response, byteBuffer);
                byteBuffer.flip();
                channel.write(byteBuffer);
            }
        } else if(cmd.equals("RPUSH")) {
            String listName = request.getParameter(1);
            for(int i = 2; i < request.getParameterCount(); i++) {
                String valueToAppend = request.getParameter(2);
                ListStore.add(listName, valueToAppend);
            }
            int listSize = ListStore.size(listName);
            byteBuffer.clear();
            byteBuffer.put((":" + listSize + "\r\n").getBytes());
            byteBuffer.flip();
            channel.write(byteBuffer);
        }
    }
}
