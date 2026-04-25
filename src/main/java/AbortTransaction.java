public class AbortTransaction extends RuntimeException {
    public AbortTransaction(String message) {
        super(message);
    }
}
