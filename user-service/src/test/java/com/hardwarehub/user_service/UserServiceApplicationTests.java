package com.hardwarehub.user_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.hardwarehub.user_service.config.PostgresTestConfig;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
class UserServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
