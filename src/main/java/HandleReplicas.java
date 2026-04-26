public class HandleReplicas {
    private static final String hardCodedRDBBinary = "UkVESVMwMDEx+glyZWRpcy12ZXIFNy4yLjD6CnJlZGlzLWJpdHPAQPoFY3RpbWXCbQi8ZfoIdXNlZC1tZW3CsMQQAPoIYW9mLWJhc2XAAP/wbjv+wP9aog==";
    public static String handleREPLCONF(Request request) {
        return "+OK\r\n";
    }
    public static String handlePSYNC(Request request) {
        StringBuilder sb = new StringBuilder();
        sb.append("+FULLRESYNC ");
        sb.append(InfoHandler.get("master_replid"));
        sb.append(" 0\r\n");
        sb.append("$");
        sb.append(hardCodedRDBBinary.length());
        sb.append("\r\n");
        sb.append(hardCodedRDBBinary);
        return sb.toString();
    }
}
