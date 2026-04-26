import java.nio.channels.SocketChannel;

public class ProcessRequest {

    public static String process(Request request, SocketChannel channel) {
        String cmd = request.getParameter(0).toUpperCase();
        return switch (cmd) {
            case "PING" -> "+PONG\r\n";
            case "INFO" -> InfoHandler.handleINFO(request);
            case "ECHO" -> {
                String value = request.getParameter(1);
                yield String.format("$%d\r\n%s\r\n", value.length(), value);
            }
            case "SET" -> MapStore.handleSet(request);
            case "GET" -> MapStore.handleGet(request);
            case "INCR" -> MapStore.handleINCR(request);
            case "WATCH" -> MapStore.handleWATCH(request);
            case "UNWATCH" -> MapStore.handleUNWATCH(request);
            case "RPUSH" -> ListStore.handleRPUSH(request);
            case "LPUSH" -> ListStore.handleLPUSH(request);
            case "LPOP" -> ListStore.handleLPOP(request);
            case "BLPOP" -> ListStore.handleBLPOP(request, channel);
            case "LRANGE" -> ListStore.handleLRANGE(request);
            case "LLEN" -> ListStore.handleLLEN(request);
            case "XADD" -> StreamStore.handleXADD(request);
            case "XRANGE" -> StreamStore.handleXRANGE(request);
            case "XREAD" -> StreamStore.handleXREAD(request, channel);
            case "TYPE" -> handleTYPE(request);
            case "REPLCONF" -> HandleReplicas.handleREPLCONF(request);
            default -> throw new IllegalArgumentException("Unknow Operation");
        };
    }

    private static String handleTYPE(Request request) {
        String key = request.getParameter(1);
        if(MapStore.isKeyExists(key)) {
            return "+string\r\n";
        } else if(ListStore.isListExists(key)) {
            return "+list\r\n";
        } else if(StreamStore.isStreamExists(key)) {
            return "+stream\r\n";
        } else {
            return "+none\r\n";
        }
    }
}
