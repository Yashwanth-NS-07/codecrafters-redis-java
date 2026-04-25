import java.nio.channels.SocketChannel;

public class ProcessRequest {

    public static String process(Request request, SocketChannel channel) {
        String cmd = request.getParameter(0);
        switch (cmd) {
            case "PING":
            case "ping": {
                return "+PONG\r\n";
            }
            case "ECHO": {
                String value = request.getParameter(1);
                return String.format("$%d\r\n%s\r\n", value.length(), value);
            }
            case "SET": {
                return MapStore.handleSet(request);
            }
            case "GET": {
                return MapStore.handleGet(request);
            }
            case "INCR": {
                return MapStore.handleINCR(request);
            }
            case "WATCH": {
                return MapStore.handleWATCH(request);
            }
            case "UNWATCH": {
                return MapStore.handleUNWATCH(request);
            }
            case "RPUSH": {
                return ListStore.handleRPUSH(request);
            }
            case "LPUSH": {
                return ListStore.handleLPUSH(request);
            }
            case "LPOP": {
                return ListStore.handleLPOP(request);
            }
            case "BLPOP": {
                return ListStore.handleBLPOP(request, channel);
            }
            case "LRANGE": {
                return ListStore.handleLRANGE(request);
            }
            case "LLEN": {
                return ListStore.handleLLEN(request);
            }
            case "XADD": {
                return StreamStore.handleXADD(request);
            }
            case "XRANGE": {
                return StreamStore.handleXRANGE(request);
            }
            case "XREAD": {
                return StreamStore.handleXREAD(request, channel);
            }
            case "TYPE": {
                return handleTYPE(request);
            }
            default: {
                throw new IllegalArgumentException("Unknow Operation");
            }
        }
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
