import java.nio.ByteBuffer;

public class ResponseUtils {
    public static void writeArrayResponse(Response response, ByteBuffer byteBuffer) {
        int pCount = response.getParameterCount();
        byteBuffer.put(("*" + pCount + "\r\n").getBytes());
        for(int i = 0; i < pCount; i++) {
            Object value = response.getParameter(i);
            if(value instanceof String s) {
                byteBuffer.put(("$" + s.length() + "\r\n").getBytes());
                byteBuffer.put(s.getBytes());
                byteBuffer.put("\r\n".getBytes());
            } else if(value instanceof Response r) {
                writeArrayResponse(r, byteBuffer);
            } else {
                throw new RuntimeException("Unknown type in response object");
            }
        }
    }

    public static void writeBulkStringResponse(Response response, ByteBuffer byteBuffer) {
        String value = (String) response.getParameter(0);
        byteBuffer.put(("$" + value.length() + "\r\n").getBytes());
        byteBuffer.put(value.getBytes());
        byteBuffer.put("\r\n".getBytes());
    }
}
