package com.test.learningtx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class LearningTxApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearningTxApplication.class, args);
    }

}
