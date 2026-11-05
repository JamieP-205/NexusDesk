package dev.jamie.nexusdesk.service;

public class Problem extends RuntimeException {
    private final int status;
    public Problem(int status, String message) {
        super(message);
        this.status = status;
    }
    public int status() { return status; }
    public boolean isConflict() { return status == 409; }
    public static Problem invalid(String message) { return new Problem(400, message); }
    public static Problem conflict(String message) { return new Problem(409, message); }
    public static Problem missing() { return new Problem(404, "I couldn't find that item, or you don't have access to it."); }
}
