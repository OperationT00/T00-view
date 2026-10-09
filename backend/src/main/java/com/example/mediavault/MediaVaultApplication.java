package com.example.mediavault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.context.annotation.Import(com.example.pikpak.web.PikPakConfiguration.class)
public class MediaVaultApplication {
    public static void main(String[] args) { SpringApplication.run(MediaVaultApplication.class, args); }
}
