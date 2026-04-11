import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

public class Response {
    private final int parameterCount;
    private final List<String> parameterList;

    public Response(int parameterCount) {
        this.parameterCount = parameterCount;
        this.parameterList = new ArrayList<>(parameterCount);
    }

    public void add(String parameter) {
        assert parameterCount == parameterList.size();
        parameterList.add(parameter);
    }

    public String getParameter(int i) {
        return parameterList.get(i);
    }

    public int getParameterCount() {
        return this.parameterCount;
    }

    public static void writeResponse (Response response, SocketChannel channel, ByteBuffer byteBuffer) throws IOException {
        byteBuffer.clear();
        int pCount = response.getParameterCount();
        if(pCount > 1) {
            byteBuffer.put(("*" + pCount + "\r\n").getBytes());
        }
        for(int i = 0; i < pCount; i++) {
            String value = response.getParameter(i);
            byteBuffer.put(("$" + value.length() + "\r\n").getBytes());
            byteBuffer.put(value.getBytes());
            byteBuffer.put("\r\n".getBytes());
        }
        byteBuffer.flip();
        channel.write(byteBuffer);
    }
}
