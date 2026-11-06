package com.hardwarehub.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.config.location=classpath:/application-test.properties")
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
    }

}
