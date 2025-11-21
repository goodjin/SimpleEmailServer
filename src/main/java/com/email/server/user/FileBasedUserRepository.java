package com.email.server.user;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileBasedUserRepository implements UserRepository {
    private static final Logger logger = LoggerFactory.getLogger(FileBasedUserRepository.class);
    private final Map<String, User> users = new ConcurrentHashMap<>();

    public FileBasedUserRepository(Config config) {
        loadUsers(config);
    }

    private void loadUsers(Config config) {
        if (!config.hasPath("users")) {
            logger.warn("No users configured");
            return;
        }

        for (ConfigObject obj : config.getObjectList("users")) {
            Config userConfig = obj.toConfig();
            String email = userConfig.getString("email");
            String password = userConfig.getString("password");

            User user = new User(email, password);
            users.put(email, user);
            logger.info("Loaded user: {}", user.getUsername());
        }

        logger.info("Loaded {} users from configuration", users.size());
    }

    @Override
    public User findByUsername(String username) {
        // Try to find by email first
        User user = users.get(username);
        if (user != null) {
            return user;
        }

        // If not found, try to match by username part (backwards compatibility)
        for (User u : users.values()) {
            if (u.getUsername().equals(username)) {
                return u;
            }
        }
        return null;
    }

    @Override
    public boolean validate(String emailOrUsername, String password) {
        User user = findByUsername(emailOrUsername);
        if (user == null) {
            return false;
        }
        return user.getPassword().equals(password);
    }
}
