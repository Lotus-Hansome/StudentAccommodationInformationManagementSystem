package com.dormitory;

public class User {
    private final String username;
    private final String passwordHash;
    private final UserRole role;
    private final String studentId;
    private final boolean enabled;

    public User(String username, String password, UserRole role) {
        this(username, password, role, "");
    }

    public User(String username, String password, UserRole role, String studentId) {
        this(username, password, role, studentId, true);
    }

    public User(String username, String passwordHash, UserRole role, String studentId, boolean enabled) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.studentId = studentId == null ? "" : studentId;
        this.enabled = enabled;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public String getStudentId() {
        return studentId;
    }

    public boolean isBoundToStudent() {
        return !studentId.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }
}
