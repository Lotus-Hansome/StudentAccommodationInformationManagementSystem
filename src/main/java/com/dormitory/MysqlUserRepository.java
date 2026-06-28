package com.dormitory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MysqlUserRepository implements UserRepository {
    private final MysqlConnectionFactory connectionFactory;

    public MysqlUserRepository(MysqlConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Optional<User> findByUsername(String username) throws IOException {
        String sql = """
                SELECT username, password_hash, role, student_id, enabled
                FROM users
                WHERE username = ?
                """;
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(readUser(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new IOException("读取用户失败：" + e.getMessage(), e);
        }
    }

    @Override
    public List<User> listAll() throws IOException {
        String sql = """
                SELECT username, password_hash, role, student_id, enabled
                FROM users
                ORDER BY role, username
                """;
        try (Connection connection = connectionFactory.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            List<User> users = new ArrayList<>();
            while (resultSet.next()) {
                users.add(readUser(resultSet));
            }
            return users;
        } catch (SQLException e) {
            throw new IOException("读取用户列表失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void create(User user) throws IOException {
        String sql = """
                INSERT INTO users (username, password_hash, role, student_id, enabled)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            fillUser(statement, user);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("创建用户失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void update(User user) throws IOException {
        String sql = """
                UPDATE users
                SET role = ?, student_id = ?, enabled = ?
                WHERE username = ?
                """;
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, user.getRole().name());
            statement.setString(2, blankToNull(user.getStudentId()));
            statement.setBoolean(3, user.isEnabled());
            statement.setString(4, user.getUsername());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("更新用户失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String username) throws IOException {
        String sql = "DELETE FROM users WHERE username = ?";
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("删除用户失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void updatePassword(String username, String passwordHash) throws IOException {
        String sql = "UPDATE users SET password_hash = ? WHERE username = ?";
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passwordHash);
            statement.setString(2, username);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("更新密码失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void updateLastLogin(String username) throws IOException {
        String sql = "UPDATE users SET last_login_at = CURRENT_TIMESTAMP WHERE username = ?";
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("更新登录时间失败：" + e.getMessage(), e);
        }
    }

    private User readUser(ResultSet resultSet) throws SQLException {
        return new User(
                resultSet.getString("username"),
                resultSet.getString("password_hash"),
                UserRole.valueOf(resultSet.getString("role")),
                resultSet.getString("student_id"),
                resultSet.getBoolean("enabled"));
    }

    private void fillUser(PreparedStatement statement, User user) throws SQLException {
        statement.setString(1, user.getUsername());
        statement.setString(2, user.getPasswordHash());
        statement.setString(3, user.getRole().name());
        statement.setString(4, blankToNull(user.getStudentId()));
        statement.setBoolean(5, user.isEnabled());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
