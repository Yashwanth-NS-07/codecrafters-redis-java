import com.sun.nio.sctp.SctpSocketOption;
import com.sun.source.tree.Scope;

import javax.swing.plaf.synth.SynthColorChooserUI;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;

public class HandleReplicas {
    private static final byte[] rdbBytes;

    private static final List<SocketChannel> replicas = new ArrayList<>();
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

        replicas.add(channel);
        return "";
    }

    public static void propagateToReplicas(Request request) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(request.toRawString().getBytes());
        List<SocketChannel> replicasToRemoveIfIoException = new LinkedList<>();
        for(SocketChannel replica: replicas) {
            while(byteBuffer.hasRemaining()) {
                try {
                    replica.write(byteBuffer);
                } catch (IOException e) {
                    replicasToRemoveIfIoException.add(replica);
                    break;
                }
            }
            byteBuffer.clear();
        }
        for(SocketChannel replicaToRemove: replicasToRemoveIfIoException) {
            replicas.remove(replicaToRemove);
        }
    }
}
