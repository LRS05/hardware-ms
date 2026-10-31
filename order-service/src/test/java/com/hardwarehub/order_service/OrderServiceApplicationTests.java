package com.hardwarehub.order_service;

import com.hardwarehub.order_service.config.PostgresTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "jwt.secret-key=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
})
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
class OrderServiceApplicationTests
{
    @Test
    void contextLoads()
    {
    }
}
