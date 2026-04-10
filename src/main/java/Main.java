import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class Main {
    private static boolean isRunning = true;
    private static ByteBuffer byteBuffer = ByteBuffer.allocate(100);
    public static void main(String[] args) throws IOException {
        System.out.println("Starting Redis server...");
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(6379));
        serverSocketChannel.configureBlocking(false);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Stopping Redis server...");
            isRunning = false;
        }));

        Selector selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        StringBuilder sb = new StringBuilder();

        while(isRunning) {
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            System.out.println("Number of connections: " + iterator.hasNext());
            while(iterator.hasNext()  && isRunning) {
                SelectionKey selectionKey = iterator.next();
                iterator.remove();
                if(selectionKey.isAcceptable()) {
                    SocketChannel clientSocketChannel = ((ServerSocketChannel) selectionKey.channel()).accept();
                    clientSocketChannel.configureBlocking(false);
                    clientSocketChannel.register(selector, SelectionKey.OP_READ);
                } else if(selectionKey.isReadable()) {
                    SocketChannel clientSocketChannel = (SocketChannel) selectionKey.channel();
                    int readBytes = 0;
                    while((readBytes = clientSocketChannel.read(byteBuffer)) > 0) {
                        if(readBytes >= 100) throw new RuntimeException("read more than 100 bytes, it's not supported yet");
                        byteBuffer.flip();
                        CharBuffer charBuffer = byteBuffer.asCharBuffer();
                        while(charBuffer.hasRemaining()) {
                            sb.append(charBuffer.get());
                        }
                        byteBuffer.reset();
                        if(sb.equals("PING")) {
                            byteBuffer.put("+PONG\r\n".getBytes(), 0, 7);
                            clientSocketChannel.write(byteBuffer);
                            byteBuffer.reset();
                        }
                    }
                    if(readBytes == -1) {
                        clientSocketChannel.close();
                    }
                }
            }
        }
    }
}
