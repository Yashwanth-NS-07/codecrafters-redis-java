import java.util.Base64;

public class HandleReplicas {
    private static final String rdbString;
    static {
        String s = "UkVESVMwMDEx+glyZWRpcy12ZXIFNy4yLjD6CnJlZGlzLWJpdHPAQPoFY3RpbWXCbQi8ZfoIdXNlZC1tZW3CsMQQAPoIYW9mLWJhc2XAAP/wbjv+wP9aog==";
        rdbString = new String(Base64.getDecoder().decode(s));
    }
    public static String handleREPLCONF(Request request) {
        return "+OK\r\n";
    }
    public static String handlePSYNC(Request request) {
        StringBuilder sb = new StringBuilder();
        sb.append("+FULLRESYNC ");
        sb.append(InfoHandler.get("master_replid"));
        sb.append(" 0\r\n");
        sb.append("$");
        sb.append(rdbString.length());
        sb.append("\r\n");
        sb.append(rdbString);
;
        return sb.toString();
    }
}
