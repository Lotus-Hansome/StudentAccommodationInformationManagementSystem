package com.dormitory;

public class User {
    private final String username;
    private final String password;
    private final UserRole role;

    public User(String username, String password, UserRole role) {
        this.username = username;
        this.password = password;
        this.role = role;
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
}
