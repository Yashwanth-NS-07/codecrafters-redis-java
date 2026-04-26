public class HandleReplicas {
    public static String handleREPLCONF(Request request) {
        return "+OK\r\n";
    }
    public static String handlePSYNC(Request request) {
        String sb = "+FULLRESYNC " +
                InfoHandler.get("master_replid") +
                " 0\r\n";
        return sb;
    }
}
