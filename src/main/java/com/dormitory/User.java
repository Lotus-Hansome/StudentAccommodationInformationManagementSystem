package com.dormitory;

public class User {
    private final String username;
    private final String password;
    private final UserRole role;
    private final String studentId;

    public User(String username, String password, UserRole role) {
        this(username, password, role, "");
    }

    public User(String username, String password, UserRole role, String studentId) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.studentId = studentId == null ? "" : studentId;
    }

    public String getUsername() {
        return username;
    }

    public boolean passwordMatches(String inputPassword) {
        return password.equals(inputPassword);
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
}
