package com.example.job.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.example.job")
@EntityScan(basePackages = "com.example.job.common.entity")
@EnableJpaRepositories(basePackages = "com.example.job.common.repository")
@EnableScheduling
public class JobApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobApiApplication.class, args);
    }
}
