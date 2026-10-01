package dev.jamie.nexusdesk.model;

public record Account(long id, String name, String email, String role, boolean enabled) {
    public boolean isStaff() { return role.equals("TECHNICIAN") || role.equals("ADMIN"); }
    public boolean isAdmin() { return role.equals("ADMIN"); }
}
