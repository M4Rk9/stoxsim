package com.stoxsim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class StoxSimApplication {

    public static void main(String[] args) {
        SpringApplication.run(StoxSimApplication.class, args);
    }
}
