package com.dormitory;

import java.util.List;
import java.util.Optional;

public class AuthService {
    private final List<User> users = List.of(
            new User("admin", "admin123", UserRole.ADMIN),
            new User("student", "student123", UserRole.USER));

    public Optional<User> login(String username, String password) {
        return users.stream()
                .filter(user -> user.getUsername().equalsIgnoreCase(username))
                .filter(user -> user.passwordMatches(password))
                .findFirst();
    }
}
