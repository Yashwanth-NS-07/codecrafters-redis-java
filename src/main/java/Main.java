import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    private static boolean isRunning = true;
    private static final ExecutorService pool = Executors.newFixedThreadPool(3);

    public static void main(String[] args) throws IOException {
        System.out.print("Starting Redis server...");
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(6379));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.print("Stopping Redis server...");
            isRunning = false;
        }));

        Selector selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("done");

        while(isRunning) {
            selector.select(1000);
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while(iterator.hasNext()  && isRunning) {
                SelectionKey selectionKey = iterator.next();
                iterator.remove();
                if(selectionKey.isAcceptable()) {
                    SocketChannel clientSocketChannel = ((ServerSocketChannel) selectionKey.channel()).accept();
                    clientSocketChannel.configureBlocking(false);
                    clientSocketChannel.register(selector, SelectionKey.OP_READ);
                    System.out.println("Accepted connection from: " + clientSocketChannel.getRemoteAddress());
                } else if(selectionKey.isReadable()) {
                    selectionKey.interestOps(0);
                    pool.submit(createHandleReadRunnable(selectionKey));
                }
            }
        }
        for(SelectionKey key: selector.keys()) {
            key.channel().close();
        }
        selector.close();
        System.out.println("done");
    }
    private static Runnable createHandleReadRunnable(SelectionKey selectionKey) {
        return () -> {
            ByteBuffer byteBuffer = ByteBuffer.allocate(1000);
            SocketChannel clientSocketChannel = (SocketChannel) selectionKey.channel();
            Optional<Request> optRequest = Request.readRequest(clientSocketChannel);
            if(optRequest.isEmpty()) return;
            Request request = optRequest.get();
            try {
                ProcessRequest.process(request, clientSocketChannel, byteBuffer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            selectionKey.interestOps(SelectionKey.OP_READ);
        };
    }
}
