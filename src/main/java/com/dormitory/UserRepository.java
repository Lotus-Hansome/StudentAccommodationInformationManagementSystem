package com.dormitory;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByUsername(String username) throws IOException;

    List<User> listAll() throws IOException;

    void create(User user) throws IOException;

    void update(User user) throws IOException;

    void updatePassword(String username, String passwordHash) throws IOException;

    void updateLastLogin(String username) throws IOException;
}
