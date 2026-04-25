public class ResponseUtils {
    public static String writeArrayResponse(Response response) {
        int pCount = response.getParameterCount();

        if(pCount == 0) {
            return "*-1\r\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(("*" + pCount + "\r\n"));
        for(int i = 0; i < pCount; i++) {
            Object value = response.getParameter(i);
            if(value instanceof String s) {
                sb.append("$" + s.length() + "\r\n");
                sb.append(s);
                sb.append("\r\n");
            } else if(value instanceof Response r) {
                sb.append(writeArrayResponse(r));
            } else {
                throw new RuntimeException("Unknown type in response object");
            }
        }
        return sb.toString();
    }

    public static String writeBulkStringResponse(Response response) {
        StringBuilder sb = new StringBuilder();
        String value = (String) response.getParameter(0);
        sb.append(("$" + value.length() + "\r\n"));
        sb.append(value);
        sb.append("\r\n");
        return sb.toString();
    }
}
