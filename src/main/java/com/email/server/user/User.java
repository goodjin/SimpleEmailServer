package com.email.server.user;

public class User {
    private final String email;
    private final String password;

    public User(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    // For backwards compatibility, extract username from email
    public String getUsername() {
        if (email.contains("@")) {
            return email.split("@")[0];
        }
        return email;
    }

    @Override
    public String toString() {
        return "User{username='" + getUsername() + "', email='" + email + "'}";
    }
}
