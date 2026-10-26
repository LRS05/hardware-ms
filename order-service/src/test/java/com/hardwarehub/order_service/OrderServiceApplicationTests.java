package com.hardwarehub.order_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.hardwarehub.order_service.config.PostgresTestConfig;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "jwt.secret-key=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
})
@Import(PostgresTestConfig.class)
class OrderServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
