package com.hardwarehub.user_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.hardwarehub.user_service.config.PostgresTestConfig;

@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
@SpringBootTest(properties = "spring.config.location=classpath:/application-test.properties")
class UserServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
