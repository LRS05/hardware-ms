package com.hardwarehub.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "jwt.secret-key=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
class ApiGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
