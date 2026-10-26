package com.hardwarehub.product_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.hardwarehub.product_service.config.PostgresTestConfig;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
class ProductServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
