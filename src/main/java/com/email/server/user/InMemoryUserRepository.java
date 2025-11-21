package com.email.server.user;

import java.util.HashMap;
import java.util.Map;

public class InMemoryUserRepository implements UserRepository {
    private final Map<String, String> users = new HashMap<>();

    public InMemoryUserRepository() {
        // Add default user for testing
        users.put("user@example.com", "password");
    }

    public void addUser(String username, String password) {
        users.put(username, password);
    }

    @Override
    public boolean validate(String username, String password) {
        String storedPassword = users.get(username);
        return storedPassword != null && storedPassword.equals(password);
    }
}
