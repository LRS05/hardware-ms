package com.hardwarehub.order_service;

import com.hardwarehub.order_service.config.PostgresTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
@SpringBootTest(properties = "spring.config.location=classpath:/application-test.properties")
class OrderServiceApplicationTests
{
    @Test
    void contextLoads()
    {
    }
}
