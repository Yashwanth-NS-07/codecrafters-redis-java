import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Base64;

public class HandleReplicas {
    private static final byte[] rdbBytes;
    static {
        String s = "UkVESVMwMDEx+glyZWRpcy12ZXIFNy4yLjD6CnJlZGlzLWJpdHPAQPoFY3RpbWXCbQi8ZfoIdXNlZC1tZW3CsMQQAPoIYW9mLWJhc2XAAP/wbjv+wP9aog==";
        rdbBytes = Base64.getDecoder().decode(s);
    }
    public static String handleREPLCONF(Request request) {
        return "+OK\r\n";
    }
    public static String handlePSYNC(Request request, SocketChannel channel) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("+FULLRESYNC ");
        sb.append(InfoHandler.get("master_replid"));
        sb.append(" 0\r\n");
        sb.append("$");
        sb.append(rdbBytes.length);
        sb.append("\r\n");
        ByteBuffer byteBuffer = ByteBuffer.allocate(2000);
        byteBuffer.put(sb.toString().getBytes());
        byteBuffer.put(rdbBytes);
        byteBuffer.flip();
        while(byteBuffer.hasRemaining()) {
            channel.write(byteBuffer);
        }
        return "";
    }
}
