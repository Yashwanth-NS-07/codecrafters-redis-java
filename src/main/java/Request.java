import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Request {
    private int parameterCount;
    private List<Parameter> parameterList;

    private Request(int parameterCount) {
        this.parameterCount = parameterCount;
        this.parameterList = new ArrayList<>(parameterCount);
    }
    private static final ByteBuffer byteBuffer = ByteBuffer.allocate(1000);
    public static Optional<Request> readRequest(SocketChannel channel) {
        try {
            byteBuffer.clear();
            int bytesRead = channel.read(byteBuffer);
            if(bytesRead == -1) {
                channel.close();
                return Optional.empty();
            }

            byteBuffer.flip();
            // skipping * character
            byteBuffer.get();
            StringBuilder sb = new StringBuilder();
            int pCount = -1;
            while(byteBuffer.hasRemaining()) {
                char c = (char) byteBuffer.get();
                if(c != '\r') sb.append(c);
                else {
                    pCount = Integer.parseInt(sb.toString());
                    // skipping \n character
                    byteBuffer.get();
                    break;
                }
            }
            if(pCount <= 0) return Optional.empty();
            Request request = new Request(pCount);
            for(int i = 0; i < pCount; i++) {
                // skipping $ character
                byteBuffer.get();

                sb.delete(0, sb.length());
                while(byteBuffer.hasRemaining()) {
                    char c = (char) byteBuffer.get();
                    if(c == '\r') {
                        int length = Integer.parseInt(sb.toString());
                        // skipping \n character
                        byteBuffer.get();

                        byte[] value = new byte[length];
                        byteBuffer.get(value);
                        request.addParameter(
                                new Parameter(
                                        length,
                                        new String(value)
                                )
                        );
                        // skipping \r\n
                        byteBuffer.getShort();
                    } else {
                        sb.append(c);
                    }
                }
            }
            return Optional.of(request);
        } catch (IOException ioe) {
            System.out.println(ioe.getMessage());
            try {
                channel.close();
            } catch (IOException e) {
                // ignoring
            }
            return Optional.empty();
        }
    }
    public int getParameterCount() {
        return this.parameterCount;
    }
    private void addParameter(Parameter parameter) {
        this.parameterList.add(parameter);
    }
    public Parameter getParameter(int i) {
        return parameterList.get(i);
    }
    private static class Parameter {
        private int count;
        private String value;

        public Parameter(int count, String value) {
            this.count = count;
            this.value = value;
        }

        public int getCount() {
            return count;
        }

        public String getValue() {
            return value;
        }
    }
}
