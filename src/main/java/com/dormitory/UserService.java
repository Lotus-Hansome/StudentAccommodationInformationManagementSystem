package com.dormitory;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class UserService {
    private final UserRepository repository;
    private final StudentDormService studentDormService;

    public UserService(UserRepository repository) {
        this(repository, null);
    }

    public UserService(UserRepository repository, StudentDormService studentDormService) {
        this.repository = repository;
        this.studentDormService = studentDormService;
    }

    public synchronized List<User> listAll() {
        try {
            return repository.listAll();
        } catch (IOException e) {
            throw new IllegalStateException("读取用户列表失败：" + e.getMessage(), e);
        }
    }

    public synchronized Optional<User> findByUsername(String username) {
        try {
            return repository.findByUsername(normalizeText(username));
        } catch (IOException e) {
            throw new IllegalStateException("读取用户失败：" + e.getMessage(), e);
        }
    }

    public synchronized void create(String username, String password, String role, String studentId, boolean enabled) {
        String requestedUsername = normalizeText(username);
        String normalizedPassword = normalizeText(password);
        UserRole userRole = parseRole(role);
        if (normalizedPassword.length() < 6) {
            throw new IllegalArgumentException("初始密码至少需要 6 位。");
        }
        if (userRole == UserRole.ADMIN && requestedUsername.isBlank()) {
            throw new IllegalArgumentException("管理员用户名不能为空。");
        }
        try {
            String normalizedStudentId = validateStudentBinding(userRole, studentId, "");
            String normalizedUsername = userRole == UserRole.USER ? normalizedStudentId : requestedUsername;
            if (repository.findByUsername(normalizedUsername).isPresent()) {
                throw new IllegalArgumentException(userRole == UserRole.USER ? "该学号的学生账号已存在。" : "用户名已存在。");
            }
            repository.create(new User(
                    normalizedUsername,
                    PasswordHasher.hash(normalizedPassword),
                    userRole,
                    normalizedStudentId,
                    enabled));
        } catch (IOException e) {
            throw new IllegalStateException("创建用户失败：" + e.getMessage(), e);
        }
    }

    public synchronized void update(String username, String role, String studentId, boolean enabled, String currentUsername) {
        String normalizedUsername = normalizeText(username);
        UserRole userRole = parseRole(role);
        if (normalizedUsername.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空。");
        }
        if (normalizedUsername.equalsIgnoreCase(currentUsername) && !enabled) {
            throw new IllegalArgumentException("不能禁用当前登录账号。");
        }
        try {
            User existing = repository.findByUsername(normalizedUsername)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在。"));
            if (existing.getRole() == UserRole.ADMIN
                    && existing.isEnabled()
                    && (userRole != UserRole.ADMIN || !enabled)
                    && !hasOtherEnabledAdmin(existing.getUsername())) {
                throw new IllegalArgumentException("系统至少需要保留一个启用的管理员账号。");
            }
            String normalizedStudentId = validateStudentBinding(userRole, studentId, normalizedUsername);
            repository.update(new User(
                    existing.getUsername(),
                    existing.getPasswordHash(),
                    userRole,
                    normalizedStudentId,
                    enabled));
        } catch (IOException e) {
            throw new IllegalStateException("更新用户失败：" + e.getMessage(), e);
        }
    }

    public synchronized void delete(String username, String currentUsername) {
        String normalizedUsername = normalizeText(username);
        if (normalizedUsername.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空。");
        }
        if (normalizedUsername.equalsIgnoreCase(normalizeText(currentUsername))) {
            throw new IllegalArgumentException("不能删除当前登录账号。");
        }
        try {
            User existing = repository.findByUsername(normalizedUsername)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在。"));
            if (existing.getRole() == UserRole.ADMIN && !hasOtherEnabledAdmin(existing.getUsername())) {
                throw new IllegalArgumentException("系统至少需要保留一个启用的管理员账号。");
            }
            repository.delete(existing.getUsername());
        } catch (IOException e) {
            throw new IllegalStateException("删除用户失败：" + e.getMessage(), e);
        }
    }

    public synchronized void changePassword(String username, String oldPassword, String newPassword) {
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

    public synchronized void resetPassword(String username, String newPassword) {
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
        String normalizedRole = normalizeText(role);
        if ("ADMIN".equalsIgnoreCase(normalizedRole)) {
            return UserRole.ADMIN;
        }
        if ("USER".equalsIgnoreCase(normalizedRole)) {
            return UserRole.USER;
        }
        throw new IllegalArgumentException("用户角色只能是管理员或普通用户。");
    }

    public synchronized Optional<String> disableStudentAccount(String studentId) {
        String normalizedStudentId = normalizeText(studentId);
        if (normalizedStudentId.isBlank()) {
            return Optional.empty();
        }
        try {
            Optional<User> account = repository.listAll().stream()
                    .filter(user -> user.getRole() == UserRole.USER)
                    .filter(user -> user.getStudentId().equalsIgnoreCase(normalizedStudentId))
                    .findFirst();
            if (account.isEmpty() || !account.get().isEnabled()) {
                return account.map(User::getUsername);
            }
            User user = account.get();
            repository.update(new User(
                    user.getUsername(),
                    user.getPasswordHash(),
                    user.getRole(),
                    user.getStudentId(),
                    false));
            return Optional.of(user.getUsername());
        } catch (IOException e) {
            throw new IllegalStateException("禁用学生账号失败：" + e.getMessage(), e);
        }
    }

    private boolean hasOtherEnabledAdmin(String username) throws IOException {
        return repository.listAll().stream()
                .anyMatch(user -> !user.getUsername().equalsIgnoreCase(username)
                        && user.getRole() == UserRole.ADMIN
                        && user.isEnabled());
    }

    private String validateStudentBinding(UserRole role, String studentId, String username) throws IOException {
        String normalizedStudentId = normalizeText(studentId);
        if (role == UserRole.ADMIN) {
            if (!normalizedStudentId.isBlank()) {
                throw new IllegalArgumentException("管理员账号不能绑定学号。");
            }
            return "";
        }
        if (normalizedStudentId.isBlank()) {
            throw new IllegalArgumentException("普通用户必须绑定学号。");
        }
        if (studentDormService != null && studentDormService.findByStudentId(normalizedStudentId).isEmpty()) {
            throw new IllegalArgumentException("学号不存在，不能创建或绑定学生账号。");
        }
        boolean alreadyBound = repository.listAll().stream()
                .anyMatch(user -> !user.getUsername().equalsIgnoreCase(username)
                        && user.getStudentId().equalsIgnoreCase(normalizedStudentId));
        if (alreadyBound) {
            throw new IllegalArgumentException("该学号已绑定其他账号。");
        }
        return normalizedStudentId;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
