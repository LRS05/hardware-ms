package com.hardwarehub.payment_service;

import com.hardwarehub.payment_service.config.PostgresTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
@SpringBootTest(properties = "spring.config.location=classpath:/application-test.properties")
class PaymentServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
