import java.nio.ByteBuffer;

public class ResponseUtils {
    public static void writeArrayResponse(Response response, ByteBuffer byteBuffer) {
        int pCount = response.getParameterCount();
        byteBuffer.put(("*" + pCount + "\r\n").getBytes());
        for(int i = 0; i < pCount; i++) {
            String value = response.getParameter(i);
            byteBuffer.put(("$" + value.length() + "\r\n").getBytes());
            byteBuffer.put(value.getBytes());
            byteBuffer.put("\r\n".getBytes());
        }
    }

    public static void writeBulkStringResponse(Response response, ByteBuffer byteBuffer) {
        String value = response.getParameter(0);
        byteBuffer.put(("$" + value.length() + "\r\n").getBytes());
        byteBuffer.put(value.getBytes());
        byteBuffer.put("\r\n".getBytes());
    }
}
