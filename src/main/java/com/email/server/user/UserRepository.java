package com.email.server.user;

public interface UserRepository {
    boolean validate(String emailOrUsername, String password);

    User findByUsername(String emailOrUsername);
}
