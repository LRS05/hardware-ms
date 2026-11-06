package com.hardwarehub.config_server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles({"test", "native"})
@SpringBootTest
class ConfigServerApplicationTests {

	@Test
	void contextLoads() {
	}

}
