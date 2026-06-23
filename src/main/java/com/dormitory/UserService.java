package com.dormitory;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class UserService {
    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public List<User> listAll() {
        try {
            return repository.listAll();
        } catch (IOException e) {
            throw new IllegalStateException("读取用户列表失败：" + e.getMessage(), e);
        }
    }

    public void create(String username, String password, String role, String studentId, boolean enabled) {
        String normalizedUsername = normalizeText(username);
        String normalizedPassword = normalizeText(password);
        UserRole userRole = parseRole(role);
        if (normalizedUsername.isBlank() || normalizedPassword.isBlank()) {
            throw new IllegalArgumentException("用户名和初始密码不能为空。");
        }
        if (userRole == UserRole.USER && normalizeText(studentId).isBlank()) {
            throw new IllegalArgumentException("普通用户必须绑定学号。");
        }
        try {
            if (repository.findByUsername(normalizedUsername).isPresent()) {
                throw new IllegalArgumentException("用户名已存在。");
            }
            repository.create(new User(
                    normalizedUsername,
                    PasswordHasher.hash(normalizedPassword),
                    userRole,
                    normalizeText(studentId),
                    enabled));
        } catch (IOException e) {
            throw new IllegalStateException("创建用户失败：" + e.getMessage(), e);
        }
    }

    public void update(String username, String role, String studentId, boolean enabled, String currentUsername) {
        String normalizedUsername = normalizeText(username);
        UserRole userRole = parseRole(role);
        if (normalizedUsername.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空。");
        }
        if (normalizedUsername.equalsIgnoreCase(currentUsername) && !enabled) {
            throw new IllegalArgumentException("不能禁用当前登录账号。");
        }
        if (userRole == UserRole.USER && normalizeText(studentId).isBlank()) {
            throw new IllegalArgumentException("普通用户必须绑定学号。");
        }
        try {
            User existing = repository.findByUsername(normalizedUsername)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在。"));
            repository.update(new User(
                    existing.getUsername(),
                    existing.getPasswordHash(),
                    userRole,
                    normalizeText(studentId),
                    enabled));
        } catch (IOException e) {
            throw new IllegalStateException("更新用户失败：" + e.getMessage(), e);
        }
    }

    public void changePassword(String username, String oldPassword, String newPassword) {
        String normalizedNewPassword = normalizeText(newPassword);
        if (normalizedNewPassword.length() < 6) {
            throw new IllegalArgumentException("新密码至少需要 6 位。");
        }
        try {
            User user = repository.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在。"));
            if (!PasswordHasher.verify(oldPassword == null ? "" : oldPassword, user.getPasswordHash())) {
                throw new IllegalArgumentException("原密码不正确。");
            }
            repository.updatePassword(user.getUsername(), PasswordHasher.hash(normalizedNewPassword));
        } catch (IOException e) {
            throw new IllegalStateException("修改密码失败：" + e.getMessage(), e);
        }
    }

    public void resetPassword(String username, String newPassword) {
        String normalizedUsername = normalizeText(username);
        String normalizedPassword = normalizeText(newPassword);
        if (normalizedUsername.isBlank() || normalizedPassword.length() < 6) {
            throw new IllegalArgumentException("用户名不能为空，新密码至少需要 6 位。");
        }
        try {
            Optional<User> user = repository.findByUsername(normalizedUsername);
            if (user.isEmpty()) {
                throw new IllegalArgumentException("用户不存在。");
            }
            repository.updatePassword(user.get().getUsername(), PasswordHasher.hash(normalizedPassword));
        } catch (IOException e) {
            throw new IllegalStateException("重置密码失败：" + e.getMessage(), e);
        }
    }

    private UserRole parseRole(String role) {
        return "ADMIN".equalsIgnoreCase(normalizeText(role)) ? UserRole.ADMIN : UserRole.USER;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
