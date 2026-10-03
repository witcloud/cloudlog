package com.ayun;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CloudLog 项目启动类
 */
@EnableScheduling
@EnableAsync
@SpringBootApplication
public class CloudLogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudLogApplication.class, args);
    }

}
