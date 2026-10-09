package com.example.mediavault.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BootstrapRunner implements ApplicationRunner {
    private final UserService users;
    private final String username,password;
    public BootstrapRunner(UserService users,@Value("${vault.bootstrap.username}") String username,@Value("${vault.bootstrap.password}") String password) {
        this.users=users;this.username=username;this.password=password;
    }
    public void run(ApplicationArguments args) { users.bootstrap(username,password); }
}
