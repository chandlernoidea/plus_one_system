package com.nail.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NailApplication {

    public static void main(String[] args) {
        SpringApplication.run(NailApplication.class, args);
    }
}
