package com.example.async;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AsyncSseDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AsyncSseDemoApplication.class, args);
    }
} 