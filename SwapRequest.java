public class SwapRequest {
    User from;
    User to;
    String status = "pending";

    public SwapRequest(User from, User to) {
        this.from = from;
        this.to = to;
    }
}