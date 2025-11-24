package com.email.server.user;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryUserRepository implements UserRepository {
    private final Map<String, String> users = new ConcurrentHashMap<>();

    public InMemoryUserRepository() {
        // Add default user for testing
        users.put("user@example.com", "password");
    }

    public void addUser(String username, String password) {
        users.put(username, password);
    }

    @Override
    public User findByUsername(String emailOrUsername) {
        // Try exact email match first
        if (users.containsKey(emailOrUsername)) {
            return new User(emailOrUsername, users.get(emailOrUsername));
        }

        // Try username part match
        for (Map.Entry<String, String> entry : users.entrySet()) {
            String email = entry.getKey();
            if (email.contains("@") && email.split("@")[0].equals(emailOrUsername)) {
                return new User(email, entry.getValue());
            }
        }

        return null;
    }

    @Override
    public boolean validate(String emailOrUsername, String password) {
        User user = findByUsername(emailOrUsername);
        return user != null && user.getPassword().equals(password);
    }
}
