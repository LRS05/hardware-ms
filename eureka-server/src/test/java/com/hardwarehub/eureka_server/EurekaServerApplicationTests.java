package com.hardwarehub.eureka_server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.config.location=classpath:/application-test.properties")
class EurekaServerApplicationTests {

	@Test
	void contextLoads() {
	}

}
