package com.ctms.ctms_backend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Disabled("Requires Docker for Testcontainers Postgres; unavailable in this org's environment")
class CtmsBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
