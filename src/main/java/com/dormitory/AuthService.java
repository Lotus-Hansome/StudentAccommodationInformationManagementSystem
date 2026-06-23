package com.dormitory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AuthService {
    private final UserRepository repository;

    public AuthService() {
        this(new DemoUserRepository());
    }

    public AuthService(UserRepository repository) {
        this.repository = repository;
    }

    public Optional<User> login(String username, String password) {
        try {
            Optional<User> user = repository.findByUsername(normalizeText(username));
            if (user.isEmpty() || !user.get().isEnabled()) {
                return Optional.empty();
            }
            if (!PasswordHasher.verify(password == null ? "" : password, user.get().getPasswordHash())) {
                return Optional.empty();
            }
            repository.updateLastLogin(user.get().getUsername());
            return user;
        } catch (IOException e) {
            throw new IllegalStateException("登录校验失败：" + e.getMessage(), e);
        }
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static class DemoUserRepository implements UserRepository {
        private final List<User> users = new ArrayList<>();

        private DemoUserRepository() {
            users.add(new User("admin", PasswordHasher.hash("admin123"), UserRole.ADMIN, "", true));
            users.add(new User("student", PasswordHasher.hash("student123"), UserRole.USER, "20230001", true));
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return users.stream()
                    .filter(user -> user.getUsername().equalsIgnoreCase(username))
                    .findFirst();
        }

        @Override
        public List<User> listAll() {
            return List.copyOf(users);
        }

        @Override
        public void create(User user) {
            users.add(user);
        }

        @Override
        public void update(User user) {
            users.removeIf(item -> item.getUsername().equalsIgnoreCase(user.getUsername()));
            users.add(user);
        }

        @Override
        public void updatePassword(String username, String passwordHash) {
            findByUsername(username).ifPresent(user -> update(new User(
                    user.getUsername(),
                    passwordHash,
                    user.getRole(),
                    user.getStudentId(),
                    user.isEnabled())));
        }

        @Override
        public void updateLastLogin(String username) {
        }
    }
}
