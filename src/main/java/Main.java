import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

public class Main {
    private static boolean isRunning = true;
    private static final Map<String, String> argMap = new HashMap<>();

    public static void main(String[] args) throws IOException {
        int port = 6379;
        prepareArgMap(args);

        if(argMap.containsKey("--port")) {
            port = Integer.parseInt(argMap.get("--port"));
        }

        if(argMap.containsKey("--replicaof")) {
            connectToMaster(argMap.get("--replicaof"));
        }

        System.out.print("Starting Redis server...");
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(port));

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
                        SocketChannel clientSocketChannel = (SocketChannel) selectionKey.channel();
                        handleRead(clientSocketChannel);
                }
            }
        }
        for(SelectionKey key: selector.keys()) {
            key.channel().close();
        }
        selector.close();
        System.out.println("done");
    }
    private static void handleRead(SocketChannel clientSocketChannel) {
        Optional<Request> optRequest = Request.readRequest(clientSocketChannel);
        if(optRequest.isEmpty()) return;
        Request request = optRequest.get();
        try {
            TransactionManager.handleRequest(request, clientSocketChannel);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void connectToMaster(String masterAddress) throws IOException {
        String[] parts = masterAddress.split(" ");
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.bind(new InetSocketAddress(parts[0], Integer.parseInt(parts[1])));
        channel.finishConnect();

        Response response = new Response();
        response.add("PING");
        ByteBuffer byteBuffer = ByteBuffer.wrap(ResponseUtils.writeArrayResponse(response).getBytes());
        while(byteBuffer.hasRemaining()) {
            channel.write(byteBuffer);
        }
    }

    private static void prepareArgMap(String[] args) {
        for(int i = 0; i < args.length; i+=2) {
            String key = args[i];
            String value = args[i+1];
            argMap.put(key, value);
            if(!args[i].equals("--port")) {
                InfoHandler.put(key, value);
            }
        }
    }
}
